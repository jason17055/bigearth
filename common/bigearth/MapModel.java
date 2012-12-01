package bigearth;

import java.util.*;

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

	public RegionProfile getRegion(int regionId)
	{
		Location loc = new SimpleLocation(regionId);
		return regions.get(loc);
	}

	public void put(Location loc, RegionProfile p)
	{
		regions.put(loc, p);
		fireRegionUpdated(loc);
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
}
