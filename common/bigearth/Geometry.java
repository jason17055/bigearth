package bigearth;

import javax.vecmath.Point3d;

public interface Geometry
{
	int getCellCount();

	int getNeighborCount(int cellId);

	/**
	 * Returns an array of cells neighboring the given cell.
	 * The neighbors are listed in counter-clockwise order.
	 */
	int [] getNeighbors(int cellId);

	interface EdgeId extends Location
	{
		int [] getAdjacentCells();
		VertexId [] getEndpoints();
	};

	interface VertexId extends Location
	{
		int [] getAdjacentCells();

		//not sure if this is needed
		//EdgeId [] getAdjacentEdges();

		//not sure if this is needed
		//VertexId [] getNearbyVertices();
	};

	EdgeId getEdgeBetween(int cell1, int cell2);
	EdgeId getEdgeByEndpoints(VertexId fromVertex, VertexId toVertex);

	EdgeId [] getSurroundingEdges(int cellId);

	VertexId getVertex(int cell1, int cell2, int cell3);
	VertexId [] getSurroundingVertices(int cellId);
	VertexId [] getNearbyVertices(VertexId vertex);

	Point3d getCenterPoint(int cellId);
	int findCell(Point3d pt);

	Point3d [] getCellBoundary(int cellId);
}
