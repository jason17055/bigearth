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
	Map<String, MobServant> presentMobs;

	/// Elevation as generated randomly by MakeWorld.
	int elevation;
	/// Average temperature, in tenths of degrees Celsius.
	int temperature;
	/// Average annual rainfall, in millimeters.
	int annualRains;
	/// Average additional moisture from flooding of flood plains.
	int floods;

	WildlifeServant wildlife;

	boolean dirty;

	RegionServant(WorldMaster world, int regionId)
	{
		this.world = world;
		this.regionId = regionId;
		this.biome = BiomeType.GRASSLAND;
		this.sides = new RegionSideDetail[6];
		this.corners = new RegionCornerDetail[6];
		this.waterLevel = Integer.MIN_VALUE;
		this.presentMobs = new HashMap<String, MobServant>();
		this.wildlife = new WildlifeServant(this);
	}

	private void init()
	{
		int numSides = world.getGeometry().getNeighborCount(regionId);
	}

	public void adjustWildlife(int delta)
	{
		wildlife.adjust(delta);
		dirty = true;
	}

	void endOfYear_stage1()
	{
		wildlife.doWildlifeMaintenance_stage1();
	}

	void endOfYear_cleanup()
	{
		wildlife.doWildlifeMaintenance_cleanup();
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

	//implements ShadowRegion
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

	//implements ShadowRegion
	public void importWildlife(int newWildlife)
	{
		assert newWildlife >= 0;
		wildlife.wildlifeImmigrants += newWildlife;
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

	void addMob(String mobName, MobServant mob)
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
		out.writeNumberField("wildlife", wildlife.wildlife);
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
		if (in.getCurrentToken() != JsonToken.START_OBJECT)
			throw new InputMismatchException(regionFile + ": " + in.getCurrentLocation() + ": expected START_OBJECT");

		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("wildlife"))
			{
				in.nextToken();
				wildlife.wildlife = in.getIntValue();
			}
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

		wildlife.initPigsAndSheep();
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
			MobServant mob = MobServant.parse(in, mobName, world.config);
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
		MobServant mob = new MobServant(mobName);
		mob.displayName = characterName;
		mob.avatarName = avatarName;
		mob.location = loc;
		presentMobs.put(mob.name, mob);

		// tell WorldMaster where this mob is
		world.mobs.put(mobName, this);
	}

	MobServant getMob(String mobName)
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

	void mobHunt(final String mobName, final MobServant mob)
	{
		
	}

	/**
	 * Called to start a mob activity.
	 */
	void mobActivity(final String mobName)
	{
		assert mobName != null;

		final MobServant mob = getMob(mobName);
		assert mob != null;
		assert mob.activity != null;
		assert mob.wakeUp == null;

		long requiredTime;
		if (mob.activity.activity.equals("hunt"))
		{
			// animals per year, given the size of this mob
			double huntingRate = wildlife.calculateHuntingRate(mob.population);

			// time required to harvest one animal
			final double ONE_YEAR = world.config.ticksPerYear;
			requiredTime = (long) Math.ceil(
				ONE_YEAR / huntingRate
				);
		}
		else if (mob.activity.activity.equals("gather-wood"))
		{
			requiredTime = 5000;
		}
		else
		{
			requiredTime = 5000;
		}

		long wakeUp = mob.activityStarted + requiredTime;
		mob.wakeUp = world.scheduler.scheduleAt(new Runnable() {
		public void run()
		{
			mob.wakeUp = null;
			mobActivityCompleted(mobName, mob);
		}
		}, wakeUp);
	}

	private void mobCompletedHunting(String mobName, MobServant mob)
	{
		if (Math.random() < wildlife.chanceOfCatchingSheep())
		{
			mob.addCommodity(CommodityType.SHEEP, 1);
			wildlife.wildSheepCount--;
		}
		else if (Math.random() < wildlife.chanceOfCatchingPig())
		{
			mob.addCommodity(CommodityType.PIG, 1);
			wildlife.wildPigCount--;
		}
		else
		{
			mob.addCommodity(CommodityType.MEAT, 1);
			wildlife.wildlifeHunted++;
		}
	}

	private void mobCompletedGatheringWood(String mobName, MobServant mob)
	{
		mob.addCommodity(CommodityType.WOOD, 1);
	}

	void mobActivityCompleted(String mobName, MobServant mob)
	{
		if (mob.activity.activity.equals("hunt"))
		{
			// completed hunting
			mobCompletedHunting(mobName, mob);
		}
		else if (mob.activity.equals("gather-wood"))
		{
			mobCompletedGatheringWood(mobName, mob);
		}

		mob.activity = null;
		mob.checkpoint();
		mobChanged(mobName);
	}

	void mobChanged(String mobName)
	{
		assert mobName != null;

		MobServant mob = getMob(mobName);
		assert mob != null;

		if (mob.owner != null)
		{
			MobInfo data = mob.makeProfileForOwner();
			MobChangeNotification n = new MobChangeNotification(mobName, data);
			notifyLeader(mob.owner, n);
		}

		//TODO- inform everyone else who can see this mob
	}

	void mobSetActivity(String mobName, Command command)
	{
		assert mobName != null;
		assert command != null;

		if (mobIsHot(mobName))
			return;
		mobCancelActivity(mobName);

		MobServant mob = getMob(mobName);
		assert mob != null;

		mob.activity = command;
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

	void mobMovedIn(final String mobName, final MobServant mob,
		Location dest, long delay)
	{
		addMob(mobName, mob);
		world.mobs.put(mobName, this);

		Location oldLoc = mob.location;
		mob.location = dest;
		mob.activity = Command.newInstance("move");
		mob.activityStarted = currentTime();
		mob.nutrition -= MobServant.NUTRITION_COST_FOR_MOVEMENT;

		assert mob.wakeUp == null;

		long wakeUp = mob.activityStarted + delay;
		mob.wakeUp = world.scheduler.scheduleAt(new Runnable() {
		public void run()
		{
			mob.wakeUp = null;
			mob.activity = null;
			mob.checkpoint();
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
		MobServant mob = getMob(mobName);
		assert mob != null;

		if (mob.activity == null || mob.activity.equals(""))
			return false;

		if (mob.activity.equals("move"))
			return true;

		return false;
	}

	void mobCancelActivity(String mobName)
	{
		MobServant mob = getMob(mobName);
		assert mob != null;
		assert !mobIsHot(mobName);

		if (mob.wakeUp != null)
		{
			world.scheduler.cancel(mob.wakeUp);
			mob.wakeUp = null;
		}
	}

	public int getWildlifeCount()
	{
		return wildlife.wildlife;
	}

	void resetWildlife()
	{
		wildlife.wildlife = 0;
	}
}
