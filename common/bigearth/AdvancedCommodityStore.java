package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

public class AdvancedCommodityStore
{
	long lastUpdated; //timestamp
	Map<CommodityType, Long> quantities;
	Map<String, Producer> producers;

	public AdvancedCommodityStore()
	{
		quantities = new HashMap<>();
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

	public void removeProducer(String key)
	{
		update();
		producers.remove(key);
	}

	public void setProducer(String key, CommodityType ct, double rate)
	{
		CommoditiesBag bag = new CommoditiesBag();
		bag.add(ct, 1);
		setProducer(key, bag, rate);
	}

	public void setProducer(String key, CommoditiesBag bag, double rate)
	{
		setProducer(key, new Producer(bag, rate));
	}

	public void setProducer(String key, Producer newProducer)
	{
		update();

		Producer oldProducer = producers.get(key);
		if (oldProducer != null)
		{
			maybeTransferPartial(oldProducer, newProducer);
		}
		producers.put(key, newProducer);
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

	private void maybeTransferPartial(Producer oldProducer, Producer newProducer)
	{
		if (newProducer.output.isSupersetOf(oldProducer.output))
		{
			newProducer.partial = oldProducer.partial;
		}
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

	private void produced(Producer p, long numProduced)
	{
		assert numProduced > 0;

		for (Map.Entry<CommodityType,Long> e : p.output.stock.entrySet())
		{
			CommodityType ct = e.getKey();
			long v = e.getValue();

			Long oldBalanceObj = quantities.get(ct);
			long oldBalance = oldBalanceObj != null ? oldBalanceObj.longValue() : 0;

			long newBalance = oldBalance + numProduced * v;
			if (newBalance != 0)
				quantities.put(ct, newBalance);
			else
				quantities.remove(ct);
		}

		p.produced(numProduced);
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

		for (Producer p : producers.values())
		{
			p.partial += p.rate * elapsedTicks;
			if (p.partial >= 1.0)
			{
				double f = Math.floor(p.partial);
				p.partial -= f;
				assert p.partial >= 0.0 && p.partial < 1.0;

				long numProduced = (long)f;
				produced(p, numProduced);
			}
		}

		//TODO- consumers


		lastUpdated = curTime;
	}

	public static class Producer
	{
		CommoditiesBag output;
		double rate;
		double partial;

		public Producer(CommoditiesBag output, double rate)
		{
			this.output = output;
			this.rate = rate;
			this.partial = 0.0;
		}

		public CommoditiesBag getOutput()
		{
			return output;
		}

		public double getRate()
		{
			return rate;
		}

		protected void produced(long numProduced)
		{
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
