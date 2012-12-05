package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

class CommoditiesHelper
{
	static Map<CommodityType, Long> parseCommodities(JsonParser in)
		throws IOException
	{
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		Map<CommodityType, Long> stock;
		stock = new EnumMap<CommodityType, Long>(CommodityType.class);
		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			CommodityType ct = CommodityType.valueOf(in.getCurrentName());
			in.nextToken();
			long amt = in.getLongValue();

			stock.put(ct, amt);
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;

		return stock;
	}

	static void writeCommodities(Map<CommodityType, Long> stock, JsonGenerator out)
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
