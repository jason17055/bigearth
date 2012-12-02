package bigearth;

public interface Stoppable
{
	/**
	 * Stop the thread, wait for it to finish.
	 */
	void requestStop();
}
