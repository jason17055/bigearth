package bigearth;

import javax.vecmath.*;

class RegionDetail
{
	int numSides;
	int [] terrains;

	RegionDetail(int numSides)
	{
		this.numSides = numSides;
		this.terrains = new int[numSides * 16];
	}
}
