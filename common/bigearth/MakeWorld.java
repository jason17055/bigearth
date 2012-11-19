package bigearth;

import java.io.*;
import java.util.*;
import javax.vecmath.*;
import com.fasterxml.jackson.core.*;

public class MakeWorld
{
	static final int AVERAGE_RAINFALL = 990;

	File worldDir;

	SphereGeometry g;
	int [] elevation;
	int [] temperature; //in 10th degrees Celsius
	int [] annualRains; //in millimeters-per-year
	int [] floods;

	WorldConfig worldConfig;
	WorldMaster world;

	protected MakeWorld()
	{
	}

	public WorldConfig getConfig()
	{
		return worldConfig;
	}

	private static void arrayHelper(JsonGenerator j, String fieldName, int [] a)
		throws IOException
	{
		j.writeArrayFieldStart(fieldName);
		for (int i = 0; i < a.length; i++)
		{
			j.writeNumber(a[i]);
		}
		j.writeEndArray();
	}

	//not being used any more?
	static int [] json_readIntArray(JsonParser in)
		throws IOException
	{
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_ARRAY;

		int [] tmp = new int[1000];
		int count = 0;
		while (in.nextToken() != JsonToken.END_ARRAY)
		{
			if (count == tmp.length)
			{
				tmp = Arrays.copyOf(tmp, tmp.length*2);
			}
			tmp[count++] = in.getIntValue();
		}

		return Arrays.copyOf(tmp, count);
	}

	public static MakeWorld create(File worldDir, int geometrySize)
		throws IOException
	{
		if (!worldDir.mkdir())
			throw new IOException("Could not create directory: "+worldDir);

		Properties props = new Properties();
		props.setProperty("geometry", "sphere:"+geometrySize);
		FileOutputStream propsOut = new FileOutputStream(new File(worldDir, "world.config"));
		props.store(propsOut, "MakeWorld");
		propsOut.close();

		MakeWorld me = new MakeWorld();
		me.worldConfig = WorldConfig.load(worldDir);

		me.worldDir = worldDir;
		me.g = (SphereGeometry) me.worldConfig.getGeometry();

		int numCells = me.g.getCellCount();
		me.elevation = new int[numCells];
		me.temperature = new int[numCells];
		me.annualRains = new int[numCells];
		me.floods = new int[numCells];

		WorldMaster world = new WorldMaster(me.worldConfig);

		for (int i = 0; i < numCells; i++)
		{
			int regionId = i+1;
			world.regions[i] = RegionDetail.create(world, regionId);
		}
		world.load();

		me.world = world;
		return me;
	}

	public static MakeWorld load(File worldDir)
		throws IOException
	{
		MakeWorld me = new MakeWorld();
		me.worldConfig = WorldConfig.load(worldDir);
		me.world = new WorldMaster(me.worldConfig);
		me.load();
		return me;
	}

	private void load()
		throws IOException
	{
		this.g = (SphereGeometry) worldConfig.getGeometry();

		int numCells = g.getCellCount();
		this.elevation = new int[numCells];
		this.temperature = new int[numCells];
		this.annualRains = new int[numCells];
		this.floods = new int[numCells];

		assert world.regions != null;

		for (int i = 0; i < world.regions.length; i++)
		{
			elevation[i] = world.regions[i].elevation;
			temperature[i] = world.regions[i].temperature;
			annualRains[i] = world.regions[i].annualRains;
			floods[i] = world.regions[i].floods;
		}
	}

	public void generate()
	{
		int numCells = g.getCellCount();

		this.elevation = new int[numCells];
		this.temperature = new int[numCells];

		// generate height map for entire sphere
		for (int i = 0, m = numCells/20; i <= m; i++)
		{
			bumpMap(g, elevation, 1);
		}
		int seaLevel = findSeaLevel(elevation, 0.6);
		for (int i = 0; i < numCells; i++)
		{
			elevation[i] -= seaLevel;
		}

		// generate temperature map
		// - start by calculating temperature based on latitude
		for (int i = 0; i < numCells; i++)
		{
			Point3d p = g.getCenterPoint(i+1);
			double lat = Math.asin(p.z);
			temperature[i] = (int)
				Math.round(240 - 200 * Math.pow(lat,2));
		}
		// - then apply some random noise to those numbers
		for (int i = 0, m = numCells/100; i <= m; i++)
		{
			bumpMap(g, temperature, i%2 != 0 ? 12 : -12);
		}

		//
		// determine rainfall levels and places of standing water
		//
		this.annualRains = new int[numCells];
		for (int i = 0; i < 20; i++)
		{
			status("Generating rains ("+(i*5)+"%)");
			generateRainfalls_oneStep(annualRains);
		}
		normalizeRains(annualRains);

		// modify all regions according to the numbers we generated...
		for (int i = 0; i < numCells; i++)
		{
			RegionDetail region = world.regions[i];
			region.elevation = this.elevation[i];
			region.temperature = this.temperature[i];
			region.annualRains = this.annualRains[i];
			region.floods = this.floods[i];
			region.biome = (
				this.elevation[i] >= 0 ? BiomeType.GRASSLAND :
				BiomeType.OCEAN);
		}
	}

	void doOneStep()
	{
		world.doOneStep();
	}

	private RegionDetail loadRegionDetail_DEPRECATE(int regionId)
	{
		File regionFile = new File(worldDir, "region"+regionId+".txt");
		if (regionFile.exists())
		{
			try
			{
				return RegionDetail.load(regionFile, world, regionId);
			}
			catch (IOException e)
			{
				// file exists but we got an error reading it
				throw new Error("unexpected", e);
			}
		}
		else
		{
			return RegionDetail.create(world, regionId);
		}
	}

	void status(String message)
	{
		System.out.println(message);
	}

	void normalizeRains(int [] rainfall)
	{
		// find mean rainfall amount
		double sum = 0.0;
		int count = 0;
		int minRain = Integer.MAX_VALUE;
		int maxRain = Integer.MIN_VALUE;
		for (int i = 0; i < rainfall.length; i++)
		{
			if (elevation[i] >= 0)
			{
				sum += rainfall[i];
				count++;

				if (rainfall[i] < minRain)
					minRain = rainfall[i];
				if (rainfall[i] > maxRain)
					maxRain = rainfall[i];
			}
		}
		double meanRainfall = sum / count;

		// find standard deviation
		sum = 0.0;
		for (int i = 0; i < rainfall.length; i++)
		{
			if (elevation[i] >= 0)
			{
				sum += Math.pow(rainfall[i]-meanRainfall, 2.0);
			}
		}
		double stddevRain = Math.sqrt(sum / count);

		System.out.println("rainfall stats (before normalization):");
		System.out.printf("  minimum rainfall  :%6d\n", minRain);
		System.out.printf("  maximum rainfall  :%6d\n", maxRain);
		System.out.printf("  average rainfall  :%8.1f\n", meanRainfall);
		System.out.printf("  standard deviation:%8.1f\n", stddevRain);

		for (int i = 0; i < rainfall.length; i++)
		{
			double x = (rainfall[i] - meanRainfall) / stddevRain;
			rainfall[i] = (int)Math.round(AVERAGE_RAINFALL * Math.exp(x));
		}
	}

	void generateRainfalls_oneStep(int [] rains)
	{
		int numCells = g.getCellCount();

		int highestTemperature = Integer.MIN_VALUE;
		for (int i = 0; i < numCells; i++)
		{
			if (temperature[i] > highestTemperature)
				highestTemperature = temperature[i];
		}

		RouletteWheel<Integer> rw = new RouletteWheel<Integer>();
		for (int i = 0; i < numCells; i++)
		{
			int hasWater = elevation[i] < 0 ? 20 : rains[i];
			double fit = hasWater * LogisticFunction((temperature[i] + 300 - highestTemperature)/100.0);
			if (fit > 0)
				rw.add(i+1, fit);
		}

		for (int s = 0; s < numCells; s++)
		{
			int start = rw.next();

			int startRaining = (int) Math.floor(Math.random() * 50) + 10;
			int stopRaining = startRaining + (int) Math.floor(Math.random() * 14) + 2;

			for (int dist = 0; dist < stopRaining; dist++)
			{
				int c_height = elevation[start-1];
				Point3d c_pt = g.getCenterPoint(start);
				double c_lat = Math.asin(c_pt.z);
				double c_lgt = Math.atan2(c_pt.y, c_pt.x);
				double winds = Math.cos(c_lat * 4.0);

				RouletteWheel<Integer> rw2 = new RouletteWheel<Integer>();
				int [] nn = g.getNeighbors(start);
				for (int n : nn)
				{
					Point3d n_pt = g.getCenterPoint(n);
					double n_lat = Math.asin(n_pt.z);
					double n_lgt = Math.atan2(n_pt.y, n_pt.x);
					double n_dir = Math.atan2(n_lat-c_lat, n_lgt-c_lgt);
					double wind_aid = Math.cos(n_dir) * winds;

					double height_diff = elevation[n-1] - c_height;
					double xfer = LogisticFunction(height_diff/2.0 * wind_aid);

					rw2.add(n, xfer);
				}

				start = rw2.next();

				int new_height_diff = elevation[start-1] - c_height;
				if (new_height_diff >= 1)
					dist += new_height_diff; // as clouds move over higher land, they
							// are more likely to rain

				if (dist >= startRaining)
					rains[start-1]++;
			}
		}
	}

	String getCellCoordsAsString(int cellId)
	{
		Point3d c_pt = g.getCenterPoint(cellId);
		double c_lat = Math.asin(c_pt.z);
		double c_lgt = Math.atan2(c_pt.y, c_pt.x);

		return String.format("%.0f%s%s %.0f%s%s",
			Math.abs(c_lat), "°", c_lat>0 ? "N" : c_lat<0 ? "S" : "",
			Math.abs(c_lgt), "°", c_lgt>0 ? "E" : c_lgt<0 ? "W" : "");
	}

	static double LogisticFunction(double t)
	{
		return 1.0 / (1.0 + Math.exp(-t));
	}

	static String getDirectionName(double compassDir)
	{
		int x = (int) Math.floor((compassDir + Math.PI/8 + Math.PI*2)/(Math.PI/4)) % 8;
		return x == 0 ? "E" :
			x == 1 ? "NE" :
			x == 2 ? "N" :
			x == 3 ? "NW" :
			x == 4 ? "W" :
			x == 5 ? "SW" :
			x == 6 ? "S" :
			x == 7 ? "SE" : "<bad>";
	}

	static double getSeaCoverage(int [] elevations, int seaLevel)
	{
		int countAbove = 0;
		int countBelow = 0;
		for (int lev : elevations)
		{
			if (lev >= seaLevel)
				countAbove++;
			else
				countBelow++;
		}
		return countBelow / (double)(countAbove+countBelow);
	}

	static int findSeaLevel(int [] elevations, double desiredSeaCoverage)
	{
		int minLevel = Integer.MAX_VALUE;
		int maxLevel = Integer.MIN_VALUE;
		for (int lev : elevations)
		{
			if (lev > maxLevel)
				maxLevel = lev;
			if (lev < minLevel)
				minLevel = lev;
		}

		while (minLevel + 1 < maxLevel)
		{
			int i = (minLevel+maxLevel+1)/2;
			double cov = getSeaCoverage(elevations, i);
			if (cov < desiredSeaCoverage)
			{
				// too much land, not enough sea;
				// sea level should be higher
				minLevel = i;
			}
			else
			{
				// too much sea, not enough land;
				// sea level should be lower
				maxLevel = i;
			}
		}
		return minLevel;
	}

	void bumpMap(SphereGeometry g, int [] values, int delta)
	{
		double M = Math.sqrt(1.0/3.0);
		Vector3d v = new Vector3d(
			Math.random() * 2 * M - M,
			Math.random() * 2 * M - M,
			Math.random() * 2 * M - M
			);

		for (int i = 0; i < values.length; i++)
		{
			Point3d c = g.getCenterPoint(i+1);
			Vector3d u = new Vector3d();
			u.sub(c, v);
			double dp = u.dot(v);
			if (dp > 0)
			{
				values[i] += delta;
			}
		}
	}

	public int getRegionTemperature(int regionId)
	{
		assert regionId >= 1 && regionId <= temperature.length;
		return temperature[regionId-1];
	}

	public void generateBiomes()
	{
		int numCells = g.getCellCount();
		for (int i = 0; i < numCells; i++)
		{
			RegionDetail region = world.regions[i];
			if (region.getBiome().isWater())
				continue;

			int temp = getRegionTemperature(i+1);
			int moisture = annualRains[i] + floods[i];

			double heightVariation = 0.0;
			int [] nn = g.getNeighbors(i+1);
			for (int nid : nn)
			{
				int hdiff = elevation[nid-1] - elevation[i];
				heightVariation += Math.pow(hdiff, 2.0);
			}
			heightVariation /= nn.length;

			if (temp < -20)
			{
				region.biome = BiomeType.GLACIER;
			}
			else if (elevation[i] >= 3 && heightVariation >= 4.0)
			{
				region.biome = BiomeType.MOUNTAIN;
			}
			else if (heightVariation >= 1.8)
			{
				region.biome = BiomeType.HILLS;
			}
			else if (moisture < 200 && temp >= 130)
			{
				region.biome = BiomeType.DESERT;
			}
			else if (moisture < 300 && temp < 100)
			{
				region.biome = BiomeType.TUNDRA;
			}
			else if (moisture >= 1500 && temp >= 210)
			{
				region.biome = BiomeType.JUNGLE;
			}
			else if (moisture >= 1800)
			{
				region.biome = BiomeType.SWAMP;
			}
			else if (moisture >= 1200)
			{
				region.biome = BiomeType.FOREST;
			}
			else if (moisture >= 600)
			{
				region.biome = BiomeType.GRASSLAND;
			}
			else
			{
				region.biome = BiomeType.PLAINS;
			}
		}
	}

	BiomeType getRegionBiome(int regionId)
	{
		return world.regions[regionId-1].biome;
	}

	void save()
		throws IOException
	{
		world.save();
	}
}
