package bigearth;

public enum BiomeType
{
	OCEAN,
	GRASSLAND,
	FOREST,
	SWAMP,
	JUNGLE,
	DESERT,
	GLACIER,
	TUNDRA,
	MOUNTAIN,
	HILLS,
	PLAINS;

	public double getWildlifeQuota()
	{
		return 1000.0;
	}
}
