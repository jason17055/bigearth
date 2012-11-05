package bigearth;

import java.awt.Color;

public enum TerrainType
{
	GRASS         ( 1, 0x00ff00),
	OCEAN         ( 2, 0x0000ff),
	LAKE          ( 3, 0x6666ff),
	DEEP_SEA      ( 4, 0x0000cc),
	SHEER_CLIFF   ( 5, 0),
	ROCKY_SHORE   ( 6, 0),
	GRAVEL_BEACH  ( 7, 0),
	SANDY_BEACH   ( 8, 0),
	FLAT_SAND     ( 9, 0),
	SAND_DUNES    (10, 0),
	FLAT_GRAVEL   (11, 0),
	ROCK_OUTCROP  (12, 0),
	ROCKY_PIT     (13, 0),
	STONY_RAVINE  (14, 0),
	ARID_GRASS    (15, 0),
	DESERT_OASIS  (16, 0),
	MATURE_FOREST (17, 0),
	YOUNG_FOREST  (18, 0),
	SHRUB         (19, 0),
	STREAM        (20, 0),
	CLAY          (21, 0),
	POND          (22, 0),
	GRAVEL        (23, 0),
	SNOW          (24, 0),
	JUNGLE        (25, 0),
	MARSH         (26, 0),
	LICHEN        (27, 0) ;

	final char id;
	final Color color;

	TerrainType(int id, int c)
	{
		this.id = (char) id;
		this.color = new Color(c);
	}

	public static TerrainType load(int id)
	{
		switch (id)
		{
		case 1: return GRASS;
		case 2: return OCEAN;
		case 3: return LAKE;
		default:
			assert false;
			return null;
		}
	}
}
