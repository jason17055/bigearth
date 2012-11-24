package bigearth;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

public class Client
{
	String host;
	String user;
	String pass;
	Map<String, String> cookies = new HashMap<String,String>();

	static final int DEFAULT_PORT = 2626;

	public Client(String host, String user, String pass)
	{
		this.host = host;
		this.user = user;
		this.pass = pass;
	}

	public Client(String host, String user, char [] pass)
	{
		this(host, user, new String(pass));
	}

	void login()
		throws IOException, LoginFailedException
	{
		String myHost;
		int port;

		int colonIdx = host.indexOf(':');
		if (colonIdx == -1)
		{
			myHost = host;
			port = DEFAULT_PORT;
		}
		else
		{
			myHost = host.substring(0, colonIdx);
			port = Integer.parseInt(host.substring(colonIdx+1));
		}

		URL url = new URL("http", myHost, port, "/login");
		System.out.println(url);

		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		conn.setDoOutput(true);
		conn.setDoInput(true);
		conn.setRequestMethod("POST");
		String x = "user=" + URLEncoder.encode(user, "UTF-8")
			+ "&password=" + URLEncoder.encode(pass, "UTF-8");
		byte [] xx = x.getBytes("UTF-8");

		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		conn.setRequestProperty("Content-Length", Integer.toString(xx.length));
		OutputStream out = conn.getOutputStream();
		out.write(xx);
		out.close();

		// read cookie from response
		String headerName = null;
		for (int i = 1; (headerName = conn.getHeaderFieldKey(i)) != null; i++)
		{
			if (headerName.equals("Set-Cookie"))
			{
				String cookie = conn.getHeaderField(i);
				String [] parts = cookie.split(";");
				String [] parts2 = parts[0].split("=");
				cookies.put(parts2[0], parts2[1]);
			}
		}

		int status = conn.getResponseCode();
		if (status == 401)
		{
			throw new LoginFailedException();
		}

		InputStream in  = conn.getInputStream();
		byte [] buf = new byte[1024];
		int nread;
		while ( (nread = in.read(buf)) != -1)
		{
			System.out.write(buf, 0, nread);
		}
		in.close();
	}

	public static void main(String [] args)
		throws Exception
	{
		JTextField hostField = new JTextField();
		hostField.setText("localhost");

		JTextField userField = new JTextField();
		JPasswordField passField = new JPasswordField();

		JComponent [] inputs = new JComponent[] {
			new JLabel("Hostname"),
			hostField,
			new JLabel("Username"),
			userField,
			new JLabel("Password"),
			passField
			};
		int rv = JOptionPane.showOptionDialog(null, inputs,
			"Login",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE, null, null, null);
		if (rv != JOptionPane.OK_OPTION)
			return;

		try
		{
		Client me = new Client(hostField.getText(),
				userField.getText(),
				passField.getPassword());
		me.login();

		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(null, e,
				"Error", JOptionPane.ERROR_MESSAGE);
		}
	}
}

class LoginFailedException extends Exception
{
}
