package bigearth;

import java.io.*;
import java.util.*;
import com.fasterxml.jackson.core.*;

public class WorldMaster
	implements Saveable
{
	WorldConfig config;
	RegionServant [] regions;
	Map<String, LeaderInfo> leaders;
	Map<String, RegionServant> mobs;
	Scheduler scheduler;

	int year;
	int lastSeqId;

	public WorldMaster(WorldConfig config)
	{
		this.config = config;
		this.regions = new RegionServant[config.getGeometry().getCellCount()];
		this.leaders = new HashMap<String, LeaderInfo>();
		this.mobs = new HashMap<String, RegionServant>();
		this.scheduler = new Scheduler(config);
	}

	public void load()
		throws IOException
	{
		File inFile = new File(config.path, "world.txt");
		JsonParser in = new JsonFactory().createJsonParser(inFile);

		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("year"))
				year = in.nextIntValue(year);
			else if (s.equals("lastMobId") || s.equals("lastSeqId"))
				lastSeqId = in.nextIntValue(0);
			else if (s.equals("leaders"))
			{
				parseLeaders(in);
			}
			else if (s.equals("gameTime"))
			{
				in.nextToken();
				scheduler.setGameTime(in.getLongValue());
			}
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized world property: "+s);
			}
		}
		in.close();

		for (int i = 0; i < regions.length; i++)
		{
			int regionId = i + 1;
			regions[i] = RegionServant.load(this, regionId);

			for (String s : regions[i].presentMobs.keySet())
			{
				mobs.put(s, regions[i]);
			}
		}
	}

	void parseLeaders(JsonParser in)
		throws IOException
	{
		leaders.clear();

		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String leaderName = in.getCurrentName();
			LeaderInfo leader = new LeaderInfo(leaderName, this);
			leader.parse(in);
			leaders.put(leaderName, leader);
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}

	ShadowRegion getShadowRegion(int regionId)
	{
		assert regionId >= 1 && regionId <= regions.length;
		assert regions[regionId-1] != null;

		return regions[regionId-1];
	}

	String nextUniqueName(String prefix)
	{
		return prefix + (++lastSeqId);
	}

	void newLeader(String name)
	{
		LeaderInfo leader = new LeaderInfo(name, this);
		leaders.put(name, leader);
	}

	public final int [] getRegionNeighbors(int regionId)
	{
		return getGeometry().getNeighbors(regionId);
	}

	public final Geometry getGeometry()
	{
		return config.getGeometry();
	}

	int getRegionIdForLocation(Location loc)
	{
		if (loc instanceof SimpleLocation)
			return ((SimpleLocation) loc).regionId;
		else if (loc instanceof Geometry.EdgeId)
		{
			return ((Geometry.EdgeId) loc).getAdjacentCells()[0];
		}
		else if (loc instanceof Geometry.VertexId)
		{
			return ((Geometry.VertexId) loc).getAdjacentCells()[0];
		}
		else
		{
			throw new IllegalArgumentException("not a recognized loc");
		}
	}

	public RegionServant getRegionForMob(String mobName)
	{
		assert mobName != null;
		assert mobs.containsKey(mobName);

		return mobs.get(mobName);
	}

	public RegionServant getRegionForLocation(Location loc)
	{
		int regionId = getRegionIdForLocation(loc);
		return regions[regionId-1];
	}

	//implements Saveable
	public void save()
		throws IOException
	{
		File f2 = new File(config.path, "world.txt");
		JsonGenerator out = new JsonFactory().createJsonGenerator(f2, JsonEncoding.UTF8);
		out.writeStartObject();
		out.writeNumberField("year", year);
		out.writeNumberField("gameTime", scheduler.currentTime());
		out.writeNumberField("lastSeqId", lastSeqId);
		out.writeFieldName("leaders");
		out.writeStartObject();
		for (String name : leaders.keySet())
		{
			out.writeFieldName(name);
			leaders.get(name).write(out);
		}
		out.writeEndObject(); //end "leaders" property

		out.writeEndObject();
		out.close();
	}

	public void saveAll()
		throws IOException
	{
		save();
		for (int i = 0; i < regions.length; i++)
		{
			regions[i].save();
		}
	}

	void doOneStep()
	{
		year++;
		for (RegionServant r : regions)
		{
			r.endOfYear_stage1();
		}
		for (RegionServant r : regions)
		{
			r.endOfYear_cleanup();
		}
	}

	LeaderInfo getLeaderByUsername(String user)
	{
		return leaders.get(user);
	}

	boolean regionHasMobOwnedBy(int regionId, String user)
	{
		RegionServant region = regions[regionId-1];
		for (MobServant mob : region.presentMobs.values())
		{
			if (mob.owner != null && mob.owner.equals(user))
				return true;
		}
		return false;
	}

	void regionChanged(int regionId)
	{
		for (String leaderName : leaders.keySet())
		{
			if (leaderCanSeeRegion(leaderName, regionId))
			{
				discoverTerrain(leaderName, new SimpleLocation(regionId));
			}
		}
	}

	boolean leaderCanSeeRegion(String user, int regionId)
	{
		// check whether the designated region has a mob owned by this
		// player

		if (regionHasMobOwnedBy(regionId, user))
			return true;

		// check the same thing for any of the region's neighbors

		for (int nid : getRegionNeighbors(regionId))
		{
			if (regionHasMobOwnedBy(nid, user))
				return true;
		}

		return false;
	}

	RegionProfile makeRegionProfileFor(String user, int regionId)
	{
		assert leaderCanSeeRegion(user, regionId);

		RegionServant region = regions[regionId-1];

		RegionProfile p = new RegionProfile();
		p.biome = region.biome;
		if (region.city != null)
			p.citySize = 1;

		for (int i = 0; i < 6; i++)
		{
			if (region.sides[i] != null)
				p.sides[i] = region.sides[i].feature;
			if (region.corners[i] != null)
				p.corners[i] = region.corners[i].feature;
		}

		return p;
	}

	void wantSaved(Saveable obj)
	{
		try
		{
			obj.save();
		}
		catch (IOException e)
		{
			//lame
			e.printStackTrace(System.err);
		}
	}

	boolean isAdjacent(Location loc1, Location loc2)
	{
		assert loc1 instanceof SimpleLocation;
		assert loc2 instanceof SimpleLocation;

		int region1 = ((SimpleLocation) loc1).regionId;
		int region2 = ((SimpleLocation) loc2).regionId;

		for (int nid : getGeometry().getNeighbors(region1))
		{
			if (nid == region2)
				return true;
		}
		return false;
	}

	MobServant getMob(String mobName)
	{
		RegionServant svt = getRegionForMob(mobName);
		return svt.presentMobs.get(mobName);
	}

	void requestMovement(String mobName, Location dest)
	{
		MobServant mob = getMob(mobName);
		assert mob != null;

		//TODO- reject request if the mob is busy

		// ignore request if movement not allowed
		if (!isAdjacent(mob.location, dest))
			return;

		RegionServant fromRegion = getRegionForMob(mobName);
		RegionServant toRegion = getRegionForLocation(dest);

		// ignore request if destination is not tolerable
		// to this mob
		if (!fromRegion.mobCanMoveTo(mobName, dest))
			return;

		// check whether mob is busy
		if (fromRegion.mobIsHot(mobName))
			return;

		fromRegion.mobCancelActivity(mobName);

		long delay = fromRegion.mobMovementDelay(mobName, dest);
		assert delay > 0;

		Location oldLoc = mob.location;

		fromRegion.removeMob(mobName);

		toRegion.mobMovedIn(mobName, mob, dest, delay);

		mobMoved(mobName, oldLoc, dest);
		discoverTerrain(mob.owner, dest);
		discoverTerrainBorder(mob.owner, dest);
		wantSaved(this);
	}

	void mobMoved(String mobName, Location oldLoc, Location newLoc)
	{
		MobServant mob = getMob(mobName);
		if (mob.owner != null)
		{
			MobInfo data = new MobInfo();
			data.location = newLoc;
			MobChangeNotification n = new MobChangeNotification(mobName, data);
			notifyLeader(mob.owner, n);
		}

		//TODO- inform everyone else who can see this mob
	}

	void discoverTerrain(String user, Location loc)
	{
		MapModel map = leaders.get(user).map;

		int regionId = getRegionIdForLocation(loc);
		RegionProfile p = makeRegionProfileFor(user, regionId);
		if (map.updateRegion(loc, p))
		{
			fireMapUpdate(user, regionId, p);
		}
	}

	void discoverTerrainBorder(String user, Location loc)
	{
		int regionId = getRegionIdForLocation(loc);
		for (int nid : getGeometry().getNeighbors(regionId))
		{
			discoverTerrain(user, new SimpleLocation(nid));
		}
	}

	void fireMapUpdate(String user, int regionId, RegionProfile p)
	{
		MapUpdateNotification n = new MapUpdateNotification(regionId, p);
		notifyLeader(user, n);
	}

	void notifyLeader(String user, Notification n)
	{
		LeaderInfo leader = leaders.get(user);
		if (leader == null)
			return;

		leader.sendNotification(n);
	}

	EventDispatchThread eventDispatchThread;
	public void start()
	{
		assert eventDispatchThread == null;

		eventDispatchThread = new EventDispatchThread(scheduler);
		eventDispatchThread.start();
	}

	public void stop()
		throws InterruptedException
	{
		assert eventDispatchThread != null;

System.out.println("in world::stop()");

		eventDispatchThread.requestStop();
		eventDispatchThread.join();
	}

	class RealTimeLockHack implements Runnable
	{
		long time;
		boolean activated = false;
		boolean released = false;

		public void run()
		{
			this.time = EventDispatchThread.currentTime();
			acquire();
			waitForRelease();
		}

		synchronized void acquire()
		{
			this.activated = true;
			notifyAll();
		}

		synchronized void waitForAcquisition()
		{
			while (!activated)
			{
				try {
				wait();
				} catch (InterruptedException e) {}
			}
		}

		synchronized void release()
		{
			this.released = true;
			notifyAll();
		}

		synchronized void waitForRelease()
		{
			while (!released)
			{
				try {
				wait();
				} catch (InterruptedException e) {}
			}
		}
	}

	RealTimeLockHack acquireRealTimeLock()
	{
		RealTimeLockHack hack = new RealTimeLockHack();
		long t = scheduler.convertToGameTime(System.currentTimeMillis());
		scheduler.scheduleAt(hack, t);

		hack.waitForAcquisition();
		return hack;
	}
}
