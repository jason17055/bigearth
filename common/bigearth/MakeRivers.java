package bigearth;

import java.util.*;

public class MakeRivers
{
	MakeWorld world;
	List<Geometry.VertexId> todo;
	Set<Geometry.VertexId> remaining;
	Map<Geometry.VertexId, Geometry.VertexId> drainage;
	Map<Geometry.EdgeId, RiverInfo> rivers;
	Map<Geometry.VertexId, LakeInfo> lakes;

	static final int LAKE_UNIT_VOLUME = 2000;

	public MakeRivers(MakeWorld world)
	{
		this.world = world;
		this.todo = new ArrayList<Geometry.VertexId>();
		this.remaining = new HashSet<Geometry.VertexId>();
		this.drainage = new HashMap<Geometry.VertexId, Geometry.VertexId>();
		this.rivers = new HashMap<Geometry.EdgeId, RiverInfo>();
	}

	void initialize()
	{
		todo.clear();
		remaining.clear();
		drainage.clear();
		rivers.clear();

		int numCells = world.g.getCellCount();
		for (int i = 1; i <= numCells; i++)
		{
			for (Geometry.VertexId v : world.g.getSurroundingVertices(i))
			{
				remaining.add(v);
			}
		}
	}

	private double getVertexHeight(Geometry.VertexId vtx)
	{
		double sum = 0.0;
		int [] cells = vtx.getAdjacentCells();
		for (int cellId : cells)
		{
			sum += world.elevation[cellId-1];
		}
		return sum / cells.length;
	}

	/**
	 * @return true if a river was added, false if no more rivers to add
	 */
	private boolean addRiverAt(Geometry.VertexId fromVertex)
	{
		double myHeight = getVertexHeight(fromVertex);
		ArrayList<Geometry.VertexId> candidates = new ArrayList<Geometry.VertexId>();
		for (Geometry.VertexId neighborVertex : world.g.getNearbyVertices(fromVertex))
		{
			if (!remaining.contains(neighborVertex))
				continue;

			double neighborHeight = getVertexHeight(neighborVertex);
			if (neighborHeight < myHeight)
				continue; // never create a river coming from lower elevation

			candidates.add(neighborVertex);
		}

		if (candidates.isEmpty())
			return false;

		int i = (int) Math.floor(Math.random() * candidates.size());
		Geometry.VertexId toVertex = candidates.get(i);

		remaining.remove(toVertex);
		todo.add(toVertex);

		RiverInfo ri = new RiverInfo();
		ri.upstreamVertex = toVertex;
		rivers.put(world.g.getEdgeByEndpoints(fromVertex, toVertex), ri);
		drainage.put(toVertex, fromVertex);

		return true;
	}

	/**
	 * @return true if a river was added, false if no more rivers to add
	 */
	private boolean drainageStep()
	{
		for (;;)
		{
			if (todo.isEmpty())
			{
				// find a starting spot
				ArrayList<Geometry.VertexId> candidates = new ArrayList<Geometry.VertexId>();
				double bestH = Double.POSITIVE_INFINITY;

				for (Geometry.VertexId v : remaining)
				{
					double h = getVertexHeight(v);
					if (h < bestH)
					{
						bestH = h;
						candidates.clear();
						candidates.add(v);
					}
					else if (h == bestH)
					{
						candidates.add(v);
					}
				}

				if (candidates.isEmpty())
					return false; //no more rivers to add

				int i = (int) Math.floor(Math.random() * candidates.size());
				Geometry.VertexId choice = candidates.get(i);
				remaining.remove(choice);
				todo.add(choice);
			}

			// choose from TODO array randomly
			int i = (int) Math.floor(Math.random() * todo.size());
			Geometry.VertexId v = todo.get(i);
			if (addRiverAt(v))
				return true;

			todo.remove(i);
		}
	}

	void addWaterToRiver(Geometry.VertexId startVtx, int water)
	{
		Geometry.VertexId v = startVtx;
		while (drainage.containsKey(v))
		{
			Geometry.VertexId nextVtx = drainage.get(v);
			Geometry.EdgeId eId = world.g.getEdgeByEndpoints(v, nextVtx);
			RiverInfo r = rivers.get(eId);
			assert r != null;

			r.volume += water;
			v = nextVtx;
		}

		if (!lakes.containsKey(v))
		{
			lakes.put(v, new LakeInfo());
		}
		lakes.get(v).volume += water;
	}

	private void processMultiHexLake(LakeInfo lake)
	{
		assert lake.remaining > 0;

		// a list of adjacent regions that this lake can expand to
		Set<Integer> adjacentRegions = new HashSet<Integer>();

		// a list of bording vertices that drain away from this lake
		Set<Geometry.VertexId> drains = new HashSet<Geometry.VertexId>();

		for (int aRegion : lake.regions)
		{
			for (int nid : world.g.getNeighbors(aRegion))
			{
				if (!lake.regions.contains(nid))
				{
					if (world.elevation[nid-1] < lake.waterLevel)
						adjacentRegions.add(nid);
				}
			}

			for (Geometry.VertexId vId : world.g.getSurroundingVertices(aRegion))
			{
				if (lake.isOnBorder(vId))
				{
					Geometry.VertexId sink = vId;
					while (drainage.containsKey(sink))
					{
						sink = drainage.get(sink);
						if (lake.contains(sink))
							break;
					}

					if (!lake.contains(sink))
					{
		System.out.println("lake outlet "+vId+" drains to "+sink);
						drains.add(vId);
					}
				}
			}
		}

		// check if any of the bording vertices drain out of this lake
		if (!drains.isEmpty())
		{
			int num = drains.size();
			for (Geometry.VertexId drainVtx : drains)
			{
				int water = lake.remaining / num;
				addWaterToRiver(drainVtx, water);
				lake.remaining -= water;
				num--;
			}

			assert num == 0;
			assert lake.remaining == 0;

System.out.println("found outlet for lake");
			return;
		}

		// check if there are any adjacent vertices we can expand to
		if (!adjacentRegions.isEmpty())
		{
			Integer [] a = adjacentRegions.toArray(new Integer[0]);
			int i = (int) Math.floor(Math.random() * a.length);
			int expandTo = a[i];

			lake.regions.add(expandTo);
			lake.remaining -= LAKE_UNIT_VOLUME * (lake.waterLevel - world.elevation[expandTo-1]);

System.out.println("lake area is now " + lake.regions.size()+"; ("+lake.remaining+ " remaining)");
			return;
		}

		// increase height of this lake
		lake.waterLevel++;
		lake.remaining -= LAKE_UNIT_VOLUME * lake.regions.size();
System.out.println("lake water level is now "+lake.waterLevel+"; ("+lake.remaining + " remaining)");
		return;
	}

	private void processSingleHexLake(int regionId, LakeInfo lake)
	{
		System.out.println("region "+regionId+" is now a lake");
		System.out.println("remaining volume "+lake.volume);

		while (lake.remaining > 0)
		{
			processMultiHexLake(lake);
		}
	}

	private void processSinglePointLake(Geometry.VertexId lakeVertex)
	{
		// see if we can carve a path to a neighboring separate river system
		Geometry.VertexId [] candidates = world.g.getNearbyVertices(lakeVertex);
		Geometry.VertexId best = null;
		double bestV = Double.POSITIVE_INFINITY;

		double lakeHeight = getVertexHeight(lakeVertex);
		int water = lakes.get(lakeVertex).volume;

		for (Geometry.VertexId neighborVertex : candidates)
		{
			Geometry.VertexId termVtx = neighborVertex;
			while (drainage.containsKey(termVtx))
			{
				termVtx = drainage.get(termVtx);
			}

			// the neighboring river we pick must terminate at some
			// vertex other than our own
			if (termVtx == lakeVertex) 
				continue;

			// the neighboring river we pick must terminate at a
			// lower-elevation vertex
			if (getVertexHeight(termVtx) >= lakeHeight)
				continue;

			// assuming the above criteria are met, pick the
			// neighboring vertex which is lowest in elevation

			double h = getVertexHeight(neighborVertex);
			if (h < bestV)
			{
				bestV = h;
				best = neighborVertex;
			}
		}

		LakeInfo lake = lakes.get(lakeVertex);
		if (best != null)
		{
			lake.type = LakeType.NONTERMINAL;

			RiverInfo ri = new RiverInfo();
			ri.upstreamVertex = lakeVertex;
			ri.volume = water;
			rivers.put(world.g.getEdgeByEndpoints(lakeVertex, best), ri);
			drainage.put(lakeVertex, best);

			// add this lake's volume to the downstream river
			addWaterToRiver(best, water);
		}
		else
		{
			int [] cc = lakeVertex.getAdjacentCells();
			int lowest = 0;
			for (int i = 1; i < cc.length; i++)
			{
				if (world.elevation[cc[i]-1] < world.elevation[cc[lowest]-1])
				{
					lowest = i;
				}
			}

			// expand lake
			int regionId = cc[lowest];
			lake.remaining = lake.volume;
			lake.remaining -= LAKE_UNIT_VOLUME;
			lake.waterLevel = world.elevation[regionId-1]+1;
			lake.regions.add(regionId);

			if (lake.remaining > 0)
			{
				processSingleHexLake(regionId, lake);
			}
		}
	}

	public void generateRivers()
	{
		for (RegionDetail r : world.regions)
		{
			r.biome = BiomeType.GRASSLAND;
			r.clearSides();
		}

		initialize();
		while (drainageStep());

		//
		// determine volume of rivers
		//

		lakes = new HashMap<Geometry.VertexId,LakeInfo>();
		for (Geometry.VertexId vtx : drainage.keySet())
		{
			int [] cells = vtx.getAdjacentCells();
			int water = 0;

			for (int cellId : cells)
			{
				water += world.annualRains[cellId-1];
			}
			water = (water - 100) / 30;
			if (water <= 0)
				continue;

			addWaterToRiver(vtx, water);
		}

		//
		// process the lakes
		//

		Geometry.VertexId [] lakeVertices = lakes.keySet().toArray(new Geometry.VertexId[0]);
		Arrays.sort(lakeVertices,
			new Comparator<Geometry.VertexId>() {
				public int compare(Geometry.VertexId a, Geometry.VertexId b)
				{
					double a_el = getVertexHeight(a);
					double b_el = getVertexHeight(b);
					return -(a_el > b_el ? 1 :
						a_el < b_el ? -1 : 0);
				}
			});

		// this goes through the lakes from highest-elevation to lowest-elevation
		for (Geometry.VertexId lakeVertex : lakeVertices)
		{
			processSinglePointLake(lakeVertex);
		}

		//
		// place lakes
		//

		for (Geometry.VertexId vId : lakes.keySet())
		{
			LakeInfo lake = lakes.get(vId);
			if (lake.regions.isEmpty())
			{
				int [] cc = vId.getAdjacentCells();

				int aRegion = cc[0];
				RegionDetail region = world.regions[aRegion-1];
				region.setLake(vId,
					lake.type == LakeType.TERMINAL ? RegionCornerDetail.PointFeature.LAKE :
					RegionCornerDetail.PointFeature.POND
					);
			}
			else
			{
System.out.println("lake at "+vId+" covers "+lake.regions.size() + " regions");
				for (Integer rId : lake.regions)
				{
					int regionId = rId;
					world.regions[regionId-1].biome = BiomeType.LAKE;
					world.regions[regionId-1].waterLevel = lake.waterLevel;
				}
			}
		}

		//
		// place rivers
		//

		for (Geometry.EdgeId eId : rivers.keySet())
		{
			RiverInfo r = rivers.get(eId);
			int [] cc = r.upstreamVertex.getAdjacentCells();
			int [] dd = eId.getAdjacentCells();

			if (world.regions[dd[0]-1].biome.isWater())
				continue;
			if (world.regions[dd[1]-1].biome.isWater())
				continue;

			int aRegion;
			int bRegion;
			if (cc[0] != dd[0] && cc[0] != dd[1])
			{
				aRegion = cc[1];
				bRegion = cc[2];
			}
			else if (cc[1] != dd[0] && cc[1] != dd[1])
			{
				aRegion = cc[2];
				bRegion = cc[0];
			}
			else
			{
				aRegion = cc[0];
				bRegion = cc[1];
			}

			RegionDetail region = world.regions[bRegion-1];
			region.setRiver(aRegion,
				r.volume > 2000 ? RegionSideDetail.SideFeature.RIVER :
				r.volume > 200 ? RegionSideDetail.SideFeature.CREEK :
				RegionSideDetail.SideFeature.BROOK);
		}
	}

	static class RiverInfo
	{
		int volume;
		Geometry.VertexId upstreamVertex;
	}

	static enum LakeType
	{
		TERMINAL,
		NONTERMINAL;
	}

	static class LakeInfo
	{
		int volume;
		LakeType type;
		Set<Integer> regions;
		int waterLevel;
		int remaining;

		LakeInfo()
		{
			this.volume = 0;
			this.remaining = 0;
			this.type = LakeType.TERMINAL;
			this.regions = new HashSet<Integer>();
			this.waterLevel = Integer.MIN_VALUE;
		}

		/**
		 * True iff the vertex is on an outer corner of this lake.
		 * I.e. the vertex touches exactly one region of the lake.
		 */
		boolean isOnBorder(Geometry.VertexId vtx)
		{
			int count = 0;
			for (int adjCell : vtx.getAdjacentCells())
			{
				if (this.regions.contains(adjCell))
					count++;
			}

			return (count == 1);
		}

		boolean contains(Geometry.VertexId vtx)
		{
			int count = 0;
			for (int adjCell : vtx.getAdjacentCells())
			{
				if (this.regions.contains(adjCell))
					count++;
			}

			return (count != 0);
		}
	}
}
