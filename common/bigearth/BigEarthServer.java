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

		server.start();
		server.join();
	}

	Map<String, Session> sessions = new HashMap<String,Session>();
	String newSession(String user)
	{
		String sid = new BigInteger(128, random).toString(16);
		Session sess = new Session(user);
		sessions.put(sid, sess);
		return sid;
	}
}

class Session
{
	String user;

	public Session(String user)
	{
		this.user = user;
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
		//TODO - check that the user account is valid

		LeaderInfo leader = server.world.getLeaderByUsername(user);
		if (leader != null)
		{
			if (leader.checkPassword(request.getParameter("password")))
			{
				doLoginSuccess(response, user);
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

	private void doLoginSuccess(HttpServletResponse response, String user)
		throws IOException, ServletException
	{
		String sid = server.newSession(user);
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
