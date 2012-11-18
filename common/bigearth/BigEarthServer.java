package bigearth;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.mortbay.jetty.*;
import org.mortbay.jetty.servlet.*;

public class BigEarthServer
{
	File worldPath;
	String nodeName;
	RandomAccessFile nodeConfigurationFile;

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
			else if (parts[0].equals("node"))
			{
				be.nodeName = parts[1];
			}
			else
			{
				System.err.println("Invalid argument: "+parts[0]);
				System.err.println("Usage: $0 world=blah node=foo");
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

		loadNodeConfiguration();
		loadWorld();
	}

	void loadNodeConfiguration()
		throws IOException
	{
		if (nodeName == null)
		{
			// determine a default node name
			java.net.InetAddress localMachine = java.net.InetAddress.getLocalHost();
			nodeName = localMachine.getHostName();
		}

		String effNodeName = nodeName.toLowerCase().replace('.', '_');
		if (!effNodeName.matches("[a-z0-9_-]+"))
		{
			System.err.println("Invalid node name");
			System.exit(1);
		}

		File filename = new File(worldPath, "node_"+effNodeName+".config");
		if (!filename.exists())
		{
			System.err.println(filename + ": not found");
			System.exit(1);
		}

		nodeConfigurationFile = new RandomAccessFile(filename, "rw");
		if (nodeConfigurationFile.getChannel().tryLock() == null)
		{
			System.err.println("File locked! Another process is already running with this node name");
			System.exit(1);
		}
	}

	void loadWorld()
		throws IOException
	{
		//TODO
	}

	void start()
		throws Exception
	{
		Server server = new Server(2626);

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
