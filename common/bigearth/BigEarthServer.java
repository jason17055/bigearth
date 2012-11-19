package bigearth;

import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.mortbay.jetty.*;
import org.mortbay.jetty.servlet.*;

public class BigEarthServer
{
	File worldPath;
	String hostName;

	WorldConfig worldConfig;
	WorldMaster world;
	Properties hostConfig;

	/**
	 * Keep a reference to the node lock file so that the GC won't
	 * discard it, causing it to be unlocked.
	 */
	RandomAccessFile hostLockFile;

	BigEarthServer()
	{
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
		context.addServlet(new ServletHolder(new TestServlet()), "/hello");

		server.start();
		server.join();
	}
}

class TestServlet extends HttpServlet
{
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException
	{
		response.setContentType("text/html;charset=utf-8");
		response.setStatus(HttpServletResponse.SC_OK);
		response.getWriter().println("<h1>Hello World</h1>");
	}
	
}
