package bigearth;

public enum CommodityType
{
	WOOD(500.0, 0, false),
	MEAT(50.0, 800, false),
	GRAIN(100.0, 1000, false),
	SHEEP(80.0, 0, true),
	PIG(60.0, 0, true),
	WILDLIFE(50.0, 0, true); //cannot be captured, only killed for meat

	public double mass;
	public int nutrition;
	public boolean isLivestock;

	private CommodityType(double mass, int nutrition, boolean isLivestock)
	{
		this.mass = mass;
		this.nutrition = nutrition;
		this.isLivestock = isLivestock;
	}
}
