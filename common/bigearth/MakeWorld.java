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
		int [] elevation = new int[g.getCellCount()];
		int [] summerRains = new int[g.getCellCount()];
		int [] winterRains = new int[g.getCellCount()];

		// generate height map for entire sphere
		for (int i = 0; i < 100; i++)
		{
			bumpMap(g, elevation, 1);
		}
		int seaLevel = findSeaLevel(elevation, 0.6);
		for (int i = 0; i < elevation.length; i++)
		{
			elevation[i] -= seaLevel;
System.out.printf("%6d:%6d\n", i+1, elevation[i]);
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
