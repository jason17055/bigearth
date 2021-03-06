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
	Map<CityJob, Integer> workers;
	Map<CityJob, Double> workerRates;
	Map<CityJob, Double> production;
	int [] children;
	long productionLastUpdated; //timestamp
	double hunger;
	Command currentOrders;

	/** Technologies that this city has learned. */
	Set<Technology> science;

	/** Technologies that this city has started learning but not yet finished learning. */
	Set<Technology> partialScience;

	CityServant(RegionServant parentRegion)
	{
		this.parentRegion = parentRegion;
		this.workers = new HashMap<CityJob, Integer>();
		this.workerRates = new HashMap<CityJob, Double>();
		this.production = new HashMap<CityJob, Double>();
		this.children = new int[getWorldMaster().config.childYears];
		this.science = new HashSet<Technology>();
		this.partialScience = new HashSet<Technology>();
	}

	public void addCommodity(CommodityType ct, long amount)
	{
		parentRegion.addCommodity(ct, amount);
	}

	public long subtractCommodity(CommodityType ct, long amount)
	{
		return parentRegion.subtractCommodity(ct, amount);
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
				workerRateChanged(fromJob, 0.0);
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
		workerRateChanged(job, d);
	}

	private void workerRateChanged(CityJob job, double newRate)
	{
		switch (job)
		{
		case FARM:
			updateFarmRate(newRate);
			break;
		}
	}

	// called when server is starting up
	public void start()
	{
		for (CityJob job : workers.keySet())
		{
			newWorkerRate(job);
		}
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

	int getFactoryCount()
	{
		return parentRegion.getZoneCount(ZoneType.STONE_WORKSHOP);
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
		final AdvancedCommodityStore stock = parentRegion.stock;
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
		ci.setPopulation(getPopulation());
		ci.setScientists(getWorkersInJob(CityJob.RESEARCH));
		ci.stock = parentRegion.stock.toCommoditiesBag();
		ci.science = this.science;
		ci.partialScience = this.partialScience;

		// build zones
		ci.zones = new HashMap<String,ZoneInfo>();
		for (Map.Entry<Integer,ZoneServant> e : parentRegion.zones.entrySet())
		{
			int zoneNumber = e.getKey();
			ZoneServant zone = e.getValue();

			String n = "/zone/"+location.toString()+"/"+zoneNumber;
			ci.zones.put(n, zone.makeProfile());
		}

		ci.newZoneChoices = getDevelopChoices();
		return ci;
	}

	private Set<ZoneType> getDevelopChoices()
	{
		HashSet<ZoneType> rv = new HashSet<>();
		for (ZoneRecipe recipe : getWorldMaster().zoneRecipes.values())
		{
			if (recipe.fromZoneType != ZoneType.NATURAL)
				continue;

			boolean hasTech = true;
			for (Technology tech : recipe.techRequirements())
			{
				if (!science.contains(tech))
				{
					hasTech = false;
					break;
				}
			}
			if (!hasTech)
				continue;

			rv.add(recipe.toZoneType);
		}
		return rv;
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

	private void beginDeveloping(DevelopCommand c)
	{
		try
		{
			parentRegion.beginDeveloping(c.gridx, c.gridy, c.toZoneType);
			checkDevelopmentCosts();
		}
		catch (RegionServant.InvalidZoneTransition e)
		{
			activityFailed("Cannot build "+c.toZoneType+" at that location.");
			return;
		}

		currentOrders = null;
		cityChanged();
	}

	private void setFactoryRecipe(SetFactoryRecipeCommand c)
	{
		int zoneNumber = getZoneNumberFromName(c.zone);
		ZoneServant zone = parentRegion.zones.get(zoneNumber);
		if (zone != null)
		{
			zone.recipe = c.recipe;
			cityChanged();
		}
	}

	private int getZoneNumberFromName(String zoneName)
	{
		int i = zoneName.lastIndexOf('/');
		int zoneNumber = Integer.parseInt(zoneName.substring(i+1));
		return zoneNumber;
	}

	private void setZoneStorage(SetZoneStorageCommand c)
	{
		int zoneNumber = getZoneNumberFromName(c.zone);
		ZoneServant zone = parentRegion.zones.get(zoneNumber);
		if (zone != null)
		{
			zone.commodity = c.commodity;
			cityChanged();
		}
	}

	private void destroyZone(DestroyZoneCommand c)
	{
		int zoneNumber = getZoneNumberFromName(c.zone);
		parentRegion.destroyZone(zoneNumber);
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
			beginDeveloping((DevelopCommand) currentOrders);
			return;
		}
		else if (currentOrders instanceof EquipCommand)
		{
			beginEquipping();
			return;
		}
		else if (currentOrders instanceof SetFactoryRecipeCommand)
		{
			setFactoryRecipe((SetFactoryRecipeCommand) currentOrders);
			return;
		}
		else if (currentOrders instanceof SetZoneStorageCommand)
		{
			setZoneStorage((SetZoneStorageCommand) currentOrders);
			return;
		}
		else if (currentOrders instanceof DestroyZoneCommand)
		{
			destroyZone((DestroyZoneCommand) currentOrders);
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
			{
				CommoditiesBag bag = CommoditiesBag.parse(in);
				parentRegion.stock.add(bag);
			}
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
			else if (s.equals("science"))
				science = TechnologyBag.parseTechnologySet(in);
			else if (s.equals("partialScience"))
				partialScience = TechnologyBag.parseTechnologySet(in);
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

			workers.put(job, amt);
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
		if (!science.isEmpty())
		{
			out.writeFieldName("science");
			TechnologyBag.writeTechnologySet(out, science);
		}
		if (!partialScience.isEmpty())
		{
			out.writeFieldName("partialScience");
			TechnologyBag.writeTechnologySet(out, partialScience);
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
		int housing = 200 * parentRegion.getZoneCount(ZoneType.MUD_COTTAGE)
			+ 200 * parentRegion.getZoneCount(ZoneType.WOOD_COTTAGE)
			+ 200 * parentRegion.getZoneCount(ZoneType.STONE_COTTAGE);
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
		endOfYear_research();
		endOfYear_manufacturing();
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

	int countPastures(CommodityType animalType)
	{
		int count = 0;
		for (ZoneServant zone : parentRegion.zones.values())
		{
			if (zone.type == ZoneType.PASTURE
			&& zone.commodity == animalType)
			{
				count++;
			}
		}
		return count;
	}

	private void processLivestock(CommodityType animalType, double shepherdPoints)
	{
		long numAnimals = getStock(animalType);
		if (numAnimals == 0)
			return;

		int numRealPastures = countPastures(animalType);
		double pastures = Math.max(
				numRealPastures,
				0.5 + 0.9 * numRealPastures
				);

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

	private void learnTechnology(Technology tech)
	{
		science.add(tech);
	}

	private void endOfYear_research()
	{
		double pts = getProduction(CityJob.RESEARCH);
		production.remove(CityJob.RESEARCH);

		// subtract off required points for maintaining already known sciences
		int numSciences = science.size();
		pts -= numSciences * getWorldConfig().maintainTechnologyWorkCost;

		if (pts < 0.0)
		{
			// some sciences may be forgotten
			int numVulnerable = (int) Math.ceil(-pts / getWorldConfig().maintainTechnologyWorkCost);
			System.out.println("TODO- "+numVulnerable+" of "+numSciences+" technologies vulnerable to loss");

		}

		if (pts <= 0.0)
		{
			partialScience.clear();
			return;
		}

		int scienceOutput = (int)Math.floor(pts / getWorldConfig().newTechnologyWorkCost);
		for (Iterator< Technology> it = partialScience.iterator();
				it.hasNext(); )
		{
			Technology tech = it.next();

			if (scienceOutput >= 1)
			{
				scienceOutput -= 1;
				it.remove();
				learnTechnology(tech);
			}
			else
			{
				assert scienceOutput == 0;
				break;
			}
		}

		if (scienceOutput == 0)
			return;

		// look for new topics to start research on
		ArrayList<Technology> candidates = new ArrayList<Technology>();
		for (Technology tech : Technology.values())
		{
			if (science.contains(tech) || partialScience.contains(tech))
				continue;

			boolean haveAllPrereqs = true;
			for (Technology t2 : tech.prerequisites)
			{
				if (!science.contains(t2))
					haveAllPrereqs = false;
			}
			if (!haveAllPrereqs)
				continue;

			if (!parentRegion.stock.isSupersetOf(tech.resourceCost))
				continue;

			candidates.add(tech);
		}

		// pick random items from candidates list until we run out of
		// science output or the candidates list is empty

		while (scienceOutput >= 1 && !candidates.isEmpty())
		{
			int L = candidates.size();
			int i = (int)Math.floor(Math.random() * L);

			Technology tech = candidates.remove(i);

			// we have to check the cost again, because it's possible
			// to have multiple technologies in the candidates list that
			// individually can be paid for but not both.

			if (parentRegion.stock.isSupersetOf(tech.resourceCost))
			{
				parentRegion.stock.subtract(tech.resourceCost);
				partialScience.add(tech);
				scienceOutput -= 1;
			}
		}
	}

	private void endOfYear_developLand()
	{
		checkDevelopmentCosts();

		double pts = getProduction(CityJob.DEVELOP_LAND);
		if (pts != 0.0)
		{
			parentRegion.continueDeveloping(pts);
			production.remove(CityJob.DEVELOP_LAND);
		}
	}

	private void processManufacturing(CommodityRecipe recipe, double pts)
	{
		if (pts > 0)
		{
			if (recipe == CommodityRecipe.STONE_TO_STONE_BLOCK)
			{
				long numProducable = (long)Math.floor(pts / 5.0);
				long numProduced = parentRegion.stock.subtract(CommodityType.STONE, numProducable);
				parentRegion.stock.add(CommodityType.STONE_BLOCK, numProduced);
			}
			if (recipe == CommodityRecipe.STONE_TO_STONE_WEAPON)
			{
				long numProducable = (long)Math.floor(pts / 1.0);
				long numProduced = 5 * parentRegion.stock.subtract(CommodityType.STONE, numProducable / 5);
				parentRegion.stock.add(CommodityType.STONE_WEAPON, numProduced);
			}
		}
	}

	private void endOfYear_manufacturing()
	{
		double manufacturePoints = getProduction(CityJob.MANUFACTURE);
		production.remove(CityJob.MANUFACTURE);

		ArrayList<ZoneServant> factories = new ArrayList<>();
		for (ZoneServant zone : parentRegion.zones.values())
		{
			if (zone.type == ZoneType.STONE_WORKSHOP && zone.recipe != null)
			{
				factories.add(zone);
			}
		}

		if (factories.isEmpty())
			return;

		double maxYield = factories.size() * 100.0;
		double z = maxYield - maxYield * Math.exp(-1 * manufacturePoints / maxYield);
		double yieldPerFactory = z / factories.size();

		for (ZoneServant zone : factories)
		{
			assert zone.type == ZoneType.STONE_WORKSHOP;
			assert zone.recipe != null;
			processManufacturing(zone.recipe, yieldPerFactory);
		}
	}

	private void updateFarmRate(double farmingRate)
	{
		int numFarms = parentRegion.getZoneCount(ZoneType.FARM);
		if (numFarms == 0 || farmingRate == 0)
		{
			parentRegion.stock.removeProducer("farm");
			return;
		}

		double maxYield = numFarms * 30.0;
		double z = maxYield - maxYield * Math.exp(-1 * farmingRate / maxYield);
		double foodYield = z * getWorldConfig().foodPerFarmer;
		assert foodYield >= 0.0;

		double asGrain = foodYield / CommodityType.GRAIN.nutrition;
		double grainPerTick = asGrain / getWorldConfig().ticksPerYear;
		parentRegion.stock.setProducer("farm", CommodityType.GRAIN, grainPerTick);
	}

	private void endOfYear_farming()
	{
		double farmerPoints = getProduction(CityJob.FARM);
		production.remove(CityJob.FARM);

		// do nothing, since farm production is now handled as a
		// AdvancedCommodityStore.Producer
	}

	private void endOfYear_gathering()
	{
		double gatheringPoints = getProduction(CityJob.GATHER_RESOURCES);
		production.remove(CityJob.GATHER_RESOURCES);

		parentRegion.applyProduction_gatherResources(gatheringPoints);
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

		double shepherdPoints = getProduction(CityJob.SHEPHERD);
		for (CommodityType type : livestockTypes)
		{
			double portion = (double)getStock(type) / (double)totalLivestockCount;
			processLivestock(type,
				portion * shepherdPoints
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

		// check for factories
		int numFactories = getFactoryCount();
		int requestedManufacturers = 50 * numFactories;
		jobLevels.add(new JobLevel(CityJob.MANUFACTURE, requestedManufacturers).priority(10));

		// ensure that no one is idle
		jobLevels.add(new JobLevel(CityJob.GATHER_RESOURCES, getAdults()).priority(1));

		return jobLevels.toArray(new JobLevel[0]);
	}

	public long getStock(CommodityType ct)
	{
		return parentRegion.stock.getQuantity(ct);
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
