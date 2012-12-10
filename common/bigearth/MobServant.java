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
	Map<CommodityType, Long> stock;
	int nutrition;
	int population;

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
		return stock.get(ct);
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
		out.writeEndObject();
	}

	double getEncumbranceFactor()
	{
		double capacity = 100 * 20;
		return totalMass / capacity;
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

	static final long TIME_PER_UNIT_DROPPED = 50;
	long activity_Drop()
	{
		long amt = subtractCommodity(activity.commodity, activity.amount);
		if (amt != 0)
		{
			parentRegion.addCommodity(activity.commodity, amt);
		}
		return amt * TIME_PER_UNIT_DROPPED;
	}

	private WorldMaster getWorldMaster()
	{
		return parentRegion.world;
	}

	void activityFailed(String message)
	{
		MobMessageNotification n = new MobMessageNotification(name, message);
		getWorldMaster().notifyLeader(owner, n);
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
			activityRequiredTime = activity_Drop();
		}
		else if (activity.activity.equals("build-city"))
		{
			startBuildingCity();
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
}
