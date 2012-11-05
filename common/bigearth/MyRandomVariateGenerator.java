package bigearth;

public class MyRandomVariateGenerator
{
	/**
	 * Returns a random number between 0 and Infinity.
	 * The number is likely to be close to 1.0.
	 * With 30% likelihood, the number will be between 0.95 and 1.05.
	 * With 80% likelihood, the number will be between 0.86 and 1.16.
	 * With 90% likelihood, the number will be between 0.82 and 1.22.
	 * With 98% likelihood, the number will be between 0.73 and 1.36.
	 */
	public static double next()
	{
		double t = Math.random();
		if (t == 0) return 0;
		return Math.exp( -Math.log((1.0/t) - 1.0) / 15.0 );
	}
}
