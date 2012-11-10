package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;

public class RegionSideDetail
{
	public static enum SideFeature
	{
		NONE,
		BROOK,
		CREEK,
		RIVER;

		public boolean isRiver()
		{
			return this.ordinal() == BROOK.ordinal() ||
				this.ordinal() == CREEK.ordinal() ||
				this.ordinal() == RIVER.ordinal();
		}
	}

	SideFeature feature;

	public RegionSideDetail()
	{
		this.feature = SideFeature.NONE;
	}

	public static RegionSideDetail parse(JsonParser in)
		throws IOException
	{
		RegionSideDetail m = new RegionSideDetail();

		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("feature"))
				m.feature = SideFeature.valueOf(in.nextTextValue());
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized side property: "+s);
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
