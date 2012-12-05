package bigearth;

import java.io.*;
import java.util.*;

/**
 * Public information about a world. Immutable.
 * All hosts in the cluster read this file at startup.
 * If the world configuration changes, all hosts in the cluster
 * need to be restarted.
 */
public class WorldConfig implements WorldConfigIfc
{
	File path;
	Properties properties;
	Geometry geometry;
	long ticksPerYear;

	private WorldConfig(File path)
	{
		this.path = path;
		this.properties = new Properties();
	}

	public static WorldConfig load(File worldPath)
		throws IOException
	{
		WorldConfig m = new WorldConfig(worldPath);
		m.load_real();
		return m;
	}

	private void load_real()
		throws IOException
	{
		File worldPropsFile = new File(path, "world.config");
		FileInputStream in = new FileInputStream(worldPropsFile);
		properties.load(in);
		in.close();

		this.geometry = GeometryFactory.getInstance(
				properties.getProperty("geometry", "sphere:20")
				);
		this.ticksPerYear = Long.parseLong(
				properties.getProperty("ticksPerYear", "60000")
				);
	}

	// implements WorldConfigIfc
	public Geometry getGeometry()
	{
		return this.geometry;
	}

	// implements WorldConfigIfc
	public long getTicksPerYear()
	{
		return this.ticksPerYear;
	}

	public boolean isValidHost(String hostName)
	{
		String [] hosts = properties.getProperty("cluster.hosts", "localhost").split("\\s*,\\s*");
		return Arrays.asList(hosts).contains(hostName);
	}
}
