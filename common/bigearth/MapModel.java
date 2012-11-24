package bigearth;

import java.util.*;

public class MapModel
{
	Geometry geometry;
	Map<Location, RegionProfile> regions;

	public MapModel(Geometry geometry)
	{
		this.geometry = geometry;
		this.regions = new HashMap<Location, RegionProfile>();
	}

	public Geometry getGeometry()
	{
		return geometry;
	}

	public RegionProfile getRegion(int regionId)
	{
		Location loc = new SimpleLocation(regionId);
		return regions.get(loc);
	}

	public void put(Location loc, RegionProfile p)
	{
		regions.put(loc, p);
	}
}
