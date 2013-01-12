package bigearth;

import java.io.*;
import java.util.*;
import com.fasterxml.jackson.core.*;

class RegionProfile
	implements Cloneable
{
	BiomeType biome;
	int citySize;
	String cityName;
	RegionSideDetail.SideFeature [] sides;
	RegionCornerDetail.PointFeature [] corners;
	CommoditiesBag stock;
	HashSet<RegionEmblem> emblems;

	public RegionProfile()
	{
		sides = new RegionSideDetail.SideFeature[6];
		corners = new RegionCornerDetail.PointFeature[6];
		emblems = new HashSet<RegionEmblem>();
	}

	public BiomeType getBiome()
	{
		return biome;
	}

	public boolean hasBiome()
	{
		return biome != null;
	}

	public boolean hasCityName()
	{
		return cityName != null;
	}

	public boolean hasCitySize()
	{
		return citySize != 0;
	}

	public boolean hasEmblem(RegionEmblem emblem)
	{
		return emblems.contains(emblem);
	}

	public boolean hasStock()
	{
		return stock != null;
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append(biome.name());
		return sb.toString();
	}

	@Override
	public boolean equals(Object obj)
	{
		if (obj instanceof RegionProfile)
		{
			RegionProfile rhs = (RegionProfile) obj;
			if (this.biome != rhs.biome)
				return false;
			if (this.citySize != rhs.citySize)
				return false;
			if (this.stock != null)
			{
				if (rhs.stock == null)
					return false;
				if (!this.stock.equals(rhs.stock))
					return false;
			}
			else if (rhs.stock != null)
			{
				return false;
			}
			if (this.cityName != null)
			{
				if (!this.cityName.equals(rhs.cityName))
					return false;
			}
			else if (rhs.cityName != null)
			{
				return false;
			}
			for (int i = 0; i < 6; i++)
			{
				if (this.sides[i] != rhs.sides[i])
					return false;
				if (this.corners[i] != rhs.corners[i])
					return false;
			}
			if (!this.emblems.equals(rhs.emblems))
				return false;
			return true;
		}
		else
			return false;
	}

	public Object clone()
	{
		try
		{
		return super.clone();
		}
		catch (CloneNotSupportedException e)
		{
			throw new Error("unexpected");
		}
	}

	/**
	 * Returns a new region profile, with properties set to the
	 * combination of this profile and the reference profile.
	 * Where properties conflict, the reference profile is favored.
	 */
	public RegionProfile merge(RegionProfile ref)
	{
		RegionProfile n = (RegionProfile) clone();
		if (ref.hasBiome())
			n.biome = ref.biome;
		if (ref.hasCitySize())
			n.citySize = ref.citySize;
		if (ref.hasCityName())
			n.cityName = ref.cityName;
		if (ref.hasStock())
			n.stock = ref.stock;
		for (int i = 0; i < 6; i++)
		{
			if (ref.sides[i] != null)
				n.sides[i] = ref.sides[i];
			if (ref.corners[i] != null)
				n.corners[i] = ref.corners[i];
		}
		for (RegionEmblem emblem : ref.emblems)
		{
			this.emblems.remove(emblem.getOpposite());
			this.emblems.add(emblem);
		}
		return n;
	}

	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		if (hasBiome())
			out.writeStringField("biome", biome.name());
		if (hasCitySize())
			out.writeNumberField("citySize", citySize);
		if (hasCityName())
			out.writeStringField("cityName", cityName);
		if (hasStock())
		{
			out.writeFieldName("stock");
			stock.write(out);
		}
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
		if (!emblems.isEmpty())
		{
			out.writeFieldName("emblems");
			out.writeStartArray();
			for (RegionEmblem emblem : emblems)
			{
				out.writeString(emblem.name());
			}
			out.writeEndArray();
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
			else if (s.equals("citySize"))
			{
				in.nextToken();
				citySize = in.getIntValue();
			}
			else if (s.equals("cityName"))
				cityName = in.nextTextValue();
			else if (s.equals("stock"))
				stock = CommoditiesBag.parse(in);
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
			else if (s.equals("emblems"))
				parseEmblems(in);
			else
			{
				in.nextToken();
				in.skipChildren();
			}
		}
	}

	private void parseEmblems(JsonParser in)
		throws IOException
	{
		in.nextToken();
		if (in.getCurrentToken() != JsonToken.START_ARRAY)
			throw new InputMismatchException();

		while (in.nextToken() != JsonToken.END_ARRAY)
		{
			RegionEmblem em = RegionEmblem.valueOf(in.getText());
			emblems.add(em);
		}
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
