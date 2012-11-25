package bigearth;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import com.fasterxml.jackson.core.*;

public class Client
{
	String host;
	int port;
	String user;
	String pass;
	Map<String, String> cookies = new HashMap<String,String>();
	WorldStub world;
	MapModel map;
	MyListenerThread listenerThread;

	static final int DEFAULT_PORT = 2626;

	public Client(String host, String user, String pass)
	{
		int colon = host.lastIndexOf(':');
		if (colon == -1)
		{
			this.host = host;
			this.port = DEFAULT_PORT;
		}
		else
		{
			this.host = host.substring(0, colon);
			this.port = Integer.parseInt(host.substring(colon+1));
		}
		this.user = user;
		this.pass = pass;
	}

	public Client(String host, String user, char [] pass)
	{
		this(host, user, new String(pass));
	}

	private void addCookiesToRequest(HttpURLConnection conn)
	{
		//
		// make Cookie header
		//
		StringBuilder sb = new StringBuilder();
		for (String cookieName : cookies.keySet())
		{
			if (sb.length() != 0)
			{
				sb.append("; ");
			}
			sb.append(cookieName);
			sb.append("=");
			sb.append(cookies.get(cookieName));
		}
		if (sb.length() != 0)
		{
			conn.setRequestProperty("Cookie", sb.toString());
		}
	}

	HttpURLConnection makeRequest(String method, String path)
		throws IOException
	{
		assert path.startsWith("/");

		URL url = new URL("http", host, port, path);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod(method);

		addCookiesToRequest(conn);

		return conn;
	}

	Geometry getGeometry()
	{
		return world.geometry;
	}

	MapModel getMap()
		throws IOException
	{
		HttpURLConnection conn = makeRequest("GET", "/my/map");
		conn.setDoOutput(false);
		conn.setDoInput(true);
		conn.connect();

		map = new MapModel(getGeometry());

		JsonParser in = new JsonFactory().createJsonParser(
					conn.getInputStream()
				);
		in.nextToken();
		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			Location loc = LocationHelper.parse(s, getGeometry());
			RegionProfile p = RegionProfile.parse_s(in, world);
			map.put(loc, p);
		}
		in.close();

		return map;
	}

	MobListModel getMyMobs()
		throws IOException
	{
		HttpURLConnection conn = makeRequest("GET", "/my/mobs");
		conn.setDoOutput(false);
		conn.setDoInput(true);
		conn.connect();

		MobListModel model = new MobListModel();
		JsonParser in = new JsonFactory().createJsonParser(
					conn.getInputStream()
				);
		model.parse(in, world);
		in.close();

		return model;
	}

	void login()
		throws IOException, LoginFailedException
	{
		HttpURLConnection conn = makeRequest("POST", "/login");
		conn.setDoOutput(true);
		conn.setDoInput(true);

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

		world = new WorldStub();
		JsonParser in = new JsonFactory().createJsonParser(
					conn.getInputStream()
				);
		in.nextToken();
		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("year"))
				world.year = in.nextIntValue(0);
			else if (s.equals("geometry"))
				world.geometry = GeometryFactory.getInstance(in.nextTextValue());
		}
		in.close();
	}

	int nextEventNumber = 0;
	void getEvents()
		throws IOException
	{
		HttpURLConnection conn = makeRequest("GET", "/events/"+nextEventNumber);
		conn.setDoOutput(false);
		conn.setDoInput(true);
		conn.connect();

		JsonParser in = new JsonFactory().createJsonParser(
				conn.getInputStream()
			);
		in.nextToken();
		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("next"))
			{
				nextEventNumber = in.nextIntValue(0);
			}
			else if (s.equals("event"))
			{
				in.nextToken();
				assert in.getCurrentToken() == JsonToken.START_OBJECT;

				Notification n = Notification.parse_1(in, world);
				fireNotification(n);
			}
			else if (s.equals("events"))
			{
				parseEvents(in);
			}
		}
		in.close();
	}

	void parseEvents(JsonParser in)
		throws IOException
	{
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_ARRAY;

		while (in.nextToken() != JsonToken.END_ARRAY)
		{
			Notification n = Notification.parse_1(in, world);
			fireNotification(n);
		}

		assert in.getCurrentToken() == JsonToken.END_ARRAY;
	}

	void fireNotification(Notification n)
	{
		//TODO

		System.out.println("notification "+n);
	}

	public void moveMobTo(String mobName, Location dest)
		throws IOException
	{
		HttpURLConnection conn = makeRequest("POST", "/move");
		conn.setDoOutput(true);
		conn.setDoInput(true);

		String x = "mob=" + URLEncoder.encode(mobName, "UTF-8")
			+ "&dest=" + URLEncoder.encode(dest.toString(), "UTF-8");
		byte [] xx = x.getBytes("UTF-8");

		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		conn.setRequestProperty("Content-Length", Integer.toString(xx.length));
		OutputStream out = conn.getOutputStream();
		out.write(xx);
		out.close();

		int status = conn.getResponseCode();
		assert status == 204;
	}

	void startListenerThread()
	{
		listenerThread = new MyListenerThread();
		listenerThread.start();
	}

	class MyListenerThread extends Thread
	{
		public void run()
		{
			try
			{
				while (true)
					getEvents();
			}
			catch (IOException e)
			{
				e.printStackTrace(System.err);
			}
		}
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
		me.startListenerThread();

		MapModel map = me.getMap();
		MobListModel myMobs = me.getMyMobs();

		MainWindow w = new MainWindow(me);
		w.setMap(map);
		w.setMobList(myMobs);

		w.setVisible(true);

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

class WorldStub implements WorldConfigIfc
{
	int year;
	Geometry geometry;

	// implements WorldConfigIfc
	public Geometry getGeometry()
	{
		return geometry;
	}
}
