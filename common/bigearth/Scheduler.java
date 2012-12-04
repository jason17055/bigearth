package bigearth;

import java.util.*;

public class Scheduler
{
	WorldConfig config;
	long time;

	///amount added to a game time to convert to equivalent real time
	long realTimeOffset;

	PriorityQueue<Event> queue = new PriorityQueue<Event>();

	public Scheduler(WorldConfig config)
	{
		this.config = config;
	}

	public Event scheduleAt(Runnable callback, long aTime)
	{
		Event ev = new Event(callback, aTime);
		schedule(ev);
		return ev;
	}

	public long convertToGameTime(long realTime)
	{
		return realTime - realTimeOffset;
	}

	public synchronized long currentTime()
	{
		return time;
	}

	public synchronized void setGameTime(long newTime)
	{
		long curRealTime = System.currentTimeMillis();
		realTimeOffset = (curRealTime - newTime);
		notifyAll();
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
			time = nextTime;
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

	public synchronized void cancel(Event ev)
	{
		queue.remove(ev);
		notifyAll();
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
