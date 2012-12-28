package bigearth;

import java.net.URL;

public enum ZoneType
{
	NATURAL,
	UNDER_CONSTRUCTION,
	MUD_COTTAGE,
	WOOD_COTTAGE,
	STONE_COTTAGE,
	FARM,
	PASTURE,
	STONE_WORKSHOP,
	STONE_BLOCK_FACTORY,
	STONE_WEAPON_FACTORY;

	public String getDisplayName()
	{
		String n = name();
		n = n.replace('_', ' ');
		n = n.toLowerCase();
		n = n.substring(0,1).toUpperCase() + n.substring(1);
		return n;
	}

	/// Can return null.
	public URL getIconResource()
	{
		String n = name();
		n = n.replace('_', '-');
		n = n.toLowerCase();
		return ZoneType.class.getResource("/zone_images/"+n+".png");
	}
}
