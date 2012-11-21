package bigearth;

import java.io.*;
import java.util.*;
import com.fasterxml.jackson.core.*;

public class WorldMaster
{
	WorldConfig config;
	RegionDetail [] regions;
	Map<String, LeaderInfo> leaders;

	int year;
	int lastSeqId;

	public WorldMaster(WorldConfig config)
	{
		this.config = config;
		this.regions = new RegionDetail[config.getGeometry().getCellCount()];
		this.leaders = new HashMap<String, LeaderInfo>();
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
			regions[i] = RegionDetail.load(regionFilename, this, regionId);
		}
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

	void newLeader(String leaderDisplayName)
	{
		String name = "leader"+(++lastSeqId);
		LeaderInfo leader = new LeaderInfo(name);
		leader.displayName = leaderDisplayName;
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

	public RegionDetail getRegionForLocation(Location loc)
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
		j.writeEndObject();
		j.close();

		for (int i = 0; i < regions.length; i++)
		{
			File regionFile = new File(config.path, "region"+(i+1)+".txt");
			regions[i].save(regionFile);
		}

		File leadersDir = config.path;
		for (String name : leaders.keySet())
		{
			File leaderFile = new File(leadersDir, name+".txt");
			leaders.get(name).save(leaderFile);
		}
	}

	void doOneStep()
	{
		year++;
		for (RegionDetail r : regions)
		{
			r.endOfYear_stage1();
		}
		for (RegionDetail r : regions)
		{
			r.endOfYear_cleanup();
		}
	}
}
