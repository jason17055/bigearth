package bigearth;

import java.io.*;
import javax.vecmath.*;
import com.fasterxml.jackson.core.*;

class RegionDetail
	implements ShadowRegion
{
	MakeWorld world;
	int regionId;

	BiomeType biome;

	int wildlife;
	int wildlifeHunted;
	int wildlifeBirths;
	int wildlifeDeaths;
	int wildlifeEmigrants;
	int wildlifeImmigrants;

	int [] terrains;
	boolean dirty;

	static final double WILDLIFE_LIFESPAN = 5.0;
	static final double WILDLIFE_EMIGRATION_RATE = 0.25;

	RegionDetail(MakeWorld world, int regionId)
	{
		this.world = world;
		this.regionId = regionId;
		this.biome = BiomeType.GRASSLAND;
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

	static double Randomizer(double x)
	{
		return x * MyRandomVariateGenerator.next();
	}

	void doWildlifeMaintenance_stage1()
	{
		final double wildlifeQuota = biome.getWildlifeQuota();
		wildlifeBirths = (int) Math.round(Randomizer((wildlifeQuota/WILDLIFE_LIFESPAN) * Math.pow(0.5 - 0.5 * Math.cos(Math.PI * Math.sqrt(wildlife / wildlifeQuota)), 2.0)));

		assert wildlifeBirths >= 0;

		int deaths = (int) Math.round(Randomizer(wildlife / WILDLIFE_LIFESPAN));
		wildlifeDeaths = wildlifeHunted > deaths ? 0 :
			deaths > wildlife ? wildlife - wildlifeHunted :
			deaths - wildlifeHunted;

		int adjustedCount = wildlife - (wildlifeHunted + wildlifeDeaths);
		adjustedCount = Math.max(0, adjustedCount);

		double quotaPortion = ((double)adjustedCount) / wildlifeQuota;
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
if (wildlife > 100)
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

	public BiomeType getBiome()
	{
		return biome;
	}

	public void importWildlife(int newWildlife)
	{
		assert newWildlife >= 0;
		this.wildlifeImmigrants += newWildlife;
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
		out.writeArrayFieldStart("terrains");
		for (int i = 0; i < terrains.length; i++)
		{
			out.writeNumber(terrains[i]);
		}
		out.writeEndArray();
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
			else if (s.equals("biome"))
				biome = BiomeType.valueOf(in.nextTextValue());
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized property: "+s);
			}
		}
	}
}
