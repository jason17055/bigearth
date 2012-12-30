package bigearth;

import java.net.URL;

public enum CommodityType
{
	WOOD(500.0, 0, false),
	CLAY(25.0, 0, false),
	STONE(25.0, 0, false),
	COPPER_ORE(25.0, 0, false),
	STONE_BLOCK(20.0, 0, false),
	STONE_WEAPON(2.5, 0, false),
	MEAT(1.0, 1, false),
	GRAIN(1.0, 1, false),
	SHEEP(80.0, 0, true),   // TODO 80kg sheep gives 20kg meat
	PIG(70.0, 0, true),     // TODO 70kg pig gives 35kg meat
	CATTLE(750.0, 0, true), // TODO 750kg cow gives 250kg meat
	HORSE(650.0, 0, true),
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

	public String getDisplayName()
	{
		String n = name();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n.length(); i++)
		{
			char c = n.charAt(i);
			sb.append(
				i == 0 ? c :
				c == '_' ? ' ' :
				Character.toLowerCase(c)
				);
		}
		return sb.toString();
	}

	public URL getIconResource()
	{
		String n = name();
		n = n.toLowerCase();
		n = n.replace('_', '-');
		return CommodityType.class.getResource("/commodity_images/"+n+".png");
	}
}
