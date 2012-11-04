package bigearth;

import java.io.*;
import javax.vecmath.*;
import com.fasterxml.jackson.core.*;

class RegionDetail
{
	MakeWorld world;
	int regionId;

	int wildlife;
	int [] terrains;
	boolean dirty;

	RegionDetail(MakeWorld world, int regionId)
	{
		this.world = world;
		this.regionId = regionId;
	}

	private void init()
	{
		int numSides = world.g.getNeighborCount(regionId);
		int detailLevel = world.regionDetailLevel;
		this.terrains = new int[numSides * (1 << (detailLevel*2))];
	}

	public void adjustWildlife(int delta)
	{
		this.wildlife += delta;
		if (wildlife < 0)
			wildlife = 0;
		dirty = true;
	}

	public void setTerrainType(int terrainId, TerrainType type)
	{
		terrains[terrainId] = type.id;
		dirty = true;
	}

	void save(File regionFile)
		throws IOException
	{
		JsonGenerator out = new JsonFactory().createJsonGenerator(regionFile,
					JsonEncoding.UTF8);
		out.writeStartObject();
		out.writeNumberField("wildlife", wildlife);
		out.writeArrayFieldStart("terrains");
		for (int i = 0; i < terrains.length; i++)
		{
			out.writeNumber(terrains[i]);
		}
		out.writeEndArray();
		out.writeEndObject();
		out.close();
	}

	static RegionDetail create(MakeWorld world, int regionId)
	{
		RegionDetail m = new RegionDetail(world, regionId);
		m.init();
		return m;
	}

	static RegionDetail load(File regionFile, MakeWorld world, int regionId)
		throws IOException
	{
		RegionDetail m = new RegionDetail(world, regionId);
		m.load(regionFile);
		return m;
	}

	void load(File regionFile)
		throws IOException
	{
		JsonParser in = new JsonFactory().createJsonParser(regionFile);
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("terrains"))
				terrains = MakeWorld.json_readIntArray(in);
			else if (s.equals("wildlife"))
				wildlife = in.nextIntValue(wildlife);
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized property: "+s);
			}
		}
	}
}
