package bigearth;

import java.io.*;
import java.util.*;
import com.fasterxml.jackson.core.*;

class RegionProfile
{
	BiomeType biome;
	RegionSideDetail.SideFeature [] sides;
	RegionCornerDetail.PointFeature [] corners;
	Map<String, MobInfo> mobs;

	public RegionProfile()
	{
		sides = new RegionSideDetail.SideFeature[6];
		corners = new RegionCornerDetail.PointFeature[6];
	}

	public BiomeType getBiome()
	{
		return biome;
	}

	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		if (biome != null)
			out.writeStringField("biome", biome.name());
		for (int i = 0; i < sides.length; i++)
		{
			if (sides[i] != null)
			{
				out.writeFieldName("side"+i);
				out.writeString(sides[i].name());
			}
		}
		for (int i = 0; i < corners.length; i++)
		{
			if (corners[i] != null)
			{
				out.writeFieldName("corner"+i);
				out.writeString(corners[i].name());
			}
		}

		if (mobs != null)
		{
			out.writeFieldName("mobs");
			writeMobs(out);
		}

		out.writeEndObject();
	}

	void writeMobs(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		for (String mobName : mobs.keySet())
		{
			MobInfo mob = mobs.get(mobName);
			out.writeFieldName(mobName);
			mob.write(out);
		}
		out.writeEndObject();
	}

	static RegionProfile parse_s(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		RegionProfile me = new RegionProfile();
		me.parse(in, world);
		return me;
	}

	public void parse(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		in.nextToken();
		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("biome"))
				biome = BiomeType.valueOf(in.nextTextValue());
			else if (s.equals("side0"))
				sides[0] = RegionSideDetail.SideFeature.valueOf(in.nextTextValue());
			else if (s.equals("side1"))
				sides[1] = RegionSideDetail.SideFeature.valueOf(in.nextTextValue());
			else if (s.equals("side2"))
				sides[2] = RegionSideDetail.SideFeature.valueOf(in.nextTextValue());
			else if (s.equals("side3"))
				sides[3] = RegionSideDetail.SideFeature.valueOf(in.nextTextValue());
			else if (s.equals("side4"))
				sides[4] = RegionSideDetail.SideFeature.valueOf(in.nextTextValue());
			else if (s.equals("side5"))
				sides[5] = RegionSideDetail.SideFeature.valueOf(in.nextTextValue());
			else if (s.equals("corner0"))
				corners[0] = RegionCornerDetail.PointFeature.valueOf(in.nextTextValue());
			else if (s.equals("corner1"))
				corners[1] = RegionCornerDetail.PointFeature.valueOf(in.nextTextValue());
			else if (s.equals("corner2"))
				corners[2] = RegionCornerDetail.PointFeature.valueOf(in.nextTextValue());
			else if (s.equals("corner3"))
				corners[3] = RegionCornerDetail.PointFeature.valueOf(in.nextTextValue());
			else if (s.equals("corner4"))
				corners[4] = RegionCornerDetail.PointFeature.valueOf(in.nextTextValue());
			else if (s.equals("corner5"))
				corners[5] = RegionCornerDetail.PointFeature.valueOf(in.nextTextValue());
			else if (s.equals("mobs"))
				parseMobs(in, world);
			else
			{
				in.nextToken();
				in.skipChildren();
			}
		}
	}

	void parseMobs(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		mobs = new HashMap<String, MobInfo>();

		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() != JsonToken.END_OBJECT)
		{
			String mobName = in.getCurrentName();
			MobInfo mob = MobInfo.parse(in, mobName, world);
			mobs.put(mobName, mob);
		}
	}

	public boolean hasAnyMobs()
	{
		return this.mobs != null && !this.mobs.isEmpty();
	}

	public MobInfo getTopmostMob()
	{
		if (this.mobs == null)
			return null;

		MobInfo [] mobs = this.mobs.values().toArray(new MobInfo[0]);
		if (mobs.length == 0)
			return null;

		return mobs[0];
	}

	public RegionSideDetail.SideFeature getSideFeature(int sideNumber)
	{
		if (sides[sideNumber] != null)
			return sides[sideNumber];
		else
			return RegionSideDetail.SideFeature.NONE;
	}

	public RegionCornerDetail.PointFeature getCornerFeature(int cornerNumber)
	{
		if (corners[cornerNumber] != null)
			return corners[cornerNumber];
		else
			return RegionCornerDetail.PointFeature.NONE;
	}
}
