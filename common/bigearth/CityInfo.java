package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

public class CityInfo
{
	String displayName;
	String owner;
	Location location;
	CommoditiesBag stock;
	int population;
	boolean populationIsKnown;
	int children;
	boolean childrenIsKnown;
	int houses;
	boolean housesIsKnown;
	int underConstruction;
	boolean underConstructionIsKnown;
	int farms;
	boolean farmsIsKnown;
	int pastures;
	boolean pasturesIsKnown;
	Set<Technology> science;
	Set<Technology> partialScience;
	int scientists;
	boolean scientistsIsKnown;

	CityInfo()
	{
	}

	public long getStock(CommodityType ct)
	{
		assert hasStock();

		return stock.getQuantity(ct);
	}

	public boolean hasChildren()
	{
		return childrenIsKnown;
	}

	public boolean hasDisplayName()
	{
		return displayName != null;
	}

	public boolean hasFarms()
	{
		return farmsIsKnown;
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

	public boolean hasPartialScience()
	{
		return partialScience != null;
	}

	public boolean hasPastures()
	{
		return pasturesIsKnown;
	}

	public boolean hasPopulation()
	{
		return populationIsKnown;
	}

	public boolean hasScience()
	{
		return science != null;
	}

	public boolean hasScientists()
	{
		return scientistsIsKnown;
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

	public void setFarms(int farms)
	{
		this.farms = farms;
		this.farmsIsKnown = true;
	}

	public void setHouses(int houses)
	{
		this.houses = houses;
		this.housesIsKnown = true;
	}

	public void setPastures(int pastures)
	{
		this.pastures = pastures;
		this.pasturesIsKnown = true;
	}

	public void setPopulation(int population)
	{
		this.population = population;
		this.populationIsKnown = true;
	}

	public void setScientists(int scientists)
	{
		this.scientists = scientists;
		this.scientistsIsKnown = true;
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
			else if (s.equals("farms"))
			{
				in.nextToken();
				setFarms(in.getIntValue());
			}
			else if (s.equals("houses"))
			{
				in.nextToken();
				setHouses(in.getIntValue());
			}
			else if (s.equals("owner"))
				owner = in.nextTextValue();
			else if (s.equals("location"))
				location = LocationHelper.parse(in.nextTextValue(), world);
			else if (s.equals("partialScience"))
				partialScience = TechnologyBag.parseTechnologySet(in);
			else if (s.equals("pastures"))
			{
				in.nextToken();
				setPastures(in.getIntValue());
			}
			else if (s.equals("science"))
				science = TechnologyBag.parseTechnologySet(in);
			else if (s.equals("stock"))
				stock = CommoditiesBag.parse(in);
			else if (s.equals("population"))
			{
				in.nextToken();
				population = in.getIntValue();
				populationIsKnown = true;
			}
			else if (s.equals("scientists"))
			{
				in.nextToken();
				setScientists(in.getIntValue());
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
		if (hasFarms())
			out.writeNumberField("farms", farms);
		if (hasHouses())
			out.writeNumberField("houses", houses);
		if (hasLocation())
			out.writeStringField("location", location.toString());
		if (hasOwner())
			out.writeStringField("owner", owner);
		if (hasPartialScience())
		{
			out.writeFieldName("partialScience");
			TechnologyBag.writeTechnologySet(out, partialScience);
		}
		if (hasPastures())
			out.writeNumberField("pastures", pastures);
		if (hasPopulation())
			out.writeNumberField("population", population);
		if (hasScience())
		{
			out.writeFieldName("science");
			TechnologyBag.writeTechnologySet(out, science);
		}
		if (hasScientists())
			out.writeNumberField("scientists", scientists);
		if (hasStock())
		{
			out.writeFieldName("stock");
			stock.write(out);
		}
		if (hasUnderConstruction())
			out.writeNumberField("underConstruction", underConstruction);
		out.writeEndObject();
	}
}
