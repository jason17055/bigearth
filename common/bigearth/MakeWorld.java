package bigearth;

import java.io.*;
import java.util.*;
import javax.vecmath.*;
import com.fasterxml.jackson.core.*;

public class MakeWorld
{
	static final int AVERAGE_RAINFALL = 990;

	File worldDir;
	int geometrySize;
	int regionDetailLevel;

	SphereGeometry g;
	int [] elevation;
	int [] temperature; //in 10th degrees Celsius
	int [] annualRains; //in millimeters-per-year
	int [] drainage;    //pointers to neighboring regions
	int [] riverVolume;
	int [] lakeLevel;
	int [] floods;
	RegionDetail [] regions;

	int year;

	protected MakeWorld()
	{
	}

	public MakeWorld(File worldDir, int geometrySize)
	{
		this.worldDir = worldDir;
		this.geometrySize = geometrySize;
		this.regionDetailLevel = 2;
		this.g = new SphereGeometry(geometrySize);

		int numCells = g.getCellCount();
		this.elevation = new int[numCells];
		this.temperature = new int[numCells];
		this.annualRains = new int[numCells];
		this.drainage = new int[numCells];
		this.riverVolume = new int[numCells];
		this.lakeLevel = new int[numCells];
		this.floods = new int[numCells];
		this.regions = new RegionDetail[numCells];
	}

	public void save()
		throws IOException
	{
		File f2 = new File(worldDir, "world.txt");
		JsonGenerator j = new JsonFactory().createJsonGenerator(f2, JsonEncoding.UTF8);
		j.writeStartObject();
		j.writeNumberField("size", geometrySize);
		j.writeNumberField("regionDetailLevel", regionDetailLevel);
		j.writeNumberField("year", year);
		arrayHelper(j, "elevation", elevation);
		arrayHelper(j, "temperature", temperature);
		arrayHelper(j, "annualRains", annualRains);
		arrayHelper(j, "drainage", drainage);
		arrayHelper(j, "riverVolume", riverVolume);
		arrayHelper(j, "lakeLevel", lakeLevel);
		arrayHelper(j, "floods", floods);
		j.writeEndObject();
		j.close();

		for (int i = 0; i < regions.length; i++)
		{
			File regionFile = new File(worldDir, "region"+(i+1)+".txt");
			regions[i].save(regionFile);
		}
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

	public static MakeWorld load(File worldDir)
		throws IOException
	{
		MakeWorld me = new MakeWorld();
		me.worldDir = worldDir;
		me.load();
		return me;
	}

	public void load()
		throws IOException
	{
		File inFile = new File(worldDir, "world.txt");
		JsonParser in = new JsonFactory().createJsonParser(inFile);

		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("size"))
				geometrySize = in.nextIntValue(geometrySize);
			else if (s.equals("regionDetailLevel"))
				regionDetailLevel = in.nextIntValue(regionDetailLevel);
			else if (s.equals("year"))
				year = in.nextIntValue(year);
			else if (s.equals("elevation"))
				elevation = json_readIntArray(in);
			else if (s.equals("temperature"))
				temperature = json_readIntArray(in);
			else if (s.equals("annualRains"))
				annualRains = json_readIntArray(in);
			else if (s.equals("drainage"))
				drainage = json_readIntArray(in);
			else if (s.equals("riverVolume"))
				riverVolume = json_readIntArray(in);
			else if (s.equals("lakeLevel"))
				lakeLevel = json_readIntArray(in);
			else if (s.equals("floods"))
				floods = json_readIntArray(in);
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized property: "+s);
			}
		}

		in.close();

		this.g = new SphereGeometry(geometrySize);

		assert this.elevation != null;
		assert this.temperature != null;
		assert this.annualRains != null;
		assert this.drainage != null;
		assert this.riverVolume != null;
		assert this.lakeLevel != null;
		assert this.floods != null;

		this.regions = new RegionDetail[g.getCellCount()];
		for (int i = 0; i < regions.length; i++)
		{
			this.regions[i] = loadRegionDetail(i+1);
			if (this.regions[i].biome == null)
			{
			this.regions[i].biome = (
				this.elevation[i] >= 0 ? BiomeType.GRASSLAND :
				BiomeType.OCEAN);
			}
		}
	}

	public void generate()
	{
	/*	Mongo m;
		DB db;
		m = new Mongo();
		db = m.getDB("world1");

		DBCollection regions = db.getCollection("regions");
	*/
		
		this.g = new SphereGeometry(geometrySize);
		int numCells = g.getCellCount();

		this.elevation = new int[numCells];
		this.temperature = new int[numCells];
		this.regions = new RegionDetail[numCells];

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

		generateDrainage();
		generateRivers();
		generateFloods();

		this.regions = new RegionDetail[g.getCellCount()];
		for (int i = 0; i < regions.length; i++)
		{
			this.regions[i] = loadRegionDetail(i+1);
			this.regions[i].biome = (
				this.elevation[i] >= 0 ? BiomeType.GRASSLAND :
				BiomeType.OCEAN);
		}
	}

	void doOneStep()
	{
		year++;
		for (RegionDetail r : regions)
		{
			r.endOfYear_stage1();
		}
		for (RegionDetail r : regions)
		{
			r.endOfYear_cleanup();
		}
	}

	private RegionDetail loadRegionDetail(int regionId)
	{
		File regionFile = new File(worldDir, "region"+regionId+".txt");
		if (regionFile.exists())
		{
			try
			{
				return RegionDetail.load(regionFile, this, regionId);
			}
			catch (IOException e)
			{
				// file exists but we got an error reading it
				throw new Error("unexpected", e);
			}
		}
		else
		{
			return RegionDetail.create(this, regionId);
		}
	}

	void enhanceRegion(int regionId)
	{
		assert regionId >= 1 && regionId <= g.getCellCount();

		int [] nn = g.getNeighbors(regionId);
		RegionDetail [] neighbors = new RegionDetail[nn.length];
		for (int i = 0; i < nn.length; i++)
			neighbors[i] = loadRegionDetail(nn[i]);

		//TODO... something...
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

	void generateDrainage()
	{
		int numCells = g.getCellCount();
		this.drainage = new int[numCells];

		List<Integer> todo = new ArrayList<Integer>();
		for (;;)
		{
			if (todo.isEmpty())
			{
				// find a starting spot
				int best = -1;
				int bestVal = Integer.MAX_VALUE;
				for (int i = 0; i < numCells; i++)
				{
					if (drainage[i] == 0 && elevation[i] < bestVal)
					{
						best = i+1;
						bestVal = elevation[i];
					}
				}
				if (best == -1)
					return;

				drainage[best-1] = -1;
				todo.add(best);
			}

			// pick a place to branch from
			int i = (int)Math.floor(Math.random() * todo.size());
			int cur = todo.get(i);
			int curEl = elevation[cur-1];

			// pick a direction to branch to
			RouletteWheel<Integer> rw = new RouletteWheel<Integer>();
			for (int n : g.getNeighbors(cur))
			{
				if (drainage[n-1] == 0 && elevation[n-1] >= curEl)
				{
					rw.add(n, 1);
				}
			}

			if (!rw.isEmpty())
			{
				int n = rw.next();
				drainage[n-1] = cur;
				todo.add(n);
			}
			else
			{
				todo.remove(i);
			}
		}
	}

	static final int LAKE_VOLUME = 800;
	void generateRivers()
	{
		status("Generating rivers");

		int numCells = g.getCellCount();
		this.riverVolume = new int[numCells];
		this.lakeLevel = new int[numCells];
		for (int i = 0; i < numCells; i++)
		{
			lakeLevel[i] = elevation[i];
		}

		int [] lakeVolume = new int[numCells];

		for (int i = 0; i < numCells; i++)
		{
			if (elevation[i] < 0)
				continue;

			int water = annualRains[i];
			int cur = i+1;
			while (drainage[cur-1] > 0)
			{
				cur = drainage[cur-1];
				riverVolume[cur-1] += water;
			}

			lakeVolume[cur-1] += water;
		}

		//
		// process river sinks that are above sea level
		// (turn them into lakes)
		//

		processLakes(lakeVolume);


		// normalize river volumes
		double sum = 0.0;
		int count = 0;
		for (int i = 0; i < numCells; i++)
		{
			if (elevation[i] < 0)
				continue;

			sum += riverVolume[i];
			count++;
		}
		double meanRiverVolume = sum / count;

		int threshold = (int) Math.ceil(meanRiverVolume*2);
		for (int i = 0; i < numCells; i++)
		{
			if (elevation[i] >= 0)
			{
				if (riverVolume[i] > threshold)
				{
					riverVolume[i] -= threshold;
					continue;
				}
			}
			riverVolume[i] = 0;
		}
	}

	void processLakes(int [] lakeVolume)
	{
		for (;;)
		{
			// look for the highest-elevation lake
			int best = -1;
			int bestEl = -1;
			for (int i = 0; i < lakeVolume.length; i++)
			{
				if (elevation[i] > bestEl && lakeVolume[i] > 0)
				{
					best = i + 1;
					bestEl = elevation[i];
				}
			}

			if (best == -1)
				break; //got them all

			makeLake(best, lakeVolume);
		}
	}

	void makeLake(int cellId, int [] lakeVolume)
	{
		int remaining = lakeVolume[cellId-1];
		lakeVolume[cellId-1] = 0;

		// raise this lake's level by one
		int level = lakeLevel[cellId-1] + 1;

		Queue<Integer> Q = new ArrayDeque<Integer>();
		Q.add(cellId);

		while (!Q.isEmpty() && remaining > 0)
		{
			int cur = Q.remove();

			if (lakeLevel[cur-1] < level)
			{
				remaining -= LAKE_VOLUME * (level - lakeLevel[cur-1]);
				lakeLevel[cur-1] = level;
			}

			// check where this cell drains to
			int sink = cur;
			while (drainage[sink-1] > 0)
				sink = drainage[sink-1];

			if (lakeLevel[sink-1] < level)
			{
				// this tile sinks out of this lake system
				// dump the leftover water there
				sink = cur;
				while (drainage[sink-1] > 0)
				{
					sink = drainage[sink-1];
					riverVolume[sink-1] += remaining;
				}

				lakeVolume[sink-1] += remaining;
				return;
			}

			for (int n : g.getNeighbors(cur))
			{
				if (lakeLevel[n-1] < level)
					Q.add(n);
			}
		}

		lakeVolume[cellId-1] = remaining > 0 ? remaining : 0;
	}

	void generateFloods()
	{
		this.floods = new int[g.getCellCount()];

		int maxRiverVolume = 3;
		for (int i = 0; i < riverVolume.length; i++)
		{
			if (riverVolume[i] > maxRiverVolume)
				maxRiverVolume = riverVolume[i];
		}

		Queue<Integer> Q = new ArrayDeque<Integer>();
		for (int i = 0; i < floods.length; i++)
		{
			if (riverVolume[i] > 0)
			{
				int floodLevel = 1+(int)Math.round(9.0 * Math.sqrt((double)riverVolume[i]/maxRiverVolume));
				floods[i] = floodLevel;
				Q.add(i+1);
			}
			else if (elevation[i] < 0)
			{
				floods[i] = 4;
				Q.add(i+1);
			}
			else if (lakeLevel[i] > elevation[i])
			{
				floods[i] = 2;
				Q.add(i+1);
			}
		}

		while (!Q.isEmpty())
		{
			int cur = Q.remove();
			for (int n : g.getNeighbors(cur))
			{
				int heightDiff = elevation[n-1] - elevation[cur-1];
				int floodLevel = floods[cur-1] - (1 + heightDiff);
				if (elevation[n-1] >= 0 && floodLevel > floods[n-1])
				{
					floods[n-1] = floodLevel;
					Q.add(n);
				}
			}
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

	ShadowRegion getShadowRegion(int regionId)
	{
		assert regionId >= 1 && regionId <= regions.length;
		return regions[regionId-1];
	}

	public TerrainGeometry getTerrainGeometry()
	{
		return new TerrainGeometry(g, regionDetailLevel);
	}
}
