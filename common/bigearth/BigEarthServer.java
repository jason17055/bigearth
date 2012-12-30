package bigearth;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.security.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.mortbay.jetty.*;
import org.mortbay.jetty.servlet.*;
import com.fasterxml.jackson.core.*;

public class BigEarthServer
{
	File worldPath;
	String hostName;

	WorldConfig worldConfig;
	WorldMaster world;
	Properties hostConfig;

	AdvertiserThread advertiser;

	SecureRandom random;

	static final String COOKIE_NAME = "sid";

	/**
	 * Keep a reference to the node lock file so that the GC won't
	 * discard it, causing it to be unlocked.
	 */
	RandomAccessFile hostLockFile;

	BigEarthServer()
	{
		random = new SecureRandom();
	}

	public static void main(String [] args)
		throws Exception
	{
		BigEarthServer be = new BigEarthServer();

		for (int i = 0; i < args.length; i++)
		{
			String [] parts = args[i].split("=", 2);
			if (parts[0].equals("world"))
			{
				be.worldPath = new File(parts[1]);
			}
			else if (parts[0].equals("hostname"))
			{
				be.hostName = parts[1];
			}
			else
			{
				System.err.println("Invalid argument: "+parts[0]);
				System.err.println("Usage: $0 world=blah hostname=foo");
				System.exit(1);
			}
		}

		be.load();
		be.start();

		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		in.readLine();

		System.out.println("Shutting down");
		be.stop();
		be.world.saveAll();
	}

	void load()
		throws IOException
	{
		if (worldPath == null)
		{
			System.err.println("World not specified");
			System.exit(1);
		}

		if (!worldPath.exists())
		{
			System.err.println(worldPath + ": does not exist");
			System.exit(1);
		}

		worldConfig = WorldConfig.load(worldPath);


		loadNodeConfiguration();
		loadWorld();
	}

	void loadNodeConfiguration()
		throws IOException
	{
		if (hostName == null)
		{
			// determine a default node name
			java.net.InetAddress localMachine = java.net.InetAddress.getLocalHost();
			hostName = localMachine.getHostName();
		}

		// require the node name to be listed in the world properties file
		if (!worldConfig.isValidHost(hostName))
		{
			System.err.println(hostName + " is not a valid host for this world");
			System.exit(1);
		}

		String effNodeName = hostName.toLowerCase().replace('.', '_');
		if (!effNodeName.matches("[a-z0-9_-]+"))
		{
			System.err.println("Invalid node name");
			System.exit(1);
		}

		hostConfig = new Properties(worldConfig.properties);

		File cfgFilename = new File(worldPath, "node_"+effNodeName+".config");
		if (cfgFilename.exists())
		{
			FileInputStream in = new FileInputStream(cfgFilename);
			hostConfig.load(in);
			in.close();
		}

		File lockFilename = new File(worldPath, "node_"+effNodeName+".lockfile");
		hostLockFile = new RandomAccessFile(lockFilename, "rw");
		if (hostLockFile.getChannel().tryLock() == null)
		{
			System.err.println("File locked! Another process is already running with this node name");
			System.exit(1);
		}
	}

	void loadWorld()
		throws IOException
	{
		world = new WorldMaster(worldConfig);
		world.load();
	}

	Server httpServer;
	void start()
		throws Exception
	{
		int portNumber = Integer.parseInt(hostConfig.getProperty("http.port", "2626"));
		httpServer = new Server(portNumber);

		Context context = new Context(httpServer, "/", Context.SESSIONS);
		context.addServlet(new ServletHolder(new LoginServlet(this)), "/login");
		context.addServlet(new ServletHolder(new GetCityServlet(this)), "/city");
		context.addServlet(new ServletHolder(new GetEventsServlet(this)), "/events");
		context.addServlet(new ServletHolder(new GetMapServlet(this)), "/my/map");
		context.addServlet(new ServletHolder(new GetMobsServlet(this)), "/mobs");
		context.addServlet(new ServletHolder(new MobOrdersServlet(this)), "/mob/orders");

		httpServer.start();
		world.start();

		advertiser = new AdvertiserThread();
		advertiser.start();
	}

	void stop()
		throws Exception
	{
		advertiser.requestStop();
		advertiser.join();
		httpServer.stop();
		world.stop();
	}

	Map<String, Session> sessions = new HashMap<String,Session>();
	String newSession(String user)
	{
		String sid = new BigInteger(128, random).toString(16);
		Session sess = new Session(user);
		sess.notificationStream = new NotificationStream();
		sessions.put(sid, sess);
		return sid;
	}

	Session getSessionFromRequest(HttpServletRequest request)
	{
		for (Cookie c : request.getCookies())
		{
			if (c.getName().equals(COOKIE_NAME))
			{
				String sid = c.getValue();
				return sessions.get(sid);
			}
		}
		return null;
	}

	private void doAdvertisement()
	{
		class AdvertisementInfo
		{
			long population;
			int year;
		}
		final AdvertisementInfo info = new AdvertisementInfo();
		world.invokeAndWait(new Runnable() {
		public void run()
		{
			info.year = world.year;
			info.population = world.getTotalPopulation();
		}
		});

		try
		{

		URL url = new URL("http://jason.long.name/bigearth/server-api/world.php");
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);

		String x = "url="+URLEncoder.encode(System.getenv("HOSTNAME"),"UTF-8")
			+"&secret=x"
			+"&size="+world.getGeometry().getCellCount()
			+"&population="+info.population
			+"&year="+info.year
			+"&year_real_world_duration="+world.config.ticksPerYear
			;
		byte [] xx = x.getBytes("UTF-8");

		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		conn.setRequestProperty("Content-Length", Integer.toString(xx.length));

		OutputStream out = conn.getOutputStream();
		out.write(xx);
		out.close();

		conn.connect();
		if (conn.getResponseCode() != 200)
		{

		System.out.println("ADVERTISEMENT- got "+conn.getResponseCode());

		InputStream in = conn.getInputStream();
		for (;;)
		{
			byte [] buf = new byte[100];
			int nread = in.read(buf);
			if (nread <= 0) break;

			System.out.write(buf, 0, nread);
		}

		}

		} catch (IOException e)
		{
			System.err.println("Warning: Unable to advertise");
			e.printStackTrace(System.err);
		}
	}

	class AdvertiserThread extends Thread
		implements Stoppable
	{
		boolean stopRequested = false;
		final static long INTERVAL = 5*60*1000; //five minutes

		synchronized boolean isStopRequested()
		{
			return stopRequested;
		}

		public void run()
		{
			while (!isStopRequested())
			{
				doAdvertisement();
				try
				{
				Thread.sleep(INTERVAL);
				}
				catch (InterruptedException e)
				{
				}
			}
		}

		//implements Stoppable
		public synchronized void requestStop()
		{
			stopRequested = true;
			interrupt();
		}
	}
}

class Session
{
	String user;
	NotificationStream notificationStream;

	public Session(String user)
	{
		this.user = user;
	}
}

abstract class BigEarthServlet extends HttpServlet
{
	protected BigEarthServer server;
	protected BigEarthServlet(BigEarthServer server)
	{
		this.server = server;
	}

	protected void doFailure(HttpServletResponse response, int statusCode, String errorMessage)
		throws IOException
	{
		response.setStatus(statusCode);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");

		JsonGenerator out = new JsonFactory().createJsonGenerator(
				response.getOutputStream(),
				JsonEncoding.UTF8);
		out.writeStartObject();
		out.writeStringField("error", errorMessage);
		out.writeEndObject();
		out.close();
	}

	void doSuccessNoContent(HttpServletResponse response)
		throws IOException
	{
		response.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}

	protected Session checkSession(HttpServletRequest request, HttpServletResponse response)
		throws IOException
	{
		Session s = server.getSessionFromRequest(request);
		if (s == null)
		{
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");

			JsonGenerator out = new JsonFactory().createJsonGenerator(
					response.getOutputStream(),
					JsonEncoding.UTF8);
			out.writeStartObject();
			out.writeStringField("error", "not a valid session");
			out.writeEndObject();
			out.close();
		}
		return s;
	}
}

class GetMobsServlet extends BigEarthServlet
{
	GetMobsServlet(BigEarthServer server)
	{
		super(server);
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException
	{
		Session s = checkSession(request, response);
		if (s == null)
			return;

		WorldMaster.RealTimeLockHack lock = server.world.acquireRealTimeLock();
		try
		{

		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");

		JsonGenerator out = new JsonFactory().createJsonGenerator(
				response.getOutputStream(),
				JsonEncoding.UTF8);
		out.writeStartObject();

		for (String mobName : server.world.mobs.keySet())
		{
			MobServant mob = server.world.getMob(mobName);
			if (mob.owner != null && mob.owner.equals(s.user))
			{
				MobInfo mobProfile = mob.makeProfileForOwner();
				out.writeFieldName(mobName);
				mobProfile.write(out);
			}
			else if (mob.isSeenBy(s.user))
			{
				MobInfo mobProfile = mob.makeProfileForObserver();
				out.writeFieldName(mobName);
				mobProfile.write(out);
			}
		}

		out.writeEndObject();
		out.close();

		}
		finally
		{
			lock.release();
		}
	}
}

class GetEventsServlet extends BigEarthServlet
{
	GetEventsServlet(BigEarthServer server)
	{
		super(server);
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException
	{
		Session s = checkSession(request, response);
		if (s == null)
			return;

		LeaderInfo leader = server.world.leaders.get(s.user);
		assert leader != null;

		String fromStr = request.getParameter("from");
		int eventNumber = Integer.parseInt(fromStr);

		Notification [] events;
		try
		{
			events = s.notificationStream.consumeFrom(eventNumber);
		}
		catch (InterruptedException e)
		{
			events = new Notification[0];
		}
		catch (NotificationStream.OutOfSyncException e)
		{
			doInvalidArgumentFailure(response);
			return;
		}

		if (events.length == 0)
		{
			response.setStatus(HttpServletResponse.SC_NO_CONTENT);
			return;
		}

		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");

		JsonGenerator out = new JsonFactory().createJsonGenerator(
				response.getOutputStream(),
				JsonEncoding.UTF8);
		out.writeStartObject();
		out.writeNumberField("next", eventNumber + events.length);
		out.writeFieldName("events");
		out.writeStartArray();
		for (Notification n : events)
		{
			n.write(out);
		}
		out.writeEndArray();
		out.writeEndObject();
		out.close();
	}

	void doInvalidArgumentFailure(HttpServletResponse response)
		throws IOException
	{
		response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");

		JsonGenerator out = new JsonFactory().createJsonGenerator(
				response.getOutputStream(),
				JsonEncoding.UTF8);
		out.writeStartObject();
		out.writeStringField("error", "Invalid argument");
		out.writeEndObject();
		out.close();
	}
}

class GetCityServlet extends BigEarthServlet
{
	GetCityServlet(BigEarthServer server)
	{
		super(server);
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException
	{
		final Session s = checkSession(request, response);
		if (s == null)
			return;

		String locStr = request.getParameter("location");
		final Location cityLocation = LocationHelper.parse(locStr, server.world.config);

		class ResultInfo
		{
			int errorCode = 0;
			String errorMessage;
			CityInfo cityData;
		}
		final ResultInfo result = new ResultInfo();

		server.world.invokeAndWait(new Runnable() {
		public void run()
		{

		assert server.world.leaders.containsKey(s.user);

		CityServant city = server.world.getCity(cityLocation);
		if (city == null)
		{
			result.errorCode = HttpServletResponse.SC_NOT_FOUND;
			result.errorMessage = "Invalid city";
			return;
		}

		result.cityData = city.makeProfileFor(s.user);

		}
		});

		if (result.errorCode != 0)
		{
			doFailure(response, result.errorCode, result.errorMessage);
			return;
		}

		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");

		JsonGenerator out = new JsonFactory().createJsonGenerator(
				response.getOutputStream(),
				JsonEncoding.UTF8);
		result.cityData.write(out);
		out.close();
	}

	private Command parseOrders(HttpServletRequest request)
		throws IOException
	{
		JsonParser in = new JsonFactory().createJsonParser(request.getInputStream());
		Command orders = Command.parse(in, server.world.config);
		in.close();
		return orders;
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response)
		throws IOException
	{
		final Session s = checkSession(request, response);
		if (s == null)
			return;

		String locStr = request.getParameter("location");
		final Location cityLocation = LocationHelper.parse(locStr, server.world.config);
		final Command orders = parseOrders(request);

		class ResultInfo
		{
			int errorCode = 0;
			String errorMessage;
		}
		final ResultInfo result = new ResultInfo();

		server.world.invokeAndWait(new Runnable() {
		public void run()
		{

		CityServant city = server.world.getCity(cityLocation);
		if (city == null)
		{
			result.errorCode = HttpServletResponse.SC_NOT_FOUND;
			result.errorMessage = "Invalid city";
			return;
		}

		if (!city.canUserCommand(s.user))
		{
			result.errorCode = HttpServletResponse.SC_FORBIDDEN;
			result.errorMessage = "Forbidden";
			return;
		}

		// make the actual change
		city.setOrders(orders);
		}
		});

		if (result.errorCode != 0)
		{
			doFailure(response, result.errorCode, result.errorMessage);
			return;
		}

		// report success
		doSuccessNoContent(response);
	}
}

class GetMapServlet extends BigEarthServlet
{
	GetMapServlet(BigEarthServer server)
	{
		super(server);
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException
	{
		Session s = checkSession(request, response);
		if (s == null)
			return;

		WorldMaster.RealTimeLockHack lock = server.world.acquireRealTimeLock();
		try
		{

		assert server.world.leaders.containsKey(s.user);

		MapModel map = server.world.leaders.get(s.user).map;
		assert map != null;

		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");

		JsonGenerator out = new JsonFactory().createJsonGenerator(
				response.getOutputStream(),
				JsonEncoding.UTF8);
		map.write(out);
		out.close();

		}
		finally
		{
			lock.release();
		}
	}
}

class MobOrdersServlet extends BigEarthServlet
{
	MobOrdersServlet(BigEarthServer server)
	{
		super(server);
	}

	private Command parseOrders(HttpServletRequest request)
		throws IOException
	{
		JsonParser in = new JsonFactory().createJsonParser(request.getInputStream());
		Command orders = Command.parse(in, server.world.config);
		in.close();

		return orders;
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException
	{
		final Session s = checkSession(request, response);
		if (s == null)
			return;

		final String mobName = request.getParameter("mob");
		final Command orders = parseOrders(request);

		class ResultInfo
		{
			int errorCode = 0;
			String errorMessage;
		}
		final ResultInfo result = new ResultInfo();

		server.world.invokeAndWait(new Runnable() {
		public void run()
		{

		// check that the user is authorized to control the specified mob

		MobServant mob = server.world.getMob(mobName);
		if (mob == null)
		{
			result.errorCode = HttpServletResponse.SC_NOT_FOUND;
			result.errorMessage = "Invalid mob";
			return;
		}

		if (!mob.canUserCommand(s.user))
		{
			result.errorCode = HttpServletResponse.SC_FORBIDDEN;
			result.errorMessage = "No permission to control this mob";
			return;
		}

		// make the actual change
		mob.setOrders(orders);

		}
		});

		if (result.errorCode != 0)
		{
			doFailure(response, result.errorCode, result.errorMessage);
			return;
		}

		// report success
		doSuccessNoContent(response);
	}
}

class LoginServlet extends HttpServlet
{
	BigEarthServer server;
	LoginServlet(BigEarthServer server)
	{
		this.server = server;
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException
	{
		String user = request.getParameter("user");

		// check that the user account is valid

		LeaderInfo leader = server.world.getLeaderByUsername(user);
		if (leader != null)
		{
			if (leader.checkPassword(request.getParameter("password")))
			{
				doLoginSuccess(response, user, leader);
				return;
			}
		}

		doLoginFailure(response, "Invalid username or password.");
	}

	private void doLoginFailure(HttpServletResponse response, String errorMessage)
		throws IOException
	{
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");

		JsonGenerator out = new JsonFactory().createJsonGenerator(
				response.getOutputStream(),
				JsonEncoding.UTF8);
		out.writeStartObject();
		out.writeStringField("error", errorMessage);
		out.writeEndObject();
		out.close();
	}

	private void doLoginSuccess(HttpServletResponse response, String user, LeaderInfo leader)
		throws IOException, ServletException
	{
		WorldMaster.RealTimeLockHack lock = server.world.acquireRealTimeLock();
		try
		{

		String sid = server.newSession(user);
		Session s = server.sessions.get(sid);
		leader.streams.add(s.notificationStream);

		Cookie sessionCookie = new Cookie(BigEarthServer.COOKIE_NAME, sid);
		response.addCookie(sessionCookie);

		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");

		JsonGenerator out = new JsonFactory().createJsonGenerator(
				response.getOutputStream(),
				JsonEncoding.UTF8);
		out.writeStartObject();
		out.writeNumberField("year", server.world.year);
		out.writeNumberField("gameTime", lock.time);
		out.writeStringField("geometry", server.world.getGeometry().toString());
		out.writeNumberField("ticksPerYear", server.world.config.getTicksPerYear());
		out.writeEndObject();
		out.close();

		}
		finally
		{
			lock.release();
		}
	}
}
