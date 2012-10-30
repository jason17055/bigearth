package bigearth;

import java.util.*;

public class RouletteWheel<T>
{
	class Entry<T>
	{
		T item;
		double fitnessStart;

		Entry(T item, double fitnessStart)
		{
			this.item = item;
			this.fitnessStart = fitnessStart;
		}
	}
	double fitnessSum;
	List< Entry<T> > entries;

	public RouletteWheel()
	{
		this.fitnessSum = 0;
		this.entries = new ArrayList< Entry<T> >();
	}

	public void add(T item, double fitness)
	{
		assert fitness > 0;
		if (fitness > 0)
		{
			entries.add(new Entry<T>(item, fitnessSum));
			fitnessSum += fitness;
		}
	}

	public T next()
	{
		double r = Math.random()*fitnessSum;
		int a = 0;
		int b = entries.size();
		while (a + 1 < b)
		{
			int i = (a+b)/2;
			Entry<T> e = entries.get(i);
			if (r < e.fitnessStart)
				b = i;
			else
				a = i;
		}
		return entries.get(a).item;
	}
}
