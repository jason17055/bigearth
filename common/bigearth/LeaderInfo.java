package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

//TODO- rename to LeaderServant
public class LeaderInfo
	implements Saveable
{
	WorldMaster world;

	String name;
	String displayName;
	String password;
	Collection<NotificationStream> streams;

	LeaderInfo(String name, WorldMaster world)
	{
		this.name = name;
		this.world = world;

		this.displayName = name;
		this.streams = new ArrayList<NotificationStream>();
	}

	boolean checkPassword(String inPassword)
	{
		return (
			password != null && password.equals(inPassword)
			);
	}

	void sendNotification(Notification n)
	{
		for (NotificationStream ns : this.streams)
		{
			ns.add(n);
		}
	}

	File getLeaderFilename()
	{
		return new File(
			new File(world.config.path, "leaders"),
			name
			);
	}

	void load()
		throws IOException
	{
		File file = getLeaderFilename();

		JsonParser in = new JsonFactory().createJsonParser(file);
		parse(in);
		in.close();
	}

	//implements Saveable
	public void save()
		throws IOException
	{
		File file = getLeaderFilename();
		JsonGenerator out = new JsonFactory().createJsonGenerator(file,
				JsonEncoding.UTF8);
		write(out);
		out.close();
	}

	void parse(JsonParser in)
		throws IOException
	{
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("displayName"))
			{
				displayName = in.nextTextValue();
			}
			else if (s.equals("password"))
			{
				password = in.nextTextValue();
			}
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized leader property: " + s);
			}
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}

	void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("displayName", displayName);
		if (password != null)
		{
			out.writeStringField("password", password);
		}

		out.writeEndObject();
	}
}
