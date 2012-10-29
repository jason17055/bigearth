package bigearth;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;

public class StatusServlet extends HttpServlet
{
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
		throws ServletException, IOException
	{
		response.setContentType("text/html");
		response.setCharacterEncoding("UTF-8");
		PrintWriter out = response.getWriter();
		out.println("Hello world!");
		out.close();
	}
}	
