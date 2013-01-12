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

	/// Goods that are stockpiled.
	AdvancedCommodityStore stock;

	/// Raw materials that are on the surface, easily gathered.
	CommoditiesBag surfaceMinerals;

	/// Raw materials that are deep underground.
	CommoditiesBag undergroundMinerals;

	Map<Integer, ZoneServant> zones;

	List<ZoneDevelopment> zoneDevelopments;

	static final int ZONE_GRID_WIDTH = 8;
	static class ZoneDevelopment
	{
		int zoneNumber;
		ZoneType targetType;
		double workRemaining;
		CommoditiesBag requiredCommodities;
		CommoditiesBag generatedCommodities;

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
		this.stock = new AdvancedCommodityStore();
		this.surfaceMinerals = new CommoditiesBag();
		this.undergroundMinerals = new CommoditiesBag();
		this.seenByMob = new HashMap<SeenByKey, RegionSight>();
		this.seenByUser = new HashMap<String, UserSight>();
		this.zones = new HashMap<>();
		this.zoneDevelopments = new ArrayList<ZoneDevelopment>();
	}

	private void init()
	{
		int numSides = world.getGeometry().getNeighborCount(regionId);
	}

	private void checkZones()
	{
		final int ZONES_PER_REGION = getWorldConfig().zonesPerRegion;

		for (Iterator< Map.Entry<Integer,ZoneServant> > it = zones.entrySet().iterator();
				it.hasNext(); )
		{
			Map.Entry<Integer,ZoneServant> e = it.next();
			int zoneNumber = e.getKey();

			if (!(zoneNumber >= 1 && zoneNumber <= ZONES_PER_REGION))
			{
				it.remove();
			}
		}

		//check under construction zones
		for (Iterator<ZoneDevelopment> it = zoneDevelopments.iterator();
			it.hasNext(); )
		{
			ZoneDevelopment zd = it.next();
			if (!(zd.zoneNumber >= 1 && zd.zoneNumber <= ZONES_PER_REGION))
			{
				it.remove();
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
		for (ZoneServant zone : zones.values())
		{
			zone.start();
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

	public int getNaturalZoneCount()
	{
		final int ZONES_PER_REGION = getWorldConfig().zonesPerRegion;
		int countDeveloped = zones.size();
		return ZONES_PER_REGION - countDeveloped;
	}

	public int getZoneCount(ZoneType zoneType)
	{
		assert zoneType != null;

		if (zoneType == ZoneType.NATURAL)
			return getNaturalZoneCount();

		int count = 0;
		for (ZoneServant zone : zones.values())
		{
			if (zone.type == zoneType)
				count++;
		}
		return count;
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
		stock.write(out);
		if (!surfaceMinerals.isEmpty())
		{
			out.writeFieldName("surfaceMinerals");
			surfaceMinerals.write(out);
		}
		if (!undergroundMinerals.isEmpty())
		{
			out.writeFieldName("undergroundMinerals");
			undergroundMinerals.write(out);
		}
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
			out.writeNumberField("zoneNumber", zd.zoneNumber);
			out.writeStringField("targetType", zd.targetType.name());
			out.writeNumberField("workRemaining", zd.workRemaining);
			if (!zd.requiredCommodities.isEmpty())
			{
				out.writeFieldName("requiredCommodities");
				zd.requiredCommodities.write(out);
			}
			if (!zd.generatedCommodities.isEmpty())
			{
				out.writeFieldName("generatedCommodities");
				zd.generatedCommodities.write(out);
			}
			out.writeEndObject();
		}
		out.writeEndArray();
	}

	private void writeZones(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		for (Map.Entry<Integer,ZoneServant> e : zones.entrySet())
		{
			int zoneNumber = e.getKey();
			ZoneServant zone = e.getValue();

			out.writeFieldName(Integer.toString(zoneNumber));
			zone.write(out);
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
				if (s.equals("zoneNumber"))
				{
					in.nextToken();
					zd.zoneNumber = in.getIntValue();
				}
				else if (s.equals("targetType"))
					zd.targetType = ZoneType.valueOf(in.nextTextValue());
				else if (s.equals("workRemaining"))
				{
					in.nextToken();
					zd.workRemaining = in.getDoubleValue();
				}
				else if (s.equals("requiredCommodities"))
					zd.requiredCommodities = CommoditiesBag.parse(in);
				else if (s.equals("generatedCommodities"))
					zd.generatedCommodities = CommoditiesBag.parse(in);
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
				zd.requiredCommodities = new CommoditiesBag();
			if (zd.generatedCommodities == null)
				zd.generatedCommodities = new CommoditiesBag();

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
			int zoneNumber = Integer.parseInt(s);

			ZoneServant zone = ZoneServant.parse(in, this);
			assert zone.type != ZoneType.NATURAL;

			zones.put(zoneNumber, zone);
		}
	}

	private int findUnusedZone()
	{
		for (int i = 1; i <= getWorldConfig().zonesPerRegion; i++)
		{
			if (!zones.containsKey(i))
				return i;
		}
		return -1;
	}

	private int findZoneOfType(ZoneType zoneType)
	{
		assert zoneType != null;

		if (zoneType == ZoneType.NATURAL)
			return findUnusedZone();

		for (Map.Entry<Integer,ZoneServant> e : zones.entrySet())
		{
			int zoneNumber = e.getKey();
			ZoneServant zone = e.getValue();

			if (zone.type == zoneType)
				return zoneNumber;
		}
		return -1;
	}

	private ZoneType getZoneType(int zoneNumber)
	{
		assert zoneNumber >= 1 && zoneNumber <= getWorldConfig().zonesPerRegion;

		ZoneServant zone = zones.get(zoneNumber);
		return zone != null ? zone.type : ZoneType.NATURAL;
	}

	void beginDeveloping(ZoneType fromZoneType, ZoneType toZoneType)
		throws ZoneTypeNotFound, InvalidZoneTransition
	{
		assert fromZoneType != null;
		assert fromZoneType != ZoneType.UNDER_CONSTRUCTION;
		assert toZoneType != null;
		assert toZoneType != ZoneType.UNDER_CONSTRUCTION;

		int zoneNumber = findZoneOfType(fromZoneType);
		if (zoneNumber == -1)
			throw new ZoneTypeNotFound();

		beginDeveloping(zoneNumber, toZoneType);
	}

	void beginDeveloping(int gridx, int gridy, ZoneType toZoneType)
		throws InvalidZoneTransition
	{
		int zoneNumber = gridy * ZONE_GRID_WIDTH + gridx + 1;
		beginDeveloping(zoneNumber, toZoneType);
	}

	void beginDeveloping(int zoneNumber, ZoneType toZoneType)
		throws InvalidZoneTransition
	{
		ZoneType fromZoneType = getZoneType(zoneNumber);

		// check whether recipe exists
		ZoneRecipe recipe = getWorldMaster().zoneRecipes.get(fromZoneType, toZoneType);
		if (recipe == null)
			throw new InvalidZoneTransition();

		// designate zone for development
		ZoneServant zone = new ZoneServant(this);
		zone.type = ZoneType.UNDER_CONSTRUCTION;
		zone.gridx = (zoneNumber-1) % ZONE_GRID_WIDTH;
		zone.gridy = (zoneNumber-1) / ZONE_GRID_WIDTH;
		zones.put(zoneNumber, zone);

		// make a new development
		ZoneDevelopment zd = new ZoneDevelopment();
		zd.zoneNumber = zoneNumber;
		zd.targetType = toZoneType;
		zd.workRemaining = recipe.workRequired;
		zd.requiredCommodities = recipe.required.clone();
		zd.generatedCommodities = recipe.generated.clone();
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
				endDeveloping(zd);
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

	void endDeveloping(ZoneDevelopment zoneDevelopment)
	{
		ZoneType newZoneType = zoneDevelopment.targetType;

		assert newZoneType != null;
		assert newZoneType != ZoneType.UNDER_CONSTRUCTION;

		ZoneServant zone = zones.get(zoneDevelopment.zoneNumber);
		assert zone != null;

		zone.type = newZoneType;
		zone.start();
		world.regionChanged(regionId);
	}

	void destroyZone(int zoneNumber)
	{
		ZoneServant zone = zones.get(zoneNumber);
		if (zone != null)
		{
			zone.stop();
			zones.remove(zoneNumber);
			world.regionChanged(regionId);
		}
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
				stock.parse(in);
			else if (s.equals("zones"))
				parseZones(in);
			else if (s.equals("zoneDevelopments"))
				parseZoneDevelopments(in);
			else if (s.equals("city"))
				city = CityServant.parse(in, this);
			else if (s.equals("surfaceMinerals"))
				surfaceMinerals = CommoditiesBag.parse(in);
			else if (s.equals("undergroundMinerals"))
				undergroundMinerals = CommoditiesBag.parse(in);
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized property: "+s);
			}
		}

		in.close();
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

		MobServant mob = getMob(mobName);
		assert mob != null;

		if (mob.isHot())
		{
			// cannot change activity at this time
			return;
		}

		mob.cancelActivity();
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
		stock.add(ct, amount);
	}

	public long subtractCommodity(CommodityType ct, long amount)
	{
		return stock.subtract(ct, amount);
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
			p.stock = stock.toCommoditiesBag();
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

	public long getPopulation()
	{
		long population = 0;
		if (city != null)
			population += city.getPopulation();
		for (MobServant mob : presentMobs.values())
		{
			population += mob.getPopulation();
		}
		return population;
	}

	void applyProduction_gatherResources(double gatheringPoints)
	{
		int numNatural = getZoneCount(ZoneType.NATURAL);
		if (numNatural == 0)
			return;

		// determine how many units can be collected
		double maxYield = numNatural * 5.0;
		double z = maxYield - maxYield * Math.exp(-1 * gatheringPoints / maxYield);
		long numGathered = (long)Math.round(z * 0.25);

		// place all surface minerals of this region into an "urn"
		ProbabilityUrn<CommodityType> urn = new ProbabilityUrn<CommodityType>();
		for (CommodityType ct : surfaceMinerals.getCommodityTypesArray())
		{
			urn.add(ct, surfaceMinerals.getQuantity(ct));
		}

		// randomly pick those units
		Map<CommodityType, Long> picked = urn.pickMany(numGathered);
		long sumPicked = 0;
		for (CommodityType ct : picked.keySet())
		{
			long amt = picked.get(ct);
			long taken = surfaceMinerals.subtract(ct, amt);
			stock.add(ct, taken);
			sumPicked += amt;
		}

		if (sumPicked != 0)
			stockChanged();
	}
}
