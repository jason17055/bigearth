package bigearth;

import javax.vecmath.*;

class RegionDetail
{
	int numSides;
	char [] terrains;

	RegionDetail(int numSides)
	{
		this.numSides = numSides;
		this.terrains = new char[numSides * 16];
	}

	public void setTerrainType(int terrainId, TerrainType type)
	{
		terrains[terrainId] = type.id;
	}
}
