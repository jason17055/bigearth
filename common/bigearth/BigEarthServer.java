package bigearth;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.mortbay.jetty.*;
import org.mortbay.jetty.servlet.*;

public class BigEarthServer
{
	public static void main(String [] args)
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
