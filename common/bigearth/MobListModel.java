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

	void update(String mobName, MobInfo newMobInfo)
	{
		MobInfo oldMobInfo = mobs.get(mobName);
		if (oldMobInfo == null)
		{
			mobs.put(mobName, newMobInfo);
			fireMobUpdated(mobName);
			return;
		}

		if (newMobInfo.hasAvatarName())
		{
			oldMobInfo.avatarName = newMobInfo.avatarName;
		}
		if (newMobInfo.hasOwner())
		{
			oldMobInfo.owner = newMobInfo.owner;
		}
		if (newMobInfo.hasLocation())
		{
			oldMobInfo.location = newMobInfo.location;
		}
		if (newMobInfo.hasActivity())
		{
			oldMobInfo.activity = newMobInfo.activity;
			oldMobInfo.activityStarted = newMobInfo.activityStarted;
		}
		if (newMobInfo.hasStock())
		{
			oldMobInfo.stock = newMobInfo.stock;
		}
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
