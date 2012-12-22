package bigearth;

public enum CommodityType
{
	WOOD(500.0, 0, false),
	CLAY(25.0, 0, false),
	STONE(25.0, 0, false),
	MEAT(1.0, 1, false),
	GRAIN(1.0, 1, false),
	SHEEP(80.0, 0, true),   // TODO 80kg sheep gives 20kg meat
	PIG(70.0, 0, true),     // TODO 70kg pig gives 35kg meat
	CATTLE(750.0, 0, true), // TODO 750kg cow gives 250kg meat
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
