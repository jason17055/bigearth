package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

public class MobServant
{
	transient RegionServant parentRegion;
	transient String name;

	String displayName;
	String owner;
	Location location;
	Command activity;
	long activityStarted;
	long activityRequiredTime;
	boolean activityError;
	CommoditiesBag stock;
	double hunger;
	int population;
	Map<SimpleLocation, RegionSight> canSee;
	Flag flag;
	MobType mobType;

	transient Scheduler.Event wakeUp;

	MobServant(RegionServant parentRegion, String name)
	{
		this.parentRegion = parentRegion;
		this.name = name;
		this.displayName = name;
		this.stock = new CommoditiesBag();
		this.population = 100; //default population
		this.canSee = new HashMap<SimpleLocation, RegionSight>();
		this.flag = Flag.NONE;
		this.mobType = MobType.SETTLER;
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
		stock.add(ct, amount);
	}

	/**
	 * @return the amount actually subtracted
	 */
	public long subtractCommodity(CommodityType ct, long amount)
	{
		return stock.subtract(ct, amount);
	}

	public long getStock(CommodityType ct)
	{
		return stock.getQuantity(ct);
	}

	public boolean hasActivity()
	{
		return activity != null;
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
			else if (s.equals("mobType"))
				m.mobType = MobType.valueOf(in.nextTextValue());
			else if (s.equals("location"))
				m.location = LocationHelper.parse(in.nextTextValue(), parentRegion.getWorldConfig());
			else if (s.equals("hunger"))
			{
				in.nextToken();
				m.hunger = in.getDoubleValue();
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
				m.stock = CommoditiesBag.parse(in);
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

		return m;
	}

	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("displayName", displayName);
		out.writeStringField("mobType", mobType.name());
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
		stock.write(out);
		out.writeNumberField("hunger", hunger);
		out.writeNumberField("population", population);
		out.writeStringField("flag", flag.name());
		out.writeEndObject();
	}

	double getEncumbranceFactor()
	{
		double capacity = 100 * 20;
		return stock.getTotalMass() / capacity;
	}

	MobInfo makeProfileForObserver()
	{
		MobInfo m = new MobInfo();
		m.displayName = this.displayName;
		m.location = this.location;
		m.mobType = this.mobType;
		if (this.activity != null)
			m.activity = this.activity.command;
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
		m.mobType = this.mobType;
		m.stock = this.stock;
		if (this.activity != null)
			m.activity = this.activity.command;
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

		double hr = hunger / population;
		m.hunger = (
			hr < -2 ? HungerStatus.OVERSATIATED :
			hr < 0 ? HungerStatus.SATIATED :
			hr < 1.7 ? HungerStatus.NOT_HUNGRY :
			hr < 1.9 ? HungerStatus.HUNGRY :
			hr < 2.0 ? HungerStatus.WEAK :
			HungerStatus.FAINTING
			);

		return m;
	}

	boolean eatSomething()
	{
		long onHand = stock.getQuantity(CommodityType.MEAT);
		if (onHand == 0)
			return false;

		long desired = (long)Math.ceil(hunger / (double)CommodityType.MEAT.nutrition);

		long actual = subtractCommodity(CommodityType.MEAT, desired);
		hunger -= actual * CommodityType.MEAT.nutrition;
	System.out.println("ate "+actual+ " MEAT; hunger is now "+hunger);

		return true;
	}

	void checkpoint()
	{
		assert EventDispatchThread.isActive();
		assert population > 0;

		if (hunger / population > 1.7)
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

	private void dropAllCommoditiesToCity(CityServant city)
	{
		for (CommodityType ct : stock.getCommodityTypesArray())
		{
			long qty = stock.getQuantity(ct);
			city.addCommodity(ct, qty);
		}
		stock.clear();
	}

	void startDisbanding()
	{
		if (parentRegion.city != null)
		{
			parentRegion.city.addPopulation(this.population);
			this.population = 0;

			dropAllCommoditiesToCity(parentRegion.city);
			disband();
		}
		else
		{
			activityFailed("Cannot disband here.");
			return;
		}
	}

	void startDropping()
	{
		SimpleCommand c = (SimpleCommand) activity;

	final long TIME_PER_UNIT_DROPPED = 50;
		long amt = subtractCommodity(c.commodity, c.amount);
		if (amt != 0)
		{
			parentRegion.addCommodity(c.commodity, amt);
			stockChanged();
			parentRegion.stockChanged();
		}
		activityRequiredTime = amt * TIME_PER_UNIT_DROPPED;
	}

	void startMoving()
	{
		SimpleCommand c = (SimpleCommand) activity;

		Location dest = c.destination;
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
		SimpleCommand c = (SimpleCommand) activity;

		if (c.flag != null)
			this.flag = c.flag;
	}

	void startTaking()
	{
	final long TIME_PER_UNIT_TOOK = 50;
		SimpleCommand c = (SimpleCommand) activity;

		long amt = parentRegion.subtractCommodity(c.commodity, c.amount);
		if (amt != 0)
		{
			addCommodity(c.commodity, amt);
			parentRegion.stockChanged();
			stockChanged();
		}
		activityRequiredTime = amt * TIME_PER_UNIT_TOOK;
	}

	private void startHunting()
	{
		// animals per year, given the size of this mob
		double huntingRate = parentRegion.wildlife.calculateHuntingRate(this.population);

		// time required to harvest one animal
		final double ONE_YEAR = getWorldMaster().config.ticksPerYear;
		activityRequiredTime = (long) Math.ceil(
			ONE_YEAR / huntingRate
			);
	}

	void onActivityStarted()
	{
		if (activity.isActivity("hunt"))
		{
			startHunting();
		}
		else if (activity.isActivity("gather-wood"))
		{
			activityRequiredTime = 5000;
		}
		else if (activity.isActivity("drop"))
		{
			startDropping();
		}
		else if (activity.isActivity("move"))
		{
			startMoving();
		}
		else if (activity.isActivity("take"))
		{
			startTaking();
		}
		else if (activity.isActivity("build-city"))
		{
			startBuildingCity();
		}
		else if (activity.isActivity("disband"))
		{
			startDisbanding();
		}
		else if (activity.isActivity("set-flag"))
		{
			startSettingFlag();
		}
		else
		{
			System.err.println("Warning: unrecognized activity: "+activity.command);
		}
	}

	long currentTime()
	{
		return parentRegion.world.eventDispatchThread.lastEventTime;
	}

	WorldConfig getWorldConfig()
	{
		return parentRegion.getWorldConfig();
	}

	void onActivityFinished()
	{
		long elapsedTicks = currentTime() - activityStarted;
		double elapsedYears = (double)elapsedTicks / (double)getWorldConfig().ticksPerYear;
		hunger += elapsedYears * getWorldConfig().hungerPerAdult;

		if (activity.isActivity("hunt"))
		{
			completedHunting();
		}
		else if (activity.isActivity("gather-wood"))
		{
			completedGatheringWood();
		}
		else if (activity.isActivity("build-city"))
		{
			completedBuildingCity();
		}
	}

	private void completedHunting()
	{
		WildlifeServant wildlife = parentRegion.wildlife;

		CommodityType typeHunted = wildlife.pickOneRandomAnimal();
		wildlife.wildlifeByType.get(typeHunted).hunted++;

		boolean capturedAlive = (Math.random() < wildlife.getChanceOfDomestication(typeHunted));
		if (capturedAlive)
		{
			this.addCommodity(typeHunted, 1);
		}
		else
		{
			this.addCommodity(CommodityType.MEAT, wildlife.meatPerHead(typeHunted));
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

	void disband()
	{
		// first, tell other users that this mob is being eliminated
		for (String user : parentRegion.usersWhoCanSeeThisRegion())
		{
			MobRemoveNotification n = new MobRemoveNotification(name, MobInfo.RemovalDisposition.DISBANDED);
			notifyLeader(user, n);
		}

		// next, update owner's visibility map
		HashMap<SimpleLocation, RegionSight> tmp = new HashMap<SimpleLocation, RegionSight>();
		newVisibility(tmp);

		// finally, separate this object from the parent region so that this object
		// will be garbaged-disposed. Also, this lets our caller know that a
		// wake-up event should not be scheduled.
		parentRegion.removeMob(this.name);
		getWorldMaster().mobs.remove(this.name);
		this.parentRegion = null;
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

		newVisibility(newCanSee);
	}

	void newVisibility(Map<SimpleLocation, RegionSight> newCanSee)
	{
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

	public boolean canUserCommand(String user)
	{
		assert user != null;

		return owner != null && owner.equals(user);
	}

	void setOrders(Command c)
	{
		parentRegion.mobSetActivity(name, c);
	}

	public int getPopulation()
	{
		return population;
	}
}
