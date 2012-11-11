package bigearth;

public enum BiomeType
{
	GRASSLAND,
	OCEAN,
	LAKE,
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

	public boolean isWater()
	{
		return this.ordinal() == OCEAN.ordinal()
		|| this.ordinal() == LAKE.ordinal();
	}
}
