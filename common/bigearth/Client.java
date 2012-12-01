package bigearth;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import com.fasterxml.jackson.core.*;

public class Client
	implements Notification.Receiver
{
	String host;
	int port;
	String user;
	String pass;
	Map<String, String> cookies = new HashMap<String,String>();
	WorldStub world;
	MapModel map;
	MobListModel mobs;
	MyListenerThread listenerThread;
	ArrayList<Listener> listeners = new ArrayList<Listener>();

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

	public void addListener(Listener l)
	{
		if (listeners.isEmpty())
		{
			startListenerThread();
		}

		listeners.add(l);
	}

	public void removeListener(Listener l)
	{
		listeners.remove(l);

		if (listeners.isEmpty())
		{
			stopListenerThread();
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

		JsonParser in = new JsonFactory().createJsonParser(
					conn.getInputStream()
				);

		map = new MapModel(getGeometry());
		map.parse(in, world);

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

		JsonParser in = new JsonFactory().createJsonParser(
					conn.getInputStream()
				);

		mobs = new MobListModel();
		mobs.parse(in, world);
		in.close();

		return mobs;
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
		HttpURLConnection conn = makeRequest("GET", "/events?from="+nextEventNumber);
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
				handleNotification(n);
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
			handleNotification(n);
		}

		assert in.getCurrentToken() == JsonToken.END_ARRAY;
	}

	//implements Notification.Receiver
	public void handleMobChangeNotification(MobChangeNotification n)
	{
		if (mobs == null)
			return;

		mobs.update(n.mobName, n.mobData);
	}

	//implements Notification.Receiver
	public void handleMapUpdateNotification(MapUpdateNotification n)
	{
		if (map == null)
			return;

		Location loc = n.getLocation();
		map.put(loc, n.profile);
	}

	void handleNotification(Notification n)
	{
		n.dispatch(this);
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

	void stopListenerThread()
	{
System.out.println("in stopListenerThread()");
		listenerThread.requestStop();
	}

	class MyListenerThread extends Thread
	{
		boolean stopFlag = false;

		public void run()
		{
			try
			{
				while (!isStopFlagSet())
					getEvents();
			}
			catch (IOException e)
			{
				e.printStackTrace(System.err);
			}
			System.out.println("MyListenerThread terminated");
		}

		synchronized boolean isStopFlagSet()
		{
			return stopFlag;
		}

		synchronized void requestStop()
		{
			stopFlag = true;

			//TODO- close the http socket
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

	interface Listener
	{
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
