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
	transient int zoneNumber;

	ZoneType type;
	int gridx;
	int gridy;
	CommodityRecipe recipe;
	CommodityType commodity;

	ZoneServant(RegionServant parentRegion, int zoneNumber)
	{
		this.parentRegion = parentRegion;
		this.zoneNumber = zoneNumber;
	}

	public static ZoneServant parse(JsonParser in, RegionServant parentRegion, int zoneNumber)
		throws IOException
	{
		ZoneServant zone = new ZoneServant(parentRegion, zoneNumber);
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
			else if (s.equals("commodity"))
				commodity = CommodityType.valueOf(in.nextTextValue());
			else if (s.equals("gridx"))
				gridx = in.nextIntValue(0);
			else if (s.equals("gridy"))
				gridy = in.nextIntValue(0);
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
		out.writeNumberField("gridx", gridx);
		out.writeNumberField("gridy", gridy);
		if (commodity != null)
		{
			out.writeStringField("commodity", commodity.name());
		}
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
		zi.gridx = gridx;
		zi.gridy = gridy;
		zi.gridwidth = 1;
		zi.gridheight = 1;
		zi.recipe = recipe;
		zi.commodity = commodity;

		if (type == ZoneType.UNDER_CONSTRUCTION)
		{
			zi.setPortionCompleted(
				parentRegion.getPortionCompleted(zoneNumber)
				);
		}

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

	void stop()
	{
	}
}
