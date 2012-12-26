package bigearth;

import java.util.*;

public class ProbabilityUrn<T>
{
	Map<T, Long> entries = new HashMap<T, Long>();
	long totalCount = 0;

	public void add(T key, long amount)
	{
		if (amount == 0) return;
		assert amount > 0;

		Long X = entries.get(key);
		if (X != null)
		{
			long newAmount = X.longValue() + amount;
			entries.put(key, newAmount);
		}
		else
		{
			entries.put(key, amount);
		}
		totalCount += amount;
	}

	private T pickOneAndReplace()
	{
		long r = (long)Math.floor(Math.random() * totalCount);
		for (Map.Entry<T,Long> e : entries.entrySet())
		{
			long q = e.getValue();
			if (r < q)
			{
				return e.getKey();
			}
			else
			{
				r -= q;
			}
		}
		throw new Error("unexpected");
	}

	public Map<T, Long> pickMany(long numPicks)
	{
		HashMap<T, Long> picked = new HashMap<T, Long>();
		for (long i = 0; i < numPicks; i++)
		{
			T key = pickOneAndReplace();
			long alreadyPicked = picked.containsKey(key) ? picked.get(key) : 0;
			alreadyPicked++;
			picked.put(key, alreadyPicked);

			long remaining = entries.get(key);
			remaining--;
			if (remaining > 0)
				entries.put(key, remaining);
			else
				entries.remove(key);
			totalCount--;
		}
		return picked;
	}
}
