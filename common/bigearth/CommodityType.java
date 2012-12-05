package bigearth;

public enum CommodityType
{
	MEAT(50.0);

	public double mass;
	private CommodityType(double mass)
	{
		this.mass = mass;
	}
}
