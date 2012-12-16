package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

public class MobInfo
{
	String displayName;
	String avatarName;
	String owner;
	Location location;
	String activity;
	long activityStarted;
	Map<CommodityType, Long> stock;
	EncumbranceLevel encumbrance;
	HungerStatus hunger;

	MobInfo()
	{
	}

	public long getStock(CommodityType ct)
	{
		return stock.containsKey(ct) ? stock.get(ct) : 0;
	}

	public boolean hasActivity()
	{
		return activity != null;
	}

	public boolean hasAvatarName()
	{
		return avatarName != null;
	}

	public boolean hasEncumbrance()
	{
		return encumbrance != null;
	}

	public boolean hasHunger()
	{
		return hunger != null;
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

	public static MobInfo parse(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		MobInfo m = new MobInfo();

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("displayName"))
				m.displayName = in.nextTextValue();
			else if (s.equals("avatarName"))
				m.avatarName = in.nextTextValue();
			else if (s.equals("encumbrance"))
				m.encumbrance = EncumbranceLevel.valueOf(in.nextTextValue());
			else if (s.equals("hunger"))
				m.hunger = HungerStatus.valueOf(in.nextTextValue());
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
				m.stock = CommoditiesHelper.parseCommodities(in);
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
		if (activity != null)
		{
			out.writeStringField("activity", activity);
			out.writeNumberField("activityStarted", activityStarted);
		}
		if (stock != null)
		{
			out.writeFieldName("stock");
			CommoditiesHelper.writeCommodities(stock, out);
		}
		if (encumbrance != null)
			out.writeStringField("encumbrance", encumbrance.name());
		if (hunger != null)
			out.writeStringField("hunger", hunger.name());
		out.writeEndObject();
	}

	public static enum RemovalDisposition
	{
		LOST_SIGHT,
		MOVED_AWAY;
	}

}
