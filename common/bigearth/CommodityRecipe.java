package bigearth;

public enum CommodityRecipe
{
	STONE_TO_STONE_BLOCK,
	STONE_TO_STONE_WEAPON;

	public CommodityType getOutputCommodity()
	{
		switch(this)
		{
		case STONE_TO_STONE_BLOCK:
			return CommodityType.STONE_BLOCK;
		case STONE_TO_STONE_WEAPON:
			return CommodityType.STONE_WEAPON;
		default:
			assert false;
			return null;
		}
	}
}
