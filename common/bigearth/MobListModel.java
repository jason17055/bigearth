package bigearth;

import java.io.*;
import java.util.*;
import com.fasterxml.jackson.core.*;

public class MobListModel
{
	Map<String, MobInfo> mobs;
	List<Listener> listeners = new ArrayList<Listener>();

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
		fireMobUpdated(mobName);
	}

	private void fireMobUpdated(String mobName)
	{
		for (Listener l : listeners)
		{
			l.mobUpdated(mobName);
		}
	}

	public interface Listener
	{
		void mobUpdated(String mobName);
	}

	public void addListener(Listener l)
	{
		this.listeners.add(l);
	}

	public void removeListener(Listener l)
	{
		this.listeners.remove(l);
	}
}
