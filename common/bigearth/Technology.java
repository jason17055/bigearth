package bigearth;

import java.util.*;

public final class Technology
{
	String name;
	Set<Technology> prerequisites;
	CommoditiesBag resourceCost;

	private Technology(String name)
	{
		this.name = name;
		this.prerequisites = new HashSet<Technology>();
		this.resourceCost = CommoditiesBag.EMPTY;
	}

	public String name()
	{
		return name;
	}

	@Override
	public String toString()
	{
		return name;
	}

	private static Map<String,Technology> allTechnologies = new HashMap<String,Technology>();

	public static Technology valueOf(String name)
	{
		Technology rv = allTechnologies.get(name);
		if (rv == null)
			throw new IllegalArgumentException("Invalid technology name: "+name);
		return rv;
	}

	static
	{
		addTechGroup("alphabet", 5, 5, CommoditiesBag.EMPTY);

		CommoditiesBag husbandryCost = new CommoditiesBag();
		husbandryCost.add(CommodityType.MEAT, 50);
		addTechGroup("husbandry", 5, 5, husbandryCost);

		CommoditiesBag smeltingCost = new CommoditiesBag();
		smeltingCost.add(CommodityType.COPPER_ORE, 4);
		addTechGroup("smelting", 5, 5, smeltingCost);

		addTechGroup("burial", 5, 5, CommoditiesBag.EMPTY);

		CommoditiesBag masonryCost = new CommoditiesBag();
		masonryCost.add(CommodityType.STONE, 100);
		addTechGroup("masonry", 5, 5, masonryCost);

		CommoditiesBag potteryCost = new CommoditiesBag();
		potteryCost.add(CommodityType.CLAY, 50);
		addTechGroup("pottery", 5, 5, potteryCost);

		CommoditiesBag warriorCodeCost = new CommoditiesBag();
		warriorCodeCost.add(CommodityType.STONE_WEAPON, 20);
		warriorCodeCost.add(CommodityType.WOOD, 1);
		addTechGroup("warrior-code", 5, 5, warriorCodeCost);
	}

	static void addTechGroup(String groupName, int numRows, int numCols, CommoditiesBag occassionalCost)
	{
		for (int r = 0; r < 5; r++)
		{
			for (int c = 0; c < 5; c++)
			{
				int j = r*5+c+1;
				String techId = groupName + "-" + j;
				Technology tech = new Technology(techId);
				if (r % 2 == 1)
				{
					if (c < 5)
					{
						int k = (r-1)*5 + c + 1;
						String parentTech = groupName + "-" + k;
						tech.prerequisites.add(valueOf(parentTech));
					}

					if (c + 1 < 5)
					{
						int k = (r-1)*5 + c + 2;
						String parentTech = groupName + "-" + k;
						tech.prerequisites.add(valueOf(parentTech));
					}
				}
				else if (r > 0)
				{
					if (c >= 0)
					{
						int k = (r-1)*5 + c + 1;
						String parentTech = groupName + "-" + k;
						tech.prerequisites.add(valueOf(parentTech));
					}
					if (c - 1 >= 0)
					{
						int k = (r-1)*5 + c + 0;
						String parentTech = groupName + "-" + k;
						tech.prerequisites.add(valueOf(parentTech));
					}
				}

				if (j%2 == 1)
				{
					tech.resourceCost = occassionalCost;
				}

				registerTechnology(tech);
			}
		}
	}

	static void registerTechnology(Technology tech)
	{
		assert !allTechnologies.containsKey(tech.name);
		allTechnologies.put(tech.name, tech);
	}

	public int hashCode()
	{
		return name.hashCode();
	}
}
