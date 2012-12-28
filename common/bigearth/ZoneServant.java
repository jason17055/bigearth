package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

/**
 * The server-side implementation of a "zone".
 * A zone is a subsection of a "Region", which contains a single building
 * or land dedicated for a single purpose.
 * There are something like 64 zones in each region.
 */
public class ZoneServant
{
	transient RegionServant parentRegion;

	ZoneType type;
	CommodityRecipe recipe;

	ZoneServant(RegionServant parentRegion)
	{
		this.parentRegion = parentRegion;
	}

	public static ZoneServant parse(JsonParser in, RegionServant parentRegion)
		throws IOException
	{
		ZoneServant zone = new ZoneServant(parentRegion);
		zone.parse_real(in);
		return zone;
	}

	private void parse_real(JsonParser in)
		throws IOException
	{
		in.nextToken();
		if (in.getCurrentToken() == JsonToken.VALUE_STRING)
		{
			type = ZoneType.valueOf(in.getText());
			return;
		}
		else if (in.getCurrentToken() != JsonToken.START_OBJECT)
		{
			throw new InputMismatchException();
		}

		while (in.nextToken() != JsonToken.END_OBJECT)
		{
			String s = in.getCurrentName();
			if (s.equals("type"))
				type = ZoneType.valueOf(in.nextTextValue());
			else if (s.equals("recipe"))
				recipe = CommodityRecipe.valueOf(in.nextTextValue());
			else
			{
				System.err.println("unrecognized ZoneServant field: "+s);
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
		if (recipe != null)
		{
			out.writeFieldName("recipe");
			out.writeString(recipe.name());
		}
		out.writeEndObject();
	}

	public ZoneInfo makeProfile()
	{
		ZoneInfo zi = new ZoneInfo();
		zi.type = type;
		zi.recipe = recipe;
		return zi;
	}

	void start()
	{
		if (type == ZoneType.STONE_WORKSHOP)
		{
			if (recipe == null)
			{
				recipe = CommodityRecipe.STONE_TO_STONE_BLOCK;
			}
		}
	}
}
