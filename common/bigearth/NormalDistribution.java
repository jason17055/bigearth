package bigearth;

import java.util.Random;

public class NormalDistribution
	implements ContinuousProbabilityDistribution
{
	Random r;
	double mean;
	double sigma;

	double r1;
	double r2;
	double cached_rho;
	boolean valid;

	public NormalDistribution(Random r, double mean, double sigma)
	{
		this.r = r;
		this.mean = mean;
		this.sigma = sigma;
	}

	public double nextVariate()
	{
		if (!valid)
		{
			r1 = r.nextDouble();
			r2 = r.nextDouble();
			cached_rho = Math.sqrt(-2.0 * Math.log(1.0-r2));
			valid = true;
		}
		else
		{
			valid = false;
		}

		return cached_rho * (valid ?
			Math.cos(2.0 * Math.PI * r1) :
			Math.sin(2.0 * Math.PI * r1))
			* sigma + mean;
	}
}

interface ContinuousProbabilityDistribution
{
	double nextVariate();
}
