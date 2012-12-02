package bigearth;

public interface Stoppable
{
	/**
	 * Stop the thread, but do not wait for it to finish.
	 * If you want to wait for it to finish, use join() after
	 * calling this method.
	 */
	void requestStop();
}
