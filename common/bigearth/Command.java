package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

public abstract class Command
{
	String command;

	protected Command(String activity)
	{
		this.command = activity;
	}

	public boolean isActivity(String activity)
	{
		return this.command.equals(activity);
	}

	public abstract void write(JsonGenerator out)
		throws IOException;

	public static Command parse(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		in.nextToken();
		return parse_1(in, world);
	}

	public static Command parse_1(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		if (in.getCurrentToken() != JsonToken.START_OBJECT)
		{
			// backwards-compat: at one time, the mob's current
			// activity was just a string
			String s = in.getText();
			if (s.equals(""))
				return null;
			return new SimpleCommand(s);
		}

		if (in.getCurrentToken() != JsonToken.START_OBJECT)
			throw new InputMismatchException();

		in.nextToken();
		if (in.getCurrentToken() != JsonToken.FIELD_NAME)
			throw new InputMismatchException();
		if (!in.getCurrentName().equals("command"))
			throw new InputMismatchException();

		String commandName = in.nextTextValue();
		if (commandName.equals(DevelopCommand.COMMAND_NAME))
		{
			DevelopCommand n = new DevelopCommand();
			n.parse_cont(in, world);
			return n;
		}
		else if (commandName.equals(RenameSelfCommand.COMMAND_NAME))
		{
			RenameSelfCommand n = new RenameSelfCommand();
			n.parse_cont(in, world);
			return n;
		}
		else
		{
			SimpleCommand n = new SimpleCommand(commandName);
			n.parse_cont(in, world);
			return n;
		}
	}
}

class SimpleCommand extends Command
{
	Location destination;
	CommodityType commodity;
	long amount;
	boolean amountIsSpecified;
	Flag flag;

	public SimpleCommand(String activity)
	{
		super(activity);
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
		out.writeStringField("command", command);
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

	void parse_cont(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("commodity"))
				this.commodity = CommodityType.valueOf(in.nextTextValue());
			else if (s.equals("amount"))
			{
				in.nextToken();
				this.setAmount(in.getLongValue());
			}
			else if (s.equals("destination"))
				this.destination = LocationHelper.parse(in.nextTextValue(), world);
			else if (s.equals("flag"))
				this.flag = Flag.valueOf(in.nextTextValue());
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized command property: " + s);
			}
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}
}

class DevelopCommand extends Command
{
	static final String COMMAND_NAME = "develop";
	ZoneType fromZoneType;
	ZoneType toZoneType;

	public DevelopCommand()
	{
		super(COMMAND_NAME);
	}

	@Override
	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("command", COMMAND_NAME);
		if (fromZoneType != null)
			out.writeStringField("fromZoneType", fromZoneType.name());
		if (toZoneType != null)
			out.writeStringField("toZoneType", toZoneType.name());
		out.writeEndObject();
	}

	void parse_cont(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("fromZoneType"))
				fromZoneType = ZoneType.valueOf(in.nextTextValue());
			else if (s.equals("toZoneType"))
				toZoneType = ZoneType.valueOf(in.nextTextValue());
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized command property: " + s);
			}
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}
}

class RenameSelfCommand extends Command
{
	static final String COMMAND_NAME = "rename-self";
	String newName;

	public RenameSelfCommand()
	{
		super(COMMAND_NAME);
	}

	@Override
	public void write(JsonGenerator out)
		throws IOException
	{
		assert newName != null;

		out.writeStartObject();
		out.writeStringField("command", COMMAND_NAME);
		out.writeStringField("newName", newName);
		out.writeEndObject();
	}

	void parse_cont(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("newName"))
				newName = in.nextTextValue();
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized command property: " + s);
			}
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}
}
