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
	int childYears;
	int humanLifeExpectancy;
	long foodPerFarmer;
	long foodPerAnimal;
	/// Amount of nutrition needed per adult per year.
	double hungerPerAdult;
	/// Amount of nutrition needed per child per year.
	double hungerPerChild;
	int zonesPerRegion;
	int maxAnimalsPerPasture;

	/// How much work is required to start learning a new technology. This amount
	/// is required to start learning, and the same amount is required to finish
	/// learning it the following year.
	double newTechnologyWorkCost;

	/// How much work is required EACH YEAR to maintain a technology.
	double maintainTechnologyWorkCost;

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
		this.childYears = Integer.parseInt(
				properties.getProperty("childYears", "10")
				);
		this.humanLifeExpectancy = Integer.parseInt(
				properties.getProperty("humanLifeExpectancy", "50")
				);
		this.foodPerAnimal = Long.parseLong(
				properties.getProperty("foodPerAnimal", "2")
				);
		this.foodPerFarmer = Long.parseLong(
				properties.getProperty("foodPerFarmer", "5")
				);
		this.hungerPerAdult = Double.parseDouble(
				properties.getProperty("hungerPerAdult", "1.0")
				);
		this.hungerPerChild = Double.parseDouble(
				properties.getProperty("hungerPerChild", "1.0")
				);
		this.zonesPerRegion = Integer.parseInt(
				properties.getProperty("zonesPerRegion", "64")
				);
		this.maxAnimalsPerPasture = Integer.parseInt(
				properties.getProperty("maxAnimalsPerPasture", "200")
				);
		this.newTechnologyWorkCost = Double.parseDouble(
				properties.getProperty("newTechnologyWorkCost", "10.0")
				);
		this.maintainTechnologyWorkCost = Double.parseDouble(
				properties.getProperty("maintainTechnologyWorkCost", "0.25")
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
