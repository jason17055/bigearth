package bigearth;

public class EventDispatchThread extends Thread
	implements Stoppable
{
	Scheduler scheduler;
	boolean stopRequested = false;
	long lastEventTime;

	public EventDispatchThread(Scheduler scheduler)
	{
		assert scheduler != null;

		this.scheduler = scheduler;
	}

	synchronized boolean isStopRequested()
	{
		return stopRequested;
	}

	synchronized void setStopRequested()
	{
		stopRequested = true;
	}

	public void run()
	{
		while (!isStopRequested())
		{
			try
			{
				Scheduler.Event ev = scheduler.nextEvent();
				lastEventTime = ev.time;
System.out.printf("[t=%8d] dispatching %s\n", lastEventTime, ev.callback.getClass().getName());
				ev.run();
			}
			catch (InterruptedException e)
			{
				//ignore and loop back
			}
		}
	}

	public void requestStop()
	{
		setStopRequested();
		interrupt();
	}

	public static long currentTime()
	{
		EventDispatchThread me = (EventDispatchThread) Thread.currentThread();
		return me.lastEventTime;
	}
}
