package bigearth;

import java.io.*;
import java.util.*;
import javax.vecmath.*;
import com.fasterxml.jackson.core.*;

class RegionServant
	implements ShadowRegion, Saveable
{
	WorldMaster world;
	int regionId;

	BiomeType biome;
	RegionSideDetail [] sides;
	RegionCornerDetail [] corners;
	int waterLevel;
	Map<String, MobInfo> presentMobs;

	/// Elevation as generated randomly by MakeWorld.
	int elevation;
	/// Average temperature, in tenths of degrees Celsius.
	int temperature;
	/// Average annual rainfall, in millimeters.
	int annualRains;
	/// Average additional moisture from flooding of flood plains.
	int floods;

	int wildlife;
	int wildlifeHunted;
	int wildlifeBirths;
	int wildlifeDeaths;
	int wildlifeEmigrants;
	int wildlifeImmigrants;

	boolean dirty;

	static final double WILDLIFE_LIFESPAN = 5.0;
	static final double WILDLIFE_EMIGRATION_RATE = 0.25;

	RegionServant(WorldMaster world, int regionId)
	{
		this.world = world;
		this.regionId = regionId;
		this.biome = BiomeType.GRASSLAND;
		this.sides = new RegionSideDetail[6];
		this.corners = new RegionCornerDetail[6];
		this.waterLevel = Integer.MIN_VALUE;
		this.presentMobs = new HashMap<String, MobInfo>();
	}

	private void init()
	{
		int numSides = world.getGeometry().getNeighborCount(regionId);
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
		double biomeTolerance = 1.0 - Math.pow((WILDLIFE_PREFERRED_TEMPERATURE - this.temperature) / WILDLIFE_TEMPERATURE_TOLERANCE, 2.0);
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

		int [] nn = world.getRegionNeighbors(regionId);
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
		int [] nn = world.getRegionNeighbors(regionId);
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
		int dep = this.waterLevel - this.elevation;
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

	File getRegionFilename()
	{
		return new File(world.config.path, "region"+regionId+".txt");
	}

	void addMob(String mobName, MobInfo mob)
	{
		presentMobs.put(mobName, mob);
		world.wantSaved(this);
	}

	void removeMob(String mobName)
	{
		presentMobs.remove(mobName);
		world.wantSaved(this);
	}

	//implements Saveable
	public void save()
		throws IOException
	{
		File regionFile = getRegionFilename();
		JsonGenerator out = new JsonFactory().createJsonGenerator(regionFile,
					JsonEncoding.UTF8);
		out.writeStartObject();
		out.writeStringField("biome", biome.name());
		out.writeNumberField("wildlife", wildlife);
		out.writeNumberField("waterLevel", waterLevel);
		out.writeNumberField("elevation", elevation);
		out.writeNumberField("temperature", temperature);
		out.writeNumberField("annualRains", annualRains);
		out.writeNumberField("floods", floods);

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

		if (!presentMobs.isEmpty())
		{
			out.writeFieldName("mobs");
			out.writeStartObject();
			for (String mobName : presentMobs.keySet())
			{
				out.writeFieldName(mobName);
				presentMobs.get(mobName).write(out);
			}
			out.writeEndObject();
		}

		out.writeEndObject();
		out.close();
	}

	static RegionServant create(WorldMaster world, int regionId)
	{
		RegionServant m = new RegionServant(world, regionId);
		m.init();
		return m;
	}

	static RegionServant load(WorldMaster world, int regionId)
		throws IOException
	{
		RegionServant m = new RegionServant(world, regionId);
		m.load();
		return m;
	}

	void load()
		throws IOException
	{
		File regionFile = getRegionFilename();

		JsonParser in = new JsonFactory().createJsonParser(regionFile);
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("wildlife"))
				wildlife = in.nextIntValue(wildlife);
			else if (s.equals("waterLevel"))
				waterLevel = in.nextIntValue(waterLevel);
			else if (s.equals("elevation"))
				elevation = in.nextIntValue(elevation);
			else if (s.equals("temperature"))
				temperature = in.nextIntValue(temperature);
			else if (s.equals("annualRains"))
				annualRains = in.nextIntValue(annualRains);
			else if (s.equals("floods"))
				floods = in.nextIntValue(floods);
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
			else if (s.equals("mobs"))
				loadMobs(in);
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized property: "+s);
			}
		}
	}

	private void loadMobs(JsonParser in)
		throws IOException
	{
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		presentMobs.clear();
		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String mobName = in.getCurrentName();
			MobInfo mob = MobInfo.parse(in, mobName, world.config);
			if (mob.location == null)
			{
				mob.location = new SimpleLocation(regionId);
			}
			presentMobs.put(mobName, mob);
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}

	void spawnCharacter(Location loc, String characterName, String avatarName)
	{
		assert world.getRegionIdForLocation(loc) == this.regionId;

		String mobName = world.nextUniqueName("mob");
		MobInfo mob = new MobInfo(mobName);
		mob.displayName = characterName;
		mob.avatarName = avatarName;
		presentMobs.put(mob.name, mob);

		// tell WorldMaster where this mob is
		world.mobs.put(mobName, this);
	}

	MobInfo getMob(String mobName)
	{
		return presentMobs.get(mobName);
	}

	long currentTime()
	{
		return world.eventDispatchThread.lastEventTime;
	}

	void notifyLeader(String user, Notification n)
	{
		world.notifyLeader(user, n);
	}

	void mobActivity(final String mobName)
	{
		assert mobName != null;

		final MobInfo mob = getMob(mobName);
		assert mob != null;
		assert mob.activity != null;
		assert mob.wakeUp == null;

		long wakeUp = mob.activityStarted + 3000;
		mob.wakeUp = world.scheduler.scheduleAt(new Runnable() {
		public void run()
		{
			mob.wakeUp = null;
			mob.activity = "";
			mobChanged(mobName);
		}
		}, wakeUp);
	}

	void mobChanged(String mobName)
	{
		assert mobName != null;

		MobInfo mob = getMob(mobName);
		assert mob != null;

		if (mob.owner != null)
		{
			MobInfo data = new MobInfo(mobName);
			data.activity = mob.activity;
			data.activityStarted = mob.activityStarted;
			MobChangeNotification n = new MobChangeNotification(mobName, data);
			notifyLeader(mob.owner, n);
		}

		//TODO- inform everyone else who can see this mob
	}

	void mobSetActivity(String mobName, String activityName)
	{
		assert mobName != null;
		assert activityName != null;

		MobInfo mob = getMob(mobName);
		assert mob != null;

		mob.activity = activityName;
		mob.activityStarted = currentTime();
		mobActivity(mobName);
		mobChanged(mobName);
	}

	boolean mobCanMoveTo(String mobName, Location dest)
	{
		assert mobName != null;
		assert dest != null;
		assert dest instanceof SimpleLocation;

		int destRegionId = ((SimpleLocation) dest).regionId;
		ShadowRegion destRegion = world.getShadowRegion(destRegionId);
		BiomeType destBiome = destRegion.getBiome();

		return !destBiome.isWater();
	}

	long mobMovementDelay(String mobName, Location dest)
	{
		assert mobName != null;
		assert dest != null;
		assert dest instanceof SimpleLocation;

		int destRegionId = ((SimpleLocation) dest).regionId;
		ShadowRegion destRegion = world.getShadowRegion(destRegionId);
		BiomeType destBiome = destRegion.getBiome();
		BiomeType myBiome = getBiome();

		return 1200;
	}

	void mobMovedIn(final String mobName, final MobInfo mob,
		Location dest, long delay)
	{
		addMob(mobName, mob);
		world.mobs.put(mobName, this);

		Location oldLoc = mob.location;
		mob.location = dest;
		mob.activity = "move";
		mob.activityStarted = currentTime();

		assert mob.wakeUp == null;

		long wakeUp = mob.activityStarted + delay;
		mob.wakeUp = world.scheduler.scheduleAt(new Runnable() {
		public void run()
		{
			mob.wakeUp = null;
			mob.activity = "";
			mobChanged(mobName);
		}
		}, wakeUp);
	}

	/**
	 * Checks whether the specified mob is "hot", i.e. cannot
	 * change activity until a cooldown period has elapsed.
	 */
	boolean mobIsHot(String mobName)
	{
		MobInfo mob = getMob(mobName);
		assert mob != null;

		if (mob.activity == null || mob.activity.equals(""))
			return false;

		if (mob.activity.equals("move"))
			return true;

		return false;
	}

	void mobCancelActivity(String mobName)
	{
		MobInfo mob = getMob(mobName);
		assert mob != null;
		assert !mobIsHot(mobName);

		if (mob.wakeUp != null)
		{
			world.scheduler.cancel(mob.wakeUp);
			mob.wakeUp = null;
		}
	}
}
