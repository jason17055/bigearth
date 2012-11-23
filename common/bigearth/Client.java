package bigearth;

import java.io.*;
import java.net.*;
import javax.swing.*;

public class Client
{
	String host;
	String user;
	String pass;

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
		throws IOException
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
		conn.setRequestMethod("POST");
		OutputStream out = conn.getOutputStream();
		String x = URLEncoder.encode(user, "UTF-8")
			+ "&" + URLEncoder.encode(pass, "UTF-8");
		out.write(x.getBytes("UTF-8"));
		out.close();

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

		Client me = new Client(hostField.getText(),
				userField.getText(),
				passField.getPassword());
		me.login();
	}
}
