package bigearth.geom;

import javax.vecmath.*;

/**
 * Represents the structure of regions in the world.
 */
public interface Geometry
{
	static final int TURN_LEFT = 1;
	static final int TURN_RIGHT = -1;

	/** Returns the number of faces in this geometry. */
	int getFaceCount();

	/** Returns the number of edges (the sides between faces) in this geometry. */
	int getEdgeCount();

	/**
	 * Returns the number of vertices (the corners between faces)
	 * in this geometry. Vertices are numbered 0 through n-1.
	 */
	int getVertexCount();

	/**
	 * Modifies the given cursor so that it refers to a
	 * different edge/vertex on the face designated by the
	 * cursor.
	 * @param adjust positive to turn left, negative to turn right.
	 */
	void rotateCursor(Cursor c, int adjust);

	/**
	 * Modifies the given cursor so that it refers to the face
	 * sharing the edge designated by the cursor with the face
	 * designated by the cursor.
	 * The edge designated by the cursor is not changed.
	 */
	void stepCursor(Cursor c);

	/**
	 * Gets the edge number for the edge currently selected
	 * by the cursor.
	 * @return an integer between 0 and m-1
	 */
	int getEdge(Cursor c);

	/**
	 * Gets the vertex number for the vertex currently selected by the
	 * cursor.
	 * @return an integer between 0 and k-1
	 */
	int getVertex(Cursor c);

	Point3d getPoint(Cursor c);
	Point3d getVertexPoint(int vertex);

	/**
	 * Returns a cursor for which getEdge will return the specified edge.
	 */
	Cursor fromEdge(int edge);

	/**
	 * Returns a cursor for which getVertex will return the specified
	 * vertex.
	 */
	Cursor fromVertex(int vertex);
}
