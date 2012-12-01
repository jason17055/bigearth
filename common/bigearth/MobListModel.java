package bigearth;

import java.io.*;
import java.util.*;
import com.fasterxml.jackson.core.*;

public class MobListModel
{
	Map<String, MobInfo> mobs;

	public MobListModel()
	{
		this.mobs = new HashMap<String, MobInfo>();
	}

	void parse(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String mobName = in.getCurrentName();
			MobInfo mob = MobInfo.parse(in, mobName, world);
			mobs.put(mobName, mob);
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}

	void put(String mobName, MobInfo mob)
	{
		mobs.put(mobName, mob);
	}
}
