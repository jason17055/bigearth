package bigearth;

import java.io.*;
import java.util.*;
import com.fasterxml.jackson.core.*;

class WildlifeServant
	implements EndOfYear
{
	transient RegionServant parentRegion;
	transient WorldMaster world;

	static class Wildlife
	{
		int quantity;  // at start of game-year
		int hunted;
		int immigrants;

		// records numbers from prior game-year
		int births;
		int deaths;
		int emigrants;
	}
	Map<CommodityType, Wildlife> wildlifeByType;

	static final double WILDLIFE_LIFESPAN = 5.0;
	static final double WILDLIFE_EMIGRATION_RATE = 0.25;

	static final int WILDLIFE_PREFERRED_TEMPERATURE = 240;
	static final int WILDLIFE_TEMPERATURE_TOLERANCE = 80;

	WildlifeServant(RegionServant parentRegion)
	{
		this.parentRegion = parentRegion;
		this.world = parentRegion.world;
		this.wildlifeByType = new HashMap<CommodityType, Wildlife>();
	}

	// called when server is starting up
	public void start()
	{
		//nothing to do
	}

	void adjustWildlife(CommodityType type, int delta)
	{
		if (!wildlifeByType.containsKey(type))
			wildlifeByType.put(type, new Wildlife());
		wildlifeByType.get(type).immigrants += delta;
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

	//implements EndOfYear
	public void endOfYear_stage1()
	{
		for (CommodityType type : wildlifeByType.keySet())
		{
			endOfYear_oneSpecies(type, wildlifeByType.get(type));
		}
	}

	private double getWildlifeQuota(CommodityType type, BiomeType biome)
	{
		if (type == CommodityType.SHEEP)
		{
			switch (biome)
			{
			case GRASSLAND: return 1000;
			case FOREST: return 100;
			case SWAMP: return 50;
			case JUNGLE: return 100;
			case MOUNTAIN: return 200;
			case HILLS: return 500;
			case PLAINS: return 1000;
			default: return 0;
			}
		}
		else if (type == CommodityType.PIG)
		{
			switch (biome)
			{
			case GRASSLAND: return 750;
			case FOREST: return 1000;
			case SWAMP: return 200;
			case JUNGLE: return 800;
			case MOUNTAIN: return 50;
			case HILLS: return 500;
			case PLAINS: return 500;
			default: return 0;
			}
		}
		else
		{
			switch (biome)
			{
			case GLACIER: return 100;
			case DESERT: return 200;
			case TUNDRA: return 150;
			case MOUNTAIN: return 300;
			case PLAINS: return 500;
			case HILLS: return 500;
			case GRASSLAND: return 750;
			case FOREST: return 1000;
			case SWAMP: return 1000;
			case JUNGLE: return 1000;
			case OCEAN: return 1000;
			default: return 100;
			}
		}
	}

	private void endOfYear_oneSpecies(CommodityType type, Wildlife w)
	{
		double wildlifeQuota = getWildlifeQuota(type, parentRegion.getBiome());
		double biomeTolerance = 1.0 - Math.pow((WILDLIFE_PREFERRED_TEMPERATURE - parentRegion.temperature) / WILDLIFE_TEMPERATURE_TOLERANCE, 2.0);
		assert biomeTolerance <= 1.0;

		double quotaPortion = ((double)w.quantity) / wildlifeQuota;
		double birthRate = getBasicBirthRate(quotaPortion, 1/WILDLIFE_LIFESPAN);
		w.births = (int) Math.round(Randomizer(w.quantity * birthRate));

		assert w.births >= 0;

		double deathRate = 1.0 / WILDLIFE_LIFESPAN;
		if (biomeTolerance < 0.0)
		{
			deathRate *= Math.exp(-biomeTolerance);
		}
		int deaths = (int) Math.round(Randomizer(w.quantity * deathRate));
		w.deaths = w.hunted > deaths ? 0 :
			deaths > w.quantity ? w.quantity - w.hunted :
			deaths - w.hunted;

		int adjustedCount = w.quantity - (w.hunted + w.deaths);
		adjustedCount = Math.max(0, adjustedCount);

		quotaPortion = ((double)adjustedCount) / wildlifeQuota;
		double emigrantPortion = WILDLIFE_EMIGRATION_RATE
			* (0.5 - Math.cos(quotaPortion * Math.PI)/2);
		double eligibleEmigrants = emigrantPortion * adjustedCount;
		w.emigrants = 0;

		int [] nn = world.getRegionNeighbors(parentRegion.regionId);
		for (int n : nn)
		{
			ShadowRegion neighborRegion = world.getShadowRegion(n);
			BiomeType neighborBiome = neighborRegion.getBiome();

			double emigrants = eligibleEmigrants / nn.length;
			if (neighborBiome.isWater())
				emigrants = 0;

			int emigrantsI = (int) Math.round(Randomizer(emigrants));
			w.emigrants += emigrantsI;
			neighborRegion.importWildlife(type, emigrantsI);
		}
	}

	//implements EndOfYear
	public void endOfYear_cleanup()
	{
		for (Iterator<Map.Entry<CommodityType, Wildlife> > it = wildlifeByType.entrySet().iterator();
				it.hasNext(); )
		{
			Map.Entry<CommodityType, Wildlife> e = it.next();
			Wildlife w = e.getValue();
			w.quantity += w.births - w.deaths - w.hunted
				+ w.immigrants - w.emigrants;
			if (w.quantity > 0)
			{
				// clear for next cycle
				w.hunted = 0;
				w.immigrants = 0;
			}
			else
			{
				it.remove();
			}
		}
	}

	/**
	 * Determine ease of finding a wild animal for game, given
	 * a particular number of hunters.
	 *
	 * @param numWorkers how many workers are assigned to hunting
	 * @return the expected number of animals/year harvested by
	 *     the given number of hunters.
	 */
	double calculateHuntingRate(double numWorkers)
	{
		int wildlife = getTotalWildlife();
		if (wildlife == 0)
			return 0.0;

		double s = 0.5 * Math.sqrt(wildlife / 400.0);
		return wildlife - wildlife * Math.exp(-s * numWorkers / (double) wildlife);
	}

	int getTotalWildlife()
	{
		int sum = 0;
		for (CommodityType type : wildlifeByType.keySet())
		{
			sum += getWildlife(type);
		}
		return sum;
	}

	int getWildlife(CommodityType type)
	{
		Wildlife w = wildlifeByType.get(type);
		return w.quantity -
			w.hunted +
			w.immigrants;
	}

	// not called from anywhere at the moment
	void initPigsAndSheep()
	{
		switch(parentRegion.getBiome())
		{
		case HILLS:
			if (Math.random() < 0.6)
			{
				int wildSheepCount = (int) Math.round(15 + 50 * Math.random());
				adjustWildlife(CommodityType.SHEEP, wildSheepCount);
			}
			break;

		case PLAINS:
		case GRASSLAND:
			double r = Math.random();
			if (r < 0.2)
			{
				int wildSheepCount = (int)Math.round(10 + 40 * Math.random());
				adjustWildlife(CommodityType.SHEEP, wildSheepCount);
			}
			else if (r < 0.4)
			{
				int wildPigCount = (int)Math.round(10 + 40 * Math.random());
				adjustWildlife(CommodityType.PIG, wildPigCount);
			}
			break;

		case FOREST:
			if (Math.random() < 0.6)
			{
				int wildPigCount = (int) Math.round(15 + 50 * Math.random());
				adjustWildlife(CommodityType.PIG, wildPigCount);
			}
			break;

		default:
		}
	}

	public double getChanceOfDomestication(CommodityType type)
	{
		switch(type)
		{
		case SHEEP: return 0.85; //sheep are easy to catch
		case PIG: return 0.2;    //pigs are harder
		default:
			return 0.0;
		}
	}

	CommodityType pickOneRandomAnimal()
	{
		RouletteWheel<CommodityType> r = new RouletteWheel<CommodityType>();
		for (CommodityType type : wildlifeByType.keySet())
		{
			int wildlifeCount = getWildlife(type);
			if (wildlifeCount <= 0)
				continue;

			double fitness = getWildlife(type);
			r.add(type, fitness);
		}
		return r.next();
	}

	Map<CommodityType, Integer> takeHuntingYield(int numAnimals)
	{
		final int BATCHES = 17;

		Map<CommodityType, Integer> rv = new HashMap<CommodityType, Integer>();
		int divisor = BATCHES;
		while (numAnimals > 0)
		{
			CommodityType animal = pickOneRandomAnimal();
			if (animal == null)
			{
				// no animals left!
				return rv;
			}

			int avail = getWildlife(animal);
			int wanted = (numAnimals + divisor - 1) / divisor;
			if (wanted > numAnimals)
				wanted = numAnimals;
			if (wanted > avail)
				wanted = avail;

			numAnimals -= wanted;
			wildlifeByType.get(animal).hunted += wanted;
			Integer x = rv.get(animal);
			rv.put(animal, x != null ? (x.intValue()+wanted) : wanted);

			divisor = (divisor > 1 ? divisor-1 : 0);
		}

		return rv;
	}

	long meatPerHead(CommodityType type)
	{
		switch(type)
		{
		case PIG: return 35;
		case CATTLE: return 250;
		case SHEEP: return 20;
		default: return 25;
		}
	}

	void parse(JsonParser in)
		throws IOException
	{
		in.nextToken();
		if (in.getCurrentToken() != JsonToken.START_OBJECT)
			throw new InputMismatchException();

		while (in.nextToken() != JsonToken.END_OBJECT)
		{
			CommodityType type = CommodityType.valueOf(in.getCurrentName());
			in.nextToken();
			if (in.getCurrentToken() == JsonToken.START_ARRAY)
			{
				Wildlife w = new Wildlife();
				wildlifeByType.put(type, w);

				w.quantity = in.nextIntValue(0);
				w.hunted = in.nextIntValue(0);
				w.immigrants = in.nextIntValue(0);
				in.nextToken();
			}

			if (in.getCurrentToken() != JsonToken.END_ARRAY)
				throw new InputMismatchException();
		}
	}

	void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		for (CommodityType type : wildlifeByType.keySet())
		{
			Wildlife w = wildlifeByType.get(type);

			out.writeFieldName(type.name());
			out.writeStartArray();
			out.writeNumber(w.quantity);
			out.writeNumber(w.hunted);
			out.writeNumber(w.immigrants);
			out.writeEndArray();
		}
		out.writeEndObject();
	}
}
