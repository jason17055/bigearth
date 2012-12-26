package bigearth;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;

public class CityServant
	implements EndOfYear
{
	transient RegionServant parentRegion;

	String displayName;
	String owner;
	Location location;
	CommoditiesBag stock;
	Map<CityJob, Integer> workers;
	Map<CityJob, Double> workerRates;
	Map<CityJob, Double> production;
	int [] children;
	long productionLastUpdated; //timestamp
	double hunger;
	Command currentOrders;

	CityServant(RegionServant parentRegion)
	{
		this.parentRegion = parentRegion;
		this.stock = new CommoditiesBag();
		this.workers = new HashMap<CityJob, Integer>();
		this.workerRates = new HashMap<CityJob, Double>();
		this.production = new HashMap<CityJob, Double>();
		this.children = new int[getWorldMaster().config.childYears];
	}

	public void addCommodity(CommodityType ct, long amount)
	{
		stock.add(ct, amount);
	}

	public long subtractCommodity(CommodityType ct, long amount)
	{
		return stock.subtract(ct, amount);
	}

	public void addWorkers(int amount)
	{
		addWorkers(amount, CityJob.IDLE);
	}

	public void addWorkers(int amount, CityJob toJob)
	{
		assert amount >= 0;
		if (amount > 0)
		{
			int cur = getWorkersInJob(toJob);
			cur += amount;
			workers.put(toJob, cur);
			newWorkerRate(toJob);
		}
	}

	/**
	 * Take workers from anywhere. (It is assumed the caller will take care of
	 * giving the governor a chance to rebalance jobs afterwards.)
	 */
	int subtractWorkers(int amount)
	{
		int totalFound = 0;

		CityJob [] allJobs = workers.keySet().toArray(new CityJob[0]);
		for (CityJob job : allJobs)
		{
			int n = getWorkersInJob(job);
			if (totalFound < amount)
			{
				totalFound += subtractWorkers(amount-totalFound, job);
			}
		}

		return totalFound;
	}

	int subtractWorkers(int amount, CityJob fromJob)
	{
		assert amount >= 0;
		if (amount > 0)
		{
			Integer x = workers.get(fromJob);
			if (x == null)
				return 0;

			int curBal = x.intValue();
			if (amount < curBal)
			{
				curBal -= amount;
				assert curBal > 0;
				workers.put(fromJob, curBal);
				newWorkerRate(fromJob);
				return amount;
			}
			else
			{
				workers.remove(fromJob);
				workerRates.remove(fromJob);
				return curBal;
			}
		}
		return 0;
	}

	int transferWorkers(int amount, CityJob fromJob, CityJob toJob)
	{
		assert amount >= 0;
		if (amount > 0)
		{
			amount = subtractWorkers(amount, fromJob);
			addWorkers(amount, toJob);
			return amount;
		}
		return 0;
	}

	static double nextRandomWorkerRate()
	{
		double t = Math.random();
		if (t == 0)
			return 0;
		return Math.exp( -Math.log((1/t)-1) / 15 );
	}

	private void newWorkerRate(CityJob job)
	{
		double d = (double) getWorkersInJob(job);
		d *= nextRandomWorkerRate();

		workerRates.put(job, d);
	}

	// called when server is starting up
	public void start()
	{
		//nothing to do
	}

	public static CityServant parse(JsonParser in, RegionServant parentRegion)
		throws IOException
	{
		CityServant city = new CityServant(parentRegion);
		city.parse(in);
		return city;
	}

	public void addPopulation(int population)
	{
		addWorkers(population);
		cityChanged();
	}

	void cityChanged()
	{
		if (owner != null)
		{
			CityInfo data = makeProfile(true);
			CityUpdateNotification n = new CityUpdateNotification(location, data);
			notifyLeader(owner, n);
		}
	}

	int getAdults()
	{
		int sum = 0;
		for (Integer i : workers.values())
		{
			sum += i.intValue();
		}
		return sum;
	}

	int getChildren()
	{
		int sum = 0;
		for (int i = 0; i < children.length; i++)
		{
			sum += children[i];
		}
		return sum;
	}

	int getFarmCount()
	{
		return parentRegion.getFarmCount();
	}

	int getPopulation()
	{
		return getChildren() + getAdults();
	}

	long getTotalFood()
	{
		long sum = 0;
		for (CommodityType ct : stock.getCommodityTypesArray())
		{
			int nutritionPerUnit = ct.nutrition;
			sum += stock.getQuantity(ct) * nutritionPerUnit;
		}
		return sum;
	}

	int getWorkersInJob(CityJob job)
	{
		Integer i = workers.get(job);
		if (i != null)
			return i.intValue();
		else
			return 0;
	}

	private WorldConfig getWorldConfig()
	{
		return parentRegion.world.config;
	}

	private WorldMaster getWorldMaster()
	{
		return parentRegion.world;
	}

	private void notifyLeader(String user, Notification n)
	{
		getWorldMaster().notifyLeader(user, n);
	}

	CityInfo makeProfileFor(String user)
	{
		assert user != null;

		return makeProfile(user.equals(owner));
	}

	CityInfo makeProfile(boolean isOwner)
	{
		CityInfo ci = new CityInfo();
		ci.displayName = displayName;
		ci.location = location;
		ci.setChildren(getChildren());
		ci.setFarms(parentRegion.getZoneCount(ZoneType.FARM));
		ci.setPastures(parentRegion.getZoneCount(ZoneType.PASTURE));
		ci.setHouses(
			parentRegion.getZoneCount(ZoneType.MUD_COTTAGES)
			+ parentRegion.getZoneCount(ZoneType.WOOD_COTTAGES)
			+ parentRegion.getZoneCount(ZoneType.STONE_COTTAGES)
			);
		ci.setUnderConstruction(parentRegion.getZoneCount(ZoneType.UNDER_CONSTRUCTION));
		ci.setPopulation(getPopulation());
		ci.stock = this.stock.clone();
		return ci;
	}

	public boolean canUserCommand(String user)
	{
		assert user != null;

		return owner != null && owner.equals(user);
	}

	public void setOrders(Command c)
	{
		if (this.currentOrders != null)
			cancelOrders();

		this.currentOrders = c;
		cityActivity();
	}

	void cancelOrders()
	{
		//TODO cancel wakeup
	}

	private void activityFailed(String message)
	{
		cityMessage(message);
		this.currentOrders = null;
		cityChanged();
	}

	private void beginDeveloping()
	{
		DevelopCommand c = (DevelopCommand) currentOrders;

		try
		{
			parentRegion.beginDeveloping(c.fromZoneType, c.toZoneType);
			checkDevelopmentCosts();
		}
		catch (RegionServant.ZoneTypeNotFound e)
		{
			activityFailed("No space left to develop.");
			return;
		}
		catch (RegionServant.InvalidZoneTransition e)
		{
			activityFailed("Cannot develop "+c.toZoneType+" from "+c.fromZoneType+".");
			return;
		}

		currentOrders = null;
		cityChanged();
	}

	private void beginEquipping()
	{
		EquipCommand c = (EquipCommand) currentOrders;

		int numAdults = getAdults();
		if (numAdults < 150)
		{
			activityFailed("Not enough people in town.");
			return;
		}

		MobServant mob = parentRegion.spawnCharacter(location, c.mobType, owner);
		int actualPop = subtractWorkers(100);
		mob.population = actualPop;
		mob.mobChanged();

		currentOrders = null;
		governor_checkJobs();
		cityChanged();
	}

	void cityActivity()
	{
		if (currentOrders instanceof RenameSelfCommand)
		{
			this.displayName = ((RenameSelfCommand)currentOrders).newName;
			currentOrders = null;
			cityChanged();
			return;
		}
		else if (currentOrders instanceof DevelopCommand)
		{
			beginDeveloping();
			return;
		}
		else if (currentOrders instanceof EquipCommand)
		{
			beginEquipping();
			return;
		}

		// unrecognized command
		currentOrders = null;
		cityChanged();
	}

	private void parse(JsonParser in)
		throws IOException
	{
		in.nextToken();
		assert in.getCurrentToken() == JsonToken.START_OBJECT;

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			String s = in.getCurrentName();
			if (s.equals("displayName"))
				displayName = in.nextTextValue();
			else if (s.equals("owner"))
				owner = in.nextTextValue();
			else if (s.equals("location"))
				location = LocationHelper.parse(in.nextTextValue(), getWorldConfig());
			else if (s.equals("stock"))
				stock = CommoditiesBag.parse(in);
			else if (s.equals("population"))
			{
				in.nextToken();
				int population = in.getIntValue();
				addWorkers(population);
			}
			else if (s.equals("workers"))
				parseWorkers(in);
			else if (s.equals("children"))
				parseChildren(in);
			else if (s.equals("production"))
				parseProduction(in);
			else if (s.equals("currentOrders"))
				currentOrders = Command.parse(in, getWorldConfig());
			else if (s.equals("productionLastUpdated"))
			{
				in.nextToken();
				productionLastUpdated = in.getLongValue();
			}
			else
			{
				in.nextToken();
				in.skipChildren();
				System.err.println("unrecognized city property: "+s);
			}
		}

		assert in.getCurrentToken() == JsonToken.END_OBJECT;
	}

	private void parseChildren(JsonParser in)
		throws IOException
	{
		in.nextToken();
		if (in.getCurrentToken() != JsonToken.START_ARRAY)
			throw new InputMismatchException();

		int i = 0;
		while (in.nextToken() != JsonToken.END_ARRAY)
		{
			int n = in.getIntValue();
			if (i < children.length)
			{
				children[i] = n;
			}
			else
			{
				addWorkers(n);
			}
		}
	}

	private void parseProduction(JsonParser in)
		throws IOException
	{
		in.nextToken();
		if (in.getCurrentToken() != JsonToken.START_OBJECT)
			throw new InputMismatchException();

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			CityJob job = CityJob.valueOf(in.getCurrentName());
			in.nextToken();
			double amt = in.getDoubleValue();

			production.put(job, amt);
		}

		if (in.getCurrentToken() != JsonToken.END_OBJECT)
			throw new InputMismatchException();
	}

	private void parseWorkers(JsonParser in)
		throws IOException
	{
		in.nextToken();
		if (in.getCurrentToken() != JsonToken.START_OBJECT)
			throw new InputMismatchException();

		while (in.nextToken() == JsonToken.FIELD_NAME)
		{
			CityJob job = CityJob.valueOf(in.getCurrentName());
			in.nextToken();
			int amt = in.getIntValue();

			addWorkers(amt, job);
		}

		if (in.getCurrentToken() != JsonToken.END_OBJECT)
			throw new InputMismatchException();
	}

	private void updateProduction()
	{
		long curTime = EventDispatchThread.currentTime();
		long elapsed = curTime - productionLastUpdated;

		if (elapsed <= 0)
			return;

		double elapsedYears = ((double)elapsed) / ((double)parentRegion.world.config.getTicksPerYear());
		for (Map.Entry<CityJob,Integer> e : workers.entrySet())
		{
			CityJob job = e.getKey();
			int qty = e.getValue();

			hunger += elapsedYears * qty * getWorldConfig().hungerPerAdult;

			double rate = workerRates.get(job);
			double productionIncrease = rate * elapsedYears;

			Double oldProduction = production.get(job);
			double newProduction = (oldProduction != null ? oldProduction.doubleValue() : 0.0)
				+ productionIncrease;
			production.put(job, newProduction);
		}

		for (int i = 0; i < children.length; i++)
		{
			hunger += elapsedYears * children[i] * getWorldConfig().hungerPerChild;
		}

		productionLastUpdated = curTime;
	}

	public void write(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		out.writeStringField("displayName", displayName);
		out.writeStringField("owner", owner);
		out.writeStringField("location", location.toString());
		out.writeFieldName("stock");
		stock.write(out);
		out.writeFieldName("workers");
		writeWorkers(out);
		out.writeFieldName("production");
		writeProduction(out);
		out.writeNumberField("productionLastUpdated", productionLastUpdated);
		out.writeFieldName("children");
		writeChildren(out);
		if (currentOrders != null)
		{
			out.writeFieldName("currentOrders");
			currentOrders.write(out);
		}
		out.writeEndObject();
	}

	private void writeWorkers(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		for (CityJob job : workers.keySet())
		{
			out.writeFieldName(job.name());
			int amt = workers.get(job);
			out.writeNumber(amt);
		}
		out.writeEndObject();
	}

	private void writeProduction(JsonGenerator out)
		throws IOException
	{
		out.writeStartObject();
		for (CityJob job : production.keySet())
		{
			out.writeFieldName(job.name());
			double amt = production.get(job);
			out.writeNumber(amt);
		}
		out.writeEndObject();
	}

	private void writeChildren(JsonGenerator out)
		throws IOException
	{
		out.writeStartArray();
		for (int i = 0; i < children.length; i++)
		{
			out.writeNumber(children[i]);
		}
		out.writeEndArray();
	}

	int getPopulationCapacity()
	{
		int housing = 200 * parentRegion.getZoneCount(ZoneType.MUD_COTTAGES)
			+ 200 * parentRegion.getZoneCount(ZoneType.WOOD_COTTAGES)
			+ 200 * parentRegion.getZoneCount(ZoneType.STONE_COTTAGES);
		int townCenter = Math.max(0, 50 - housing / 20);

		return townCenter + housing;
	}

	//implements EndOfYear
	public void endOfYear_stage1()
	{
		updateProduction();

		endOfYear_children();
		endOfYear_hunting();
		endOfYear_farming();
		endOfYear_gathering();
		endOfYear_eating();
		endOfYear_deaths();
		endOfYear_livestock();
		endOfYear_developLand();
	}

	private void cityDebug(String message)
	{
		System.out.println("city["+location+"] "+message);
	}

	private void cityMessage(String message)
	{
		CityMessageNotification n = new CityMessageNotification(location, message);
		notifyLeader(owner, n);
	}

	private void processLivestock(CommodityType animalType, double shepherdPoints, double pastures)
	{
		long numAnimals = getStock(animalType);
		if (numAnimals == 0)
			return;

		double numShepherds = shepherdPoints;
		double protectedAnimals = 8 * numShepherds;
		if (protectedAnimals < numAnimals)
		{
			long unprotected = numAnimals - (long)Math.floor(protectedAnimals);
			double chanceOfEscaping = 0.25;
			PsuedoBinomialDistribution d = PsuedoBinomialDistribution.getInstance(unprotected, chanceOfEscaping);
			long lost = d.nextVariate();
			lost = subtractCommodity(animalType, lost);
			if (lost > 0)
			{
				cityMessage(lost + " " + animalType + " have run away.");
			}
		}

		// check grazing ground
		double animalsFed = pastures * getWorldConfig().maxAnimalsPerPasture;
		animalsFed = Math.min(animalsFed, getStock(animalType));

		// the ones that can't be fed get converted to meat
		long starvedAnimals = getStock(animalType) - (long)Math.floor(animalsFed);
		if (starvedAnimals > 0)
		{
		//TODO- convert to meat
			subtractCommodity(animalType, starvedAnimals);
			cityMessage(starvedAnimals + " " + animalType + " starved. (Build more pastures.)");
		}

		// check breeding
		numAnimals = getStock(animalType);
		if (numAnimals >= 2)
		{
			long births = (numAnimals/5) + (long)Math.round((numAnimals + 7.0*Math.random()) / 11.0);
			if (births > 0)
			{
				addCommodity(animalType, births);
				cityMessage("Bred "+births +" new "+animalType+".");
			}
		}
	}

	private void payDevelopmentCost(RegionServant.ZoneDevelopment zd)
	{
		for (CommodityType ct : zd.requiredCommodities.getCommodityTypesArray())
		{
			long amtWanted = zd.requiredCommodities.getQuantity(ct);
			long amtGiven = subtractCommodity(ct, amtWanted);
			zd.requiredCommodities.subtract(ct, amtGiven);
		}
	}

	private void checkDevelopmentCosts()
	{
		for (RegionServant.ZoneDevelopment zd : parentRegion.zoneDevelopments)
		{
			if (!zd.requiredCommodities.isEmpty())
			{
				// this development requires some commodity
				payDevelopmentCost(zd);
			}
		}
	}

	private void endOfYear_developLand()
	{
		double pts = getProduction(CityJob.DEVELOP_LAND);
		if (pts != 0.0)
		{
			parentRegion.continueDeveloping(pts);
			production.remove(CityJob.DEVELOP_LAND);
		}
	}

	private void endOfYear_farming()
	{
		double farmerPoints = getProduction(CityJob.FARM);
		production.remove(CityJob.FARM);

		int numFarms = parentRegion.getZoneCount(ZoneType.FARM);
		if (numFarms == 0)
			return;

		double maxYield = numFarms * 30.0;
		double z = maxYield - maxYield * Math.exp(-1 * farmerPoints / maxYield);
		double foodYield = z * getWorldConfig().foodPerFarmer;
		assert foodYield >= 0.0;

		double asGrain = foodYield / CommodityType.GRAIN.nutrition;

		addCommodity(CommodityType.GRAIN, (long)Math.floor(asGrain));
	}

	private void endOfYear_gathering()
	{
		double gatheringPoints = getProduction(CityJob.GATHER_RESOURCES);
		production.remove(CityJob.GATHER_RESOURCES);

		int numNatural = parentRegion.getZoneCount(ZoneType.NATURAL);
		if (numNatural == 0)
			return;

		double maxYield = numNatural * 5.0;
		double z = maxYield - maxYield * Math.exp(-1 * gatheringPoints / maxYield);

		long numGathered = (long)Math.round(z * 0.25);
		System.out.printf("%.1f workers gathered %d units\n",
				gatheringPoints,
				(int) numGathered
				);

		ProbabilityUrn<CommodityType> urn = new ProbabilityUrn<CommodityType>();
		for (CommodityType ct : parentRegion.surfaceMinerals.getCommodityTypesArray())
		{
			urn.add(ct, parentRegion.surfaceMinerals.getQuantity(ct));
		}

		Map<CommodityType, Long> picked = urn.pickMany(numGathered);
		for (CommodityType ct : picked.keySet())
		{
			long amt = picked.get(ct);
			long taken = parentRegion.surfaceMinerals.subtract(ct, amt);
			stock.add(ct, taken);
			System.out.println("  "+taken+" " + ct);
		}
	}

	private void endOfYear_livestock()
	{
		CommodityType [] livestockTypes = new CommodityType[] {
			CommodityType.SHEEP, CommodityType.PIG
			};

		long totalLivestockCount = 0;
		for (CommodityType type : livestockTypes)
		{
			totalLivestockCount += getStock(type);
		}

		int numRealPastures = parentRegion.getZoneCount(ZoneType.PASTURE);
		double pastures = Math.max(
				numRealPastures,
				0.5 + 0.9 * numRealPastures
				);

		double shepherdPoints = getProduction(CityJob.SHEPHERD);
		for (CommodityType type : livestockTypes)
		{
			double portion = (double)getStock(type) / (double)totalLivestockCount;
			processLivestock(type,
				portion * shepherdPoints,
				portion * pastures
				);
		}

		production.remove(CityJob.SHEPHERD);
	}

	private void endOfYear_children()
	{
		//
		// check child care supply
		//
		double childCareDemand = 0.5 * getChildren();
		double childCareSupply = getProduction(CityJob.CHILDCARE);
		production.remove(CityJob.CHILDCARE);

		if (childCareSupply < childCareDemand)
		{
			assert childCareSupply >= 0.0;
			assert childCareDemand > 0.0;

			double caredForPortion = childCareSupply / childCareDemand;
			double survivalRate = 0.60 + 0.40 * Math.pow(caredForPortion, 2.0);
			if (survivalRate > 1.0)
				survivalRate = 1.0; //could only occur through rounding error

			for (int i = 0; i < children.length; i++)
			{
				if (children[i] > 0)
				{
					PsuedoBinomialDistribution d = PsuedoBinomialDistribution.getInstance(children[i], survivalRate);
					children[i] = (int)d.nextVariate();
				}
			}
		}

		//
		// bring forward growing children
		//
		int newAdults = children[children.length-1];
		for (int i = children.length - 1; i > 0; i--)
		{
			children[i] = children[i-1];
		}
		children[0] = 0;
		addWorkers(newAdults);

		//
		// calculate new births
		//
		int capacity = getPopulationCapacity();
		double housingUsage = (double)getPopulation() / (double)(capacity > 0 ? capacity : 1);
		double coreBirthRate = 0.125 + 0.04 * (Math.random() * 2 - 1);
		double birthRate = coreBirthRate / (1 + Math.exp(-(0.85 - housingUsage) * 12.0));
		int births = (int) Math.floor(getAdults() * birthRate);
		children[0] = births;
	}

	private void endOfYear_eating()
	{
		if (hunger <= 0.0)
			return;

		System.out.println("city["+location+"] demand "+hunger+" nutrition for population");

		CommodityType [] foodPriority = new CommodityType[] {
				CommodityType.MEAT, CommodityType.GRAIN,
				CommodityType.PIG, CommodityType.SHEEP
				};
		consumeFood(foodPriority);
	}

	private void consumeFood(CommodityType [] foodPriority)
	{
		for (int i = 0; i < foodPriority.length; i++)
		{
			if (hunger <= 0.0)
				return;

			CommodityType foodType = foodPriority[i];
			long onHand = getStock(foodType);
			if (onHand == 0)
				continue;

			long wanted = (long)Math.ceil(hunger / foodType.nutrition);
			assert wanted >= 0;

			long consumed = subtractCommodity(foodType, wanted);
			System.out.println("consuming "+consumed+" units of "+foodType);
			hunger -= consumed * foodType.nutrition;
		}
	}

	private int getLifeExpectancy()
	{
		return getWorldConfig().humanLifeExpectancy;
	}

	private void endOfYear_deaths()
	{
		double sustainRate = 1.0 - 1.0/getLifeExpectancy();

		//TODO- consider effect of sickness and malnutrition

		double deathRate = 1.0 - sustainRate;

		// must avoid iterating workers directly because
		// we invoke subtractWorkers() which may modify the hash
		CityJob [] allJobs = workers.keySet().toArray(new CityJob[0]);
		for (CityJob job : allJobs)
		{
			int qty = workers.get(job);

			PsuedoBinomialDistribution d = PsuedoBinomialDistribution.getInstance(qty, deathRate);
			int deaths = (int) d.nextVariate();
			subtractWorkers(deaths, job);
		}
	}

	private void endOfYear_hunting()
	{
		final WildlifeServant wildlife = parentRegion.wildlife;

		double manYears = getProduction(CityJob.HUNT);
		double huntingYield = parentRegion.wildlife.calculateHuntingRate(manYears);
		int numAnimals = (int) Math.floor(huntingYield);
		assert numAnimals >= 0;

		Map<CommodityType, Integer> yield = wildlife.takeHuntingYield(numAnimals);
		for (CommodityType type : yield.keySet())
		{
			int num = yield.get(type);
			double p = wildlife.getChanceOfDomestication(type);

			PsuedoBinomialDistribution d = PsuedoBinomialDistribution.getInstance(num, p);
			int numCaptured = (int) d.nextVariate();
			int numKilled = num - numCaptured;

			addCommodity(type, numCaptured);
			addCommodity(CommodityType.MEAT, wildlife.meatPerHead(type) * numKilled);
		}

		production.remove(CityJob.HUNT);
	}

	//implements EndOfYear
	public void endOfYear_cleanup()
	{
		governor_endOfYear();
		cityChanged();
	}

	private void governor_endOfYear()
	{
		governor_checkJobs();
	}

	private void governor_checkJobs()
	{
		JobLevel [] jobLevels = governor_determineJobLevels();
		governor_dispatchJobAssignments(jobLevels);
	}

	private void governor_dispatchJobAssignments(JobLevel [] jobLevels)
	{
		// sort job requests by priority (highest priority first)
		Arrays.sort(jobLevels, new Comparator<JobLevel>() {
			public int compare(JobLevel a, JobLevel b) {
				return -(a.priority - b.priority);
			}});

		//
		// compute minimum assignments
		//

		int workersLeft = getAdults();
		Map<CityJob, Integer> assignments = new HashMap<CityJob, Integer>();

		int i = 0;
		while (i < jobLevels.length)
		{
			int curPriority = jobLevels[i].priority;
			int totalDemand = jobLevels[i].quantity;

			int j = i+1;
			while (j < jobLevels.length && jobLevels[j].priority == curPriority)
			{
				totalDemand += jobLevels[j].quantity;
				j++;
			}

			if (totalDemand > 0 && workersLeft > 0)
			{
				double rations = totalDemand > workersLeft ? (double)workersLeft / (double)totalDemand : 1.0;
				for (int k = i; k < j; k++)
				{
					JobLevel req = jobLevels[k];
					int toAssign = (int) Math.round(req.quantity * rations);
					if (toAssign > workersLeft)
						toAssign = workersLeft;
					assignments.put(req.job,
						(assignments.containsKey(req.job) ? assignments.get(req.job).intValue() : 0) + toAssign);
					workersLeft -= toAssign;

					assert workersLeft >= 0;
				}
			}

			i = j;
		}

		//
		// look for jobs with extra workers
		//

		List<CityJob> jobsWithExtra = new ArrayList<CityJob>();
		if (getWorkersInJob(CityJob.IDLE) > 0)
		{
			jobsWithExtra.add(CityJob.IDLE);
		}

		for (CityJob job : workers.keySet())
		{
			if (job == CityJob.IDLE)
				continue; // idle workers were already handled

			int wanted = assignments.containsKey(job) ? assignments.get(job).intValue() : 0;
			if (getWorkersInJob(job) > wanted)
			{
				jobsWithExtra.add(job);
			}
		}

		for (CityJob job : assignments.keySet())
		{
			int actual = getWorkersInJob(job);
			int wanted = assignments.get(job);
			if (actual < wanted)
			{
				int amountShort = wanted - actual;
				while (amountShort > 0 && !jobsWithExtra.isEmpty())
				{
					// each iteration will result in a deduction of amountShort
					// or a removal of an item from jobsWithExtra[].

					CityJob fromJob = jobsWithExtra.get(0);
					int wantedThere = assignments.containsKey(fromJob) ? assignments.get(fromJob).intValue() : 0;
					int extraThere = getWorkersInJob(fromJob) - wantedThere;
					if (extraThere >= amountShort)
					{
						int x = transferWorkers(amountShort, fromJob, job);
						assert x > 0;
						amountShort -= x;
					}
					else if (extraThere > 0)
					{
						int x = transferWorkers(extraThere, fromJob, job);
						amountShort -= x;
						jobsWithExtra.remove(0);
					}
					else
					{
						jobsWithExtra.remove(0);
					}
				}
			}
		}
	}

	private JobLevel [] governor_determineJobLevels()
	{
		List<JobLevel> jobLevels = new ArrayList<JobLevel>();

		long foodInStorage = getTotalFood();
		double oneYearFood = getWorldConfig().hungerPerAdult * getAdults()
			+ getWorldConfig().hungerPerChild * getChildren();

		final double DESIRED_STOCK = 1.5;
		final double MAX_ASSIGN = 4.0/3.0;

		long desiredFoodNextYear = (long)
			Math.round(oneYearFood * (MAX_ASSIGN + (1-MAX_ASSIGN) * ((double)foodInStorage / oneYearFood) / DESIRED_STOCK));
		if (desiredFoodNextYear < 0)
			desiredFoodNextYear = 0;

		// first consider farms
		int mandatoryFarmers = (int)Math.ceil(desiredFoodNextYear / getWorldConfig().foodPerFarmer);
		int numFarms = getFarmCount();
		if (mandatoryFarmers > numFarms * 15)
			mandatoryFarmers = numFarms * 15;
		int optionalFarmers = numFarms * 10;
		desiredFoodNextYear -= mandatoryFarmers * getWorldConfig().foodPerFarmer;

		jobLevels.add(new JobLevel(CityJob.FARM, mandatoryFarmers).priority(100));
		jobLevels.add(new JobLevel(CityJob.FARM, optionalFarmers).priority(10));

		// remaining food must come from hunting
		int mandatoryHunters = (int) Math.ceil(desiredFoodNextYear / getWorldConfig().foodPerAnimal);
		int optionalHunters = mandatoryHunters < 20 ? 20 - mandatoryHunters : 0;

		jobLevels.add(new JobLevel(CityJob.HUNT, mandatoryHunters).priority(100));
		jobLevels.add(new JobLevel(CityJob.HUNT, optionalHunters).priority(5));

		// consider child care
		int mandatoryChildCaretakers = (int)Math.ceil(getChildren() / 2.0);
		jobLevels.add(new JobLevel(CityJob.CHILDCARE, mandatoryChildCaretakers).priority(90));

		// consider livestock care
		long numSheep = getStock(CommodityType.SHEEP);
		long numPigs = getStock(CommodityType.PIG);
		int mandatoryShepherds = (int)Math.ceil((numSheep + numPigs) / 8.0);
		jobLevels.add(new JobLevel(CityJob.SHEPHERD, mandatoryShepherds).priority(85));

		// does the city have any land development?
		int numZonesDeveloping = parentRegion.getZoneCount(ZoneType.UNDER_CONSTRUCTION);
		jobLevels.add(new JobLevel(CityJob.DEVELOP_LAND, 100).priority(30));

		// TODO- does the city have any other tasks to perform?

		// some other jobs that people like to do...
		jobLevels.add(new JobLevel(CityJob.RESEARCH, (int)Math.floor(getAdults()/10.0)).priority(10));

		// ensure that no one is idle
		jobLevels.add(new JobLevel(CityJob.GATHER_RESOURCES, getAdults()).priority(1));

		return jobLevels.toArray(new JobLevel[0]);
	}

	public long getStock(CommodityType ct)
	{
		return stock.getQuantity(ct);
	}

	static class JobLevel
	{
		CityJob job;
		int quantity;
		int priority;

		JobLevel(CityJob job, int quantity)
		{
			this.job = job;
			this.quantity = quantity;
		}

		JobLevel priority(int newPriority)
		{
			this.priority = newPriority;
			return this;
		}
	}

	double getProduction(CityJob job)
	{
		Double dd = production.get(job);
		return dd != null ? dd.doubleValue() : 0.0;
	}
}
