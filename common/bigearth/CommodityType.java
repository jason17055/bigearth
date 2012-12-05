package bigearth;

public enum CommodityType
{
	MEAT(50.0, 800);

	public double mass;
	public int nutrition;

	private CommodityType(double mass, int nutrition)
	{
		this.mass = mass;
		this.nutrition = nutrition;
	}
}
