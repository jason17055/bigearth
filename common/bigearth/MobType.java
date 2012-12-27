package bigearth;

import java.net.URL;

public enum MobType
{
	SETTLER;

	public URL getAvatarIconResource()
	{
		String n = name().toLowerCase().replace('_','-');
		return MobType.class.getResource("/unit_images/"+n+".png");
	}
}
