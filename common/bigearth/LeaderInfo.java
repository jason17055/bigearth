package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

public class LeaderInfo
{
	String name;
	String displayName;
	String password;
	Collection<NotificationStream> streams;

	LeaderInfo(String name)
	{
		this.name = name;
		this.displayName = name;
		this.streams = new ArrayList<NotificationStream>();
	}

	boolean checkPassword(String inPassword)
	{
		return (
			password != null && password.equals(inPassword)
			);
	}

	void parse(JsonParser in)
		throws IOException
	{
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("displayName"))
			{
				displayName = in.nextTextValue();
			}
			else if (s.equals("password"))
			{
				password = in.nextTextValue();
			}
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized leader property: " + s);
			}
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}

	void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("displayName", displayName);
		if (password != null)
		{
			out.writeStringField("password", password);
		}

		out.writeEndObject();
	}
}
