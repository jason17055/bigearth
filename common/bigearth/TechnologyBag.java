package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

public abstract class TechnologyBag
{
	public static void writeTechnologySet(JsonGenerator out, Set<Technology> techs)
		throws IOException
	{
		out.writeStartArray();
		for (Technology t : techs)
		{
			out.writeString(t.name());
		}
		out.writeEndArray();
	}

	public static Set<Technology> parseTechnologySet(JsonParser in)
		throws IOException
	{
		in.nextToken();
		if (in.getCurrentToken() != JsonToken.START_ARRAY)
			throw new InputMismatchException();

		HashSet<Technology> techs = new HashSet<Technology>();
		while (in.nextToken() != JsonToken.END_ARRAY)
		{
			Technology t = Technology.valueOf(in.getText());
			techs.add(t);
		}
		return techs;
	}
}
