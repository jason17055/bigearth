package bigearth;

public class RegionCornerDetail
{
	public static enum PointFeature
	{
		NONE,
		LAKE;
	}

	PointFeature feature;

	public RegionCornerDetail()
	{
		this.feature = PointFeature.NONE;
	}
}
