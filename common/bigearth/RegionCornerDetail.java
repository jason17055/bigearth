package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;

public class RegionCornerDetail
{
	public static enum PointFeature
	{
		NONE,
		POND,
		LAKE;
	}

	PointFeature feature;

	public RegionCornerDetail()
	{
		this.feature = PointFeature.NONE;
	}

	public static RegionCornerDetail parse(JsonParser in)
		throws IOException
	{
		RegionCornerDetail m = new RegionCornerDetail();

		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("feature"))
				m.feature = PointFeature.valueOf(in.nextTextValue());
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized corner property: "+s);
			}
		}

		return m;
	}

	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("feature", feature.name());
		out.writeEndObject();
	}
}
