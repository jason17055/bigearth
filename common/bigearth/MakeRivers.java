package bigearth;

import java.util.*;

public class MakeRivers
{
	MakeWorld world;
	TerrainGeometry tg;

	MakeRivers(MakeWorld world)
	{
		this.world = world;
		this.tg = world.getTerrainGeometry();
	}

	int getTerrain(TerrainId tid)
	{
		RegionDetail r = world.regions[tid.regionId-1];
		return r.terrains[tid.tile];
	}

	void putTerrain(TerrainId tid, TerrainType terrainType)
	{
		RegionDetail r = world.regions[tid.regionId-1];
		r.setTerrainType(tid.tile, terrainType);
	}

	public void makeRivers(TerrainId startFrom)
	{
		putTerrain(startFrom, TerrainType.STREAM);

		ArrayList<TerrainId> workingSet = new ArrayList<TerrainId>();
		workingSet.add(startFrom);

		final int GRASS = TerrainType.GRASS.id;
		int count = 1;
		while (!workingSet.isEmpty() && count < 100)
		{
			int i = (int)Math.floor(Math.random() * workingSet.size());
			TerrainId tid = workingSet.get(i);

			TerrainId [] neighbors = tg.getNeighborTiles(tid);
			ArrayList<TerrainId> candidates = new ArrayList<TerrainId>();
			for (int j = 0; j < 3; j++)
			{
				if (getTerrain(neighbors[j]) != GRASS)
					continue;

				boolean grassOnLeft =
					getTerrain(tg.car(neighbors[j], tid)) == GRASS &&
					getTerrain(tg.caar(neighbors[j], tid)) == GRASS;
				boolean grassOnRight =
					getTerrain(tg.cdr(neighbors[j], tid)) == GRASS &&
					getTerrain(tg.cddr(neighbors[j], tid)) == GRASS;
				if (grassOnLeft && grassOnRight)
				{
					candidates.add(neighbors[j]);
				}
			}

			if (candidates.size() == 0)
			{
				workingSet.remove(i);
				continue;
			}

			int j = (int)Math.floor(Math.random() * candidates.size());
			TerrainId tile1 = candidates.get(j);
			putTerrain(tile1, TerrainType.STREAM);
			workingSet.add(tile1);
			count++;
		}
	}
}
