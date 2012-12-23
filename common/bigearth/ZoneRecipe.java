package bigearth;

import java.io.*;
import java.util.*;
import com.fasterxml.jackson.core.*;

public class ZoneRecipe
{
	ZoneType fromZoneType;
	ZoneType toZoneType;
	Map<CommodityType, Long> consumed;
	Map<CommodityType, Long> generated;
	double workRequired;

	public static ZoneRecipe parse1(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		ZoneRecipe me = new ZoneRecipe();
		me.parse1_real(in, world);
		return me;
	}

	private void parse1_real(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		if (in.getCurrentToken() != JsonToken.START_OBJECT)
			throw new InputMismatchException();

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("fromZoneType"))
				fromZoneType = ZoneType.valueOf(in.nextTextValue());
			else if (s.equals("toZoneType"))
				toZoneType = ZoneType.valueOf(in.nextTextValue());
			else if (s.equals("consumed"))
				consumed = CommoditiesHelper.parseCommodities(in);
			else if (s.equals("generated"))
				generated = CommoditiesHelper.parseCommodities(in);
			else if (s.equals("workRequired"))
			{
				in.nextToken();
				workRequired = in.getDoubleValue();
			}
			else
			{
				in.nextToken();
				in.skipChildren();
			}
		}

		if (in.getCurrentToken() != JsonToken.END_OBJECT)
			throw new InputMismatchException();
	}
}
