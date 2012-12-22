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
		assert in.getCurrentName().equals("event");

		String eventType = in.nextTextValue();
		if (eventType.equals(CityMessageNotification.EVENT_NAME))
		{
			CityMessageNotification n = new CityMessageNotification();
			n.parse_cont(in, world);
			return n;
		}
		else if (eventType.equals(CityUpdateNotification.EVENT_NAME))
		{
			CityUpdateNotification n = new CityUpdateNotification();
			n.parse_cont(in, world);
			return n;
		}
		else if (eventType.equals(MapUpdateNotification.EVENT_NAME))
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
		else if (eventType.equals(MobMessageNotification.EVENT_NAME))
		{
			MobMessageNotification n = new MobMessageNotification();
			n.parse_cont(in, world);
			return n;
		}
		else if (eventType.equals(MobRemoveNotification.EVENT_NAME))
		{
			MobRemoveNotification n = new MobRemoveNotification();
			n.parse_cont(in, world);
			return n;
		}
		else
		{
			UnknownNotification n = new UnknownNotification(eventType);
			n.parse_cont(in, world);
			return n;
		}
	}

	public interface Receiver
	{
		void handleCityMessageNotification(CityMessageNotification n);
		void handleCityUpdateNotification(CityUpdateNotification n);
		void handleMapUpdateNotification(MapUpdateNotification n);
		void handleMobChangeNotification(MobChangeNotification n);
		void handleMobMessageNotification(MobMessageNotification n);
		void handleMobRemoveNotification(MobRemoveNotification n);
	}
}

class UnknownNotification extends Notification
{
	String eventName;

	public UnknownNotification(String eventName)
	{
		this.eventName = eventName;
	}

	public void parse_cont(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			in.nextToken();
			in.skipChildren();
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}

	@Override
	public void dispatch(Receiver r)
	{
		// do nothing
	}

	@Override
	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("event", eventName);
		out.writeEndObject();
	}
}

class CityMessageNotification extends Notification
{
	Location cityLocation;
	String message;

	static final String EVENT_NAME = "city-message";

	public CityMessageNotification()
	{
	}

	public CityMessageNotification(Location cityLocation, String message)
	{
		this.cityLocation = cityLocation;
		this.message = message;
	}

	@Override
	public void dispatch(Receiver r)
	{
		r.handleCityMessageNotification(this);
	}

	@Override
	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("event", EVENT_NAME);
		out.writeStringField("city", cityLocation.toString());
		out.writeStringField("message", message);
		out.writeEndObject();
	}

	public void parse_cont(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("city"))
				cityLocation = LocationHelper.parse(in.nextTextValue(), world);
			else if (s.equals("message"))
				message = in.nextTextValue();
			else
			{
				in.nextToken();
				in.skipChildren();
			}
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}
}

class CityUpdateNotification extends Notification
{
	Location cityLocation;
	CityInfo cityData;

	static final String EVENT_NAME = "city-update";

	public CityUpdateNotification()
	{
	}

	public CityUpdateNotification(Location cityLocation, CityInfo cityData)
	{
		this.cityLocation = cityLocation;
		this.cityData = cityData;
	}

	@Override
	public void dispatch(Receiver r)
	{
		r.handleCityUpdateNotification(this);
	}

	@Override
	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("event", EVENT_NAME);
		out.writeStringField("city", cityLocation.toString());
		out.writeFieldName("data");
		cityData.write(out);
		out.writeEndObject();
	}

	public void parse_cont(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("city"))
				cityLocation = LocationHelper.parse(in.nextTextValue(), world);
			else if (s.equals("data"))
				cityData = CityInfo.parse(in, world);
			else
			{
				in.nextToken();
				in.skipChildren();
			}
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
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
				mobData = MobInfo.parse(in, world);
			else
			{
				in.nextToken();
				in.skipChildren();
			}
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}
}

class MobRemoveNotification extends Notification
{
	String mobName;
	MobInfo.RemovalDisposition disposition;

	static final String EVENT_NAME = "mob-remove";

	public MobRemoveNotification()
	{
	}

	public MobRemoveNotification(String mobName, MobInfo.RemovalDisposition disposition)
	{
		this.mobName = mobName;
		this.disposition = disposition;
	}

	@Override
	public void dispatch(Receiver r)
	{
		r.handleMobRemoveNotification(this);
	}

	@Override
	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("event", EVENT_NAME);
		out.writeStringField("mob", mobName);
		out.writeStringField("disposition", disposition.name());
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
			else if (s.equals("disposition"))
				disposition = MobInfo.RemovalDisposition.valueOf(in.nextTextValue());
			else
			{
				System.err.println(getClass().getName() + ": unrecognized json field: " + s);
				in.nextToken();
				in.skipChildren();
			}
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
			else
			{
				in.nextToken();
				in.skipChildren();
			}
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}
}

class MobMessageNotification extends Notification
{
	String mobName;
	String message;

	static final String EVENT_NAME = "mob-message";

	public MobMessageNotification()
	{
	}

	public MobMessageNotification(String mobName, String message)
	{
		this.mobName = mobName;
		this.message = message;
	}

	@Override
	public void dispatch(Receiver r)
	{
		r.handleMobMessageNotification(this);
	}

	@Override
	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("event", EVENT_NAME);
		out.writeStringField("mob", mobName);
		out.writeStringField("message", message);
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
			else if (s.equals("message"))
				message = in.nextTextValue();
			else
			{
				in.nextToken();
				in.skipChildren();
			}
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}
}
