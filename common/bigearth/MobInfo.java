package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

public class MobInfo
{
	String name;
	String displayName;
	String avatarName;
	String owner;
	Location location;
	String activity;
	long activityStarted;
	EnumMap<CommodityType, Long> stock;
	transient Scheduler.Event wakeUp;

	MobInfo(String name)
	{
		this.name = name;
		this.displayName = name;
	}

	public void addCommodity(CommodityType ct, long amount)
	{
		if (stock == null)
		{
			stock = new EnumMap<CommodityType, Long>(CommodityType.class);
		}

		if (stock.containsKey(ct))
		{
			long amt = stock.get(ct);
			amt += amount;
			stock.put(ct, amt);
		}
		else
		{
			stock.put(ct, amount);
		}
	}

	public long getStock(CommodityType ct)
	{
		return stock.get(ct);
	}

	public boolean hasActivity()
	{
		return activity != null;
	}

	public boolean hasAvatarName()
	{
		return avatarName != null;
	}

	public boolean hasOwner()
	{
		return owner != null;
	}

	public boolean hasLocation()
	{
		return location != null;
	}

	public boolean hasStock()
	{
		return stock != null;
	}

	public static MobInfo parse(JsonParser in, String mobName, WorldConfigIfc world)
		throws IOException
	{
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		MobInfo m = new MobInfo(mobName);

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
			else if (s.equals("activity"))
				m.activity = in.nextTextValue();
			else if (s.equals("activityStarted"))
			{
				in.nextToken();
				m.activityStarted = in.getLongValue();
			}
			else if (s.equals("stock"))
				m.parseCommodities(in);
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

	private void parseCommodities(JsonParser in)
		throws IOException
	{
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		stock = new EnumMap<CommodityType, Long>(CommodityType.class);
		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			CommodityType ct = CommodityType.valueOf(in.getCurrentName());
			in.nextToken();
			long amt = in.getLongValue();

			stock.put(ct, amt);
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
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
		if (activity != null)
		{
			out.writeStringField("activity", activity);
			out.writeNumberField("activityStarted", activityStarted);
		}
		if (stock != null)
		{
			out.writeFieldName("stock");
			writeCommodities(out);
		}
		out.writeEndObject();
	}

	void writeCommodities(JsonGenerator out)
		throws IOException
	{
		assert stock != null;

		out.writeStartObject();
		for (CommodityType ct : stock.keySet())
		{
			long amt = stock.get(ct);
			out.writeNumberField(ct.name(), amt);
		}
		out.writeEndObject();
	}
}
