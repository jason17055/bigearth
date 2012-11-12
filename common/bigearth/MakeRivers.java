package bigearth;

import java.util.*;

public class MakeRivers
{
	MakeWorld world;
	List<Geometry.VertexId> todo;
	Set<Geometry.VertexId> remaining;
	Map<Geometry.VertexId, Geometry.VertexId> drainage;
	Map<Geometry.VertexId, Double> riverElevation;
	Map<Geometry.EdgeId, RiverInfo> rivers;
	Map<Geometry.VertexId, LakeInfo> lakes;
	Map<Integer, LakeInfo> lakesByRegion;

	static final int LAKE_UNIT_VOLUME = 1500;
	static final int OCEAN_SIZE_THRESHOLD = 12;
	static final double MIN_RIVER_SLOPE = 0.1;

	public MakeRivers(MakeWorld world)
	{
		this.world = world;
		this.todo = new ArrayList<Geometry.VertexId>();
		this.remaining = new HashSet<Geometry.VertexId>();
		this.drainage = new HashMap<Geometry.VertexId, Geometry.VertexId>();
		this.riverElevation = new HashMap<Geometry.VertexId, Double>();
		this.rivers = new HashMap<Geometry.EdgeId, RiverInfo>();
		this.lakes = new HashMap<Geometry.VertexId,LakeInfo>();
		this.lakesByRegion = new HashMap<Integer, LakeInfo>();
	}

	void initialize()
	{
		todo.clear();
		remaining.clear();
		drainage.clear();
		riverElevation.clear();
		rivers.clear();
		lakes.clear();
		lakesByRegion.clear();

		int numCells = world.g.getCellCount();
		for (int i = 1; i <= numCells; i++)
		{
			for (Geometry.VertexId v : world.g.getSurroundingVertices(i))
			{
				remaining.add(v);
				riverElevation.put(v, getVertexHeightReal(v));
			}
		}
	}

	private double getVertexHeight(Geometry.VertexId vtx)
	{
		assert riverElevation.containsKey(vtx);
		return riverElevation.get(vtx);
	}

	private double getVertexHeightReal(Geometry.VertexId vtx)
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

		addRiverSegment(toVertex, fromVertex);
		return true;
	}

	private void addRiverSegment(Geometry.VertexId upstream, Geometry.VertexId downstream)
	{
		RiverInfo ri = new RiverInfo();
		ri.upstreamVertex = upstream;
		rivers.put(world.g.getEdgeByEndpoints(upstream, downstream), ri);
		drainage.put(upstream, downstream);

		assert checkDrainageCycle(upstream);
		enforceRiverSlope(upstream);
	}

	/**
	 * Updates river elevation downstream of a given vertex,
	 * ensuring that the river slopes down in elevation by
	 * at least a certain rate.
	 */
	void enforceRiverSlope(Geometry.VertexId vtx)
	{
		double h = getVertexHeight(vtx);
		while (drainage.containsKey(vtx))
		{
			vtx = drainage.get(vtx);
			double h1 = getVertexHeight(vtx);
			if (h1 <= h - MIN_RIVER_SLOPE)
			{
				h = h1;
			}
			else
			{
				riverElevation.put(vtx, h - MIN_RIVER_SLOPE);
				h = h - MIN_RIVER_SLOPE;
			}
		}

		LakeInfo lake = getLakeAt(vtx);
		if (lake != null)
		{
			double h1 = lake.lakeElevation;
			if (h1 <= h)
			{
				h = h1;
			}
			else
			{
				lake.lakeElevation = h;
			}

			// check all outputs for this river
			for (Geometry.VertexId drainVtx : getLakeDrains(lake))
			{
				h1 = getVertexHeight(drainVtx);
				if (h1 > h)
				{
					riverElevation.put(drainVtx, h);
				}
				enforceRiverSlope(drainVtx);
			}
		}
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

				LakeInfo lake = new LakeInfo(choice, getVertexHeight(choice));
				lake.lakeElevation = lake.originHeight;
				lakes.put(choice, lake);
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
		LakeInfo lake = null; //never add water to initial lake
		while (lake == null && drainage.containsKey(v))
		{
			Geometry.VertexId nextVtx = drainage.get(v);
			Geometry.EdgeId eId = world.g.getEdgeByEndpoints(v, nextVtx);
			RiverInfo r = rivers.get(eId);
			assert r != null;

			r.volume += water;
			v = nextVtx;
			lake = getLakeAt(v);
		}

		assert lake != null;

		lake.volume += water;
		lake.remaining += water;

System.out.println("  added "+water+" water to "+lake.toString());
	}

	private LakeInfo getLakeAt(Geometry.VertexId lakeVertex)
	{
		for (int regionId : lakeVertex.getAdjacentCells())
		{
			if (lakesByRegion.containsKey(regionId))
			{
				return lakesByRegion.get(regionId);
			}
		}

		LakeInfo lake = lakes.get(lakeVertex);
		if (lake != null)
		{
			while (lake.subsumedBy != null)
				lake = lake.subsumedBy;
		}
		return lake;
	}

	private Geometry.VertexId [] getLakeSources(LakeInfo lake)
	{
		Set<Geometry.VertexId> result = new HashSet<Geometry.VertexId>();

		if (lake.isSinglePointLake())
		{
			for (Geometry.VertexId v : world.g.getNearbyVertices(lake.origin))
			{
				if (drainage.containsKey(v) && drainage.get(v).equals(lake.origin))
				{
					result.add(v);
				}
			}
			return result.toArray(new Geometry.VertexId[0]);
		}

		for (int regionId : lake.regions)
		{
			for (Geometry.VertexId v : world.g.getSurroundingVertices(regionId))
			{
				if (lake.isOnBorder(v))
				{
					for (Geometry.VertexId v2 : world.g.getNearbyVertices(v))
					{
						if (drainage.containsKey(v2) && drainage.get(v2).equals(v))
						{
							result.add(v2);
						}
					}
				}
			}
		}
		return v2;
	}

	private Geometry.VertexId [] getLakeDrains(LakeInfo lake)
	{
		if (lake.isSinglePointLake())
		{
			if (drainage.containsKey(lake.origin))
			{
				return new Geometry.VertexId[] { drainage.get(lake.origin) };
			}
			else
			{
				return new Geometry.VertexId[0];
			}
		}

		Set<Geometry.VertexId> drains = new HashSet<Geometry.VertexId>();
		for (int regionid : lake.regions)
		{
			for (Geometry.VertexId vtx : world.g.getSurroundingVertices(regionid))
			{
				if (drainage.containsKey(vtx))
				{
					drains.add(vtx);
				}
			}
		}

		return drains.toArray(new Geometry.VertexId[0]);
	}

	private void processMultiHexLake(LakeInfo lake)
	{
		assert lake.remaining > 0;

System.out.println(lake.toString() + " : processMultiHexLake");

		// a list of bordering vertices that drain away from this lake
		Geometry.VertexId [] drains = getLakeDrains(lake);
		if (!lake.isOcean && drains.length != 0)
		{
			int num = drains.length;
System.out.println("  lake has " + num + " outlets");

			for (Geometry.VertexId drainVtx : drains)
			{
				int water = lake.remaining / num;
				addWaterToRiver(drainVtx, water);
				lake.remaining -= water;
				num--;
			}

			assert num == 0;
			assert lake.remaining == 0;

			return;
		}

		// a list of adjacent regions that this lake can expand to
		Set<Integer> adjacentRegions = new HashSet<Integer>();
		Set<Integer> adjacentLowerRegions = new HashSet<Integer>();

		for (int aRegion : lake.regions)
		{
			for (int nid : world.g.getNeighbors(aRegion))
			{
				if (!lake.regions.contains(nid))
				{
					if (world.elevation[nid-1] == lake.waterLevel-1)
						adjacentRegions.add(nid);
					else if (world.elevation[nid-1] < lake.waterLevel-1)
						adjacentLowerRegions.add(nid);
				}
			}
		}

		// check if there are any adjacent vertices we can expand to
		if (!adjacentRegions.isEmpty())
		{
			Integer [] a = adjacentRegions.toArray(new Integer[0]);
			int i = (int) Math.floor(Math.random() * a.length);
			int expandTo = a[i];

System.out.println("expanding lake to region "+expandTo);
assert !lake.regions.contains(expandTo);

			addRegionToLake(lake, expandTo);
			return;
		}

		// increase height of this lake
		if (adjacentLowerRegions.isEmpty())
		{
			lake.waterLevel++;
			lake.remaining -= LAKE_UNIT_VOLUME * lake.regions.size();
System.out.println("lake water level is now "+lake.waterLevel+"; ("+lake.remaining + " remaining)");
			return;
		}

		// nothing else to do
		System.out.println("nothing else to do");
		lake.remaining = 0;
		return;
	}

	private void reduceLakeDepth(LakeInfo lake1, int newWaterLevel)
	{
		if (newWaterLevel < lake1.waterLevel)
		{
			int delta = lake1.waterLevel - newWaterLevel;
			lake1.remaining += (LAKE_UNIT_VOLUME * delta * lake1.regions.size());
			lake1.waterLevel = newWaterLevel;

		// The following code removes regions from the lake that
		// are now above the new water level. This creates problems
		// when invoked indirectly from addRegionToLake(), because
		// the newly added region may no longer be part of the lake
		//...
		// actually, what I am probably missing is an appropriate
		// call to lakesByRegion.remove()...
		//
		//	Integer [] a = lake1.regions.toArray(new Integer[0]);
		//	for (int r1 : a)
		//	{
		//		if (world.elevation[r1-1] >= lake1.waterLevel)
		//		{
		//			lake1.remaining -= LAKE_UNIT_VOLUME * (world.elevation[r1-1] - lake1.waterLevel);
		//			lake1.regions.remove(r1);
		//		}
		//	}
		}
	}

	private void mergeLakes(LakeInfo lake1, LakeInfo lake2)
	{
		assert lake1 != lake2;

		int newWaterLevel = Math.min(lake1.waterLevel, lake2.waterLevel);
		reduceLakeDepth(lake1, newWaterLevel);
		reduceLakeDepth(lake2, newWaterLevel);

		for (int r2 : lake2.regions)
		{
			lake1.regions.add(r2);
			lakesByRegion.put(r2, lake1);
		}
		lake2.regions.clear();
		lake2.subsumedBy = lake1;

		lake1.remaining += lake2.remaining;
		lake1.volume += lake2.volume;
		lake1.originHeight = Math.min(lake1.originHeight, lake2.originHeight);

		lake2.remaining = 0;
		lake2.volume = 0;
	}

	private void addRegionToLake(LakeInfo lake, int regionId)
	{
System.out.println(lake.toString() + " : addRegionToLake");

		// check if the specified regionId is already part of a
		// regional lake
		if (lakesByRegion.containsKey(regionId))
		{
			LakeInfo otherLake = lakesByRegion.get(regionId);
			mergeLakes(lake, otherLake);
			return;
		}

		// subsume all single-point lakes that border the
		// specified region
		for (LakeInfo lake1 : lakes.values())
		{
			if (lake == lake1)
				continue;
			if (lake1.subsumedBy != null)
				continue;
			if (!lake1.isSinglePointLake())
				continue;

			boolean found = false;
			for (int r1 : lake1.origin.getAdjacentCells())
			{
				if (r1 == regionId)
					found = true;
			}

			if (found)
			{
				lake.remaining += lake1.remaining;
				lake.volume += lake1.volume;
				lake1.remaining = 0;
				lake1.volume = 0;
				lake1.subsumedBy = lake;
			}
		}

		lake.regions.add(regionId);
		lakesByRegion.put(regionId, lake);
		lake.remaining -= LAKE_UNIT_VOLUME * (lake.waterLevel - world.elevation[regionId-1]);

		// check drainage rules for the points around this region
		for (Geometry.VertexId v : world.g.getSurroundingVertices(regionId))
		{
			if (drainage.containsKey(v))
			{
				LakeInfo drainsTo = getSink(v);
				if (drainsTo.lakeElevation >= lake.lakeElevation)
				{
					// can't have this; remove that drainage rule
					System.out.println("  removing drainage "+v);
					drainage.remove(v);
				}
			}
		}
	}

	/**
	 * Determines where a given vertex drains to.
	 * @return the LakeInfo object for where it drains to.
	 */
	private LakeInfo getSink(Geometry.VertexId v)
	{
		assert drainage.containsKey(v);

		for (;;)
		{
			v = drainage.get(v);

			LakeInfo lake = getLakeAt(v);
			if (lake != null)
				return lake;

			assert drainage.containsKey(v);
		}
	}

	private void processLakeExcess(Geometry.VertexId lakeVertex, LakeInfo lake)
	{
		assert lake.subsumedBy == null;
		if (lake.isOcean)
		{
			processOcean(lake);
		}
		else if (lake.regions.isEmpty())
		{
			processSinglePointLake(lake);
		}
		else
		{
			processSingleHexLake(lake);
		}
	}

	private void processSingleHexLake(LakeInfo lake)
	{
		while (lake.remaining > 0)
		{
			processMultiHexLake(lake);
		}

		if (lake.regions.size() >= OCEAN_SIZE_THRESHOLD &&
			lake.waterLevel < 0)
		{
			processOcean(lake);
		}
	}

	private void processOcean(LakeInfo lake)
	{
		// make this an ocean
		lake.isOcean = true;
		while (lake.waterLevel < 0)
		{
			lake.remaining = 1;
System.out.println("OCEAN size "+lake.regions.size() + " depth "+lake.waterLevel);
			processMultiHexLake(lake);
		}
		lake.remaining = 0;
	}

	private void processSinglePointLake(LakeInfo lake)
	{
System.out.println(lake.toString() + " : processSinglePointLake");
		Geometry.VertexId lakeVertex = lake.origin;
		assert lake.subsumedBy == null;
		assert lake.remaining > 0;
		assert getLakeDrains(lake).length == 0;

		// make sure this lake isn't already contained by an
		// already-processed lake
		for (int cellid : lakeVertex.getAdjacentCells())
		{
			if (lakesByRegion.containsKey(cellid))
			{
				throw new Error("unexpected: this lake in another lake; this should be prevented by addRegionToLake()");
			}
		}

		// see if we can carve a path to a neighboring, separate river system
		Geometry.VertexId [] candidates = world.g.getNearbyVertices(lakeVertex);
		Geometry.VertexId best = null;
		double bestV = Double.POSITIVE_INFINITY;

		lake.lakeElevation = getVertexHeight(lakeVertex);

		for (Geometry.VertexId neighborVertex : candidates)
		{
			// the neighboring river we pick must terminate at some
			// lake... make sure that lake is not our own

			LakeInfo termLake = getSink(neighborVertex);
			if (termLake == lake)
				continue;

			assert termLake != null;
			assert termLake.subsumedBy == null;

			// the neighboring river we pick must terminate at a
			// lower-elevation vertex
			if (termLake.lakeElevation >= lake.lakeElevation)
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

		int water = lake.volume;
		if (best != null)
		{
			lake.type = LakeType.NONTERMINAL;

			addRiverSegment(lakeVertex, best);

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

			// expand lake from single point to a full region
			int regionId = cc[lowest];
			lake.remaining = lake.volume;
			lake.waterLevel = world.elevation[regionId-1] + 1;
			addRegionToLake(lake, regionId);

			if (lake.remaining > 0)
			{
				processSingleHexLake(lake);
			}
		}
	}

	boolean checkDrainageCycle(Geometry.VertexId startVtx)
	{
		Geometry.VertexId v = startVtx;
		while (drainage.containsKey(v))
		{
			v = drainage.get(v);
			if (v.equals(startVtx))
				return false;
		}
		return true;
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
			}});

System.out.println("processing pending lakes...");
		for (Geometry.VertexId lakeVertex : lakeVertices)
		{
			LakeInfo lake = lakes.get(lakeVertex);
			if (lake.remaining > 0 && lake.subsumedBy == null)
			{
				processLakeExcess(lakeVertex, lake);
			}
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
				for (Integer rId : lake.regions)
				{
					int regionId = rId;
					world.regions[regionId-1].biome =
						lake.isOcean ? BiomeType.OCEAN : BiomeType.LAKE;
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
		LakeType type;

		Geometry.VertexId origin;
		double originHeight;
		double lakeElevation;
		Set<Integer> regions;
		int waterLevel;

		/// This is the lake's inflow, units water per units time.
		int volume;

		/// This is how much volume of water still needs dealt with.
		/// Dealing with this will make the lake grow in depth
		/// and surface area until a drainage point is found.
		int remaining;

		boolean isOcean;
		LakeInfo subsumedBy;

		LakeInfo(Geometry.VertexId origin, double originHeight)
		{
			this.origin = origin;
			this.originHeight = originHeight;
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

		boolean isSinglePointLake()
		{
			return regions.isEmpty();
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

		public String toString()
		{
			return String.format("%s at %s (h=%.1f)",
				isSinglePointLake() ? "Point lake" : String.format("%d-region lake", regions.size()),
				origin.toString(),
				lakeElevation);
		}
	}
}
