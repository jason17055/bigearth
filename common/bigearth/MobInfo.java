package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;

public class MobInfo
{
	String name;
	String displayName;
	String avatarName;
	String owner;
	Location location;

	MobInfo()
	{
	}

	MobInfo(String name)
	{
		this.name = name;
		this.displayName = name;
	}

	public static MobInfo parse(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		return parse(in, null, world);
	}

	public static MobInfo parse1(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		return parse1(in, null, world);
	}

	public static MobInfo parse(JsonParser in, String mobName, WorldConfigIfc world)
		throws IOException
	{
		in.nextToken();
		return parse1(in, mobName, world);
	}

	public static MobInfo parse1(JsonParser in, String mobName, WorldConfigIfc world)
		throws IOException
	{
		MobInfo m = new MobInfo(mobName);

		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("displayName"))
				m.displayName = in.nextTextValue();
			else if (s.equals("avatarName"))
				m.avatarName = in.nextTextValue();
			else if (s.equals("location"))
				m.location = LocationHelper.parse(in.nextTextValue(), world);
			else if (s.equals("owner"))
				m.owner = in.nextTextValue();
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized mob property: "+s);
			}
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;

		return m;
	}

	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("displayName", displayName);
		if (avatarName != null)
			out.writeStringField("avatarName", avatarName);
		if (location != null)
			out.writeStringField("location", location.toString());
		if (owner != null)
			out.writeStringField("owner", owner);
		out.writeEndObject();
	}
}
