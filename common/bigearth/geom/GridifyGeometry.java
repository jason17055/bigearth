package bigearth.geom;

import javax.vecmath.*;
import static bigearth.geom.Decahedron.*;

/**
 * Subdivides each face of a geometry into a grid of smaller faces.
 * Grid must be square, so that the parent geometry can put a top
 * adjacent to a side, etc.
 */
public class GridifyGeometry implements Geometry
{
	final Geometry majorShape;
	final int size;

	/**
	 * @param size the number of divisions
	 */
	public GridifyGeometry(Geometry majorShape, int size)
	{
		assert majorShape != null;
		assert size >= 0;

		this.majorShape = majorShape;
		this.size = size;
	}

	//implements Geometry
	public int getFaceCount()
	{
		return majorShape.getFaceCount()*(size+1)*(size+1);
	}

	public int getEdgeCount()
	{
		int majEdges = majorShape.getEdgeCount() * (size+1);
		int intEdges = majorShape.getFaceCount() * interiorEdgesPerMajorFace();
		return majEdges + intEdges;
	}

	public int getVertexCount()
	{
		int majVertices = majorShape.getVertexCount();
		int edgVertices = majorShape.getEdgeCount() * size;
		int intVertices = majorShape.getFaceCount() * size*size;
		return majVertices + edgVertices + intVertices;
	}

	static class Components
	{
		Cursor c;
		int x;
		int y;
	}

	void compose(Cursor rv, Cursor c, int x, int y)
	{
		rv.location = (y*(size+1)+x)*majorShape.getFaceCount() + c.location;
		rv.orientation = c.orientation;
	}

	Components decompose(Cursor c)
	{
		final int msize = majorShape.getFaceCount();
		Components m = new Components();
		m.c = new Cursor(c.location % msize, c.orientation);
		m.x = (c.location / msize) % (size + 1);
		m.y = (c.location / msize) / (size + 1);
		return m;
	}

	public void rotateCursor(Cursor c, int adjust)
	{
		majorShape.rotateCursor(c, adjust);
	}

	private void stepCursor_acrossMajEdge(Cursor c, Cursor mc, int i)
	{
		majorShape.stepCursor(mc);
		switch (mc.orientation) {
		case NORTHEAST:
			compose(c, mc, i, size);
			return;
		case NORTHWEST:
			compose(c, mc, 0, i);
			return;
		case SOUTHWEST:
			compose(c, mc, size-i, 0);
			return;
		case SOUTHEAST:
			compose(c, mc, size, size-i);
			return;
		}
		throw new Error("invalid orientation: " + mc.orientation);
	}

	public void stepCursor(Cursor c)
	{
		Components m = decompose(c);
		switch (m.c.orientation) {
		case NORTHEAST:

			if (m.y >= size) {
				stepCursor_acrossMajEdge(c, m.c, size-m.x);
			}
			else {
				compose(c, m.c, m.x, m.y + 1);
				c.orientation = SOUTHWEST;
			}
			return;

		case NORTHWEST:

			if (m.x <= 0) {
				stepCursor_acrossMajEdge(c, m.c, size-m.y);
			}
			else {
				compose(c, m.c, m.x-1, m.y);
				c.orientation = SOUTHEAST;
			}
			return;

		case SOUTHWEST:

			if (m.y <= 0) {
				stepCursor_acrossMajEdge(c, m.c, m.x);
			}
			else {
				compose(c, m.c, m.x, m.y-1);
				c.orientation = NORTHEAST;
			}
			return;

		case SOUTHEAST:

			if (m.x >= size) {
				stepCursor_acrossMajEdge(c, m.c, m.y);
			}
			else {
				compose(c, m.c, m.x+1, m.y);
				c.orientation = NORTHWEST;
			}
			return;
		}

		throw new Error("Invalid orientation");
	}

	Point3d getPoint(int m, int x, int y)
	{
		Point3d E = POINTS[VERTICES[m][0]];
		Point3d N = POINTS[VERTICES[m][1]];
		Point3d W = POINTS[VERTICES[m][2]];
		Point3d S = POINTS[VERTICES[m][3]];

		Vector3d W_to_N = new Vector3d(N);
		W_to_N.sub(W);
		W_to_N.scale((double)y / (double)(size+1));

		Vector3d S_to_E = new Vector3d(E);
		S_to_E.sub(S);
		S_to_E.scale((double)y / (double)(size+1));

		Vector3d NW_to_SE = new Vector3d(S);
		NW_to_SE.add(S_to_E);
		NW_to_SE.sub(W);
		NW_to_SE.sub(W_to_N);
		NW_to_SE.scale((double)x / (double)(size+1));

		Vector3d rv = new Vector3d(W);
		rv.add(W_to_N);
		rv.add(NW_to_SE);
		rv.normalize();

		return new Point3d(rv);
	}

	//implements Geometry
	public Point3d getPoint(Cursor c)
	{
		Components m = decompose(c);
		int o = m.c.orientation;

		return getPoint(m.c.location,
			m.x + (o == 0 || o == 3 ? 1 : 0),
			m.y + (o == 0 || o == 1 ? 1 : 0));
	}

	private int interiorEdgesPerMajorFace()
	{
		return size * 2 * (size+1);
	}

	private int _edgeOnMajorEdge(int majEdge, int majFace, int i)
	{
		boolean reversed = majorShape.fromEdge(majEdge).location != majFace;
		return majEdge * (size+1) + (reversed ? size-i : i);
	}

	//implements Geometry
	public int getEdge(Cursor c)
	{
		Components m = decompose(c);
		int o = m.c.orientation;
		if (o == NORTHEAST || o == SOUTHWEST) {
			if (o == NORTHEAST) {
				m.y++;
			}
			if (m.y == 0) {
				// an edge along the major edge
				assert o == SOUTHWEST;
				int majEdge = majorShape.getEdge(m.c);
				return _edgeOnMajorEdge(majEdge, m.c.location, m.x);
			}
			else if (m.y == size + 1) {
				// an edge along the major edge
				assert o == NORTHEAST;
				int majEdge = majorShape.getEdge(m.c);
				return _edgeOnMajorEdge(majEdge, m.c.location, size-m.x);
			}
			else {
				// an interior edge
				return majorShape.getEdgeCount() * (size+1)
				+ m.c.location * interiorEdgesPerMajorFace()
				+ 2 * ((m.y-1) * (size+1) + m.x);
			}
		}
		else {
			if (o == SOUTHEAST) {
				m.x++;
			}
			if (m.x == 0) {
				// an edge along the major edge
				assert o == NORTHWEST;
				int majEdge = majorShape.getEdge(m.c);
				return _edgeOnMajorEdge(majEdge, m.c.location, size-m.y);
			}
			else if (m.x == size + 1) {
				// an edge along the major edge
				assert o == SOUTHEAST;
				int majEdge = majorShape.getEdge(m.c);
				return _edgeOnMajorEdge(majEdge, m.c.location, m.y);
			}
			else {
				// an interior edge
				return majorShape.getEdgeCount() * (size+1)
				+ m.c.location * interiorEdgesPerMajorFace()
				+ 2 * ((m.x-1) * (size+1) + m.y) + 1;
			}
		}
	}

	private Cursor fromEdge_edgEdge(int majEdge, int i)
	{
		assert i >= 0 && i <= size;

		Cursor rv = new Cursor();
		Cursor mc = majorShape.fromEdge(majEdge);
		switch (mc.orientation) {
		case NORTHEAST:
			compose(rv, mc, size-i, size);
			return rv;
		case NORTHWEST:
			compose(rv, mc, 0, size-i);
			return rv;
		case SOUTHWEST:
			compose(rv, mc, i, 0);
			return rv;
		case SOUTHEAST:
			compose(rv, mc, size, i);
			return rv;
		}
		throw new Error("Invalid orientation");
	}

	public Cursor fromEdge(int edge)
	{
		int majEdges = majorShape.getEdgeCount();
		if (edge < majEdges * (size+1)) {
			// an edge along a major edge
			int majEdge = edge / (size+1);
			int i = edge % (size+1);
			return fromEdge_edgEdge(majEdge, i);
		}

		Cursor rv = new Cursor();

		edge -= majEdges * (size+1);
		int majFace = edge / interiorEdgesPerMajorFace();
		edge = edge % interiorEdgesPerMajorFace();
		int i = edge / (2 * (size+1));
		edge = edge % (2 * (size+1));
		int j = edge / 2;
		int k = edge % 2;

		if (k == 0) {
			// interior edge, northeast of some cell
			int y = i;
			int x = j;
			compose(rv, new Cursor(majFace, NORTHEAST), x, y);
			return rv;
		}
		else {
			assert k == 1;
			// interior edge, southeast of some cell
			int x = i;
			int y = j;
			compose(rv, new Cursor(majFace, SOUTHEAST), x, y);
			return rv;
		}
	}

	//implements Geometry
	public int getVertex(Cursor c)
	{
		Components m = decompose(c);
		int o = m.c.orientation;
		if (o == 0 || o == 3) {
			m.x++;
		}
		if (o == 0 || o == 1) {
			m.y++;
		}

		if (m.x == 0) {
			if (m.y == 0) {
				// major vertex
				assert o == 2; //west
				return majorShape.getVertex(m.c);
			}
			else if (m.y == size+1) {
				// major vertex
				assert o == 1; //north
				return majorShape.getVertex(m.c);
			}
			else {
				// vertex along a major edge
				m.c.orientation = NORTHWEST;
				return _vertexOnMajorEdge(m.c, size+1-m.y);
			}
		}
		else if (m.x == size+1) {
			if (m.y == 0) {
				// major vertex
				assert o == 3; //south
				return majorShape.getVertex(m.c);
			}
			else if (m.y == size+1) {
				// major vertex
				assert o == 0; //east
				return majorShape.getVertex(m.c);
			}
			else {
				// vertex along a major edge
				m.c.orientation = SOUTHEAST;
				return _vertexOnMajorEdge(m.c, m.y);
			}
		}
		else if (m.y == 0) {
			assert m.x > 0 && m.x <= size;
			m.c.orientation = SOUTHWEST;
			return _vertexOnMajorEdge(m.c, m.x);
		}
		else if (m.y == size+1) {
			assert m.x > 0 && m.x <= size;
			m.c.orientation = NORTHEAST;
			return _vertexOnMajorEdge(m.c, size+1-m.x);
		}
		else {
			// internal vertex
			assert m.x > 0 && m.x <= size;
			assert m.y > 0 && m.y <= size;
			return _vertexInternal(m.c.location, m.x, m.y);
		}
	}

	private int _vertexOnMajorEdge(Cursor mc, int i)
	{
		int majEdge = majorShape.getEdge(mc);
		boolean natural = majorShape.fromEdge(majEdge).location == mc.location;

		assert i >= 1 && i <= size;
		if (!natural) {
			i = (size+1)-i;
		}

		return majorShape.getVertexCount() +
			size * majEdge +
			(i-1);
	}

	private int _vertexInternal(int majFace, int x, int y)
	{
		assert x >= 1 && x <= size;
		assert y >= 1 && y <= size;
		return majorShape.getVertexCount() +
			size * majorShape.getEdgeCount() +
			size * size * majFace +
			size * (y-1) +
			(x-1);
	}

	public Cursor fromVertex(int vertex)
	{
		Cursor rv = new Cursor();

		int majVertices = majorShape.getVertexCount();
		if (vertex < majVertices) {
			// a major vertex
			return fromVertex_majVertex(vertex);
		}

		vertex -= majVertices;
		int majEdges = majorShape.getEdgeCount();
		if (vertex < majEdges * size) {
			// a vertex on a major edge
			return fromVertex_edgVertex(vertex/size, vertex%size);
		}

		vertex -= majEdges * size;
		int majFace = vertex / (size*size);
		vertex = vertex % (size*size);

		int y = (vertex / size)+1;
		int x = (vertex % size)+1;
		return fromVertex_intVertex(majFace, x, y);
	}

	private Cursor fromVertex_majVertex(int vertex)
	{
		Cursor mc = majorShape.fromVertex(vertex);
		Cursor rv = new Cursor();
		switch (mc.orientation) {
		case 0:
			compose(rv, mc, size, size);
			return rv;
		case 1:
			compose(rv, mc, 0, size);
			return rv;
		case 2:
			compose(rv, mc, 0, 0);
			return rv;
		case 3:
			compose(rv, mc, size, 0);
			return rv;
		}
		throw new Error("invalid orientation: "+mc.orientation);
	}

	private Cursor fromVertex_edgVertex(int majEdge, int i)
	{
		assert i >= 0 && i < size;

		return fromEdge_edgEdge(majEdge, i+1);
	}

	private Cursor fromVertex_intVertex(int majFace, int x, int y)
	{
		assert x >= 1 && x <= size;
		assert y >= 1 && y <= size;

		Cursor rv = new Cursor();
		compose(rv, new Cursor(majFace, 0), x-1, y-1);
		return rv;
	}

	public Point3d getVertexPoint(int vertex)
	{
		Cursor c = fromVertex(vertex);
		return getPoint(c);
	}
}
