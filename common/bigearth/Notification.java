package bigearth;

import java.io.*;
import com.fasterxml.jackson.core.*;

public abstract class Notification
{
	public abstract void write(JsonGenerator out)
		throws IOException;

	public abstract void dispatch(Receiver r);

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
		else if (eventType.equals(MobChangeNotification.EVENT_NAME))
		{
			MobChangeNotification n = new MobChangeNotification();
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

	public interface Receiver
	{
		void handleMapUpdateNotification(MapUpdateNotification n);
		void handleMobChangeNotification(MobChangeNotification n);
	}
}

class MobChangeNotification extends Notification
{
	String mobName;
	MobInfo mobData;

	static final String EVENT_NAME = "mob-change";

	public MobChangeNotification()
	{
	}

	public MobChangeNotification(String mobName, MobInfo mobData)
	{
		this.mobName = mobName;
		this.mobData = mobData;
	}

	@Override
	public void dispatch(Receiver r)
	{
		r.handleMobChangeNotification(this);
	}

	@Override
	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("event", EVENT_NAME);
		out.writeStringField("mob", mobName);
		out.writeFieldName("data");
		mobData.write(out);
		out.writeEndObject();
	}

	public void parse_cont(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("mob"))
				mobName = in.nextTextValue();
			else if (s.equals("data"))
				mobData = MobInfo.parse(in, mobName, world);
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
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

	public Location getLocation()
	{
		return new SimpleLocation(regionId);
	}

	@Override
	public void dispatch(Receiver r)
	{
		r.handleMapUpdateNotification(this);
	}

	@Override
	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("event", EVENT_NAME);
		out.writeNumberField("region", regionId);
		out.writeFieldName("data");
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
			else if (s.equals("data"))
				profile = RegionProfile.parse_s(in, world);
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}
}
