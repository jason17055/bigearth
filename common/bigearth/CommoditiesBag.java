package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

class CommoditiesBag
{
	private Map<CommodityType, Long> stock;

	/**
	 * A shared CommoditiesBag object to represent an empty stock or
	 * cost or reward. This should be treated as an immutable object,
	 * since the same object is shared by others.
	 */
	public static final CommoditiesBag EMPTY = new CommoditiesBag();

	public CommoditiesBag()
	{
		stock = new EnumMap<CommodityType, Long>(CommodityType.class);
	}

	public CommoditiesBag clone()
	{
		CommoditiesBag rv = new CommoditiesBag();
		rv.stock.putAll(this.stock);
		return rv;
	}

	static CommoditiesBag parse(JsonParser in)
		throws IOException
	{
		CommoditiesBag rv = new CommoditiesBag();

		in.nextToken();
		if (in.getCurrentToken() != JsonToken.START_OBJECT)
			throw new InputMismatchException();

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			CommodityType ct = CommodityType.valueOf(in.getCurrentName());
			in.nextToken();
			long amt = in.getLongValue();

			rv.stock.put(ct, amt);
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;

		return rv;
	}

	public void write(JsonGenerator out)
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

	public boolean contentsEqual(Object obj)
	{
		CommoditiesBag rhs = (CommoditiesBag) obj;

		if (this.stock.size() != rhs.stock.size())
			return false;
		for (CommodityType ct : this.stock.keySet())
		{
			if (!rhs.stock.containsKey(ct))
				return false;
			long amt1 = this.stock.get(ct);
			long amt2 = rhs.stock.get(ct);
			if (amt1 != amt2)
				return false;
		}
		return true;
	}

	public void add(CommodityType ct, long amount)
	{
		assert amount >= 0;
		if (amount == 0)
			return;

		if (stock.containsKey(ct))
		{
			long amt = stock.get(ct);
			amt += amount;
			stock.put(ct, amt);
		}
		else
		{
			stock.put(ct, amount);
		}
	}

	public void subtract(CommoditiesBag rhs)
	{
		for (Map.Entry<CommodityType, Long> e : rhs.stock.entrySet())
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

		if (stock.containsKey(ct))
		{
			long curBal = stock.get(ct);
			if (amount < curBal)
			{
				stock.put(ct, curBal - amount);
				return amount;
			}
			else
			{
				stock.remove(ct);
				return curBal;
			}
		}
		return 0;
	}

	public void clear()
	{
		stock.clear();
	}

	public boolean isEmpty()
	{
		return stock.isEmpty();
	}

	public boolean isSupersetOf(CommoditiesBag rhs)
	{
		for (Map.Entry<CommodityType, Long> e : rhs.stock.entrySet())
		{
			CommodityType ct = e.getKey();
			long qty = e.getValue();

			if (getQuantity(ct) < qty)
				return false;
		}
		return true;
	}

	public CommodityType [] getCommodityTypesArray()
	{
		return stock.keySet().toArray(new CommodityType[0]);
	}

	public long getQuantity(CommodityType ct)
	{
		Long x = stock.get(ct);
		return x != null ? x.longValue() : 0;
	}

	public double getTotalMass()
	{
		double totalMass = 0.0;
		for (Map.Entry<CommodityType, Long> e : stock.entrySet())
		{
			CommodityType ct = e.getKey();
			long qty = e.getValue();
			totalMass += ct.mass * qty;
		}
		return totalMass;
	}
}
