package bigearth;

public interface Geometry
{
	int getCellCount();
	int [] getNeighbors(int cellId);
}
