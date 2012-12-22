package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

public class CityInfo
{
	String displayName;
	String owner;
	Location location;
	Map<CommodityType, Long> stock;
	int population;
	boolean populationIsKnown;
	int children;
	boolean childrenIsKnown;
	int houses;
	boolean housesIsKnown;
	int underConstruction;
	boolean underConstructionIsKnown;

	CityInfo()
	{
	}

	public long getStock(CommodityType ct)
	{
		assert hasStock();

		Long x = stock.get(ct);
		return x != null ? x.longValue() : 0;
	}

	public boolean hasChildren()
	{
		return childrenIsKnown;
	}

	public boolean hasDisplayName()
	{
		return displayName != null;
	}

	public boolean hasHouses()
	{
		return housesIsKnown;
	}

	public boolean hasOwner()
	{
		return owner != null;
	}

	public boolean hasLocation()
	{
		return location != null;
	}

	public boolean hasPopulation()
	{
		return populationIsKnown;
	}

	public boolean hasStock()
	{
		return stock != null;
	}

	public boolean hasUnderConstruction()
	{
		return underConstructionIsKnown;
	}

	public void setChildren(int children)
	{
		this.children = children;
		this.childrenIsKnown = true;
	}

	public void setHouses(int houses)
	{
		this.houses = houses;
		this.housesIsKnown = true;
	}

	public void setPopulation(int population)
	{
		this.population = population;
		this.populationIsKnown = true;
	}

	public void setUnderConstruction(int underConstruction)
	{
		this.underConstruction = underConstruction;
		this.underConstructionIsKnown = true;
	}

	public static CityInfo parse(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		CityInfo c = new CityInfo();
		c.parse_real(in, world);
		return c;
	}

	private void parse_real(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("children"))
			{
				in.nextToken();
				setChildren(in.getIntValue());
			}
			else if (s.equals("displayName"))
				displayName = in.nextTextValue();
			else if (s.equals("houses"))
			{
				in.nextToken();
				setHouses(in.getIntValue());
			}
			else if (s.equals("owner"))
				owner = in.nextTextValue();
			else if (s.equals("location"))
				location = LocationHelper.parse(in.nextTextValue(), world);
			else if (s.equals("stock"))
				stock = CommoditiesHelper.parseCommodities(in);
			else if (s.equals("population"))
			{
				in.nextToken();
				population = in.getIntValue();
				populationIsKnown = true;
			}
			else if (s.equals("underConstruction"))
			{
				in.nextToken();
				setUnderConstruction(in.getIntValue());
			}
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized city property: " + s);
			}
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}

	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		if (hasChildren())
			out.writeNumberField("children", children);
		if (hasDisplayName())
			out.writeStringField("displayName", displayName);
		if (hasHouses())
			out.writeNumberField("houses", houses);
		if (hasLocation())
			out.writeStringField("location", location.toString());
		if (hasOwner())
			out.writeStringField("owner", owner);
		if (hasPopulation())
			out.writeNumberField("population", population);
		if (hasStock())
		{
			out.writeFieldName("stock");
			CommoditiesHelper.writeCommodities(stock, out);
		}
		if (hasUnderConstruction())
			out.writeNumberField("underConstruction", underConstruction);
		out.writeEndObject();
	}
}
