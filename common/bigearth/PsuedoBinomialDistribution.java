package bigearth;

import java.util.Random;

public abstract class PsuedoBinomialDistribution
	implements DiscreteProbabilityDistribution
{
	static Random prng = new Random();

	public static PsuedoBinomialDistribution getInstance(int n, double p)
	{
		if (n < 15)
		{
			return new RealBinomialDistribution(prng, n, p);
		}
		else if (p * Math.pow(n, 0.31) < 0.47)
		{
			return new PoissonApproxBinomialDistribution(prng, n, p);
		}
		else
		{
			return new NormalApproxBinomialDistribution(prng, n, p);
		}
	}

	static class RealBinomialDistribution extends PsuedoBinomialDistribution
	{
		Random r;
		int n;
		double p;
		RealBinomialDistribution(Random r, int n, double p)
		{
			this.r = r;
			this.n = n;
			this.p = p;
		}

		@Override
		public int nextVariate()
		{
			int count = 0;
			for (int i = 0; i < n; i++)
			{
				if (r.nextDouble() < p)
					count++;
			}
			return count;
		}
	}

	static class PoissonApproxBinomialDistribution extends PsuedoBinomialDistribution
	{
		int n;
		PoissonDistribution dist;

		PoissonApproxBinomialDistribution(Random r, int n, double p)
		{
			this.n = n;
			this.dist = new PoissonDistribution(r, n*p);
		}

		@Override
		public int nextVariate()
		{
			int x = dist.nextVariate();
			assert x >= 0;
			if (x > n) x = n;
			return x;
		}
	}

	static class NormalApproxBinomialDistribution extends PsuedoBinomialDistribution
	{
		int n;
		NormalDistribution dist;

		NormalApproxBinomialDistribution(Random r, int n, double p)
		{
			this.n = n;
			this.dist = new NormalDistribution(r,
				n * p,
				Math.sqrt(n * p * (1-p))
				);
		}

		@Override
		public int nextVariate()
		{
			int x = (int) Math.round(dist.nextVariate());
			if (x < 0) x = 0;
			if (x > n) x = n;
			return x;
		}
	}
}
