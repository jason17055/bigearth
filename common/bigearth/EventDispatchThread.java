package bigearth;

public class EventDispatchThread extends Thread
	implements Stoppable
{
	Scheduler scheduler;
	WorldMaster world;
	boolean stopRequested = false;
	long lastEventTime;

	public EventDispatchThread(Scheduler scheduler, WorldMaster world)
	{
		assert scheduler != null;

		this.scheduler = scheduler;
		this.world = world;
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
		lastEventTime = scheduler.currentTime();
		world.startReal();

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

	/**
	 * Checks whether the currently running thread is an
	 * EventDispatchThread.
	 */
	public static boolean isActive()
	{
		return Thread.currentThread() instanceof EventDispatchThread;
	}

	public static long currentTime()
	{
		assert EventDispatchThread.isActive();

		EventDispatchThread me = (EventDispatchThread) Thread.currentThread();
		return me.lastEventTime;
	}
}
