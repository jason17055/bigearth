package bigearth;

import java.io.*;
import java.math.BigInteger;
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
			System.err.println("Not a valid host for this world");
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

	void start()
		throws Exception
	{
		int portNumber = Integer.parseInt(hostConfig.getProperty("http.port", "2626"));
		Server server = new Server(portNumber);

		Context context = new Context(server, "/", Context.SESSIONS);
		context.addServlet(new ServletHolder(new LoginServlet(this)), "/login");
		context.addServlet(new ServletHolder(new GetEventsServlet(this)), "/events");
		context.addServlet(new ServletHolder(new GetMapServlet(this)), "/my/map");
		context.addServlet(new ServletHolder(new GetMyMobsServlet(this)), "/my/mobs");
		context.addServlet(new ServletHolder(new MoveMobServlet(this)), "/move");

		server.start();
		world.start();
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

class GetMyMobsServlet extends HttpServlet
{
	BigEarthServer server;
	GetMyMobsServlet(BigEarthServer server)
	{
		this.server = server;
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException
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
			return;
		}

		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");

		JsonGenerator out = new JsonFactory().createJsonGenerator(
				response.getOutputStream(),
				JsonEncoding.UTF8);
		out.writeStartObject();

		for (String mobName : server.world.mobs.keySet())
		{
			MobInfo mob = server.world.mobs.get(mobName);
			if (mob.owner != null && mob.owner.equals(s.user))
			{
				out.writeFieldName(mobName);
				out.writeStartObject();
				out.writeStringField("avatarName", mob.avatarName);
				out.writeStringField("location", mob.location.toString());
				out.writeEndObject();
			}
		}

		out.writeEndObject();
		out.close();
	}
}

class GetEventsServlet extends HttpServlet
{
	BigEarthServer server;
	GetEventsServlet(BigEarthServer server)
	{
		this.server = server;
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException
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
			return;
		}

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

class GetMapServlet extends HttpServlet
{
	BigEarthServer server;
	GetMapServlet(BigEarthServer server)
	{
		this.server = server;
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException
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
			return;
		}

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
}

class MoveMobServlet extends HttpServlet
{
	BigEarthServer server;
	MoveMobServlet(BigEarthServer server)
	{
		this.server = server;
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException
	{
		Session s = server.getSessionFromRequest(request);
		if (s == null)
		{
			doFailure(response, "Not logged in");
			return;
		}

		String mobName = request.getParameter("mob");
		String destStr = request.getParameter("dest");
		Location dest = LocationHelper.parse(destStr, server.world.config);

		WorldMaster.RealTimeLockHack lock = server.world.acquireRealTimeLock();
		try
		{

		// check that the user is authorized to control the specified mob

		MobInfo mob = server.world.mobs.get(mobName);
		if (mob == null)
		{
			doFailure(response, "Invalid mob");
			return;
		}

		if (mob.owner == null || !mob.owner.equals(s.user))
		{
			doFailure(response, "Not authorized");
			return;
		}

		// make the actual change
		server.world.requestMovement(mobName, dest);

		// report success
		response.setStatus(HttpServletResponse.SC_NO_CONTENT);

		}
		finally
		{
			lock.release();
		}
	}

	private void doFailure(HttpServletResponse response, String errorMessage)
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
		out.writeStringField("geometry", server.world.getGeometry().toString());
		out.writeEndObject();
		out.close();
	}
}
