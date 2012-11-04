package bigearth;

import java.awt.Color;

public enum TerrainType
{
	GRASS (1, 0x00ff00),
	OCEAN (2, 0x0000ff),
	LAKE  (3, 0x6666ff);

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
