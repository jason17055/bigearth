package bigearth;

import java.io.*;
import com.fasterxml.jackson.core.*;

class RegionProfile
{
	BiomeType biome;
	RegionSideDetail.SideFeature [] sides;
	RegionCornerDetail.PointFeature [] corners;

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
			else
			{
				in.nextToken();
				in.skipChildren();
			}
		}
	}

	boolean hasAnyMobs;
	public boolean hasAnyMobs()
	{
		return this.hasAnyMobs;
	}

	public RegionSideDetail.SideFeature getSideFeature(int sideNumber)
	{
		return sides[sideNumber];
	}

	public RegionCornerDetail.PointFeature getCornerFeature(int cornerNumber)
	{
		return corners[cornerNumber];
	}
}
