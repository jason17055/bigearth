package bigearth.geom;

import javax.vecmath.*;

/**
 * A ten-sided polyhedron with 4-sided faces.
 */
public class Decahedron implements Geometry
{
	static final int NORTHEAST = 0;
	static final int NORTHWEST = 1;
	static final int SOUTHWEST = 2;
	static final int SOUTHEAST = 3;

	public Decahedron()
	{
	}

	//
	//implements Geometry
	//
	public int getFaceCount()
	{
		return 10;
	}

	public int getEdgeCount()
	{
		return 20;
	}

	public int getVertexCount()
	{
		return 12;
	}

	public void rotateCursor(Cursor c, int adjust)
	{
		c.orientation = (c.orientation + 4 + (adjust%4)) % 4;
	}

	public void stepCursor(Cursor c)
	{
		assert c.location >= 0 && c.location < 10;
		assert c.orientation >= 0 && c.orientation < 4;

		int l = c.location;
		int o = c.orientation;
		c.location = NEIGHBORS[l][o];
		c.orientation = EDGE_DIRECTIONS2[l][o];
	}

	static final int [][] NEIGHBORS = new int[][] {
		{ 1, 4, 9, 5 },
		{ 2, 0, 5, 6 },
		{ 3, 1, 6, 7 },
		{ 4, 2, 7, 8 },
		{ 0, 3, 8, 9 },
		{ 1, 0, 9, 6 },
		{ 2, 1, 5, 7 },
		{ 3, 2, 6, 8 },
		{ 4, 3, 7, 9 },
		{ 0, 4, 8, 5 }
		};

	public static Point3d fromPolar(double lgt, double lat)
	{
		double zz = Math.cos(lat);
		return new Point3d(
			Math.cos(lgt) * zz,
			Math.sin(lgt) * zz,
			Math.sin(lat)
			);
	}

	static final int[][] EDGES = new int[][] {
		{ 0, 4, 9, 10 },
		{ 1, 0, 5, 11 },
		{ 2, 1, 6, 12 },
		{ 3, 2, 7, 13 },
		{ 4, 3, 8, 14 },
		{ 5, 10, 19, 15 },
		{ 6, 11, 15, 16 },
		{ 7, 12, 16, 17 },
		{ 8, 13, 17, 18 },
		{ 9, 14, 18, 19 }
		};
	static final Cursor [] EDGE_CURSORS = new Cursor[] {
		new Cursor(0, NORTHEAST),
		new Cursor(1, NORTHEAST),
		new Cursor(2, NORTHEAST),
		new Cursor(3, NORTHEAST),
		new Cursor(4, NORTHEAST),
		new Cursor(5, NORTHEAST),
		new Cursor(6, NORTHEAST),
		new Cursor(7, NORTHEAST),
		new Cursor(8, NORTHEAST),
		new Cursor(9, NORTHEAST),
		new Cursor(0, SOUTHEAST),
		new Cursor(1, SOUTHEAST),
		new Cursor(2, SOUTHEAST),
		new Cursor(3, SOUTHEAST),
		new Cursor(4, SOUTHEAST),
		new Cursor(5, SOUTHEAST),
		new Cursor(6, SOUTHEAST),
		new Cursor(7, SOUTHEAST),
		new Cursor(8, SOUTHEAST),
		new Cursor(9, SOUTHEAST)
		};

	static final int[][] EDGE_DIRECTIONS2 = new int[][] {
		{ NORTHWEST, NORTHEAST, NORTHEAST, NORTHWEST },
		{ NORTHWEST, NORTHEAST, NORTHEAST, NORTHWEST },
		{ NORTHWEST, NORTHEAST, NORTHEAST, NORTHWEST },
		{ NORTHWEST, NORTHEAST, NORTHEAST, NORTHWEST },
		{ NORTHWEST, NORTHEAST, NORTHEAST, NORTHWEST },
		{ SOUTHWEST, SOUTHEAST, SOUTHEAST, SOUTHWEST },
		{ SOUTHWEST, SOUTHEAST, SOUTHEAST, SOUTHWEST },
		{ SOUTHWEST, SOUTHEAST, SOUTHEAST, SOUTHWEST },
		{ SOUTHWEST, SOUTHEAST, SOUTHEAST, SOUTHWEST },
		{ SOUTHWEST, SOUTHEAST, SOUTHEAST, SOUTHWEST }
		};

	static final int[][] VERTICES = new int[][] {
		{ 1, 0, 5, 6 },
		{ 2, 0, 1, 7 },
		{ 3, 0, 2, 8 },
		{ 4, 0, 3, 9 },
		{ 5, 0, 4, 10 },
		{ 7, 1, 6, 11 },
		{ 8, 2, 7, 11 },
		{ 9, 3, 8, 11 },
		{ 10, 4, 9, 11 },
		{ 6, 5, 10, 11 }
		};
	static final Cursor[] VERTEX_CURSORS = new Cursor[] {
		new Cursor(0, 1),
		new Cursor(0, 0),
		new Cursor(1, 0),
		new Cursor(2, 0),
		new Cursor(3, 0),
		new Cursor(4, 0),
		new Cursor(5, 2),
		new Cursor(6, 2),
		new Cursor(7, 2),
		new Cursor(8, 2),
		new Cursor(9, 2),
		new Cursor(9, 3)
		};

	static final double ATAN12 = Math.atan(0.5);
	static final Point3d [] POINTS = new Point3d[] {
		new Point3d(0,0,1), //north pole
		fromPolar(-(Math.PI * 2.0/5.0 * 0.5), ATAN12),
		fromPolar(-(Math.PI * 2.0/5.0 * 1.5), ATAN12),
		fromPolar(-(Math.PI * 2.0/5.0 * 2.5), ATAN12),
		fromPolar(-(Math.PI * 2.0/5.0 * 3.5), ATAN12),
		fromPolar(-(Math.PI * 2.0/5.0 * 4.5), ATAN12),
		fromPolar(-(Math.PI * 2.0/5.0 * 0), -ATAN12),
		fromPolar(-(Math.PI * 2.0/5.0 * 1), -ATAN12),
		fromPolar(-(Math.PI * 2.0/5.0 * 2), -ATAN12),
		fromPolar(-(Math.PI * 2.0/5.0 * 3), -ATAN12),
		fromPolar(-(Math.PI * 2.0/5.0 * 4), -ATAN12),
		new Point3d(0,0,-1) //south pole
		};

	Point3d [] getPoints(int cell)
	{
		Point3d [] pp = new Point3d[4];
		for (int i = 0; i < 4; i++) {
			pp[i] = POINTS[VERTICES[cell][i]];
		}
		return pp;
	}

	public int getEdge(Cursor c)
	{
		assert c.location >= 0 && c.location < 10;
		assert c.orientation >= 0 && c.orientation < 4;
		return EDGES[c.location][c.orientation];
	}

	public int getVertex(Cursor c)
	{
		assert c.location >= 0 && c.location < 10;
		assert c.orientation >= 0 && c.orientation < 4;
		return VERTICES[c.location][c.orientation];
	}

	public Point3d getPoint(Cursor c)
	{
		return getVertexPoint(getVertex(c));
	}

	public Point3d getVertexPoint(int vertex)
	{
		assert vertex >= 0 && vertex < 12;
		return POINTS[vertex];
	}

	public Cursor fromEdge(int edge)
	{
		return EDGE_CURSORS[edge];
	}

	public Cursor fromVertex(int vertex)
	{
		return VERTEX_CURSORS[vertex];
	}
}
