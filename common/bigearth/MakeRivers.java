package bigearth;

import java.util.*;

public class MakeRivers
{
	final MakeWorld world;
	final Geometry g;

	List<Integer> todo;
	Set<Integer> remaining;
	Map<Integer, Integer> drainage;

	double [] riverElevation;
	int [] riverVolumes;
	Map<Integer, LakeInfo> lakesByRegion;
	Collection<LakeInfo> lakes;

	static final int LAKE_UNIT_VOLUME = 1500;
	static final int OCEAN_SIZE_THRESHOLD = 30;
	static final double MIN_RIVER_SLOPE = 0.1;

	public MakeRivers(MakeWorld world)
	{
		this.world = world;
		this.g = world.g;

		this.todo = new ArrayList<Integer>();
		this.remaining = new HashSet<Integer>();
		this.drainage = new HashMap<Integer, Integer>();

		this.riverElevation = new double[g.getFaceCount()];
		this.riverVolumes = new int[g.getFaceCount()];
		this.lakesByRegion = new HashMap<Integer,LakeInfo>();
		this.lakes = new ArrayList<LakeInfo>();
	}

	void initialize()
	{
		todo.clear();
		remaining.clear();
		drainage.clear();

		Arrays.fill(riverVolumes, 0);
		lakesByRegion.clear();
		lakes.clear();

		int numCells = world.g.getFaceCount();
		for (int i = 0; i < numCells; i++)
		{
			remaining.add(i);
			riverElevation[i] = world.elevation[i];
		}
	}

	/**
	 * @return true if a river was added, false if no more rivers to add
	 */
	private boolean addRiverAt(int fromRegion)
	{
		assert fromRegion >= 0 && fromRegion < g.getFaceCount();

		double myHeight = riverElevation[fromRegion];
		ArrayList<Integer> candidates = new ArrayList<Integer>();
		for (int nid : g.getNeighbors(fromRegion+1))
		{
			if (!remaining.contains(nid-1))
				continue;

			double neighborHeight = riverElevation[nid-1];
			if (neighborHeight < myHeight)
				continue; // never create a river coming from lower elevation

			candidates.add(nid-1);
		}

		if (candidates.isEmpty())
			return false;

		int i = (int) Math.floor(Math.random() * candidates.size());
		int toRegion = candidates.get(i);

		remaining.remove(toRegion);
		todo.add(toRegion);

		addRiverSegment(toRegion, fromRegion);
		return true;
	}

	private void addRiverSegment(int upstream, int downstream)
	{
		assert upstream >= 0 && upstream < g.getFaceCount();
		assert downstream >= 0 && downstream < g.getFaceCount();

		drainage.put(upstream, downstream);

		assert checkDrainageCycle(upstream);
		enforceRiverSlope(upstream);
	}

	void enforceRiverSlope(int vtx)
	{
		assert vtx >= 0 && vtx < g.getFaceCount();

		enforceRiverSlope(vtx, riverElevation[vtx]);
	}

	/**
	 * Updates river elevation downstream of a given vertex,
	 * ensuring that the river slopes down in elevation by
	 * at least a certain rate.
	 */
	void enforceRiverSlope(int vtx, double maxElev)
	{
		assert vtx >= 0 && vtx < g.getFaceCount();

		double h = maxElev;
		while (drainage.containsKey(vtx))
		{
			int nextVtx = drainage.get(vtx);

			h -= MIN_RIVER_SLOPE;

			vtx = nextVtx;
			double h1 = riverElevation[vtx];
			if (h1 <= h)
			{
				h = h1;
				return; // no change needed, stop here
			}
			else
			{
				// make a change to river elevation
				riverElevation[vtx] = h;
			}
		}

		LakeInfo lake = lakesByRegion.get(vtx);
		assert lake != null;

		if (lake.lakeElevation > h)
		{
			//TODO
			//checkLakeElevation(lake);
		}
	}

	/**
	 * Updates river elevation downstream of a given lake,
	 * ensuring that the river slopes down in elevation
	 * by at least a certain rate.
	 */
	void enforceRiverSlope(LakeInfo lake)
	{
		if (lake.type == LakeType.NONTERMINAL) {
			enforceRiverSlope(lake.drain, lake.lakeElevation);
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
				ArrayList<Integer> candidates = new ArrayList<Integer>();
				double bestH = Double.POSITIVE_INFINITY;

				for (int v : remaining)
				{
					double h = riverElevation[v];
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
				int choice = candidates.get(i);
				remaining.remove(choice);
				todo.add(choice);

				LakeInfo lake = new LakeInfo(choice);
				lake.lakeElevation = riverElevation[choice];
				lakesByRegion.put(choice, lake);
				lakes.add(lake);
			}

			// choose from TODO array randomly
			int i = (int) Math.floor(Math.random() * todo.size());
			int v = todo.get(i);
			if (addRiverAt(v))
				return true;

			todo.remove(i);
		}
	}

	void addWaterToRiver(int startRegion, int water)
	{
		assert startRegion >= 0 && startRegion < g.getFaceCount();

		int v = startRegion;
		LakeInfo lake = null; //never add water to initial lake
		while (lake == null && drainage.containsKey(v))
		{
			int next = drainage.get(v);
			riverVolumes[next] += water;

			v = next;
			lake = lakesByRegion.get(v);
		}

		assert lake != null;

		lake.volume += water;
		lake.remaining += water;
	}

	private void processMultiHexLake(LakeInfo lake)
	{
		assert lake.remaining > 0;
		assert !lake.regions.isEmpty();

		// find the lowest adjacent region that this lake can expand to
		Set<Integer> candidates = new HashSet<Integer>();
		double bestEl = Double.POSITIVE_INFINITY;

		for (int aRegion : lake.regions)
		{
			for (int nid : g.getNeighbors(aRegion+1))
			{
				if (!lake.regions.contains(nid-1))
				{
					if (riverElevation[nid-1] < bestEl)
					{
						bestEl = riverElevation[nid-1];
						candidates.clear();
						candidates.add(nid-1);
					}
					else if (riverElevation[nid-1] == bestEl)
					{
						candidates.add(nid-1);
					}
				}
			}
		}

		if (candidates.isEmpty())
		{
			// this presumably means the entire world is covered in ocean
			throw new Error("Oops, entire world is covered in ocean now");
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

	private void reduceLakeDepth(LakeInfo lake, double newWaterElevation)
	{
		assert newWaterElevation <= lake.lakeElevation;

		double deltaVolume = LAKE_UNIT_VOLUME * (newWaterElevation-lake.lakeElevation) * lake.regions.size();
		//lake.remaining += Math.floor(deltaVolume);
		lake.lakeElevation = newWaterElevation;
	}

	private void mergeLakes(LakeInfo lake1, LakeInfo lake2)
	{
		assert lake1 != null;
		assert lake2 != null;
		assert lake1 != lake2;

		double newEl = Math.min(lake1.lakeElevation, lake2.lakeElevation);
		reduceLakeDepth(lake1, newEl);
		reduceLakeDepth(lake2, newEl);

		for (int r2 : lake2.regions)
		{
			lake1.regions.add(r2);
			lakesByRegion.put(r2, lake1);
		}
		lake2.regions.clear();

		lake1.remaining += lake2.remaining;
		lake1.volume += lake2.volume;

		lake2.remaining = 0;
		lake2.volume = 0;

		if (lake1.type == LakeType.TERMINAL) {
			lake1.type = lake2.type;
			lake1.drain = lake2.drain;
		}
		else if (lake1.type == LakeType.NONTERMINAL && lake2.type == LakeType.NONTERMINAL) {
			drainage.remove(lake2.drain);
			lake2.type = LakeType.TERMINAL;
		}

		lakes.remove(lake2);
	}

	/**
	 * Adds a region to the specified lake, merging adjacent lakes automatically
	 * as needed. Also, the lake's elevation is raised to the max possible given
	 * the lake's sources.
	 */
	private void addRegionToLake(LakeInfo lake, int regionId)
	{
		assert lake != null;
		assert regionId >= 0 && regionId < g.getFaceCount();

System.out.println(lake.toString() + " : addRegionToLake");

		// check if the specified regionId is already part of a
		// regional lake
		if (lakesByRegion.containsKey(regionId))
		{
			throw new Error("Oops, somehow two lakes are touching.");
		}

		lake.regions.add(regionId);
		lakesByRegion.put(regionId, lake);

		if (lake.floorElevation < world.elevation[regionId]) {
			lake.floorElevation = world.elevation[regionId];
		}

		double deltaVolume = LAKE_UNIT_VOLUME * (lake.lakeElevation - (world.elevation[regionId] - 0.5));
		lake.lakeVolume += deltaVolume;
		lake.remaining -= Math.floor(deltaVolume);

		// check drainage rules for the new region
		if (lake.type == LakeType.NONTERMINAL)
		{
			if (drainage.containsKey(regionId)) {
				drainage.remove(regionId);
			}
		}
		else {
			assert lake.type == LakeType.TERMINAL;

			// does the new region drain to a lower lake?
			if (getUltimateSink(regionId) != lake)
			{
				lake.drain = regionId;
				lake.type = LakeType.NONTERMINAL;
			}
			else 
			{
				drainage.remove(regionId);
			}
		}

		// check if this region touches any other lakes
		for (int nid : g.getNeighbors(regionId+1))
		{
			LakeInfo otherLake = lakesByRegion.get(nid-1);
			if (otherLake != null && otherLake != lake)
			{
				mergeLakes(lake, otherLake);
			}
		}
	}

	private void clearRiver(int upstreamRegion)
	{
		assert upstreamRegion >= 0 && upstreamRegion < g.getFaceCount();
		assert drainage.containsKey(upstreamRegion);

		drainage.remove(upstreamRegion);
	}

	/**
	 * Determines where a given region drains to.
	 * @return the LakeInfo object for where it drains to.
	 */
	private LakeInfo getSink(int srcRegion)
	{
		assert drainage.containsKey(srcRegion);

		for (;;)
		{
			srcRegion = drainage.get(srcRegion);

			LakeInfo lake = lakesByRegion.get(srcRegion);
			if (lake != null)
				return lake;

			assert drainage.containsKey(srcRegion);
		}
	}

	private LakeInfo getUltimateSink(int srcRegion)
	{
		while (drainage.containsKey(srcRegion))
		{
			LakeInfo lake = getSink(srcRegion);
			if (lake.type == LakeType.TERMINAL) {
				return lake;
			}
			srcRegion = lake.drain;
		}
		throw new Error("no drainage known for region "+(srcRegion+1));
	}

	void processLakeExcess(LakeInfo lake)
	{
		assert lake.remaining > 0;

		while (lake.remaining > 0)
		{
			processMultiHexLake(lake);
		}
	}

	boolean checkDrainageCycle(int startVtx)
	{
		assert startVtx >= 0 && startVtx < g.getFaceCount();

		int v = startVtx;
		while (drainage.containsKey(v))
		{
			v = drainage.get(v);
			if (v == startVtx)
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

		for (int srcRegion : drainage.keySet())
		{
			int water = world.annualRains[srcRegion];
			water = (water - 30) / 10;
			if (water <= 0)
				continue;

			addWaterToRiver(srcRegion, water);
		}

		//
		// process the lakes
		//

		// lowest-elevation to highest-elevation

		LakeInfo [] allLakes = lakes.toArray(new LakeInfo[0]);
		Arrays.sort(allLakes,
			new Comparator<LakeInfo>() {
			public int compare(LakeInfo a, LakeInfo b)
			{
				double a_el = a.lakeElevation;
				double b_el = b.lakeElevation;
				return (a_el > b_el ? 1 :
					a_el < b_el ? -1 : 0);
			}});

System.out.println("processing pending lakes...");
		for (LakeInfo lake : allLakes)
		{
			if (lake.remaining > 0)
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
		for (RegionServant r : world.world.regions)
		{
			r.biome = BiomeType.GRASSLAND;
			r.clearSides();
		}

		for (LakeInfo lake : lakes)
		{
			boolean isOcean = lake.isOcean;
			for (int regionId : lake.regions)
			{
				world.world.regions[regionId-1].biome =
					isOcean ? BiomeType.OCEAN : BiomeType.LAKE;
				world.world.regions[regionId-1].waterLevel = (int) Math.ceil(lake.lakeElevation);
			}
		}
	}

	int directionOf(int srcRegion, int destRegion)
	{
		int [] nn = g.getNeighbors(srcRegion);
		int dir = 0;
		for (int i = 1; i < nn.length; i++) {
			if (nn[i] == destRegion) {
				dir = i;
			}
		}
		return dir;
	}

		//
		// place rivers
		//
	void placeRivers()
	{
		int numRegions = g.getFaceCount();
		for (int regionId = 0; regionId < numRegions; regionId++) {

			if (drainage.containsKey(regionId)) {
				int volume = riverVolumes[regionId];
				int drainsTo = drainage.get(regionId);
				int dir = directionOf(regionId+1, drainsTo+1);

				RegionServant fromRegion = world.world.regions[regionId];
				fromRegion.riverOut[dir] = true;
				fromRegion.riverSize =
					volume > 2000 ? 3 :
					volume > 200 ? 2 :
					1;

				RegionServant toRegion = world.world.regions[drainsTo];
				dir = directionOf(drainsTo+1, regionId+1);
				toRegion.riverIn[dir] = true;
			}
		}
	}

	static enum LakeType
	{
		TERMINAL,
		NONTERMINAL;
	}

	static class LakeInfo
	{
		// Whether the lake has a drain or not.
		LakeType type;

		// What regions this lake is composed of.
		Set<Integer> regions;

		// What region this lake started from.
		int origin;

		// The elevation of this lake's surface.
		double lakeElevation;

		/// This is the lake's inflow, units water per units time.
		int volume;

		/// This is how much volume of water still needs dealt with.
		/// Dealing with this will make the lake grow in depth
		/// and surface area until a drainage point is found.
		int remaining;

		// Total volume of space taken up by water in this lake.
		double lakeVolume;

		boolean isOcean;

		int floorElevation;
		int drain;

		LakeInfo(int origin)
		{
			this.origin = origin;
			this.volume = 0;
			this.remaining = 0;
			this.type = LakeType.TERMINAL;
			this.regions = new HashSet<Integer>();
			this.regions.add(origin);
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
				String.format("%d-region lake", regions.size()),
				origin,
				lakeElevation,
				remaining);
		}
	}

	void generateFloods()
	{
System.out.println("in generateFloods");

		int numRegions = g.getFaceCount();
		assert world.floods.length == numRegions;

		int [] floods = world.floods;
		for (int i = 0; i < numRegions; i++)
		{
			floods[i] = 0;
		}

		int [] riverMagnitude = new int[numRegions];
		final int maxRiverMagnitude = 3;
		for (int i = 0; i < numRegions; i++)
		{
			int riv = riverVolumes[i];

			int water = riv >= 5000 ? 3 :
				riv >= 3500 ? 2 :
				riv >= 2000 ? 1 : 0;
			riverMagnitude[i] = water;
		}

final int OCEAN_FLOOD = 5;
final int LAKE_FLOOD = 10;

		Queue<Integer> Q = new ArrayDeque<Integer>();
		for (int i = 0; i < numRegions; i++)
		{
			LakeInfo lake = lakesByRegion.get(i);
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
			else if (riverMagnitude[i] > 0)
			{
				int floodLevel = 1+(int)Math.round(8.0 * Math.sqrt((double)riverMagnitude[i]/maxRiverMagnitude));
				floods[i] = floodLevel;
				Q.add(i+1);
			}
		}

		while (!Q.isEmpty())
		{
			int cur = Q.remove();
			for (int n : g.getNeighbors(cur+1))
			{
				int heightDiff = world.elevation[n-1] - world.elevation[cur];
				int floodLevel = floods[cur] - Math.max(1, 1 + heightDiff);
				if (floodLevel > floods[n-1])
				{
					floods[n-1] = floodLevel;
					Q.add(n);
				}
			}
		}
	}
}
