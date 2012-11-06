package bigearth;

public final class TerrainId
{
	public int regionId;
	public int tile;

	public TerrainId(int regionId, int tile)
	{
		assert regionId >= 1;
		assert tile >= 0;

		this.regionId = regionId;
		this.tile = tile;
	}

	public boolean equals(Object o)
	{
		if (o instanceof TerrainId)
		{
			TerrainId rhs = (TerrainId) o;
			return this.regionId == rhs.regionId
			&& this.tile == rhs.tile;
		}
		return false;
	}

	public int hashCode()
	{
		return 33 * this.regionId + tile;
	}
}
