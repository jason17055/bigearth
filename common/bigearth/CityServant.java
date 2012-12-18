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
	Map<CommodityType, Long> stock;
	Map<CityJob, Integer> workers;
	Map<CityJob, Double> workerRates;
	Map<CityJob, Double> production;
	int [] children;
	long productionLastUpdated; //timestamp

	CityServant(RegionServant parentRegion)
	{
		this.parentRegion = parentRegion;
		this.stock = new EnumMap<CommodityType, Long>(CommodityType.class);
		this.workers = new HashMap<CityJob, Integer>();
		this.workerRates = new HashMap<CityJob, Double>();
		this.production = new HashMap<CityJob, Double>();
		this.children = new int[getWorldMaster().config.childYears];
	}

	public void addCommodity(CommodityType ct, long amount)
	{
		assert stock != null;

		if (amount == 0)
			return;

		assert amount > 0;

		if (stock.containsKey(ct))
		{
			long amt = stock.get(ct);
			amt += amount;
			stock.put(ct, amt);
		}
		else
		{
			stock.put(ct, amount);
		}
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
			CityUpdateNotification n = new CityUpdateNotification(owner, data);
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
		for (Map.Entry<CommodityType,Long> e : stock.entrySet())
		{
			int nutritionPerUnit = e.getKey().nutrition;
			sum += e.getValue().intValue() * nutritionPerUnit;
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
		ci.stock = CommoditiesHelper.makeClone(this.stock);
		return ci;
	}

	boolean canUserRename(String user)
	{
		return (owner != null && owner.equals(user));
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
				stock = CommoditiesHelper.parseCommodities(in);
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
		for (CityJob job : workers.keySet())
		{
			double rate = workerRates.get(job);
			double productionIncrease = rate * elapsedYears;

			Double oldProduction = production.get(job);
			double newProduction = (oldProduction != null ? oldProduction.doubleValue() : 0.0)
				+ productionIncrease;
			production.put(job, newProduction);
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
		CommoditiesHelper.writeCommodities(stock, out);
		out.writeFieldName("workers");
		writeWorkers(out);
		out.writeFieldName("production");
		writeProduction(out);
		out.writeNumberField("productionLastUpdated", productionLastUpdated);
		out.writeFieldName("children");
		writeChildren(out);
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
		//FIXME- make this based on housing
		return 200;
	}

	//implements EndOfYear
	public void endOfYear_stage1()
	{
		updateProduction();

		endOfYear_children();
		endOfYear_hunting();
	}

	private void endOfYear_children()
	{
		// TODO- check child care supply

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
			int numCaptured = d.nextVariate();
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
		long oneYearFood = getWorldConfig().hungerPerAdult * getAdults()
			+ getWorldConfig().hungerPerChild * getChildren();

		final double DESIRED_STOCK = 1.5;
		final double MAX_ASSIGN = 4.0/3.0;

		long desiredFoodNextYear = (long)
			Math.round(oneYearFood * (MAX_ASSIGN + (1-MAX_ASSIGN) * ((double)foodInStorage / (double)oneYearFood) / DESIRED_STOCK));
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

		// TODO- does the city have tasks to perform?

		// some other jobs that people like to do...
		jobLevels.add(new JobLevel(CityJob.RESEARCH, (int)Math.floor(getAdults()/10.0)).priority(10));

		// ensure that no one is idle
		jobLevels.add(new JobLevel(CityJob.HUNT, getAdults()).priority(1));

		return jobLevels.toArray(new JobLevel[0]);
	}

	public long getStock(CommodityType ct)
	{
		Long x = stock.get(ct);
		return x != null ? x.longValue() : 0;
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
