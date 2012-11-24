package bigearth;

import java.util.*;

public class MapModel
{
	Map<Location, RegionProfile> regions;

	public MapModel()
	{
		this.regions = new HashMap<Location, RegionProfile>();
	}

	public void put(Location loc, RegionProfile p)
	{
		regions.put(loc, p);
	}
}
