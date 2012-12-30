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
	CommodityType commodity;
	CommodityRecipe recipe;

	public ZoneInfo()
	{
	}

	public boolean hasCommodity()
	{
		return commodity != null;
	}

	public boolean hasRecipe()
	{
		return recipe != null;
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
			else if (s.equals("recipe"))
				recipe = CommodityRecipe.valueOf(in.nextTextValue());
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
		if (hasCommodity())
			out.writeStringField("commodity", commodity.name());
		if (hasRecipe())
			out.writeStringField("recipe", recipe.name());
		out.writeEndObject();
	}
}
