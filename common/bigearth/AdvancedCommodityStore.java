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
	Map<String, Producer> producers;

	public AdvancedCommodityStore()
	{
		quantities = new HashMap<>();
		partials = new HashMap<>();
		rates = new HashMap<>();
		producers = new HashMap<>();
	}

	public void add(CommoditiesBag rhs)
	{
		for (Map.Entry<CommodityType,Long> e : rhs.stock.entrySet())
		{
			CommodityType ct = e.getKey();
			long qty = e.getValue();
			add(ct, qty);
		}
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

	void addProducer(String key, CommodityType ct, double rate)
	{
		CommoditiesBag bag = new CommoditiesBag();
		bag.add(ct, 1);
		addProducer(key, bag, rate);
	}

	void addProducer(String key, CommoditiesBag bag, double rate)
	{
		update();
		producers.put(key, new Producer(bag, rate));
		calculateFlow();
	}

	void removeProducer(String key)
	{
		update();
		producers.remove(key);
		calculateFlow();
	}

	public CommodityType [] getCommodityTypesArray()
	{
		return quantities.keySet().toArray(new CommodityType[0]);
	}

	public long getQuantity(CommodityType ct)
	{
		update();
		Long L = quantities.get(ct);
		return L != null ? L.longValue() : 0;
	}

	public boolean isSupersetOf(CommoditiesBag bag)
	{
		return toCommoditiesBag().isSupersetOf(bag);
	}

	public void subtract(CommoditiesBag rhs)
	{
		for (Map.Entry<CommodityType,Long> e : rhs.stock.entrySet())
		{
			CommodityType ct = e.getKey();
			long qty = e.getValue();
			subtract(ct, qty);
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

	private void calculateFlow()
	{
		rates.clear();
		for (Producer p : producers.values())
		{
			for (Map.Entry<CommodityType,Long> e : p.getOutput().stock.entrySet())
			{
				CommodityType ct = e.getKey();
				long qty = e.getValue();

				double rate = rates.containsKey(ct) ? rates.get(ct) : 0.0;
				rate += qty * p.getRate();
				rates.put(ct, rate);
			}
		}

		for (Iterator< Map.Entry<CommodityType,Double> > it = partials.entrySet().iterator();
				it.hasNext(); )
		{
			Map.Entry<CommodityType,Double> e = it.next();
			if (!rates.containsKey(e.getKey()))
				it.remove();
		}
	}

	private void update()
	{
		long curTime = EventDispatchThread.currentTime();
		assert curTime >= lastUpdated;

		long elapsedTicks = curTime - lastUpdated;
		if (elapsedTicks <= 0)
			return;

		// Note- because this method is invoked automatically from
		// so many of the public methods, be careful not to call one
		// of those public methods from here.

		for (CommodityType ct : rates.keySet())
		{
			Long curBalanceL = quantities.get(ct);
			long curBalance = curBalanceL != null ? curBalanceL.longValue() : 0;

			double rate = rates.get(ct);

			Double partialObj = partials.get(ct);
			double partial = partialObj != null ? partialObj.doubleValue() : 0.0;

			partial += rate * elapsedTicks;
			if (partial < 0.0 || partial >= 1.0)
			{
				double delta = Math.floor(partial);
				curBalance += (long)delta;

				partial = partial - Math.floor(partial);
				assert partial >= 0.0 && partial < 1.0;
			}

			if (curBalance < 0)
			{
				curBalance = 0;
				partial = 0.0;
			}

			if (curBalance != 0)
				quantities.put(ct, curBalance);
			else
				quantities.remove(ct);

			partials.put(ct, partial);
		}

		lastUpdated = curTime;
	}

	public static class Producer
	{
		CommoditiesBag output;
		double rate;

		public Producer(CommoditiesBag output, double rate)
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
