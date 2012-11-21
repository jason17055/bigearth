package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;

public class LeaderInfo
{
	String name;
	String displayName;

	LeaderInfo(String name)
	{
		this.name = name;
		this.displayName = name;
	}

	void save(File filename)
		throws IOException
	{
		JsonGenerator out = new JsonFactory().createJsonGenerator(filename,
					JsonEncoding.UTF8);
		out.writeStartObject();
		out.writeStringField("displayName", displayName);

		out.writeEndObject();
		out.close();
	}
}
