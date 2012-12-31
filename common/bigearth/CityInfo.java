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
	Set<Technology> science;
	Set<Technology> partialScience;
	int scientists;
	boolean scientistsIsKnown;
	Map<String,ZoneInfo> zones;
	Set<ZoneType> newZoneChoices;

	public CityInfo()
	{
	}

	public int getHouses()
	{
		assert hasZones();

		return getZoneCount(ZoneType.MUD_COTTAGE)
			+ getZoneCount(ZoneType.WOOD_COTTAGE)
			+ getZoneCount(ZoneType.STONE_COTTAGE);
	}

	public long getStock(CommodityType ct)
	{
		assert hasStock();

		return stock.getQuantity(ct);
	}

	public int getZoneCount(ZoneType zoneType)
	{
		assert hasZones();

		int count = 0;
		for (ZoneInfo zone : zones.values())
		{
			if (zone.type == zoneType)
				count++;
		}
		return count;
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
		return hasZones();
	}

	public boolean hasHouses()
	{
		return hasZones();
	}

	public boolean hasNewZoneChoices()
	{
		return newZoneChoices != null;
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
		return hasZones();
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
		return hasZones();
	}

	public boolean hasZones()
	{
		return zones != null;
	}

	public void setChildren(int children)
	{
		this.children = children;
		this.childrenIsKnown = true;
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
			else if (s.equals("owner"))
				owner = in.nextTextValue();
			else if (s.equals("location"))
				location = LocationHelper.parse(in.nextTextValue(), world);
			else if (s.equals("partialScience"))
				partialScience = TechnologyBag.parseTechnologySet(in);
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
			else if (s.equals("newZoneChoices"))
				parseNewZoneChoices(in);
			else if (s.equals("zones"))
			{
				parseZones(in);
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

	private void parseNewZoneChoices(JsonParser in)
		throws IOException
	{
		newZoneChoices = new HashSet<ZoneType>();
		in.nextToken();
		if (in.getCurrentToken() != JsonToken.START_ARRAY)
			throw new InputMismatchException();

		while (in.nextToken() != JsonToken.END_ARRAY)
		{
			ZoneType z = ZoneType.valueOf(in.getText());
			newZoneChoices.add(z);
		}
	}

	private void parseZones(JsonParser in)
		throws IOException
	{
		zones = new HashMap<String,ZoneInfo>();
		in.nextToken();
		if (in.getCurrentToken() != JsonToken.START_OBJECT)
			throw new InputMismatchException();

		while (in.nextToken() != JsonToken.END_OBJECT)
		{
			String s = in.getCurrentName();
			ZoneInfo zone = ZoneInfo.parse(in);

			zones.put(s, zone);
		}
	}

	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		if (hasChildren())
			out.writeNumberField("children", children);
		if (hasDisplayName())
			out.writeStringField("displayName", displayName);
		if (hasLocation())
			out.writeStringField("location", location.toString());
		if (hasOwner())
			out.writeStringField("owner", owner);
		if (hasPartialScience())
		{
			out.writeFieldName("partialScience");
			TechnologyBag.writeTechnologySet(out, partialScience);
		}
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
		if (hasNewZoneChoices())
		{
			out.writeFieldName("newZoneChoices");
			writeNewZoneChoices(out);
		}
		if (hasZones())
		{
			out.writeFieldName("zones");
			writeZones(out);
		}
		out.writeEndObject();
	}

	private void writeNewZoneChoices(JsonGenerator out)
		throws IOException
	{
		out.writeStartArray();
		for (ZoneType z : newZoneChoices)
		{
			out.writeString(z.name());
		}
		out.writeEndArray();
	}

	private void writeZones(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		for (Map.Entry<String,ZoneInfo> e : zones.entrySet())
		{
			String name = e.getKey();
			ZoneInfo zone = e.getValue();

			out.writeFieldName(name);
			zone.write(out);
		}
		out.writeEndObject();
	}
}
