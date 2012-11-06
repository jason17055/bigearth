package bigearth;

import javax.vecmath.*;

public class TerrainGeometry
{
	Geometry g;
	int depth;

	public TerrainGeometry(Geometry g, int depth)
	{
		this.g = g;
		this.depth = depth;
	}

	public int findTileInRegion(int regionId, Point3d pt)
	{
		// predeclare some Vectors for us to use in this method
		Vector3d v = new Vector3d();
		Vector3d v2 = new Vector3d();

		// First step is to find which of the six(or five) wedges of
		// the region the given point is within. To do this, we use
		// a little vector math to determine whether the point is on
		// the positive or negative side of a plane. The plane is
		// the boundary between two wedges.

		int [] nn = g.getNeighbors(regionId); //only used to determine number of neighbors

		int [] pick = new int[nn.length];
		for (int i = 0; i < nn.length; i++)
		{
			// determine the boundary for this wedge...
			Point3d [] boundary = getTerrainBoundary(regionId, i, 0);

			// calculate the normal for the plane describing the boundary
			// between this wedge and the next wedge in counter-clockwise order.

			v.cross(new Vector3d(boundary[0]), new Vector3d(boundary[2]));
			v.normalize();

			v2.sub(pt, boundary[0]);
			double proj = v2.dot(v);

			// proj > 0 means the given point is on the positive side of the
			// plane (i.e. the next wedge);
			// proj < 0 means the given point is on the negative side of the plane

			int j = (i+1)%nn.length;
			pick[i] = proj < 0 ? i : j;
		}

		// now determine which wedge was determined
		int tile = 0;
		for (int i = 0; i + 1 < nn.length; i++)
		{
			if (pick[i] == pick[i+1] && pick[i] == i+1)
			{
				tile = i+1;
				break;
			}
		}

		// now that we've got the major wedge, go iteratively deeper,
		// each time breaking the current triangle into four smaller triangles
		// and picking the inner triangle that contains the desired point

		for (int de = 0; de < depth; de++)
		{
			Point3d [] boundary = getTerrainBoundary(regionId, tile, de);
			v.sub(boundary[0], pt);
			double dS = v.length();

			v.sub(boundary[1], pt);
			double dNE = v.length();

			v.sub(boundary[2], pt);
			double dNW = v.length();

			Point3d ptA = new Point3d();
			ptA.interpolate(boundary[1], boundary[2], 0.5);
			v.sub(ptA, pt);
			double dN = v.length();

			ptA.interpolate(boundary[0], boundary[2], 0.5);
			v.sub(ptA, pt);
			double dSW = v.length();

			ptA.interpolate(boundary[0], boundary[1], 0.5);
			v.sub(ptA, pt);
			double dSE = v.length();

			tile = tile * 4 + (
				dS < dN ? 0 :
				dNE < dSW ? 1 :
				dNW < dSE ? 2 :
				3);
		}
		return tile;
	}

	/**
	 * Returns the number of terrain tiles in a given region.
	 * @param regionId identifies a region
	 */
	public int getRegionTileCount(int regionId)
	{
		return g.getNeighbors(regionId).length * (1 << (2*depth));
	}

	/**
	 * Returns the three coordinates of the boundary of a particular
	 * terrain tile.
	 * The coordinates are returned in counter-clockwise order.
	 */
	public Point3d [] getTerrainBoundary(int regionId, int tile)
	{
		return getTerrainBoundary(regionId, tile, depth);
	}

	/**
	 * Helper function for getTerrainBoundary.
	 */
	private Point3d [] getTerrainBoundary(int regionId, int tile, int de)
	{
		assert regionId >= 1;
		assert tile >= 0;
		assert de >= 0;

		if (de <= 0)
		{
			Point3d c = g.getCenterPoint(regionId);
			int [] nn = g.getNeighbors(regionId);
			assert tile < nn.length;

			Point3d d = g.getCenterPoint(nn[tile]);
			Point3d e = g.getCenterPoint(nn[(tile+1)%nn.length]);
			Point3d f = g.getCenterPoint(nn[(tile+nn.length-1)%nn.length]);

			Vector3d aPt = new Vector3d();
			aPt.set(c);
			aPt.add(d);
			aPt.add(f);
			aPt.normalize();
			f = new Point3d(aPt);

			aPt.set(c);
			aPt.add(d);
			aPt.add(e);
			aPt.normalize();
			e = new Point3d(aPt);

			return new Point3d[] { c, f, e };
		}

		Point3d [] basePts = getTerrainBoundary(regionId, tile/4, de-1);
		int x = tile%4;
		if (x == 0)
		{
			Point3d p1 = new Point3d();
			Point3d p2 = new Point3d();
			p2.interpolate(basePts[0], basePts[1], 0.5);
			p1.interpolate(basePts[0], basePts[2], 0.5);
			return new Point3d[] { basePts[0], p2, p1 };
		}
		else if (x == 1)
		{
			Point3d p0 = new Point3d();
			Point3d p2 = new Point3d();
			p0.interpolate(basePts[1], basePts[2], 0.5);
			p2.interpolate(basePts[1], basePts[0], 0.5);
			return new Point3d[] { p0, p2, basePts[1] };
		}
		else if (x == 2)
		{
			Point3d p0 = new Point3d();
			Point3d p1 = new Point3d();
			p0.interpolate(basePts[2], basePts[1], 0.5);
			p1.interpolate(basePts[2], basePts[0], 0.5);
			return new Point3d[] { p0, basePts[2], p1 };
		}
		else
		{
			assert x == 3;

			Point3d p0 = new Point3d();
			Point3d p1 = new Point3d();
			Point3d p2 = new Point3d();
			p0.interpolate(basePts[1], basePts[2], 0.5);
			p1.interpolate(basePts[0], basePts[2], 0.5);
			p2.interpolate(basePts[0], basePts[1], 0.5);
			return new Point3d[] { p0, p1, p2 };
		}
	}

	/**
	 * Fills in information about the three neighbors of a given terrain tile.
	 * The neighbors are identified in counter-clockwise order.
	 */
	public void getNeighborTiles(int [] neighborRegions, int [] neighborTiles, int regionId, int tile)
	{
		assert neighborRegions != null && neighborRegions.length == 3;
		assert neighborTiles != null && neighborTiles.length == 3;

		TileInfo ti = getTileInfo(regionId, tile, depth);
		neighborRegions[0] = ti.n0_region;
		neighborRegions[1] = ti.n1_region;
		neighborRegions[2] = ti.n2_region;
		neighborTiles[0] = ti.n0_tile;
		neighborTiles[1] = ti.n1_tile;
		neighborTiles[2] = ti.n2_tile;
	}

	public TerrainId [] getNeighborTiles(TerrainId tile)
	{
		int [] rr = new int[3];
		int [] tt = new int[3];
		getNeighborTiles(rr, tt, tile.regionId, tile.tile);

		TerrainId [] rv = new TerrainId[] {
			new TerrainId(rr[0], tt[0]),
			new TerrainId(rr[1], tt[1]),
			new TerrainId(rr[2], tt[2])
			};
		return rv;
	}

	int RIGHT = 1;
	int LEFT = 2;
	public TerrainId getRelativeTile(TerrainId foreTile, TerrainId aftTile, int dir)
	{
		int [] rr = new int[3];
		int [] tt = new int[3];
		getNeighborTiles(rr, tt, foreTile.regionId, foreTile.tile);

		if (rr[0] == aftTile.regionId && tt[0] == aftTile.tile)
		{
			return new TerrainId(rr[dir], tt[dir]);
		}
		else if (rr[1] == aftTile.regionId && tt[1] == aftTile.tile)
		{
			return new TerrainId(rr[(1+dir)%3], tt[(1+dir)%3]);
		}
		else
		{
			return new TerrainId(rr[(2+dir)%3], tt[(2+dir)%3]);
		}
	}

	public TerrainId car(TerrainId foreTile, TerrainId aftTile)
	{
		return getRelativeTile(foreTile, aftTile, LEFT);
	}

	public TerrainId caar(TerrainId foreTile, TerrainId aftTile)
	{
		TerrainId t = car(foreTile, aftTile);
		return car(t, foreTile);
	}

	public TerrainId cdr(TerrainId foreTile, TerrainId aftTile)
	{
		return getRelativeTile(foreTile, aftTile, RIGHT);
	}

	public TerrainId cddr(TerrainId foreTile, TerrainId aftTile)
	{
		TerrainId t = cdr(foreTile, aftTile);
		return cdr(t, foreTile);
	}

	/**
	 * Helper function.
	 */
	private TileInfo getTileInfo(int regionId, int tile, int d)
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

	/**
	 * Helper function.
	 */
	private TileInfo getBaseTileInfo(int regionId, int tile)
	{
		assert regionId >= 1;
		assert tile >= 0;

		TileInfo ti = new TileInfo();
		ti.level = 0;
		ti.my_region = regionId;
		ti.my_tile = tile;

		int [] nn = g.getNeighbors(regionId);
		assert tile < nn.length;

		// previous wedge in counter-clockwise order
		ti.n0_region = regionId;
		ti.n0_attitude = TileInfo.SHARED_SOUTH_VERTEX;
		ti.n0_tile = (tile + nn.length - 1) % nn.length;

		// next wedge in counter-clockwise order
		ti.n2_region = regionId;
		ti.n2_attitude = TileInfo.SHARED_SOUTH_VERTEX;
		ti.n2_tile = (tile + 1) % nn.length;

		// figure out what region is to the "north" of this tile,
		// and which wedge of that neighboring region are we
		// connected to...

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
