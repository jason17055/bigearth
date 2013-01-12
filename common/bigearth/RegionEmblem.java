package bigearth;

import java.net.URL;

public enum RegionEmblem
{
	STOCKPILE, NO_STOCKPILE,
	GOLD,      NO_GOLD;

	public URL getIconResource()
	{
		String resourceName = "/terrain_emblems/"+name().toLowerCase()+".png";
		return RegionEmblem.class.getResource(resourceName);
	}

	public RegionEmblem getOpposite()
	{
		switch (this)
		{
		case STOCKPILE: return NO_STOCKPILE;
		case GOLD: return NO_GOLD;
		case NO_STOCKPILE: return STOCKPILE;
		case NO_GOLD: return GOLD;
		default: throw new Error("unreachable");
		}
	}
}
