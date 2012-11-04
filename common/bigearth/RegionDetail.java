package bigearth;

import java.io.*;
import javax.vecmath.*;
import com.fasterxml.jackson.core.*;

class RegionDetail
{
	int numSides;
	int detailLevel;
	int wildlife;
	char [] terrains;
	boolean dirty;

	RegionDetail(int numSides, int detailLevel)
	{
		this.numSides = numSides;
		this.detailLevel = detailLevel;
		this.terrains = new char[numSides * (1 << (detailLevel*2))];
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
		out.writeNumberField("numSides", numSides);
		out.writeNumberField("detailLevel", detailLevel);
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

	static RegionDetail load(File regionFile)
		throws IOException
	{
		JsonParser in = new JsonFactory().createJsonParser(regionFile);
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		int numSides = 6;
		int detailLevel = 0;
		int wildlife = 0;
		int [] terrains = null;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("numSides"))
				numSides = in.nextIntValue(numSides);
			else if (s.equals("detailLevel"))
				detailLevel = in.nextIntValue(detailLevel);
			else if (s.equals("terrains"))
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

		RegionDetail m = new RegionDetail(numSides, detailLevel);
		m.wildlife = wildlife;
		if (terrains != null)
		{
			for (int i = 0; i < m.terrains.length; i++)
			{
				m.terrains[i] = (char) terrains[i];
			}
		}
		return m;
	}
}
