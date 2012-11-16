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
	static final int OCEAN_SIZE_THRESHOLD = 30;
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
			Geometry.VertexId nextVtx = drainage.get(vtx);

			h -= MIN_RIVER_SLOPE;

			vtx = nextVtx;
			double h1 = getVertexHeight(vtx);
			if (h1 <= h)
			{
				h = h1;
				return; // no change needed, stop here
			}
			else
			{
				// make a change to river elevation
				riverElevation.put(vtx, h);
			}
		}

		LakeInfo lake = getLakeAt(vtx);
		assert lake != null;

		if (lake.lakeElevation > h)
		{
			checkLakeElevation(lake);
		}
	}

	/**
	 * Updates river elevation downstream of a given lake,
	 * ensuring that the river slopes down in elevation
	 * by at least a certain rate.
	 */
	void enforceRiverSlope(LakeInfo lake)
	{
		// check all outputs for this river
		for (Geometry.VertexId drainVtx : getLakeDrains(lake))
		{
			double h1 = getVertexHeight(drainVtx);
			if (h1 > lake.lakeElevation)
			{
				riverElevation.put(drainVtx, lake.lakeElevation);
			}
			enforceRiverSlope(drainVtx);
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

				LakeInfo lake = new LakeInfo(choice);
				lake.lakeElevation = getVertexHeight(choice);
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
	}

	LakeInfo getLakeAt(Geometry.VertexId lakeVertex)
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

	/**
	 * Enumerate the vertices on the border of this lake that drain
	 * out of the lake.
	 */
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

	private Geometry.VertexId [] getLakeVertices(LakeInfo lake)
	{
		if (lake.isSinglePointLake())
		{
			return new Geometry.VertexId[] { lake.origin };
		}

		Set<Geometry.VertexId> result = new HashSet<Geometry.VertexId>();
		for (int regionId : lake.regions)
		{
			for (Geometry.VertexId v : world.g.getSurroundingVertices(regionId))
			{
				result.add(v);
			}
		}
		return result.toArray(new Geometry.VertexId[0]);
	}

	/**
	 * Get all vertices that drain into a specified lake.
	 */
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
		return result.toArray(new Geometry.VertexId[0]);
	}

	private void processMultiHexLake(LakeInfo lake)
	{
		assert lake.remaining > 0;
		assert !lake.regions.isEmpty();

		// a list of bordering vertices that drain away from this lake
		Geometry.VertexId [] drains = getLakeDrains(lake);
		if (!lake.isOcean && drains.length != 0)
		{
			int num = drains.length;

			for (Geometry.VertexId drainVtx : drains)
			{
				int water = lake.remaining / num;
				addWaterToRiver(drainVtx, water);
				lake.remaining -= water;
				num--;
			}

			// a problem here could be caused by a lake having a drain
			// to itself
			assert num == 0;
			assert lake.remaining == 0;

			return;
		}

		// find the lowest adjacent region that this lake can expand to
		Set<Integer> candidates = new HashSet<Integer>();
		int bestEl = Integer.MAX_VALUE;

		for (int aRegion : lake.regions)
		{
			for (int nid : world.g.getNeighbors(aRegion))
			{
				if (!lake.regions.contains(nid))
				{
					if (world.elevation[nid-1] < bestEl)
					{
						bestEl = world.elevation[nid-1];
						candidates.clear();
						candidates.add(nid);
					}
					else if (world.elevation[nid-1] == bestEl)
					{
						candidates.add(nid);
					}
				}
			}
		}

		if (candidates.isEmpty())
		{
			// this presumably means the entire world is covered in ocean
			throw new Error("not implemented");
		}
		else
		{
			Integer [] regionsList = candidates.toArray(new Integer[0]);
			int i = (int) Math.floor(Math.random() * regionsList.length);
			int nid = regionsList[i];

			addRegionToLake(lake, nid);
			return;
		}
	}

	private void checkLakeElevation(LakeInfo lake)
	{
System.out.println(lake.toString() + " : checkLakeElevation");

		double newEl = Double.POSITIVE_INFINITY;
		for (Geometry.VertexId sourceVtx : getLakeSources(lake))
		{
			double h = getVertexHeight(sourceVtx) - MIN_RIVER_SLOPE;
			if (newEl > h)
				newEl = h;
		}

		for (int regionId : lake.regions)
		{
			double h = 0.5 + world.elevation[regionId-1];
			if (newEl > h)
				newEl = h;
		}

		if (lake.lakeElevation != newEl)
		{
	System.out.println("changing water level; was "+lake.lakeElevation+", is now " + newEl);

			double deltaVolume = LAKE_UNIT_VOLUME * (newEl-lake.lakeElevation) * lake.regions.size();
			lake.remaining -= Math.floor(deltaVolume);

			lake.lakeElevation = newEl;

			for (Geometry.VertexId vtx : getLakeVertices(lake))
			{
				riverElevation.put(vtx, lake.lakeElevation);
			}

			enforceRiverSlope(lake);
		}
	}

	private void reduceLakeDepth(LakeInfo lake, double newWaterElevation)
	{
		assert newWaterElevation <= lake.lakeElevation;

		double deltaVolume = LAKE_UNIT_VOLUME * (newWaterElevation-lake.lakeElevation) * lake.regions.size();
		//lake.remaining += Math.floor(deltaVolume);
		lake.lakeElevation = newWaterElevation;
	}

	private void mergeLakes(LakeInfo lake1, LakeInfo lake2)
	{
		assert lake1 != lake2;

		for (Geometry.VertexId v : getLakeDrains(lake1))
		{
			LakeInfo drainsTo = getSink(v);
			if (drainsTo == lake2)
			{
				// cancel this drain
				clearRiver(v);
			}
		}
		for (Geometry.VertexId v : getLakeDrains(lake2))
		{
			LakeInfo drainsTo = getSink(v);
			if (drainsTo == lake1)
			{
				// cancel this drain
				clearRiver(v);
			}
		}

		double newEl = Math.min(lake1.lakeElevation, lake2.lakeElevation);
		reduceLakeDepth(lake1, newEl);
		reduceLakeDepth(lake2, newEl);

		for (int r2 : lake2.regions)
		{
			lake1.regions.add(r2);
			lakesByRegion.put(r2, lake1);
		}
		lake2.regions.clear();
		lake2.subsumedBy = lake1;

		lake1.remaining += lake2.remaining;
		lake1.volume += lake2.volume;

		lake2.remaining = 0;
		lake2.volume = 0;
	}

	/**
	 * Adds a region to the specified lake, merging adjacent lakes automatically
	 * as needed. Also, the lake's elevation is raised to the max possible given
	 * the lake's sources.
	 */
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

		if (lake.floorElevation < world.elevation[regionId-1])
			lake.floorElevation = world.elevation[regionId-1];

		double deltaVolume = LAKE_UNIT_VOLUME * (lake.lakeElevation - (world.elevation[regionId-1] - 0.5));
		//lake.remaining -= Math.floor(deltaVolume);
		lake.remaining -= LAKE_UNIT_VOLUME;

		// check drainage rules for the points around this region
		for (Geometry.VertexId v : world.g.getSurroundingVertices(regionId))
		{
			if (drainage.containsKey(v))
			{
				LakeInfo drainsTo = getSink(v);
				if (drainsTo.lakeElevation >= lake.lakeElevation)
				{
					// can't have this; remove that drainage rule
System.out.println("  removed drain cycle at "+v);
					clearRiver(v);
				}
			}
		}

		// check if this region touches any other lakes
		for (int nid : world.g.getNeighbors(regionId))
		{
			LakeInfo otherLake = lakesByRegion.get(nid);
			if (otherLake != null && otherLake != lake)
			{
				mergeLakes(lake, otherLake);
			}
		}

		checkLakeElevation(lake);
	}

	private void clearRiver(Geometry.VertexId upstreamVtx)
	{
		assert drainage.containsKey(upstreamVtx);

		Geometry.VertexId downstreamVtx = drainage.get(upstreamVtx);
		Geometry.EdgeId eId = world.g.getEdgeByEndpoints(downstreamVtx, upstreamVtx);

		drainage.remove(upstreamVtx);
		rivers.remove(eId);
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

	void processLakeExcess(LakeInfo lake)
	{
		assert lake.subsumedBy == null;
		assert lake.remaining > 0;

		while (lake.remaining > 0)
		{
			if (lake.isSinglePointLake())
			{
				processSinglePointLake(lake);
			}
			else
			{
				processMultiHexLake(lake);
			}
		}

		if (lake.regions.size() >= OCEAN_SIZE_THRESHOLD)
		{
			processOcean(lake);
		}
	}

	private void processOcean(LakeInfo lake)
	{
		// make this an ocean
		lake.isOcean = true;
		while (lake.floorElevation < 0)
		{
			lake.remaining = 1;
System.out.println("OCEAN size "+lake.regions.size() + " depth "+lake.lakeElevation);
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

		if (drainage.containsKey(lake.origin))
		{
			int water = lake.remaining;
			lake.remaining = 0;
			addWaterToRiver(lake.origin, water);

			assert lake.remaining == 0;
			return;
		}

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

			LakeInfo termLake = getLakeAt(neighborVertex);
			if (termLake == null)
			{
				termLake = getSink(neighborVertex);
			}
			assert termLake != null;
			assert termLake.subsumedBy == null;

			// drainage back to this own lake should not be considered
			if (termLake == lake)
				continue;

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

		if (best != null)
		{
			lake.type = LakeType.NONTERMINAL;

			addRiverSegment(lakeVertex, best);

			// add this lake's volume to the downstream river
			int water = lake.remaining;
			lake.remaining = 0;
			addWaterToRiver(lakeVertex, water);

			assert lake.remaining == 0;
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
			addRegionToLake(lake, regionId);
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
				return (a_el > b_el ? 1 :
					a_el < b_el ? -1 : 0);
			}});

System.out.println("processing pending lakes...");
		for (Geometry.VertexId lakeVertex : lakeVertices)
		{
			LakeInfo lake = lakes.get(lakeVertex);
			if (lake.remaining > 0 && lake.subsumedBy == null)
			{
System.out.println(lake);
				processLakeExcess(lake);
			}
		}

		placeLakes();
		placeRivers();
	}

		//
		// place lakes
		//
	void placeLakes()
	{
		for (RegionDetail r : world.regions)
		{
			r.biome = BiomeType.GRASSLAND;
			r.clearSides();
		}

		for (Geometry.VertexId vId : lakes.keySet())
		{
			LakeInfo lake = lakes.get(vId);
			if (lake.subsumedBy != null)
				continue;

			if (lake.isSinglePointLake())
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
				boolean isOcean = lake.isOcean;
				for (int regionId : lake.regions)
				{
					world.regions[regionId-1].biome =
						isOcean ? BiomeType.OCEAN : BiomeType.LAKE;
					world.regions[regionId-1].waterLevel = (int) Math.ceil(lake.lakeElevation);
				}
			}
		}
	}

		//
		// place rivers
		//
	void placeRivers()
	{
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
		double lakeElevation;
		Set<Integer> regions;

		/// This is the lake's inflow, units water per units time.
		int volume;

		/// This is how much volume of water still needs dealt with.
		/// Dealing with this will make the lake grow in depth
		/// and surface area until a drainage point is found.
		int remaining;

		boolean isOcean;
		LakeInfo subsumedBy;

		int floorElevation;

		LakeInfo(Geometry.VertexId origin)
		{
			this.origin = origin;
			this.volume = 0;
			this.remaining = 0;
			this.type = LakeType.TERMINAL;
			this.regions = new HashSet<Integer>();
			this.floorElevation = Integer.MIN_VALUE;
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
			return String.format("%s at %s (h=%.1f, excess=%d)",
				isSinglePointLake() ? "Point lake" : String.format("%d-region lake", regions.size()),
				origin.toString(),
				lakeElevation,
				remaining);
		}
	}

	void generateFloods()
	{
System.out.println("in generateFloods");

		int [] floods = world.floods;
		for (int i = 0; i < floods.length; i++)
		{
			floods[i] = 0;
		}

		int [] riverVolume = new int[floods.length];
		final int maxRiverVolume = 3;
		for (Geometry.EdgeId eId : rivers.keySet())
		{
			RiverInfo riv = rivers.get(eId);
			int [] dd = eId.getAdjacentCells();

			int water = riv.volume >= 5000 ? 3 :
				riv.volume >= 3500 ? 2 :
				riv.volume >= 2000 ? 1 : 0;
			riverVolume[dd[0]-1] += water;
			riverVolume[dd[1]-1] += water;
		}

final int OCEAN_FLOOD = 5;
final int LAKE_FLOOD = 10;

		Queue<Integer> Q = new ArrayDeque<Integer>();
		for (int i = 0; i < floods.length; i++)
		{
			LakeInfo lake = lakesByRegion.get(i+1);
			if (lake != null && lake.isOcean)
			{
				floods[i] = OCEAN_FLOOD;
				Q.add(i+1);
			}
			else if (lake != null)
			{
				floods[i] = LAKE_FLOOD;
				Q.add(i+1);
			}
			else if (riverVolume[i] > 0)
			{
				int floodLevel = 1+(int)Math.round(8.0 * Math.sqrt((double)riverVolume[i]/maxRiverVolume));
				floods[i] = floodLevel;
				Q.add(i+1);
			}
		}

		while (!Q.isEmpty())
		{
			int cur = Q.remove();
			for (int n : world.g.getNeighbors(cur))
			{
				int heightDiff = world.elevation[n-1] - world.elevation[cur-1];
				int floodLevel = floods[cur-1] - Math.max(1, 1 + heightDiff);
				if (floodLevel > floods[n-1])
				{
					floods[n-1] = floodLevel;
					Q.add(n);
				}
			}
		}
	}
}
