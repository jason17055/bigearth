package bigearth;

import java.io.*;
import java.util.*;
import javax.vecmath.*;
import com.fasterxml.jackson.core.*;

class RegionServant
	implements ShadowRegion, Saveable, EndOfYear
{
	WorldMaster world;
	int regionId;

	BiomeType biome;
	RegionSideDetail [] sides;
	RegionCornerDetail [] corners;
	int waterLevel;
	Map<String, MobServant> presentMobs;
	CityServant city; //may be null

	/// Indexed by <mobname,owner>.
	Map<SeenByKey,RegionSight> seenByMob;

	/// Indexed by username. Values are the number of mobs contributing to the sight.
	Map<String,UserSight> seenByUser;

	/// Elevation as generated randomly by MakeWorld.
	int elevation;
	/// Average temperature, in tenths of degrees Celsius.
	int temperature;
	/// Average annual rainfall, in millimeters.
	int annualRains;
	/// Average additional moisture from flooding of flood plains.
	int floods;

	WildlifeServant wildlife;
	Map<CommodityType, Long> stock;
	Map<ZoneType, Integer> zones;

	List<ZoneDevelopment> zoneDevelopments;

	static class ZoneDevelopment
	{
		ZoneType targetType;
		double workRemaining;
		Map<CommodityType, Long> requiredCommodities;
		Map<CommodityType, Long> generatedCommodities;

		static final double REQUIRED_POINTS = 100.0;
		public double getRemaining()
		{
			return workRemaining;
		}
	}

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
		this.stock = new EnumMap<CommodityType, Long>(CommodityType.class);
		this.seenByMob = new HashMap<SeenByKey, RegionSight>();
		this.seenByUser = new HashMap<String, UserSight>();
		this.zones = new EnumMap<ZoneType, Integer>(ZoneType.class);
		this.zoneDevelopments = new ArrayList<ZoneDevelopment>();
	}

	private void init()
	{
		int numSides = world.getGeometry().getNeighborCount(regionId);
	}

	private void checkZones()
	{
		int zoneCount = 0;
		for (Map.Entry<ZoneType, Integer> e : zones.entrySet())
		{
			zoneCount += e.getValue();
		}

		final int ZONES_PER_REGION = getWorldConfig().zonesPerRegion;
		if (zoneCount < ZONES_PER_REGION)
		{
			// add more natural zones
			int deficit = ZONES_PER_REGION - zoneCount;
			int naturalZoneCount = getZoneCount(ZoneType.NATURAL);
			zones.put(ZoneType.NATURAL, naturalZoneCount + deficit);
		}
		else if (zoneCount > ZONES_PER_REGION)
		{
			// remove some natural zones
			int overage = zoneCount - ZONES_PER_REGION;
			int naturalZoneCount = getZoneCount(ZoneType.NATURAL);
			if (overage < naturalZoneCount)
			{
				zones.put(ZoneType.NATURAL, naturalZoneCount - overage);
			}
			else
			{
				zones.remove(ZoneType.NATURAL);
				if (overage > naturalZoneCount)
				{
					System.err.println("Warning: too many zones in region "+regionId);
				}
			}
		}
	}

	//implements BigEarthServant
	public void start()
	{
		checkZones();

		assert wildlife != null;
		wildlife.start();

		if (city != null)
			city.start();

		for (MobServant mob : presentMobs.values())
		{
			mob.start();
		}
	}

	public void adjustWildlife(int delta)
	{
		wildlife.adjustWildlife(CommodityType.WILDLIFE, delta);
		dirty = true;
	}

	//implements EndOfYear
	public void endOfYear_stage1()
	{
		if (city != null)
			city.endOfYear_stage1();
		wildlife.endOfYear_stage1();
	}

	//implements EndOfYear
	public void endOfYear_cleanup()
	{
		wildlife.endOfYear_cleanup();
		if (city != null)
			city.endOfYear_cleanup();
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

	public int getFarmCount()
	{
		return getZoneCount(ZoneType.FARM);
	}

	public int getZoneCount(ZoneType zone)
	{
		Integer i = zones.get(zone);
		return i != null ? i.intValue() : 0;
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
	public void importWildlife(CommodityType type, int newWildlife)
	{
		assert newWildlife >= 0;
		wildlife.adjustWildlife(type, newWildlife);
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
		out.writeFieldName("wildlife");
		wildlife.write(out);
		out.writeNumberField("waterLevel", waterLevel);
		out.writeNumberField("elevation", elevation);
		out.writeNumberField("temperature", temperature);
		out.writeNumberField("annualRains", annualRains);
		out.writeNumberField("floods", floods);
		out.writeFieldName("stock");
		CommoditiesHelper.writeCommodities(stock, out);
		out.writeFieldName("zones");
		writeZones(out);

		if (!zoneDevelopments.isEmpty())
		{
			out.writeFieldName("zoneDevelopments");
			writeZoneDevelopments(out);
		}

		if (hasCity())
		{
			out.writeFieldName("city");
			city.write(out);
		}

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

	private void writeZoneDevelopments(JsonGenerator out)
		throws IOException
	{
		out.writeStartArray();
		for (ZoneDevelopment zd : zoneDevelopments)
		{
			out.writeStartObject();
			out.writeStringField("targetType", zd.targetType.name());
			out.writeNumberField("workRemaining", zd.workRemaining);
			if (!zd.requiredCommodities.isEmpty())
			{
				out.writeFieldName("requiredCommodities");
				CommoditiesHelper.writeCommodities(zd.requiredCommodities, out);
			}
			if (!zd.generatedCommodities.isEmpty())
			{
				out.writeFieldName("generatedCommodities");
				CommoditiesHelper.writeCommodities(zd.generatedCommodities, out);
			}
			out.writeEndObject();
		}
		out.writeEndArray();
	}

	private void writeZones(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		for (Map.Entry<ZoneType,Integer> e : zones.entrySet())
		{
			ZoneType zone = e.getKey();
			int quantity = e.getValue();
			assert quantity > 0;

			out.writeNumberField(zone.name(), quantity);
		}
		out.writeEndObject();
	}

	private void parseZoneDevelopments(JsonParser in)
		throws IOException
	{
		in.nextToken();
		if (in.getCurrentToken() != JsonToken.START_ARRAY)
			throw new InputMismatchException();

		while (in.nextToken() != JsonToken.END_ARRAY)
		{
			if (in.getCurrentToken() != JsonToken.START_OBJECT)
				throw new InputMismatchException();

			ZoneDevelopment zd = new ZoneDevelopment();
			while (in.nextToken() == JsonToken.FIELD_NAME)
			{
				String s = in.getCurrentName();
				if (s.equals("targetType"))
					zd.targetType = ZoneType.valueOf(in.nextTextValue());
				else if (s.equals("workRemaining"))
				{
					in.nextToken();
					zd.workRemaining = in.getDoubleValue();
				}
				else if (s.equals("requiredCommodities"))
					zd.requiredCommodities = CommoditiesHelper.parseCommodities(in);
				else if (s.equals("generatedCommodities"))
					zd.generatedCommodities = CommoditiesHelper.parseCommodities(in);
				else
				{
					System.err.println("warning: unrecognized zone development property: "+s);
					in.nextToken();
					in.skipChildren();
				}
			}

			if (in.getCurrentToken() != JsonToken.END_OBJECT)
				throw new InputMismatchException();

			if (zd.requiredCommodities == null)
				zd.requiredCommodities = new HashMap<CommodityType, Long>();
			if (zd.generatedCommodities == null)
				zd.generatedCommodities = new HashMap<CommodityType, Long>();

			zoneDevelopments.add(zd);
		}
	}

	private void parseZones(JsonParser in)
		throws IOException
	{
		in.nextToken();
		if (in.getCurrentToken() != JsonToken.START_OBJECT)
			throw new InputMismatchException();

		while (in.nextToken() != JsonToken.END_OBJECT)
		{
			String s = in.getCurrentName();
			in.nextToken();
			int num = in.getIntValue();

			zones.put(ZoneType.valueOf(s), num);
		}
	}

	void beginDeveloping(ZoneType fromZoneType, ZoneType toZoneType)
		throws ZoneTypeNotFound, InvalidZoneTransition
	{
		assert fromZoneType != null;
		assert fromZoneType != ZoneType.UNDER_CONSTRUCTION;
		assert toZoneType != null;
		assert toZoneType != ZoneType.UNDER_CONSTRUCTION;

		// designate zone for development
		Integer numZonesI = zones.get(fromZoneType);
		if (numZonesI == null)
			throw new ZoneTypeNotFound();

		// check whether recipe exists
		ZoneRecipe recipe = getWorldMaster().zoneRecipes.get(fromZoneType, toZoneType);
		if (recipe == null)
			throw new InvalidZoneTransition();

		int newFromZones = numZonesI.intValue() - 1;
		if (newFromZones > 0)
			zones.put(fromZoneType, newFromZones);
		else
			zones.remove(fromZoneType);

		// make a new zone (of type- under construction)
		zones.put(ZoneType.UNDER_CONSTRUCTION,
			1 + getZoneCount(ZoneType.UNDER_CONSTRUCTION)
			);

		// make a new development
		ZoneDevelopment zd = new ZoneDevelopment();
		zd.targetType = toZoneType;
		zd.workRemaining = recipe.workRequired;
		zd.requiredCommodities = CommoditiesHelper.makeClone(recipe.consumed);
		zd.generatedCommodities = CommoditiesHelper.makeClone(recipe.generated);
		zoneDevelopments.add(zd);
	}

	void continueDeveloping(double productionPoints)
	{
		for (Iterator<ZoneDevelopment> it = zoneDevelopments.iterator();
				it.hasNext(); )
		{
			ZoneDevelopment zd = it.next();
			if (!zd.requiredCommodities.isEmpty())
				continue;

			double remaining = Math.max(0, zd.getRemaining());
			if (productionPoints >= remaining)
			{
				endDeveloping(zd.targetType);
				it.remove();
				productionPoints -= remaining;
			}
			else
			{
				assert productionPoints < zd.workRemaining;
				zd.workRemaining -= productionPoints;
				productionPoints = 0.0;
				break;
			}
		}
	}

	void endDeveloping(ZoneType newZoneType)
	{
		assert newZoneType != null;
		assert newZoneType != ZoneType.UNDER_CONSTRUCTION;

		int numUnderConstruction = getZoneCount(ZoneType.UNDER_CONSTRUCTION);
		if (numUnderConstruction - 1 > 0)
		{
			zones.put(ZoneType.UNDER_CONSTRUCTION, numUnderConstruction-1);
		}
		else
		{
			zones.remove(ZoneType.UNDER_CONSTRUCTION);
		}

		zones.put(newZoneType, getZoneCount(newZoneType)+1);
		world.regionChanged(regionId);
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
				wildlife.parse(in);
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
			else if (s.equals("stock"))
				stock = CommoditiesHelper.parseCommodities(in);
			else if (s.equals("zones"))
				parseZones(in);
			else if (s.equals("zoneDevelopments"))
				parseZoneDevelopments(in);
			else if (s.equals("city"))
				city = CityServant.parse(in, this);
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
			MobServant mob = MobServant.parse(in, this, mobName);
			if (mob.location == null)
			{
				mob.location = new SimpleLocation(regionId);
			}
			presentMobs.put(mobName, mob);
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}

	void spawnCity(Location loc, String owner)
	{
		assert world.getRegionIdForLocation(loc) == this.regionId;
		assert this.city == null;

		city = new CityServant(this);
		city.location = loc;
		city.owner = owner;
		world.regionChanged(regionId);
	}

	void stockChanged()
	{
		world.regionChanged(regionId);
	}

	/**
	 * The caller is given a reference to the mob servant for setting additional
	 * options, like population count and starting equipment.
	 * The caller should call mob.mobChanged() after the mob has been configured.
	 */
	MobServant spawnCharacter(Location loc, MobType mobType, String owner)
	{
		assert world.getRegionIdForLocation(loc) == this.regionId;

		String mobName = world.nextUniqueName("mob");
		MobServant mob = new MobServant(this, mobName);
		mob.location = loc;
		mob.mobType = mobType;
		mob.owner = owner;
		presentMobs.put(mob.name, mob);

		// tell WorldMaster where this mob is
		world.mobs.put(mobName, this);

		return mob;
	}

	MobServant getMob(String mobName)
	{
		return presentMobs.get(mobName);
	}

	long currentTime()
	{
		return world.eventDispatchThread.lastEventTime;
	}

	WorldMaster getWorldMaster()
	{
		return world;
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

		mob.activityRequiredTime = 0;

		mob.onActivityStarted();
		if (mob.parentRegion != this)
			return;

		if (mob.activityError)
		{
			mob.activity = null;
			return;
		}

		mob.scheduleWakeUp();
		mob.mobChanged();
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
		mob.activityError = false;
		mobActivity(mobName);
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
		mob.parentRegion = this;
		mob.location = dest;
		mob.activity = new SimpleCommand("move");
		((SimpleCommand) mob.activity).setDestination(dest);
		mob.activityStarted = currentTime();

		mob.mobMoved(oldLoc);

		assert mob.wakeUp == null;

		long wakeUp = mob.activityStarted + delay;
		mob.wakeUp = world.scheduler.scheduleAt(new Runnable() {
		public void run()
		{
			mob.wakeUp = null;
			mob.activity = null;
			mob.checkpoint();
			mob.mobChanged();
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

		if (mob.activity == null)
			return false;

		if (mob.activity.isActivity("move"))
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
		return wildlife.getTotalWildlife();
	}

	void resetWildlife()
	{
		wildlife.wildlifeByType.clear();
	}

	public void addCommodity(CommodityType ct, long amount)
	{
		assert stock != null;

		if (stock.containsKey(ct))
		{
			long amt = stock.get(ct);
			amt += amount;
			stock.put(ct, amt);
		}
		else
		{
			stock.put(ct, amount);
		}
	}

	public long subtractCommodity(CommodityType ct, long amount)
	{
		if (stock.containsKey(ct))
		{
			long bal = stock.get(ct);
			if (amount < bal)
			{
				stock.put(ct, bal-amount);
				return amount;
			}
			else
			{
				stock.remove(ct);
				return bal;
			}
		}
		return 0;
	}

	public WorldConfig getWorldConfig()
	{
		return world.config;
	}

	boolean hasCity()
	{
		return city != null;
	}

	static final class SeenByKey
	{
		String objectName;
		String owner;

		public SeenByKey(String objectName, String owner)
		{
			assert objectName != null;
			this.objectName = objectName;
			this.owner = owner;
		}

		static boolean isEqual(String s1, String s2)
		{
			if (s1 == s2)
				return true;
			else if (s1 == null || s2 == null)
				return false;
			else	
				return s1.equals(s2);
		}

		public boolean equals(Object obj)
		{
			if (obj instanceof SeenByKey)
			{
				SeenByKey rhs = (SeenByKey) obj;
				return isEqual(this.objectName, rhs.objectName)
					&& isEqual(this.owner, rhs.owner);
			}
			return false;
		}

		public int hashCode()
		{
			return objectName.hashCode();
		}
	}

	static final class UserSight
	{
		int internalCount;
		int externalCount;

		static final UserSight DEF = new UserSight();
		boolean isDefault()
		{
			return internalCount == 0 && externalCount == 0;
		}
	}

	//implements ShadowRegion
	public void mobSight(String mobName, String owner, RegionSight sight)
	{
		SeenByKey sb = new SeenByKey(mobName, owner);

		RegionSight sOld = seenByMob.get(sb);
		if (sOld == null)
			sOld = RegionSight.NONE;

		if (!sight.isNone())
		{
			seenByMob.put(sb, sight);
		}
		else
		{
			seenByMob.remove(sb);
		}

		if (owner != null)
		{
			userSight(owner,
				(sight.seeInternal ? 1 : 0) - (sOld.seeInternal ? 1 : 0),
				(sight.seeExternal ? 1 : 0) - (sOld.seeExternal ? 1 : 0)
				);
		}
	}

	void userSight(String user, int internalAdj, int externalAdj)
	{
		assert user != null;

		UserSight usOld = seenByUser.get(user);
		if (usOld == null)
			usOld = UserSight.DEF;

		UserSight usNew = new UserSight();
		usNew.internalCount = usOld.internalCount + internalAdj;
		usNew.externalCount = usOld.externalCount + externalAdj;

		assert usNew.internalCount >= 0;
		assert usNew.externalCount >= 0;

		if (usNew.isDefault())
		{
			seenByUser.remove(user);
		}
		else
		{
			seenByUser.put(user, usNew);
		}

		if (usOld.internalCount == 0 && usNew.internalCount != 0)
		{
			// user can now see "internal" properties of region
			world.discoverTerrain(user, new SimpleLocation(regionId), true);
		}
		else if (usOld.externalCount == 0 && usNew.externalCount != 0)
		{
			// user can now see "external" properties of region
			world.discoverTerrain(user, new SimpleLocation(regionId), false);
		}

		if (usOld.externalCount == 0 && usNew.externalCount != 0)
		{
			// tell user about any mobs present in this region
			for (MobServant mob : presentMobs.values())
			{
				mob.discoverMob(user);
			}
		}
		else if (usOld.externalCount != 0 && usNew.externalCount == 0)
		{
			// tell user they lost sight of the mobs in this region
			for (MobServant mob : presentMobs.values())
			{
				mob.lostSightOfMob(user);
			}
		}
	}

	/**
	 * The set of users who can see this region at any level.
	 */
	Set<String> usersWhoCanSeeThisRegion()
	{
		return seenByUser.keySet();
	}

	RegionProfile makeProfile(RegionSight sight)
	{
		assert sight.seeExternal;

		RegionProfile p = new RegionProfile();

		p.biome = this.biome;
		if (this.city != null)
		{
			p.citySize = 1;
			p.cityName = this.city.displayName;
		}

		for (int i = 0; i < 6; i++)
		{
			if (this.sides[i] != null)
				p.sides[i] = this.sides[i].feature;
			if (this.corners[i] != null)
				p.corners[i] = this.corners[i].feature;
		}

		if (sight.seeInternal)
		{
			p.stock = CommoditiesHelper.makeClone(this.stock);
		}

		return p;
	}

	public boolean isSeenBy(String user)
	{
		return seenByUser.containsKey(user);
	}

	static class InvalidZoneTransition extends Exception
	{
	}

	static class ZoneTypeNotFound extends Exception
	{
	}
}
