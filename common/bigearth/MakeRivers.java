package bigearth;

import java.util.*;

public class MakeRivers
{
	final MakeWorld world;
	final Geometry g;

	List<Integer> todo;
	Set<Integer> remaining;
	Map<Integer, Integer> drainage;

	int [] riverElevation;
	int [] riverVolumes;
	Map<Integer, LakeInfo> lakesByRegion;
	Collection<LakeInfo> lakes;

	static final int LAKE_UNIT_VOLUME = 1500;
	static final int OCEAN_SIZE_THRESHOLD = 30;
	static final double MIN_RIVER_SLOPE = 0.1;
	static final int RIVER_ELEVATION_RESOLUTION = 10;

	public MakeRivers(MakeWorld world)
	{
		this.world = world;
		this.g = world.g;

		this.todo = new ArrayList<Integer>();
		this.remaining = new HashSet<Integer>();
		this.drainage = new HashMap<Integer, Integer>();

		this.riverElevation = new int[g.getFaceCount()];
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

		int numRegions = world.g.getFaceCount();
		for (int i = 1; i <= numRegions; i++)
		{
			remaining.add(i);
		}
	}

	/**
	 * @return true if a river was added, false if no more rivers to add
	 */
	private boolean addRiverAt(int fromRegion)
	{
		assert fromRegion >= 1 && fromRegion <= g.getFaceCount();
		assert lakesByRegion.containsKey(fromRegion) || drainage.containsKey(fromRegion);

		int myHeight = riverElevation[fromRegion-1];
		ArrayList<Integer> candidates = new ArrayList<Integer>();
		for (int nid : g.getNeighbors(fromRegion))
		{
			if (!remaining.contains(nid))
				continue;

			int neighborHeight = world.elevation[nid-1] * RIVER_ELEVATION_RESOLUTION;
			if (neighborHeight <= myHeight)
				continue; // never create a river coming from lower elevation

			candidates.add(nid);
		}

		if (candidates.isEmpty())
			return false;

		int i = (int) Math.floor(Math.random() * candidates.size());
		int toRegion = candidates.get(i);

		remaining.remove(toRegion);
		todo.add(toRegion);

		addRiverSegment(toRegion, fromRegion);
		riverElevation[toRegion-1] = Math.max(
			toRiverEl(world.elevation[toRegion-1]),
			myHeight+1);

		return true;
	}

	private int toRiverEl(int worldEl)
	{
		return (worldEl - 1) * RIVER_ELEVATION_RESOLUTION + 1;
	}

	private void addRiverSegment(int upstream, int downstream)
	{
		assert upstream >= 1 && upstream <= g.getFaceCount();
		assert downstream >= 1 && downstream <= g.getFaceCount();

		drainage.put(upstream, downstream);

		assert checkDrainageCycle(upstream);
		assert checkRiverSlope(upstream);
	}

	boolean checkRiverSlope(int regionId)
	{
		assert regionId >= 1 && regionId <= g.getFaceCount();

		//TODO
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
				ArrayList<Integer> candidates = new ArrayList<Integer>();
				int bestH = Integer.MAX_VALUE;

				for (int v : remaining)
				{
					int h = world.elevation[v-1];
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
				lake.lakeElevation = world.elevation[choice-1];
				lakesByRegion.put(choice, lake);
				lakes.add(lake);

				riverElevation[choice-1] = toRiverEl(world.elevation[choice-1]);
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
		assert startRegion >= 1 && startRegion <= g.getFaceCount();

		int v = startRegion;
		LakeInfo lake = null; //never add water to initial lake
		while (lake == null && drainage.containsKey(v))
		{
			int next = drainage.get(v);
			riverVolumes[next-1] += water;

			v = next;
			lake = lakesByRegion.get(v);
		}

		assert lake != null;

		lake.volume += water;
		lake.remaining += water;
	}

	void growLake(LakeInfo lake)
	{
		assert lake.remaining > 0;
		assert !lake.regions.isEmpty();

		// find the lowest adjacent region that this lake can expand to
		Set<Integer> candidates = new HashSet<Integer>();
		double bestEl = Double.POSITIVE_INFINITY;

		for (int aRegion : lake.regions)
		{
			for (int nid : g.getNeighbors(aRegion))
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

	private void reduceLakeDepth(LakeInfo lake, int newWaterElevation)
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

		int newEl = Math.min(lake1.lakeElevation, lake2.lakeElevation);
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
		assert regionId >= 1 && regionId <= g.getFaceCount();

System.out.println(lake.toString() + " : addRegionToLake");

		// check if the specified regionId is already part of a
		// regional lake
		if (lakesByRegion.containsKey(regionId))
		{
			throw new Error("Oops, somehow two lakes are touching.");
		}

		lake.regions.add(regionId);
		lakesByRegion.put(regionId, lake);

		int deltaVolume = lake.lakeElevation + 1 - world.elevation[regionId];
		lake.lakeVolumeI += deltaVolume;

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
		for (int nid : g.getNeighbors(regionId))
		{
			LakeInfo otherLake = lakesByRegion.get(nid);
			if (otherLake != null && otherLake != lake)
			{
				mergeLakes(lake, otherLake);
			}
		}
	}

	private void clearRiver(int upstreamRegion)
	{
		assert upstreamRegion >= 1 && upstreamRegion <= g.getFaceCount();
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
		throw new Error("no drainage known for region "+srcRegion);
	}

	void processLakeExcess(LakeInfo lake)
	{
		assert lake.remaining > 0;

		while (lake.remaining > 0)
		{
			growLake(lake);
		}
	}

	boolean checkDrainageCycle(int startVtx)
	{
		assert startVtx >= 1 && startVtx <= g.getFaceCount();

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
			int water = world.annualRains[srcRegion-1];
			water = (water - 30) / 10;
			if (water <= 0)
				continue;

			addWaterToRiver(srcRegion, water);
		}

		//
		// process the lakes
		//

		// lowest-elevation to highest-elevation

if (false) {
		LakeInfo [] allLakes = lakes.toArray(new LakeInfo[0]);
		Arrays.sort(allLakes,
			new Comparator<LakeInfo>() {
			public int compare(LakeInfo a, LakeInfo b)
			{
				int a_el = a.lakeElevation;
				int b_el = b.lakeElevation;
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
				assert regionId >= 1 && regionId <= g.getFaceCount();

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
		for (int regionId = 1; regionId <= numRegions; regionId++) {

			if (drainage.containsKey(regionId)) {
				int volume = riverVolumes[regionId-1];
				int drainsTo = drainage.get(regionId);
				int dir = directionOf(regionId, drainsTo);

				RegionServant fromRegion = world.world.regions[regionId-1];
				fromRegion.riverOut[dir] = true;
				fromRegion.riverSize =
					volume > 2000 ? 3 :
					volume > 200 ? 2 :
					1;

				RegionServant toRegion = world.world.regions[drainsTo-1];
				dir = directionOf(drainsTo, regionId);
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
		int lakeElevation;

		/// This is the lake's inflow, units water per units time.
		int volume;

		/// This is how much volume of water still needs dealt with.
		/// Dealing with this will make the lake grow in depth
		/// and surface area until a drainage point is found.
		int remaining;

		// Total volume of space taken up by water in this lake.
		double lakeVolume;
		int lakeVolumeI;

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

		public String toString()
		{
			return String.format("%s at %s (h=%d, excess=%d)",
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
			for (int n : g.getNeighbors(cur))
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
