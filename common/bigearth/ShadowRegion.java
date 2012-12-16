package bigearth;

/**
 * Methods that need to be available to neighboring regions, whether or not
 * the regions are running on the same server.
 */
interface ShadowRegion
{
	BiomeType getBiome();
	void importWildlife(int newWildlife);
	void mobSight(int regionId, String mobName, boolean canSee);
}
