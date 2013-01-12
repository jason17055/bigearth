package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

/**
 * Describes a "zone". This class represents the data packet that is sent
 * over the network.
 */
public class ZoneInfo
{
	ZoneType type;
	int gridx;
	int gridy;
	int gridwidth;
	int gridheight;
	CommodityType commodity;
	CommodityRecipe recipe;
	/// For when the zone is under construction.
	double portionCompleted;
	boolean portionCompletedIsKnown;

	public ZoneInfo()
	{
	}

	public double getPortionCompleted()
	{
		return portionCompleted;
	}

	public boolean hasCommodity()
	{
		return commodity != null;
	}

	public boolean hasPortionCompleted()
	{
		return portionCompletedIsKnown;
	}

	public boolean hasRecipe()
	{
		return recipe != null;
	}

	public void setPortionCompleted(double portion)
	{
		portionCompletedIsKnown = true;
		portionCompleted = portion;
	}

	public static ZoneInfo parse(JsonParser in)
		throws IOException
	{
		ZoneInfo zone = new ZoneInfo();
		zone.parse_real(in);
		return zone;
	}

	private void parse_real(JsonParser in)
		throws IOException
	{
		in.nextToken();
		if (in.getCurrentToken() != JsonToken.START_OBJECT)
			throw new InputMismatchException();

		while (in.nextToken() != JsonToken.END_OBJECT)
		{
			String s = in.getCurrentName();
			if (s.equals("type"))
				type = ZoneType.valueOf(in.nextTextValue());
			else if (s.equals("commodity"))
				commodity = CommodityType.valueOf(in.nextTextValue());
			else if (s.equals("gridx"))
				gridx = in.nextIntValue(0);
			else if (s.equals("gridy"))
				gridy = in.nextIntValue(0);
			else if (s.equals("gridwidth"))
				gridwidth = in.nextIntValue(0);
			else if (s.equals("gridheight"))
				gridheight = in.nextIntValue(0);
			else if (s.equals("recipe"))
				recipe = CommodityRecipe.valueOf(in.nextTextValue());
			else if (s.equals("portionCompleted"))
			{
				in.nextToken();
				setPortionCompleted(in.getDoubleValue());
			}
			else
			{
				System.out.println("Warning: unrecognized ZoneInfo field: " + s);
				in.nextToken();
				in.skipChildren();
			}
		}
	}

	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("type", type.name());
		out.writeNumberField("gridx", gridx);
		out.writeNumberField("gridy", gridy);
		out.writeNumberField("gridwidth", gridwidth);
		out.writeNumberField("gridheight", gridheight);
		if (hasCommodity())
			out.writeStringField("commodity", commodity.name());
		if (hasRecipe())
			out.writeStringField("recipe", recipe.name());
		if (hasPortionCompleted())
			out.writeNumberField("portionCompleted", portionCompleted);
		out.writeEndObject();
	}
}
