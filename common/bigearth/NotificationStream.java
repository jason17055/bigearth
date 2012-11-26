package bigearth;

import java.util.*;

public class NotificationStream
{
	int initIndex = 0;
	ArrayList<Notification> notifications = new ArrayList<Notification>();

	public synchronized void add(Notification n)
	{
		notifications.add(n);
		notifyAll();
	}

	public static class OutOfSyncException extends Exception
	{
		public OutOfSyncException(String message)
		{
			super(message);
		}
	}

	public synchronized Notification [] consumeFrom(int start)
		throws OutOfSyncException, InterruptedException
	{
		if (start > initIndex)
		{
			int numToRemove = start - initIndex;
			if (numToRemove > notifications.size())
			{
				throw new OutOfSyncException("Start index is too high");
			}

			notifications.subList(0, numToRemove).clear();
			initIndex -= numToRemove;
			start -= numToRemove;
		}

		if (start < initIndex)
		{
			throw new OutOfSyncException("Start index is too low");
		}

		assert start == initIndex;

		if (notifications.isEmpty())
		{
			wait(60000); //maximum 60 seconds in milliseconds

			//FIXME- may need to reconsider what to do here
		}

		return notifications.toArray(new Notification[0]);
	}
}
