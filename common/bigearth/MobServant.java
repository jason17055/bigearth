package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

public class MobServant
{
	transient RegionServant parentRegion;
	transient String name;

	String displayName;
	String avatarName;
	String owner;
	Location location;
	Command activity;
	long activityStarted;
	long activityRequiredTime;
	boolean activityError;
	Map<CommodityType, Long> stock;
	int nutrition;
	int population;
	Map<SimpleLocation, RegionSight> canSee;
	Flag flag;

	transient Scheduler.Event wakeUp;
	transient double totalMass;

	static final int NUTRITION_COST_FOR_MOVEMENT = 100;

	MobServant(RegionServant parentRegion, String name)
	{
		this.parentRegion = parentRegion;
		this.name = name;
		this.displayName = name;
		this.stock = new EnumMap<CommodityType, Long>(CommodityType.class);
		this.population = 100; //default population
		this.canSee = new HashMap<SimpleLocation, RegionSight>();
		this.flag = Flag.NONE;
	}

	//implements BigEarthServant
	public void start()
	{
		assert wakeUp == null;
		if (activity != null)
		{
			// an activity has been started; figure out when it should end
			scheduleWakeUp();
		}

		updateVisibility();
	}

	void scheduleWakeUp()
	{
		assert wakeUp == null;
		assert activity != null;
		assert activityRequiredTime >= 0;

		long time = activityStarted + activityRequiredTime;
		wakeUp = getWorldMaster().scheduler.scheduleAt(new Runnable() {
		public void run()
		{
			wakeUp = null;
			completeActivity();
		}}, time);
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
		totalMass += ct.mass * amount;
	}

	/**
	 * @return the amount actually subtracted
	 */
	public long subtractCommodity(CommodityType ct, long amount)
	{
		assert stock != null;

		if (stock.containsKey(ct))
		{
			long curBal = stock.get(ct);
			if (amount < curBal)
			{
				stock.put(ct, curBal - amount);
				totalMass -= ct.mass * amount;
				return amount;
			}
			else
			{
				stock.remove(ct);
				totalMass -= ct.mass * curBal;
				return curBal;
			}
		}
		return 0;
	}

	public long getStock(CommodityType ct)
	{
		Long x = stock.get(ct);
		return x != null ? x.longValue() : 0;
	}

	public boolean hasActivity()
	{
		return activity != null;
	}

	public boolean hasAvatarName()
	{
		return avatarName != null;
	}

	public boolean hasOwner()
	{
		return owner != null;
	}

	public static MobServant parse(JsonParser in, RegionServant parentRegion, String mobName)
		throws IOException
	{
		MobServant m = new MobServant(parentRegion, mobName);

		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("displayName"))
				m.displayName = in.nextTextValue();
			else if (s.equals("avatarName"))
				m.avatarName = in.nextTextValue();
			else if (s.equals("location"))
				m.location = LocationHelper.parse(in.nextTextValue(), parentRegion.getWorldConfig());
			else if (s.equals("nutrition"))
			{
				in.nextToken();
				m.nutrition = in.getIntValue();
			}
			else if (s.equals("owner"))
				m.owner = in.nextTextValue();
			else if (s.equals("activity"))
				m.activity = Command.parse(in, parentRegion.getWorldConfig());
			else if (s.equals("activityStarted"))
			{
				in.nextToken();
				m.activityStarted = in.getLongValue();
			}
			else if (s.equals("stock"))
				m.stock = CommoditiesHelper.parseCommodities(in);
			else if (s.equals("population"))
			{
				in.nextToken();
				m.population = in.getIntValue();
			}
			else if (s.equals("flag"))
				m.flag = Flag.valueOf(in.nextTextValue());
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized mob property: "+s);
			}
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;

		m.totalMass = 0;
		for (CommodityType ct : m.stock.keySet())
		{
			long amt = m.stock.get(ct);
			m.totalMass += ct.mass * amt;
		}

		return m;
	}

	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("displayName", displayName);
		if (avatarName != null)
			out.writeStringField("avatarName", avatarName);
		out.writeStringField("location", location.toString());
		if (owner != null)
			out.writeStringField("owner", owner);
		if (activity != null)
		{
			out.writeFieldName("activity");
			activity.write(out);
			out.writeNumberField("activityStarted", activityStarted);
		}
		out.writeFieldName("stock");
		CommoditiesHelper.writeCommodities(stock, out);
		out.writeNumberField("nutrition", nutrition);
		out.writeNumberField("population", population);
		out.writeStringField("flag", flag.name());
		out.writeEndObject();
	}

	double getEncumbranceFactor()
	{
		double capacity = 100 * 20;
		return totalMass / capacity;
	}

	MobInfo makeProfileForObserver()
	{
		MobInfo m = new MobInfo();
		m.displayName = this.displayName;
		m.location = this.location;
		m.avatarName = this.avatarName;
		if (this.activity != null)
			m.activity = this.activity.activity;
		else
			m.activity = "";
		m.flag = this.flag;
		return m;
	}

	MobInfo makeProfileForOwner()
	{
		MobInfo m = new MobInfo();
		m.displayName = this.displayName;
		m.location = this.location;
		m.avatarName = this.avatarName;
		m.stock = this.stock;
		if (this.activity != null)
			m.activity = this.activity.activity;
		else
			m.activity = "";
		m.activityStarted = this.activityStarted;
		m.flag = this.flag;

		double level = getEncumbranceFactor();
		m.encumbrance = (
			level <= 1 ? EncumbranceLevel.UNENCUMBERED :
			level <= 1.5 ? EncumbranceLevel.BURDENED :
			level <= 2 ? EncumbranceLevel.STRESSED :
			level <= 2.5 ? EncumbranceLevel.STRAINED :
			level <= 3 ? EncumbranceLevel.OVERTAXED :
			EncumbranceLevel.OVERLOADED
			);

		m.hunger = (
			nutrition < 0 ? HungerStatus.FAINTING :
			nutrition < 50 ? HungerStatus.WEAK :
			nutrition < 150 ? HungerStatus.HUNGRY :
			nutrition < 1000 ? HungerStatus.NOT_HUNGRY :
			nutrition < 2000 ? HungerStatus.SATIATED :
			HungerStatus.OVERSATIATED
			);

		return m;
	}

	boolean eatSomething()
	{
		if (!stock.containsKey(CommodityType.MEAT))
			return false;

	System.out.println("eating one unit of MEAT");

		subtractCommodity(CommodityType.MEAT, 1);
		nutrition += CommodityType.MEAT.nutrition;
	System.out.println("nutrition level is now "+nutrition);

		return true;
	}

	void checkpoint()
	{
		assert EventDispatchThread.isActive();

		if (nutrition < 150)
		{
			eatSomething();
		}
	}

	private Geometry getGeometry()
	{
		return getWorldMaster().getGeometry();
	}

	private WorldMaster getWorldMaster()
	{
		return parentRegion.world;
	}

	void stockChanged()
	{
		// only the owner needs to be notified
		MobChangeNotification n = new MobChangeNotification(name, makeProfileForOwner());
		notifyLeader(owner, n);
	}

	void activityFailed(String message)
	{
		activityError = true;
		MobMessageNotification n = new MobMessageNotification(name, message);
		notifyLeader(owner, n);
	}

	void startBuildingCity()
	{
	final long WOOD_REQUIRED = 10;

		// check for the required commodities
		if (getStock(CommodityType.WOOD) < WOOD_REQUIRED)
		{
			// not enough wood
			activityFailed("Not enough wood");
			return;
		}

		subtractCommodity(CommodityType.WOOD, WOOD_REQUIRED);
		activityRequiredTime = 30000;
	}

	void startDropping()
	{
	final long TIME_PER_UNIT_DROPPED = 50;
		long amt = subtractCommodity(activity.commodity, activity.amount);
		if (amt != 0)
		{
			parentRegion.addCommodity(activity.commodity, amt);
			stockChanged();
			parentRegion.stockChanged();
		}
		activityRequiredTime = amt * TIME_PER_UNIT_DROPPED;
	}

	void startMoving()
	{
		Location dest = activity.destination;
		assert dest != null;

		final WorldMaster world = getWorldMaster();

		if (!world.isAdjacent(this.location, dest))
		{
			activityFailed("Not adjacent");
			return;
		}

		RegionServant toRegion = world.getRegionForLocation(dest);
		assert toRegion != null;

		if (!parentRegion.mobCanMoveTo(this.name, dest))
		{
			activityFailed("Cannot move there");
			return;
		}

		long delay = parentRegion.mobMovementDelay(this.name, dest);
		assert delay > 0;

		Location oldLoc = this.location;
		parentRegion.removeMob(this.name);

		// after this line, the mob may no longer be running on this host
		toRegion.mobMovedIn(this.name, this, dest, delay);
	}

	/**
	 * Called by the destination region servant, after the mob has been inserted
	 * into the new region, but before the movement cooldown period has begun.
	 */
	void mobMoved(Location oldLoc)
	{
		// first, inform the owner of the movement
		if (owner != null)
		{
			MobInfo data = new MobInfo();
			data.location = location;
			MobChangeNotification n = new MobChangeNotification(name, data);
			notifyLeader(owner, n);
		}

		// inform everyone else who can see this mob
		Set<String> oldObservers = getWorldMaster().getRegionForLocation(oldLoc).usersWhoCanSeeThisRegion();
		Set<String> newObservers = parentRegion.usersWhoCanSeeThisRegion();

		for (String user : oldObservers)
		{
			if (user.equals(owner)) continue;

			if (!newObservers.contains(user))
			{
				movedAwayFrom(user);
			}
		}
		for (String user : newObservers)
		{
			if (user.equals(owner)) continue;

			if (oldObservers.contains(user))
			{
				MobInfo data = new MobInfo();
				data.location = location;
				MobChangeNotification n = new MobChangeNotification(name, data);
				notifyLeader(user, n);
			}
			else
			{
				//TODO- this is to inform a new observer, who doesn't know
				// where the mob came from... The notification should include
				// where the mob came from.

				MobInfo data = new MobInfo();
				data.location = location;
				MobChangeNotification n = new MobChangeNotification(name, data);
				notifyLeader(user, n);
			}
		}

		// update our owner's visibility based on the mob's new position
		updateVisibility();
	}

	void startSettingFlag()
	{
		if (activity.flag != null)
			this.flag = activity.flag;
	}

	void startTaking()
	{
	final long TIME_PER_UNIT_TOOK = 50;
		long amt = parentRegion.subtractCommodity(activity.commodity, activity.amount);
		if (amt != 0)
		{
			addCommodity(activity.commodity, amt);
			parentRegion.stockChanged();
			stockChanged();
		}
		activityRequiredTime = amt * TIME_PER_UNIT_TOOK;
	}

	void onActivityStarted()
	{
		if (activity.activity.equals("hunt"))
		{
			// animals per year, given the size of this mob
			double huntingRate = parentRegion.wildlife.calculateHuntingRate(this.population);

			// time required to harvest one animal
			final double ONE_YEAR = getWorldMaster().config.ticksPerYear;
			activityRequiredTime = (long) Math.ceil(
				ONE_YEAR / huntingRate
				);
		}
		else if (activity.activity.equals("gather-wood"))
		{
			activityRequiredTime = 5000;
		}
		else if (activity.activity.equals("drop"))
		{
			startDropping();
		}
		else if (activity.activity.equals("move"))
		{
			startMoving();
		}
		else if (activity.activity.equals("take"))
		{
			startTaking();
		}
		else if (activity.activity.equals("build-city"))
		{
			startBuildingCity();
		}
		else if (activity.activity.equals("set-flag"))
		{
			startSettingFlag();
		}
		else
		{
			System.err.println("Warning: unrecognized activity: "+activity.activity);
		}
	}

	void onActivityFinished()
	{
		if (activity.activity.equals("hunt"))
		{
			completedHunting();
		}
		else if (activity.activity.equals("gather-wood"))
		{
			completedGatheringWood();
		}
		else if (activity.activity.equals("build-city"))
		{
			completedBuildingCity();
		}
	}

	private void completedHunting()
	{
		WildlifeServant wildlife = parentRegion.wildlife;
		if (Math.random() < wildlife.chanceOfCatchingSheep())
		{
			this.addCommodity(CommodityType.SHEEP, 1);
			wildlife.wildSheepCount--;
		}
		else if (Math.random() < wildlife.chanceOfCatchingPig())
		{
			this.addCommodity(CommodityType.PIG, 1);
			wildlife.wildPigCount--;
		}
		else
		{
			this.addCommodity(CommodityType.MEAT, 1);
			wildlife.wildlifeHunted++;
		}
	}

	private void completedGatheringWood()
	{
		addCommodity(CommodityType.WOOD, 1);
	}

	private void completedBuildingCity()
	{
		parentRegion.spawnCity(location, owner);
	}

	/**
	 * Called when the wake-up timer fires.
	 */
	void completeActivity()
	{
		onActivityFinished();
		activity = null;
		checkpoint();
		mobChanged();
	}

	/**
	 * Convenience method.
	 */
	private void notifyLeader(String user, Notification n)
	{
		getWorldMaster().notifyLeader(user, n);
	}

	void mobChanged()
	{
		if (owner != null)
		{
			MobInfo data = makeProfileForOwner();
			MobChangeNotification n = new MobChangeNotification(name, data);
			notifyLeader(owner, n);
		}

		for (String user : parentRegion.usersWhoCanSeeThisRegion())
		{
			if (user.equals(owner))
				continue;  //owner already informed

			MobInfo data = makeProfileForObserver();
			MobChangeNotification n = new MobChangeNotification(name, data);
			notifyLeader(user, n);
		}
	}

	/**
	 * Called when another user has newly observed this mob.
	 */
	void discoverMob(String user)
	{
		assert user != null;

		MobInfo data = user.equals(owner) ? makeProfileForOwner() :
				makeProfileForObserver();
		MobChangeNotification n = new MobChangeNotification(name, data);
		notifyLeader(user, n);
	}

	/**
	 * Called when another user has lost sight to the region that this mob is in.
	 */
	void lostSightOfMob(String user)
	{
		assert user != null;

		MobRemoveNotification n = new MobRemoveNotification(name,
			MobInfo.RemovalDisposition.LOST_SIGHT);
		notifyLeader(user, n);
	}

	/**
	 * Called when movement of this mob has caused another user to lose sight
	 * of this mob.
	 */
	void movedAwayFrom(String user)
	{
		assert user != null;

		MobRemoveNotification n = new MobRemoveNotification(name,
			MobInfo.RemovalDisposition.MOVED_AWAY);
		notifyLeader(user, n);
	}

	void updateVisibility()
	{
		assert canSee != null;
		assert this.location instanceof SimpleLocation;
		SimpleLocation newLoc = (SimpleLocation) this.location;

		Map<SimpleLocation, RegionSight> newCanSee = new HashMap<SimpleLocation, RegionSight>();
		newCanSee.put(newLoc, RegionSight.INTERNAL);
		for (int nid : getGeometry().getNeighbors(newLoc.regionId))
		{
			newCanSee.put(new SimpleLocation(nid), RegionSight.EXTERNAL);
		}

		for (Iterator<Map.Entry<SimpleLocation,RegionSight> > it = canSee.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<SimpleLocation, RegionSight> entry = it.next();
			SimpleLocation sl = entry.getKey();
			if (!newCanSee.containsKey(sl))
			{
				it.remove();
				lostSightOf(sl);
			}
		}
		for (SimpleLocation sl : newCanSee.keySet())
		{
			if (!canSee.containsKey(sl) || !canSee.get(sl).equals(newCanSee.get(sl)))
			{
				canSee.put(sl, newCanSee.get(sl));
				gainedSightOf(sl, newCanSee.get(sl));
			}
		}
	}

	void lostSightOf(SimpleLocation loc)
	{
		ShadowRegion otherRegion = getWorldMaster().getShadowRegion(loc.regionId);
		otherRegion.mobSight(name, owner, RegionSight.NONE);
	}

	void gainedSightOf(SimpleLocation loc, RegionSight sight)
	{
		ShadowRegion otherRegion = getWorldMaster().getShadowRegion(loc.regionId);
		otherRegion.mobSight(name, owner, sight);
	}

	public boolean isSeenBy(String user)
	{
		return parentRegion.isSeenBy(user);
	}
}
