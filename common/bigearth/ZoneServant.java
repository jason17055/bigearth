package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

/**
 * The server-side implementation of a "zone".
 * A zone is a subsection of a "Region", which contains a single building
 * or land dedicated for a single purpose.
 * There are something like 64 zones in each region.
 */
public class ZoneServant
{
	transient RegionServant parentRegion;

	ZoneType type;

	ZoneServant(RegionServant parentRegion)
	{
		this.parentRegion = parentRegion;
	}

	public static ZoneServant parse(JsonParser in, RegionServant parentRegion)
		throws IOException
	{
		ZoneServant zone = new ZoneServant(parentRegion);
		zone.parse_real(in);
		return zone;
	}

	private void parse_real(JsonParser in)
		throws IOException
	{
		in.nextToken();
		if (in.getCurrentToken() == JsonToken.VALUE_STRING)
		{
			type = ZoneType.valueOf(in.getText());
			return;
		}
		else
		{
			throw new InputMismatchException();
		}
	}

	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeString(type.name());
	}

	public ZoneInfo makeProfile()
	{
		ZoneInfo zi = new ZoneInfo();
		zi.type = type;
		return zi;
	}
}
