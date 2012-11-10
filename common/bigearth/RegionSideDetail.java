package bigearth;

public class RegionSideDetail
{
	public static enum SideFeature
	{
		NONE,
		BROOK,
		CREEK,
		RIVER;

		public boolean isRiver()
		{
			return this.ordinal() == BROOK.ordinal() ||
				this.ordinal() == CREEK.ordinal() ||
				this.ordinal() == RIVER.ordinal();
		}
	}

	SideFeature feature;

	public RegionSideDetail()
	{
		this.feature = SideFeature.NONE;
	}
}
