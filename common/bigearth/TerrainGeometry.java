package bigearth;

public class TerrainGeometry
{
	Geometry g;
	int depth;

	public TerrainGeometry(Geometry g, int depth)
	{
		this.g = g;
		this.depth = depth;
	}

	TileInfo getTileInfo(int regionId, int tile, int d)
	{
		assert regionId >= 1;
		assert tile >= 0;
		assert d >= 0;

		if (d <= 0)
		{
			return getBaseTileInfo(regionId, tile);
		}

		TileInfo ti = getTileInfo(regionId, tile/4, d-1);
		int x = tile % 4;

		if (x == 0)
		{
			assert ti.n0_attitude == TileInfo.SHARED_SOUTH_VERTEX;
			assert ti.n1_attitude == TileInfo.SHARED_NORTH_EDGE;
			assert ti.n2_attitude == TileInfo.SHARED_SOUTH_VERTEX;

			ti.level++;
			ti.my_tile = tile;

			ti.n0_tile = ti.n0_tile * 4;
			ti.n2_tile = ti.n2_tile * 4;

			ti.n1_region = ti.my_region;
			ti.n1_attitude = TileInfo.SHARED_NORTH_EDGE;
			ti.n1_tile = tile + 3;
		}
		else if (x == 1)
		{
			assert ti.n0_attitude == TileInfo.SHARED_SOUTH_VERTEX;
			assert ti.n1_attitude == TileInfo.SHARED_NORTH_EDGE;
			assert ti.n2_attitude == TileInfo.SHARED_SOUTH_VERTEX;

			ti.level++;
			ti.my_tile = tile;

			ti.n2_region = ti.n1_region;
			ti.n2_attitude = TileInfo.SHARED_SOUTH_VERTEX;
			ti.n2_tile = ti.n1_tile * 4 + 2;

			ti.n1_region = ti.n0_region;
			ti.n1_attitude = TileInfo.SHARED_NORTH_EDGE;
			ti.n1_tile = ti.n0_tile * 4 + 2;

			ti.n0_region = regionId;
			ti.n0_attitude = TileInfo.SHARED_SOUTH_VERTEX;
			ti.n0_tile = tile + 2;
		}
		else if (x == 2)
		{
			assert ti.n0_attitude == TileInfo.SHARED_SOUTH_VERTEX;
			assert ti.n1_attitude == TileInfo.SHARED_NORTH_EDGE;
			assert ti.n2_attitude == TileInfo.SHARED_SOUTH_VERTEX;

			ti.level++;
			ti.my_tile = tile;

			ti.n0_region = ti.n1_region;
			ti.n0_attitude = TileInfo.SHARED_SOUTH_VERTEX;
			ti.n0_tile = ti.n1_tile * 4 + 1;

			ti.n1_region = ti.n2_region;
			ti.n1_attitude = TileInfo.SHARED_NORTH_EDGE;
			ti.n1_tile = ti.n2_tile * 4 + 1;

			ti.n2_region = regionId;
			ti.n2_attitude = TileInfo.SHARED_SOUTH_VERTEX;
			ti.n2_tile = tile + 1;
		}
		else if (x == 3)
		{
			assert ti.n0_attitude == TileInfo.SHARED_SOUTH_VERTEX;
			assert ti.n1_attitude == TileInfo.SHARED_NORTH_EDGE;
			assert ti.n2_attitude == TileInfo.SHARED_SOUTH_VERTEX;

			ti.level++;
			ti.my_tile = tile;

			ti.n0_region = regionId;
			ti.n0_attitude = TileInfo.SHARED_SOUTH_VERTEX;
			ti.n0_tile = tile - 1;

			ti.n1_region = regionId;
			ti.n1_attitude = TileInfo.SHARED_NORTH_EDGE;
			ti.n1_tile = tile - 3;

			ti.n2_region = regionId;
			ti.n2_attitude = TileInfo.SHARED_SOUTH_VERTEX;
			ti.n2_tile = tile - 2;
		}

		return ti;
	}

	TileInfo getBaseTileInfo(int regionId, int tile)
	{
		assert regionId >= 1;
		assert tile >= 0;

		TileInfo ti = new TileInfo();
		ti.level = 0;
		ti.my_region = regionId;
		ti.my_tile = tile;

		int [] nn = g.getNeighbors(regionId);
		assert tile < nn.length;

		ti.n0_region = regionId;
		ti.n0_attitude = TileInfo.SHARED_SOUTH_VERTEX;
		ti.n0_tile = (tile + 1) % nn.length;

		ti.n2_region = regionId;
		ti.n2_attitude = TileInfo.SHARED_SOUTH_VERTEX;
		ti.n2_tile = (tile + nn.length - 1) % nn.length;

		// figure out what region is to the "north" of this tile

		int nid = nn[tile];
		int [] nn2 = g.getNeighbors(nid);

		int found = 0;
		for (int i = 1; i < nn2.length; i++)
		{
			if (nn2[i] == regionId)
			{
				found = i;
				break;
			}
		}

		ti.n1_region = nid;
		ti.n1_attitude = TileInfo.SHARED_NORTH_EDGE;
		ti.n1_tile = found;

		return ti;
	}

	static class TileInfo
	{
		int level;
		int my_region;
		int my_tile;
		int n0_region;  // tile to our "east"
		int n0_attitude;
		int n0_tile;
		int n1_region;  // tile to our "north"
		int n1_attitude;
		int n1_tile;
		int n2_region;  // tile to our "west"
		int n2_attitude;
		int n2_tile;

		static final int SHARED_SOUTH_VERTEX = 1;
		static final int SHARED_NORTH_EDGE = 2;
	}
}
