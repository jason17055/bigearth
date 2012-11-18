package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;

public class MobInfo
{
	String name;
	String displayName;
	String avatarName;
	Location location;

	MobInfo(String name)
	{
		this.name = name;
		this.displayName = name;
	}

	public static MobInfo parse(JsonParser in, String mobName, WorldConfig world)
		throws IOException
	{
		MobInfo m = new MobInfo(mobName);

		in.nextToken();
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
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized mob property: "+s);
			}
		}

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
		out.writeEndObject();
	}
}
