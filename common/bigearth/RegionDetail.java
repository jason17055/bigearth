package bigearth;

import javax.vecmath.*;

class RegionDetail
{
	int numSides;
	int [] terrains;
	Point3d regionCenterPoint;
	Point3d [] regionBoundary;

	RegionDetail(int numSides)
	{
		this.numSides = numSides;
		this.terrains = new int[numSides * 4];
	}

	Point3d [] getTerrainBoundary(int terrainId)
	{
		assert(terrainId >= 0 && terrainId < numSides);

		return new Point3d[] {
			regionCenterPoint,
			regionBoundary[terrainId],
			regionBoundary[(terrainId + 1) % numSides]
			};
	}
}
