package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

public class CityServant
{
	transient RegionServant parentRegion;

	String displayName;
	String owner;
	Location location;
	Map<CommodityType, Long> stock;
	int population;

	CityServant(RegionServant parentRegion)
	{
		this.parentRegion = parentRegion;
		this.stock = new EnumMap<CommodityType, Long>(CommodityType.class);
		this.population = 0;
	}

	// called when server is starting up
	public void start()
	{
		//nothing to do
	}

	public static CityServant parse(JsonParser in, RegionServant parentRegion)
		throws IOException
	{
		CityServant city = new CityServant(parentRegion);
		city.parse(in);
		return city;
	}

	public void addPopulation(int population)
	{
		this.population += population;
		cityChanged();
	}

	void cityChanged()
	{
		if (owner != null)
		{
			CityInfo data = makeInfoFor(owner);
			CityUpdateNotification n = new CityUpdateNotification(owner, data);
			notifyLeader(owner, n);
		}
	}

	private WorldConfigIfc getWorldConfig()
	{
		return parentRegion.getWorldConfig();
	}

	private WorldMaster getWorldMaster()
	{
		return parentRegion.world;
	}

	private void notifyLeader(String user, Notification n)
	{
		getWorldMaster().notifyLeader(user, n);
	}

	CityInfo makeInfoFor(String user)
	{
		CityInfo ci = new CityInfo();
		ci.displayName = displayName;
		ci.location = location;
		return ci;
	}

	boolean canUserRename(String user)
	{
		return (owner != null && owner.equals(user));
	}

	private void parse(JsonParser in)
		throws IOException
	{
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("displayName"))
				displayName = in.nextTextValue();
			else if (s.equals("owner"))
				owner = in.nextTextValue();
			else if (s.equals("location"))
				location = LocationHelper.parse(in.nextTextValue(), getWorldConfig());
			else if (s.equals("stock"))
				stock = CommoditiesHelper.parseCommodities(in);
			else if (s.equals("population"))
			{
				in.nextToken();
				population = in.getIntValue();
			}
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized city property: "+s);
			}
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}

	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("displayName", displayName);
		out.writeStringField("owner", owner);
		out.writeStringField("location", location.toString());
		out.writeFieldName("stock");
		CommoditiesHelper.writeCommodities(stock, out);
		out.writeNumberField("population", population);
		out.writeEndObject();
	}
}
