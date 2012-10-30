package bigearth;

import javax.vecmath.*;
//import com.mongodb.*;

public class MakeWorld
{
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

		new MakeWorld(worldName, geometrySize).run();
	}

	String worldName;
	int geometrySize;

	MakeWorld(String worldName, int geometrySize)
	{
		this.worldName = worldName;
		this.geometrySize = geometrySize;
	}

	public void run()
	{
	/*	Mongo m;
		DB db;
		m = new Mongo();
		db = m.getDB("world1");

		DBCollection regions = db.getCollection("regions");
	*/
		
		SphereGeometry g = new SphereGeometry(geometrySize);
		int numCells = g.getCellCount();

		int [] elevation = new int[numCells];
		int [] temperature = new int[numCells];
		int [] summerRains = new int[numCells];
		int [] winterRains = new int[numCells];

		// generate height map for entire sphere
		for (int i = 0; i < 100; i++)
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
			Point3d p = getCenterPoint(i+1);
			double lat = Math.asin(p.z);
			temperature[i] = (int)
				Math.round(240 - 200 * Math.pow(lat,2));
		}
		// - then apply some random noise to those numbers
		for (int i = 0; i < 30; i++)
		{
			bumpMap(g, temperature, i%2 != 0 ? 2 : -2);
		}

		//
		// determine rainfall levels
		//

		// - assign initial moisture numbers based on ocean
		//   and temperature
		for (int i = 0; i < numCells; i++)
		{
			double lat = Math.asin(g.getCenterPoint(i+1).z);
			int temp = temperature[i];

			winterRains[i] = elevation[i] < 0 ?
				(int)Math.round(30*Math.pow(0.5,(240-temp)/120.0)) :
				0;
			
			// middle latitudes will get seasonal variation
			// in rains
			summerRains[i] = winterRains[i]
				+ Math.round(6.0 * Math.sin(lat/2.0));
		}
		for (int i = 0; i < 50; i++)
		{
			//blurMoisture(summerRains);
			//blurMoisture(winterRains);
		}
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
