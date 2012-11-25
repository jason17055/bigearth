package bigearth;

import java.util.*;

public class NotificationStream
{
	int initIndex = 1;
	List<Notification> notifications = new ArrayList<Notification>();

	public synchronized void add(Notification n)
	{
		notifications.add(n);
	}

	
}
