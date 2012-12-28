package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

public class AdvancedCommodityStore
{
	long lastUpdated; //timestamp
	Map<CommodityType, Long> quantities;
	Map<CommodityType, Double> partials;
	Map<CommodityType, Double> rates;

	public AdvancedCommodityStore()
	{
		quantities = new HashMap<>();
		partials = new HashMap<>();
		rates = new HashMap<>();
	}

	public void add(CommodityType ct, long amount)
	{
		assert amount >= 0;
		if (amount == 0)
			return;

		update();
		if (quantities.containsKey(ct))
		{
			long amt = quantities.get(ct);
			amt += amount;
			quantities.put(ct, amt);
		}
		else
		{
			quantities.put(ct, amount);
		}
	}

	public long subtract(CommodityType ct, long amount)
	{
		assert amount >= 0;
		if (amount == 0)
			return 0;

		update();
		if (quantities.containsKey(ct))
		{
			long curBal = quantities.get(ct);
			if (amount < curBal)
			{
				quantities.put(ct, curBal - amount);
				return amount;
			}
			else
			{
				quantities.remove(ct);
				return curBal;
			}
		}
		return 0;
	}

	public CommoditiesBag toCommoditiesBag()
	{
		update();

		CommoditiesBag rv = new CommoditiesBag();
		for (Map.Entry<CommodityType,Long> e : quantities.entrySet())
		{
			CommodityType ct = e.getKey();
			long amt = e.getValue();
			rv.add(ct, amt);
		}
		return rv;
	}

	public void parse(JsonParser in)
		throws IOException
	{
		in.nextToken();
		if (in.getCurrentToken() != JsonToken.START_OBJECT)
			throw new InputMismatchException();

		while (in.nextToken() != JsonToken.END_OBJECT)
		{
			CommodityType ct = CommodityType.valueOf(in.getCurrentName());
			in.nextToken();
			if (in.getCurrentToken() == JsonToken.VALUE_NUMBER_INT)
			{
				long qty = in.getLongValue();
				quantities.put(ct, qty);
			}
			else
			{
				throw new InputMismatchException();
			}
		}
	}

	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		for (CommodityType ct : quantities.keySet())
		{
			long amt = quantities.get(ct);
			out.writeNumberField(ct.name(), amt);
		}
		out.writeEndObject();
	}

	private void update()
	{
		// Do nothing, for now...
	}

	public static class Source
	{
		CommoditiesBag output;
		double rate;

		public Source(CommoditiesBag output, double rate)
		{
			this.output = output;
			this.rate = rate;
		}

		public CommoditiesBag getOutput()
		{
			return output;
		}

		public double getRate()
		{
			return rate;
		}
	}

	public static class Sink
	{
		CommoditiesBag input;
		double maximumRate;

		public Sink(CommoditiesBag input, double maximumRate)
		{
			this.input = input;
			this.maximumRate = maximumRate;
		}

		public CommoditiesBag getInput()
		{
			return input;
		}

		public double getMaximumRate()
		{
			return maximumRate;
		}
	}

	public static class Converter
	{
		CommoditiesBag input;
		CommoditiesBag output;
		double maximumRate;

		public Converter(CommoditiesBag input, CommoditiesBag output, double maximumRate)
		{
			this.input = input;
			this.output = output;
			this.maximumRate = maximumRate;
		}
	}
}
