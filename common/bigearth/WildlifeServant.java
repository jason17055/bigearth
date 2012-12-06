package bigearth;

class WildlifeServant
{
	transient RegionServant parentRegion;
	transient WorldMaster world;

	int wildlife;
	int wildlifeHunted;
	int wildlifeBirths;
	int wildlifeDeaths;
	int wildlifeEmigrants;
	int wildlifeImmigrants;

	static final double WILDLIFE_LIFESPAN = 5.0;
	static final double WILDLIFE_EMIGRATION_RATE = 0.25;

	static final int WILDLIFE_PREFERRED_TEMPERATURE = 240;
	static final int WILDLIFE_TEMPERATURE_TOLERANCE = 80;

	WildlifeServant(RegionServant parentRegion)
	{
		this.parentRegion = parentRegion;
		this.world = parentRegion.world;
	}

	void adjust(int delta)
	{
		this.wildlife += delta;
		if (wildlife < 0)
			wildlife = 0;
	}

	static double Randomizer(double x)
	{
		return x * MyRandomVariateGenerator.next();
	}

	/**
	 * @param p is the proportion of the max-population-density
	 *   that the population is currently experiencing.
	 * @return birth rate, expressed as the portion of the current
	 *   population that will spawn new life
	 */
	static double getBasicBirthRate(double p, double r)
	{
// cubic function that intersects (0,0), (0.1,0), and (1.0,0)
// y=(x-0)(x-0.1)(x-1)

		double v = r - (p-0)*(p-0.1)*(p-1);  //magic
		return v > 0 ? v : 0;
	}

	void doWildlifeMaintenance_stage1()
	{
		final double wildlifeQuota = parentRegion.getBiome().getWildlifeQuota();
		double biomeTolerance = 1.0 - Math.pow((WILDLIFE_PREFERRED_TEMPERATURE - parentRegion.temperature) / WILDLIFE_TEMPERATURE_TOLERANCE, 2.0);
		assert biomeTolerance <= 1.0;

		double quotaPortion = ((double)wildlife) / wildlifeQuota;
		double birthRate = getBasicBirthRate(quotaPortion, 1/WILDLIFE_LIFESPAN);
		wildlifeBirths = (int) Math.round(Randomizer(wildlife * birthRate));

		assert wildlifeBirths >= 0;

		double deathRate = 1.0 / WILDLIFE_LIFESPAN;
		if (biomeTolerance < 0.0)
		{
			deathRate *= Math.exp(-biomeTolerance);
		}
		int deaths = (int) Math.round(Randomizer(wildlife * deathRate));
		wildlifeDeaths = wildlifeHunted > deaths ? 0 :
			deaths > wildlife ? wildlife - wildlifeHunted :
			deaths - wildlifeHunted;

		int adjustedCount = wildlife - (wildlifeHunted + wildlifeDeaths);
		adjustedCount = Math.max(0, adjustedCount);

		quotaPortion = ((double)adjustedCount) / wildlifeQuota;
		double emigrantPortion = WILDLIFE_EMIGRATION_RATE
			* (0.5 - Math.cos(quotaPortion * Math.PI)/2);
		double eligibleEmigrants = emigrantPortion * adjustedCount;
		wildlifeEmigrants = 0;

		int [] nn = world.getRegionNeighbors(parentRegion.regionId);
		for (int n : nn)
		{
			ShadowRegion neighborRegion = world.getShadowRegion(n);
			BiomeType neighborBiome = neighborRegion.getBiome();

			double emigrants = eligibleEmigrants / nn.length;
			if (neighborBiome == BiomeType.OCEAN)
				emigrants = 0;

			int emigrantsI = (int) Math.round(Randomizer(emigrants));
			wildlifeEmigrants += emigrantsI;
			neighborRegion.importWildlife(emigrantsI);
		}
	}

	void doWildlifeMaintenance_cleanup()
	{
if (false)
{
	System.out.println("Region "+parentRegion.regionId);
	System.out.printf("beginning balance :%8d\n", wildlife);
	System.out.printf("           births :%8d\n", wildlifeBirths);
	System.out.printf("           deaths :%8d\n", wildlifeDeaths);
	System.out.printf("           hunted :%8d\n", wildlifeHunted);
	System.out.printf("       immigrants :%8d\n", wildlifeImmigrants);
	System.out.printf("        emigrants :%8d\n", wildlifeEmigrants);
	System.out.println();
}
		wildlife += wildlifeBirths - wildlifeDeaths - wildlifeHunted
			+ wildlifeImmigrants - wildlifeEmigrants;
		wildlifeImmigrants = 0;
	}

	/**
	 * Determine ease of finding a wild animal for game, given
	 * a particular number of hunters.
	 *
	 * @param numWorkers how many workers are assigned to hunting
	 * @return the expected number of animals/year harvested by
	 *     the given number of hunters.
	 */
	double calculateHuntingRate(int numWorkers)
	{
		if (wildlife == 0)
			return 0.0;

		double s = 0.5 * Math.sqrt(wildlife / 400.0);
		return wildlife - wildlife * Math.exp(-s * numWorkers / (double) wildlife);
	}
}
