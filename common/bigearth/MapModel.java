package bigearth;

import java.io.*;
import java.util.*;
import com.fasterxml.jackson.core.*;

public class MapModel
{
	Geometry geometry;
	Map<Location, RegionProfile> regions;
	List<Listener> listeners = new ArrayList<Listener>();

	public MapModel(Geometry geometry)
	{
		this.geometry = geometry;
		this.regions = new HashMap<Location, RegionProfile>();
	}

	public Geometry getGeometry()
	{
		return geometry;
	}

	static final RegionProfile UNKNOWN_REGION = new RegionProfile();
	public RegionProfile getRegion(int regionId)
	{
		return getRegion(new SimpleLocation(regionId));
	}

	public RegionProfile getRegion(Location loc)
	{
		if (regions.containsKey(loc))
			return regions.get(loc);
		else
			return UNKNOWN_REGION;
	}

	public void put(Location loc, RegionProfile p)
	{
		regions.put(loc, p);
		fireRegionUpdated(loc);
	}

	public boolean updateRegion(Location loc, RegionProfile p)
	{
		if (regions.containsKey(loc))
		{
			RegionProfile old = regions.get(loc);
			if (old.equals(p))
			{
				return false;
			}
			regions.put(loc, old.merge(p));
			return true;
		}
		else
		{
			regions.put(loc, p);
			return true;
		}
	}

	public void addListener(Listener l)
	{
		this.listeners.add(l);
	}

	public void removeListener(Listener l)
	{
		this.listeners.remove(l);
	}

	private void fireRegionUpdated(Location loc)
	{
		for (Listener l : listeners)
		{
			l.regionUpdated(loc);
		}
	}

	public interface Listener
	{
		void regionUpdated(Location loc);
	}

	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();

		for (Location loc : regions.keySet())
		{
			out.writeFieldName(loc.toString());

			RegionProfile p = regions.get(loc);
			p.write(out);
		}

		out.writeEndObject();
	}

	public void parse(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			Location loc = LocationHelper.parse(s, getGeometry());
			RegionProfile p = RegionProfile.parse_s(in, world);
			this.put(loc, p);
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}
}
