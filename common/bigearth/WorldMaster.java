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
	Map<String, MobInfo> mobs;
	Scheduler scheduler;

	int year;
	int lastSeqId;

	public WorldMaster(WorldConfig config)
	{
		this.config = config;
		this.regions = new RegionServant[config.getGeometry().getCellCount()];
		this.leaders = new HashMap<String, LeaderInfo>();
		this.mobs = new HashMap<String, MobInfo>();
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
				mobs.put(s, regions[i].presentMobs.get(s));
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
		return regions[regionId-1];
	}

	MobInfo newMob()
	{
		String name = "mob"+(++lastSeqId);
		MobInfo mob = new MobInfo(name);
		return mob;
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
		for (MobInfo mob : region.presentMobs.values())
		{
			if (mob.owner != null && mob.owner.equals(user))
				return true;
		}
		return false;
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

		for (int i = 0; i < 6; i++)
		{
			if (region.sides[i] != null)
				p.sides[i] = region.sides[i].feature;
			if (region.corners[i] != null)
				p.corners[i] = region.corners[i].feature;
		}

		p.mobs = new HashMap<String, MobInfo>();
		for (String mobName : region.presentMobs.keySet())
		{
			MobInfo realMob = region.presentMobs.get(mobName);
			MobInfo mob = new MobInfo(mobName);
			mob.avatarName = realMob.avatarName;
			p.mobs.put(mobName, mob);
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

	void requestMovement(String mobName, Location dest)
	{
		MobInfo mob = mobs.get(mobName);
		assert mob != null;

		//TODO- reject request if the mob is busy

		Location oldLoc = mob.location;
		mob.location = dest;

		RegionServant fromRegion = getRegionForLocation(oldLoc);
		fromRegion.removeMob(mobName);

		RegionServant toRegion = getRegionForLocation(dest);
		toRegion.addMob(mobName, mob);

		mobMoved(mobName, oldLoc, dest);
		discoverTerrain(mob.owner, oldLoc);
		discoverTerrain(mob.owner, dest);
		discoverTerrainBorder(mob.owner, dest);
		wantSaved(this);
	}

	void mobMoved(String mobName, Location oldLoc, Location newLoc)
	{
		MobInfo mob = mobs.get(mobName);
		if (mob.owner == null)
			return;

		MobInfo data = new MobInfo(mobName);
		data.location = newLoc;
		MobChangeNotification n = new MobChangeNotification(mobName, data);
		notifyLeader(mob.owner, n);
	}

	void discoverTerrain(String user, Location loc)
	{
		int regionId = getRegionIdForLocation(loc);
		RegionProfile p = makeRegionProfileFor(user, regionId);
		leaders.get(user).map.put(loc, p);
		fireMapUpdate(user, regionId, p);
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
}
