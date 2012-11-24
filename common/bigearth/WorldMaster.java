package bigearth;

import java.io.*;
import java.util.*;
import com.fasterxml.jackson.core.*;

public class WorldMaster
{
	WorldConfig config;
	RegionServant [] regions;
	Map<String, LeaderInfo> leaders;
	Map<String, MobInfo> mobs;

	int year;
	int lastSeqId;

	public WorldMaster(WorldConfig config)
	{
		this.config = config;
		this.regions = new RegionServant[config.getGeometry().getCellCount()];
		this.leaders = new HashMap<String, LeaderInfo>();
		this.mobs = new HashMap<String, MobInfo>();
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
			File regionFilename = new File(config.path, "region"+regionId+".txt");
			regions[i] = RegionServant.load(regionFilename, this, regionId);

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
			LeaderInfo leader = new LeaderInfo(leaderName);
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
		LeaderInfo leader = new LeaderInfo(name);
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

	public void save()
		throws IOException
	{
		File f2 = new File(config.path, "world.txt");
		JsonGenerator j = new JsonFactory().createJsonGenerator(f2, JsonEncoding.UTF8);
		j.writeStartObject();
		j.writeNumberField("year", year);
		j.writeNumberField("lastSeqId", lastSeqId);
		j.writeFieldName("leaders");
		j.writeStartObject();
		for (String name : leaders.keySet())
		{
			j.writeFieldName(name);
			leaders.get(name).write(j);
		}
		j.writeEndObject(); //end "leaders" property

		j.writeEndObject();
		j.close();

		for (int i = 0; i < regions.length; i++)
		{
			File regionFile = new File(config.path, "region"+(i+1)+".txt");
			regions[i].save(regionFile);
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

		return p;
	}
}

class RegionProfile
{
	BiomeType biome;

	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		if (biome != null)
			out.writeStringField("biome", biome.name());
		out.writeEndObject();
	}

	static RegionProfile parse(Location loc, JsonParser in)
		throws IOException
	{
		RegionProfile me = new RegionProfile();
		me.parse(in);
		return me;
	}

	public void parse(JsonParser in)
		throws IOException
	{
		in.nextToken();
		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("biome"))
			{
			}
		}
	}
}
