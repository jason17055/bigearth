package bigearth;

import java.io.*;
import com.fasterxml.jackson.core.*;

public abstract class Notification
{
	public abstract void write(JsonGenerator out)
		throws IOException;

	public static Notification parse_1(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		in.nextToken();
		assert in.getCurrentToken() == JsonToken.FIELD_NAME;

		String eventType = in.nextTextValue();
		if (eventType.equals(MapUpdateNotification.EVENT_NAME))
		{
			MapUpdateNotification n = new MapUpdateNotification();
			n.parse_cont(in, world);
			return n;
		}
		else
		{
			//skip past this object
			while (in.nextToken() == JsonToken.FIELD_NAME)
			{
				in.nextToken();
				in.skipChildren();
			}

			assert in.getCurrentToken() == JsonToken.END_OBJECT;

			throw new IOException("unrecognized event type: "+eventType);
		}
	}
}

class MapUpdateNotification extends Notification
{
	int regionId;
	RegionProfile profile;

	static final String EVENT_NAME = "map-update";

	public MapUpdateNotification()
	{
	}

	public MapUpdateNotification(int regionId, RegionProfile profile)
	{
		this.regionId = regionId;
		this.profile = profile;
	}

	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("event", EVENT_NAME);
		out.writeNumberField("region", regionId);
		out.writeFieldName("profile");
		profile.write(out);
		out.writeEndObject();
	}

	public void parse_cont(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("region"))
				regionId = in.nextIntValue(0);
			else if (s.equals("profile"))
				profile = RegionProfile.parse_s(in, world);
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}
}
