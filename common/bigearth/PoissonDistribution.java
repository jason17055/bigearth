package bigearth;

import java.util.Random;

public class PoissonDistribution
	implements DiscreteProbabilityDistribution
{
	Random r;
	double lambda;

	public PoissonDistribution(Random r, double lambda)
	{
		this.r = r;
		this.lambda = lambda;
	}

	//implements DiscreteProbabilityDistribution
	public int nextVariate()
	{
		double L = Math.exp(-lambda);
		int k = 0;
		double p = 1.0;

		do
		{
			k++;
			double u = r.nextDouble();
			p = p * u;
		}
		while (p > L);

		return k-1;
	}
}
