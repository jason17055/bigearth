package bigearth;

import java.util.*;

public class Scheduler
{
	WorldConfig config;
	long time;
	PriorityQueue<Event> queue;

	public Scheduler(WorldConfig config)
	{
		this.config = config;
	}

	public void scheduleAt(Runnable callback, long aTime)
	{
		schedule(new Event(callback, aTime));
	}

	public long convertToGameTime(long realTime)
	{
		return realTime;
	}

	public synchronized Event nextEvent()
		throws InterruptedException
	{
		for (;;)
		{

		while (queue.isEmpty())
		{
			// wait indefinitely
			wait();
		}

		long nextTime = queue.peek().time;
		long curTime = convertToGameTime(System.currentTimeMillis());

		if (nextTime <= curTime)
		{
			return queue.remove();
		}

		// wait no more than time to next event
		long delay = nextTime - curTime;
		wait(delay);

		} //end for(;;)
	}

	public synchronized void schedule(Event ev)
	{
		queue.add(ev);
		if (queue.peek() == ev)
		{
			notifyAll();
		}
	}

	class Event implements Comparable<Event>, Runnable
	{
		Runnable callback;
		long time;

		public Event(Runnable callback, long time)
		{
			this.callback = callback;
			this.time = time;
		}

		// implements Comparable
		public int compareTo(Event rhs)
		{
			return this.time > rhs.time ? 1 :
				this.time < rhs.time ? -1 : 0;
		}

		// implements Runnable
		public void run()
		{
			callback.run();
		}
	}
}
