package bigearth;

import java.io.*;
import java.util.*;
import javax.vecmath.*;
import com.fasterxml.jackson.core.*;

class RegionDetail
	implements ShadowRegion
{
	MakeWorld world;
	int regionId;

	BiomeType biome;
	RegionSideDetail [] sides;
	RegionCornerDetail [] corners;
	int waterLevel;

	int wildlife;
	int wildlifeHunted;
	int wildlifeBirths;
	int wildlifeDeaths;
	int wildlifeEmigrants;
	int wildlifeImmigrants;

	int [] terrains;
	boolean dirty;

	Map<Integer,TerrainId> riverPorts;

	static final double WILDLIFE_LIFESPAN = 5.0;
	static final double WILDLIFE_EMIGRATION_RATE = 0.25;

	RegionDetail(MakeWorld world, int regionId)
	{
		this.world = world;
		this.regionId = regionId;
		this.biome = BiomeType.GRASSLAND;
		this.riverPorts = new HashMap<Integer,TerrainId>();
		this.sides = new RegionSideDetail[6];
		this.corners = new RegionCornerDetail[6];
		this.waterLevel = Integer.MIN_VALUE;
	}

	private void init()
	{
		int numSides = world.g.getNeighborCount(regionId);
		int detailLevel = world.regionDetailLevel;
		this.terrains = new int[numSides * (1 << (detailLevel*2))];
	}

	public void adjustWildlife(int delta)
	{
		this.wildlife += delta;
		if (wildlife < 0)
			wildlife = 0;
		dirty = true;
	}

	public TerrainId getRiverPort(int neighborRegionId)
	{
		return riverPorts.get(neighborRegionId);
	}

	TerrainId [] getBorderTiles(int borderRegionId)
	{
		TerrainGeometry tg = world.getTerrainGeometry();

		int [] nrr = new int[3];
		int [] ntt = new int[3];

		ArrayList<TerrainId> candidates = new ArrayList<TerrainId>();
		for (int tile = 0; tile < terrains.length; tile++)
		{
			tg.getNeighborTiles(nrr, ntt, regionId, tile);
			for (int j = 0; j < nrr.length; j++)
			{
				if (nrr[j] == borderRegionId)
				{
					candidates.add(new TerrainId(regionId, tile));
					break;
				}
			}
		}

		return candidates.toArray(new TerrainId[0]);
	}

	private TerrainId pickRandomInnerTile()
	{
		TerrainGeometry tg = world.getTerrainGeometry();

		int [] nrr = new int[3];
		int [] ntt = new int[3];

		ArrayList<TerrainId> candidates = new ArrayList<TerrainId>();
		for (int tile = 0; tile < terrains.length; tile++)
		{
			tg.getNeighborTiles(nrr, ntt, regionId, tile);
			boolean flag = false;
			for (int j = 0; j < nrr.length; j++)
			{
				if (nrr[j] != regionId)
					flag = true;
			}
			if (!flag)
				candidates.add(new TerrainId(regionId, tile));
		}

		int i = (int) Math.floor(Math.random() * candidates.size());
		return candidates.get(i);
	}

	/**
	 * Picks a terrain tile that borders the specified neighboring region
	 * for a river.
	 * If the neighboring region has a river already generated, pick the
	 * tile in our region opposite its tile.
	 * Otherwise, pick a random terrain tile on that border.
	 */
	TerrainId pickBorderTile(int borderRegionId)
	{
		// if we already determined the port...
		if (riverPorts.containsKey(borderRegionId))
		{
			return riverPorts.get(borderRegionId);
		}

		TerrainGeometry tg = world.getTerrainGeometry();

		// if the neighboring region has already determined the
		// port...
		ShadowRegion neighbor = world.getShadowRegion(borderRegionId);
		TerrainId port = neighbor.getRiverPort(regionId);
		if (port != null)
		{
			TerrainId [] portN = tg.getNeighborTiles(port);
			port = (portN[0].regionId == regionId ? portN[0] :
				portN[1].regionId == regionId ? portN[1] :
				portN[2]);
			riverPorts.put(borderRegionId, port);
			return port;
		}

		// otherwise, pick randomly

		TerrainId [] candidates = getBorderTiles(borderRegionId);
		int i = (int) Math.floor(Math.random() * candidates.length);
		port = candidates[i];
		riverPorts.put(borderRegionId, port);
		return port;
	}

	public void makeOcean()
	{
		TerrainGeometry tg = world.getTerrainGeometry();

		for (int i = 0; i < terrains.length; i++)
		{
			terrains[i] = TerrainType.DEEP_SEA.id;
		}

		for (int n : world.g.getNeighbors(regionId))
		{
			ShadowRegion neighbor = world.getShadowRegion(n);
			if (neighbor.getBiome() != BiomeType.OCEAN)
			{
				TerrainId [] tiles = getBorderTiles(n);
				for (TerrainId t : tiles)
				{
					terrains[t.tile] = TerrainType.ROCKY_SHORE.id;
					for (TerrainId tt : tg.getNeighborTiles(t))
					{
						if (tt.regionId == regionId)
							terrains[tt.tile] = TerrainType.ROCKY_SHORE.id;
					}
				}
			}
		}

		dirty = true;
	}

	void makeLand()
	{
		TerrainGeometry tg = world.getTerrainGeometry();
		for (int i = 0; i < terrains.length; i++)
		{
			terrains[i] = TerrainType.GRASS.id;
		}

		dirty = true;
	}

	void makeFreshwaterLake()
	{
		TerrainGeometry tg = world.getTerrainGeometry();
		for (int i = 0; i < terrains.length; i++)
		{
			terrains[i] = TerrainType.LAKE.id;
		}

		dirty = true;
	}

	public void makeRivers()
	{
		final int RIVER = TerrainType.STREAM.id;

		if (world.elevation[regionId-1] < 0)
		{
			makeOcean();
		}
		else if (world.lakeLevel[regionId-1] > world.elevation[regionId-1])
		{
			makeFreshwaterLake();
		}
		else
		{
			makeLand();
		}

		TerrainGeometry tg = world.getTerrainGeometry();

		// find the drainage sink
		int sinkTile;
		int sinkRegion = world.drainage[regionId-1];
		if (sinkRegion > 0)
		{
			sinkTile = pickBorderTile(sinkRegion).tile;
		}
		else
		{
			sinkTile = 0;
		}

		int [] drainage = new int[terrains.length];
		drainage[sinkTile] = -1;
		ArrayList<Integer> todo = new ArrayList<Integer>();
		todo.add(sinkTile);

		int [] nrr = new int[3];
		int [] ntt = new int[3];
		while (!todo.isEmpty())
		{
			int i = (int) Math.floor(Math.random() * todo.size());
			int tile = todo.get(i);

			tg.getNeighborTiles(nrr, ntt, regionId, tile);
			ArrayList<Integer> candidates = new ArrayList<Integer>();
			for (int j = 0; j < nrr.length; j++)
			{
				if (nrr[j] != regionId)
					continue;
				if (drainage[ntt[j]] == 0)
				{
					candidates.add(ntt[j]);
				}
			}

			if (candidates.size() == 0)
			{
				todo.remove(i);
				continue;
			}

			int j = (int) Math.floor(Math.random() * candidates.size());
			int tile1 = candidates.get(j);

			drainage[tile1] = tile+1;
			todo.add(tile1);
		}

		int countSources = 0;
		for (int n : world.g.getNeighbors(regionId))
		{
			if (world.drainage[n-1] == regionId && world.elevation[n-1] >= 0)
			{
				makeRiverFrom(pickBorderTile(n), drainage);
				countSources++;
			}
		}

		if (countSources == 0 && world.elevation[regionId-1] >= 0 && sinkRegion > 0)
		{
			makeRiverFrom(
				pickRandomInnerTile(),
				drainage);
		}
	}

	private void makeRiverFrom(TerrainId tid, int [] drainage)
	{
		int t = tid.tile;
		int count = 0;
		while (t >= 0)
		{
			setTerrainType(t, TerrainType.STREAM);
			count++;

			if (count == 2 && world.elevation[regionId-1] < 0)
				break;

			t = drainage[t] - 1;
		}
	}

	static double Randomizer(double x)
	{
		return x * MyRandomVariateGenerator.next();
	}

	/**
	 * @param p is the proportion of the max-population-density
	 *   that the population is currently experiencing.
	 * @return birth rate, expressed as the portion of the current
	 *   population that will spawn new life
	 */
	static double getBasicBirthRate(double p, double r)
	{
// cubic function that intersects (0,0), (0.1,0), and (1.0,0)
// y=(x-0)(x-0.1)(x-1)

		double v = r - (p-0)*(p-0.1)*(p-1);  //magic
		return v > 0 ? v : 0;
	}

	static final int WILDLIFE_PREFERRED_TEMPERATURE = 240;
	static final int WILDLIFE_TEMPERATURE_TOLERANCE = 80;
	void doWildlifeMaintenance_stage1()
	{
		final double wildlifeQuota = biome.getWildlifeQuota();
		double biomeTolerance = 1.0 - Math.pow((WILDLIFE_PREFERRED_TEMPERATURE - world.getRegionTemperature(regionId)) / WILDLIFE_TEMPERATURE_TOLERANCE, 2.0);
		assert biomeTolerance <= 1.0;

		double quotaPortion = ((double)wildlife) / wildlifeQuota;
		double birthRate = getBasicBirthRate(quotaPortion, 1/WILDLIFE_LIFESPAN);
		wildlifeBirths = (int) Math.round(Randomizer(wildlife * birthRate));

		assert wildlifeBirths >= 0;

		double deathRate = 1.0 / WILDLIFE_LIFESPAN;
		if (biomeTolerance < 0.0)
		{
			deathRate *= Math.exp(-biomeTolerance);
		}
		int deaths = (int) Math.round(Randomizer(wildlife * deathRate));
		wildlifeDeaths = wildlifeHunted > deaths ? 0 :
			deaths > wildlife ? wildlife - wildlifeHunted :
			deaths - wildlifeHunted;

		int adjustedCount = wildlife - (wildlifeHunted + wildlifeDeaths);
		adjustedCount = Math.max(0, adjustedCount);

		quotaPortion = ((double)adjustedCount) / wildlifeQuota;
		double emigrantPortion = WILDLIFE_EMIGRATION_RATE
			* (0.5 - Math.cos(quotaPortion * Math.PI)/2);
		double eligibleEmigrants = emigrantPortion * adjustedCount;
		wildlifeEmigrants = 0;

		int [] nn = world.g.getNeighbors(regionId);
		for (int n : nn)
		{
			ShadowRegion neighborRegion = world.getShadowRegion(n);
			BiomeType neighborBiome = neighborRegion.getBiome();

			double emigrants = eligibleEmigrants / nn.length;
			if (neighborBiome == BiomeType.OCEAN)
				emigrants = 0;

			int emigrantsI = (int) Math.round(Randomizer(emigrants));
			wildlifeEmigrants += emigrantsI;
			neighborRegion.importWildlife(emigrantsI);
		}
	}

	void doWildlifeMaintenance_cleanup()
	{
if (false)
{
	System.out.println("Region "+regionId);
	System.out.printf("beginning balance :%8d\n", wildlife);
	System.out.printf("           births :%8d\n", wildlifeBirths);
	System.out.printf("           deaths :%8d\n", wildlifeDeaths);
	System.out.printf("           hunted :%8d\n", wildlifeHunted);
	System.out.printf("       immigrants :%8d\n", wildlifeImmigrants);
	System.out.printf("        emigrants :%8d\n", wildlifeEmigrants);
	System.out.println();
}
		wildlife += wildlifeBirths - wildlifeDeaths - wildlifeHunted
			+ wildlifeImmigrants - wildlifeEmigrants;
		wildlifeImmigrants = 0;
	}

	void endOfYear_stage1()
	{
		doWildlifeMaintenance_stage1();
	}

	void endOfYear_cleanup()
	{
		doWildlifeMaintenance_cleanup();
	}

	int findNeighbor(Geometry.VertexId cornerVtx)
	{
		int [] cc = cornerVtx.getAdjacentCells();
		if (cc[0] == regionId)
		{
			return findNeighbor(cc[1]);
		}
		else
		{
			throw new Error("not implemented");
		}
	}

	/**
	 * Finds the index number of the specified region in this region's
	 * neighbor list.
	 */
	int findNeighbor(int neighborId)
	{
		int [] nn = world.g.getNeighbors(regionId);
		for (int i = 0; i < nn.length; i++)
		{
			if (nn[i] == neighborId)
			{
				return i;
			}
		}

		throw new Error("Unexpected: region "+neighborId+" is not a neighbor");
	}

	public BiomeType getBiome()
	{
		return biome;
	}

	public int getDepth()
	{
		int dep = this.waterLevel - world.elevation[regionId-1];
		return dep > 0 ? dep : 0;
	}

	public RegionSideDetail.SideFeature getSideFeature(int sideIndex)
	{
		if (sides[sideIndex] != null)
		{
			return sides[sideIndex].feature;
		}
		else
		{
			return RegionSideDetail.SideFeature.NONE;
		}
	}

	public void importWildlife(int newWildlife)
	{
		assert newWildlife >= 0;
		this.wildlifeImmigrants += newWildlife;
	}

	void setLake(Geometry.VertexId cornerVtx, RegionCornerDetail.PointFeature lakeType)
	{
		int i = findNeighbor(cornerVtx);
		if (corners[i] == null)
		{
			corners[i] = new RegionCornerDetail();
		}
		corners[i].feature = lakeType;
		dirty = true;
	}

	void clearSides()
	{
		for (int i = 0; i < sides.length; i++)
		{
			sides[i] = null;
		}
		for (int i = 0; i < corners.length; i++)
		{
			corners[i] = null;
		}
	}

	void setRiver(int neighborId, RegionSideDetail.SideFeature riverLevel)
	{
		int i = findNeighbor(neighborId);
		if (sides[i] == null)
		{
			sides[i] = new RegionSideDetail();
		}
		sides[i].feature = riverLevel;
		dirty = true;
	}

	public void setTerrainType(int terrainId, TerrainType type)
	{
		terrains[terrainId] = type.id;
		dirty = true;
	}

	void save(File regionFile)
		throws IOException
	{
		JsonGenerator out = new JsonFactory().createJsonGenerator(regionFile,
					JsonEncoding.UTF8);
		out.writeStartObject();
		out.writeStringField("biome", biome.name());
		out.writeNumberField("wildlife", wildlife);
		out.writeNumberField("waterLevel", waterLevel);
		out.writeArrayFieldStart("terrains");
		for (int i = 0; i < terrains.length; i++)
		{
			out.writeNumber(terrains[i]);
		}
		out.writeEndArray();

		for (int i = 0; i < sides.length; i++)
		{
			if (sides[i] != null)
			{
				out.writeFieldName("side"+i);
				sides[i].write(out);
			}
		}
		for (int i = 0; i < corners.length; i++)
		{
			if (corners[i] != null)
			{
				out.writeFieldName("corner"+i);
				corners[i].write(out);
			}
		}
		out.writeEndObject();
		out.close();
	}

	static RegionDetail create(MakeWorld world, int regionId)
	{
		RegionDetail m = new RegionDetail(world, regionId);
		m.init();
		return m;
	}

	static RegionDetail load(File regionFile, MakeWorld world, int regionId)
		throws IOException
	{
		RegionDetail m = new RegionDetail(world, regionId);
		m.load(regionFile);
		return m;
	}

	void load(File regionFile)
		throws IOException
	{
		JsonParser in = new JsonFactory().createJsonParser(regionFile);
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("terrains"))
				terrains = MakeWorld.json_readIntArray(in);
			else if (s.equals("wildlife"))
				wildlife = in.nextIntValue(wildlife);
			else if (s.equals("waterLevel"))
				waterLevel = in.nextIntValue(waterLevel);
			else if (s.equals("biome"))
				biome = BiomeType.valueOf(in.nextTextValue());
			else if (s.equals("side0"))
				sides[0] = RegionSideDetail.parse(in);
			else if (s.equals("side1"))
				sides[1] = RegionSideDetail.parse(in);
			else if (s.equals("side2"))
				sides[2] = RegionSideDetail.parse(in);
			else if (s.equals("side3"))
				sides[3] = RegionSideDetail.parse(in);
			else if (s.equals("side4"))
				sides[4] = RegionSideDetail.parse(in);
			else if (s.equals("side5"))
				sides[5] = RegionSideDetail.parse(in);
			else if (s.equals("corner0"))
				corners[0] = RegionCornerDetail.parse(in);
			else if (s.equals("corner1"))
				corners[1] = RegionCornerDetail.parse(in);
			else if (s.equals("corner2"))
				corners[2] = RegionCornerDetail.parse(in);
			else if (s.equals("corner3"))
				corners[3] = RegionCornerDetail.parse(in);
			else if (s.equals("corner4"))
				corners[4] = RegionCornerDetail.parse(in);
			else if (s.equals("corner5"))
				corners[5] = RegionCornerDetail.parse(in);
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized property: "+s);
			}
		}
	}
}
