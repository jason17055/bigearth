package bigearth;

import java.net.URL;

public enum RegionEmblem
{
	STOCKPILE,
	GOLD;

	public URL getIconResource()
	{
		String resourceName = "/terrain_emblems/"+name().toLowerCase()+".png";
		return RegionEmblem.class.getResource(resourceName);
	}
}
