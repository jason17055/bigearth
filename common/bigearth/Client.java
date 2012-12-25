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

	void interpretError(HttpURLConnection conn)
		throws IOException
	{
		int status = conn.getResponseCode();
		JsonParser in = new JsonFactory().createJsonParser(
					conn.getErrorStream()
				);

		String errorMessage = null;

		in.nextToken();
		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("error"))
				errorMessage = in.nextTextValue();
			else
			{
				in.nextToken();
				in.skipChildren();
			}
		}

		in.close();

		if (errorMessage != null)
			throw new IOException(errorMessage);

		if (status == 403)
		{
			throw new IOException("Forbidden");
		}
		throw new IOException("Unknown status code: " + status);
	}

	void setCityName(Location cityLocation, String newName)
		throws IOException
	{
		RenameSelfCommand c = new RenameSelfCommand();
		c.newName = newName;
		sendCityOrders(cityLocation, c);
	}

	void sendCityOrders(Location cityLocation, Command orders)
		throws IOException
	{
		String qs = "location="+URLEncoder.encode(cityLocation.toString(), "UTF-8");
		HttpURLConnection conn = makeRequest("POST", "/city?" + qs);
		conn.setDoOutput(true);
		conn.setDoInput(true);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		{
		JsonGenerator out = new JsonFactory().createJsonGenerator(bytes, JsonEncoding.UTF8);
		orders.write(out);
		out.close();
		}

		conn.setRequestProperty("Content-Type", "application/json");
		conn.setRequestProperty("Content-Length", Integer.toString(bytes.size()));
		OutputStream out = conn.getOutputStream();
		out.write(bytes.toByteArray());
		out.close();

		int status = conn.getResponseCode();
		if (status != 204)
			interpretError(conn);
	}

	CityInfo getCity(Location cityLocation)
		throws IOException
	{
		String qs = "location="+URLEncoder.encode(cityLocation.toString(), "UTF-8");
		HttpURLConnection conn = makeRequest("GET", "/city?" + qs);
		conn.setDoOutput(false);
		conn.setDoInput(true);
		conn.connect();

		JsonParser in = new JsonFactory().createJsonParser(
					conn.getInputStream()
				);
		CityInfo city = CityInfo.parse(in, world);
		in.close();

		return city;
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
		HttpURLConnection conn = makeRequest("GET", "/mobs");
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
			else if (s.equals("ticksPerYear"))
			{
				in.nextToken();
				world.ticksPerYear = in.getLongValue();
			}
			else
			{
				System.err.println("warning: unrecognized property: " + s);
				in.nextToken();
				in.skipChildren();
			}
		}
		in.close();

		assert world.geometry != null;
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

	private void fireCityMessage(Location cityLocation, String message)
	{
		for (Listener l : listeners)
			l.cityMessage(cityLocation, message);
	}

	private void fireCityUpdate(Location cityLocation, CityInfo cityData)
	{
		for (Listener l : listeners)
			l.cityUpdated(cityLocation, cityData);
	}

	private void fireMobMessage(String mobName, String message)
	{
		for (Listener l : listeners)
			l.mobMessage(mobName, message);
	}

	//implements Notification.Receiver
	public void handleCityMessageNotification(CityMessageNotification n)
	{
		fireCityMessage(n.cityLocation, n.message);
	}

	//implements Notification.Receiver
	public void handleCityUpdateNotification(CityUpdateNotification n)
	{
		fireCityUpdate(n.cityLocation, n.cityData);
	}

	//implements Notification.Receiver
	public void handleMobChangeNotification(MobChangeNotification n)
	{
		if (mobs == null)
			return;

		mobs.update(n.mobName, n.mobData);
	}

	//implements Notification.Receiver
	public void handleMobMessageNotification(MobMessageNotification n)
	{
		fireMobMessage(n.mobName, n.message);
	}

	//implements Notification.Receiver
	public void handleMobRemoveNotification(MobRemoveNotification n)
	{
		if (mobs == null)
			return;

		mobs.remove(n.mobName, n.disposition);
	}

	//implements Notification.Receiver
	public void handleMapUpdateNotification(MapUpdateNotification n)
	{
		if (map == null)
			return;

		Location loc = n.getLocation();
		map.updateRegion(loc, n.profile);
	}

	void handleNotification(Notification n)
	{
		n.dispatch(this);
	}

	void sendMobOrders(String mobName, Command orders)
		throws IOException
	{
		String qs = "mob=" + URLEncoder.encode(mobName, "UTF-8");
		HttpURLConnection conn = makeRequest("POST", "/mob/orders?" + qs);
		conn.setDoOutput(true);
		conn.setDoInput(true);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		{
		JsonGenerator out = new JsonFactory().createJsonGenerator(bytes, JsonEncoding.UTF8);
		orders.write(out);
		out.close();
		}

		conn.setRequestProperty("Content-Type", "application/json");
		conn.setRequestProperty("Content-Length", Integer.toString(bytes.size()));
		OutputStream out = conn.getOutputStream();
		out.write(bytes.toByteArray());
		out.close();

		int status = conn.getResponseCode();
		if (status != 204)
			interpretError(conn);
	}

	public void moveMobTo(String mobName, Location dest)
		throws IOException
	{
		SimpleCommand orders = new SimpleCommand("move");
		orders.setDestination(dest);
		sendMobOrders(mobName, orders);
	}

	public void setMobFlag(String mobName, Flag flag)
		throws IOException
	{
		SimpleCommand orders = new SimpleCommand("set-flag");
		orders.flag = flag;
		sendMobOrders(mobName, orders);
	}

	public void setMobActivity(String mobName, String activityName)
		throws IOException
	{
		SimpleCommand orders = new SimpleCommand(activityName);
		sendMobOrders(mobName, orders);
	}

	public void takeCommodity(String mobName, CommodityType ct, long amt)
		throws IOException
	{
		SimpleCommand orders = new SimpleCommand("take");
		orders.commodity = ct;
		orders.amount = amt;
		sendMobOrders(mobName, orders);
	}

	public void dropCommodity(String mobName, CommodityType ct, long amt)
		throws IOException
	{
		SimpleCommand orders = new SimpleCommand("drop");
		orders.commodity = ct;
		orders.amount = amt;
		sendMobOrders(mobName, orders);
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
		implements Stoppable
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

		public synchronized void requestStop()
		{
			stopFlag = true;

			//TODO- close the http socket
		}
	}

	static String [] getServersFromMetaServer()
	{
		ArrayList<String> rv = new ArrayList<String>();

		try
		{
			URL url = new URL("http://jason.long.name/bigearth/server-api/world.php");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(10000);
			JsonParser in = new JsonFactory().createJsonParser(conn.getInputStream());
			in.nextToken();
			while (in.nextToken() != JsonToken.END_ARRAY)
			{
				String gameUrl = null;

				while (in.nextToken() != JsonToken.END_OBJECT)
				{
					String s = in.getCurrentName();
					if (s.equals("url"))
						gameUrl = in.nextTextValue();
					else
					{
						in.nextToken();
						in.skipChildren();
					}
				}

				if (gameUrl != null)
				{
					rv.add(gameUrl);
				}
			}
			in.close();

			return rv.toArray(new String[0]);
		}
		catch (IOException e)
		{
			System.err.println("Warning: " + e);
			return new String[0];
		}
	}

	public static void main(String [] args)
		throws Exception
	{
		final DefaultComboBoxModel hostChoices = new DefaultComboBoxModel();
		JComboBox hostField = new JComboBox(hostChoices);
		hostField.setEditable(true);

		SwingWorker worker = new SwingWorker<String[], Void>() {
		@Override
		public String[] doInBackground()
		{
			return getServersFromMetaServer();
		}

		@Override
		public void done()
		{
			try
			{
				String [] choices = get();
				for (String s : choices)
				{
					hostChoices.addElement(s);
				}
			}
			catch (Exception e)
			{
				//ignore
				System.err.println(e);
			}
		}
		};
		worker.execute();

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

		String hostName = (String) hostField.getSelectedItem();
		try
		{
		Client me = new Client(hostName,
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
			e.printStackTrace(System.err);
			JOptionPane.showMessageDialog(null, e,
				"Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	interface Listener
	{
		void cityMessage(Location cityLocation, String message);
		void cityUpdated(Location cityLocation, CityInfo cityData);
		void mobMessage(String mobName, String message);
	}
}

class LoginFailedException extends Exception
{
}

class WorldStub implements WorldConfigIfc
{
	int year;
	Geometry geometry;
	long ticksPerYear;

	// implements WorldConfigIfc
	public Geometry getGeometry()
	{
		return geometry;
	}

	// implements WorldConfigIfc
	public long getTicksPerYear()
	{
		return ticksPerYear;
	}
}
