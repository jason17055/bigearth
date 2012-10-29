package bigearth;

public interface Geometry
{
	int getCellCount();
	int [] getNeighbors(int cellId);

	interface EdgeId
	{
		int [] getAdjacentCells();
		VertexId [] getEndpoints();
	};

	interface VertexId
	{
		int [] getAdjacentCells();

		//not sure if this is needed
		//EdgeId [] getAdjacentEdges();

		//not sure if this is needed
		//VertexId [] getNearbyVertices();
	};

	EdgeId getEdgeBetween(int cell1, int cell2);
	EdgeId [] getSurroundingEdges(int cellId);
	VertexId [] getSurroundingVertices(int cellId);
}
