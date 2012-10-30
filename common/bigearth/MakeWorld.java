package bigearth;

import java.io.*;
import java.util.*;
import javax.vecmath.*;
//import com.mongodb.*;

public class MakeWorld
{
	static final int AVERAGE_RAINFALL = 990;

	public static void main(String [] args)
		throws Exception
	{
		int geometrySize = 25;
		int baseArg = 0;
		while (baseArg < args.length && args[baseArg].startsWith("-"))
		{
			if (args[baseArg].equals("-size"))
			{
				geometrySize = Integer.parseInt(args[baseArg+1]);
				baseArg += 2;
			}
			else
			{
				throw new Error("unrecognized argument : " + args[baseArg]);
			}
		}

		if (args.length != baseArg + 1)
			throw new Error("Invalid number of arguments");

		String worldName = args[baseArg++];

		new MakeWorld(worldName, geometrySize).generate();
	}

	String worldName;
	int geometrySize;

	SphereGeometry g;
	int [] elevation;
	int [] temperature; //in 10th degrees Celsius
	int [] annualRains; //in millimeters-per-year
	int [] drainage;
	int [] riverVolume;
	int [] lakeVolume;

	MakeWorld()
	{
	}

	MakeWorld(String worldName, int geometrySize)
	{
		this.worldName = worldName;
		this.geometrySize = geometrySize;
	}

	public void save(File outFile)
		throws IOException
	{
		PrintWriter out = new PrintWriter(
			new OutputStreamWriter(
				new FileOutputStream(outFile)));
		out.println(geometrySize);
		for (int i = 0; i < g.getCellCount(); i++)
		{
			out.printf("%d %d %d %d %d %d\n",
				elevation[i],
				temperature[i],
				annualRains[i],
				drainage[i],
				riverVolume[i],
				lakeVolume[i]);
		}
		out.close();
	}

	public void load(File inFile)
		throws IOException
	{
		BufferedReader in = new BufferedReader(
			new InputStreamReader(
				new FileInputStream(inFile)));
		this.geometrySize = Integer.parseInt(in.readLine());
		this.g = new SphereGeometry(geometrySize);

		int numCells = g.getCellCount();
		this.elevation = new int[numCells];
		this.temperature = new int[numCells];
		this.annualRains = new int[numCells];
		this.drainage = new int[numCells];
		this.riverVolume = new int[numCells];
		this.lakeVolume = new int[numCells];
		for (int i = 0; i < numCells; i++)
		{
			String [] parts = in.readLine().split(" ");
			elevation[i] = Integer.parseInt(parts[0]);
			temperature[i] = Integer.parseInt(parts[1]);
			annualRains[i] = Integer.parseInt(parts[2]);
			drainage[i] = Integer.parseInt(parts[3]);
			riverVolume[i] = Integer.parseInt(parts[4]);
			lakeVolume[i] = Integer.parseInt(parts[5]);
		}
		in.close();
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

		// generate height map for entire sphere
		for (int i = 0, m = numCells/45; i <= m; i++)
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

		status("Generating rivers");
		generateRivers();
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

		for (;;)
		{
			// find lowest point that hasn't been calculated yet
			int best = -1;
			int bestVal = Integer.MAX_VALUE;
			for (int i = 0; i < numCells; i++)
			{
				if (drainage[i] == 0 && elevation[i] < bestVal)
				{
					best = i;
					bestVal = elevation[i];
				}
			}

			if (best == -1)
				break;

			drainage[best] = -1; //indicates that water has no place to go
			Queue<Integer> Q = new ArrayDeque<Integer>();
			Q.add(best+1);

			while (!Q.isEmpty())
			{
				int cur = Q.remove();
				int [] nn = g.getNeighbors(cur);
				for (int n : nn)
				{
					if (drainage[n-1] == 0)
					{
						drainage[n-1] = cur;
						Q.add(n);
					}
				}
			}
		}
	}

	void generateRivers()
	{
		int numCells = g.getCellCount();
		this.riverVolume = new int[numCells];
		this.lakeVolume = new int[numCells];

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
			rw.add(i+1, hasWater * LogisticFunction((temperature[i] + 300 - highestTemperature)/100.0));
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
}
