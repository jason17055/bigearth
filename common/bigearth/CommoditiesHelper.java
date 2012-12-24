package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

class CommoditiesHelper
{
	static Map<CommodityType, Long> createEmpty()
	{
		return new EnumMap<CommodityType, Long>(CommodityType.class);
	}

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

	static Map<CommodityType, Long> makeClone(Map<CommodityType, Long> stock)
	{
		assert stock != null;

		Map<CommodityType, Long> rv = new EnumMap<CommodityType, Long>(CommodityType.class);
		rv.putAll(stock);
		return rv;
	}

	static boolean contentsEqual(Map<CommodityType, Long> stock1, Map<CommodityType, Long> stock2)
	{
		if (stock1.size() != stock2.size())
			return false;
		for (CommodityType ct : stock1.keySet())
		{
			if (!stock2.containsKey(ct))
				return false;
			long amt1 = stock1.get(ct);
			long amt2 = stock2.get(ct);
			if (amt1 != amt2)
				return false;
		}
		return true;
	}
}
