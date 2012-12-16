package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;

public class Command
{
	String activity;
	Location destination;
	CommodityType commodity;
	long amount;
	boolean amountIsSpecified;
	Flag flag;

	private Command()
	{
	}

	private Command(String activity)
	{
		this.activity = activity;
	}

	public static Command newInstance(String activityName)
	{
		return new Command(activityName);
	}

	public void setCommodityType(CommodityType commodity)
	{
		this.commodity = commodity;
	}

	public void setAmount(long amount)
	{
		this.amount = amount;
		this.amountIsSpecified = true;
	}

	public void setDestination(Location destination)
	{
		this.destination = destination;
	}

	public void setFlag(Flag flag)
	{
		this.flag = flag;
	}

	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("activity", activity);
		if (commodity != null)
			out.writeStringField("commodity", commodity.name());
		if (amountIsSpecified)
			out.writeNumberField("amount", amount);
		if (destination != null)
			out.writeStringField("destination", destination.toString());
		if (flag != null)
			out.writeStringField("flag", flag.name());
		out.writeEndObject();
	}

	public static Command parse(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		Command c = new Command();

		in.nextToken();
		if (in.getCurrentToken() != JsonToken.START_OBJECT)
		{
			// backwards-compat: at one time, the mob's current
			// activity was just a string
			String s = in.getText();
			if (s.equals(""))
				return null;
			c.activity = s;
			return c;
		}

		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("activity"))
				c.activity = in.nextTextValue();
			else if (s.equals("commodity"))
				c.commodity = CommodityType.valueOf(in.nextTextValue());
			else if (s.equals("amount"))
			{
				in.nextToken();
				c.setAmount(in.getLongValue());
			}
			else if (s.equals("destination"))
				c.destination = LocationHelper.parse(in.nextTextValue(), world);
			else if (s.equals("flag"))
				c.flag = Flag.valueOf(in.nextTextValue());
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized command property: " + s);
			}
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;

		return c;
	}
}
