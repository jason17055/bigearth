package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

/**
 * Describes a "zone". This class represents the data packet that is sent
 * over the network.
 */
public class ZoneInfo
{
	ZoneType type;

	public ZoneInfo()
	{
	}

	public static ZoneInfo parse(JsonParser in)
		throws IOException
	{
		ZoneInfo zone = new ZoneInfo();
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
}
