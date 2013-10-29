package bigearth;

public class TestSphereGeometry
{
	public static void main(String [] args)
	{
		int gSize = Integer.parseInt(args[0]);
		Geometry g = new SphereGeometry(gSize);
		int numCells = g.getFaceCount();

		int numIters;
		if (args.length>1)
			numIters = Integer.parseInt(args[1]);
		else
			numIters = 200000000/numCells;


		System.out.println("Size: " + gSize);
		System.out.println("Number of cells: " + numCells);
		System.out.println("Performing "+numIters + " iterations");

		long startTime = System.currentTimeMillis();
		long sum = 0;
		for (int j = 0; j < numIters; j++)
		{
			for (int i = 1; i <= numCells; i++)
			{
				int [] nn = g.getNeighbors(i);
				sum += nn[0];
			}
		}
		long endTime = System.currentTimeMillis();

		double t = (endTime - startTime);
		t /= (numIters * numCells);

		System.out.printf("Elapsed time: %d ms\n", endTime-startTime);
		System.out.printf("Average time per getNeighbors() lookup : %.6f ms\n", t);
	}
}
