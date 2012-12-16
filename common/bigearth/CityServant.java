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
	int [] children;

	CityServant(RegionServant parentRegion)
	{
		this.parentRegion = parentRegion;
		this.stock = new EnumMap<CommodityType, Long>(CommodityType.class);
		this.workers = new HashMap<CityJob, Integer>();
		this.workerRates = new HashMap<CityJob, Double>();
		this.children = new int[getWorldMaster().config.childYears];
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

	int getPopulation()
	{
		int sum = 0;
		for (int i = 0; i < children.length; i++)
		{
			sum += children[i];
		}
		for (Integer i : workers.values())
		{
			sum += i.intValue();
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

	private WorldConfigIfc getWorldConfig()
	{
		return parentRegion.getWorldConfig();
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
		ci.setPopulation(getPopulation());
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

	//implements EndOfYear
	public void endOfYear_stage1()
	{
		// TODO- check child care

		
	}

	//implements EndOfYear
	public void endOfYear_cleanup()
	{
		governor_endOfYear();
		cityChanged();
	}

	private void governor_endOfYear()
	{
		int amt = getWorkersInJob(CityJob.IDLE);
		if (amt != 0)
		{
			transferWorkers(amt, CityJob.IDLE, CityJob.HUNT);
		}
	}
}
