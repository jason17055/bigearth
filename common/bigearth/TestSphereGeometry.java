package bigearth;

import bigearth.geom.*;

public class TestSphereGeometry
{
	static SphereGeometry g;
	static int numIters;

	public static void main(String [] args)
	{
		int gSize = Integer.parseInt(args[0]);
		g = new SphereGeometry(gSize);
		numIters = args.length > 1 ? Integer.parseInt(args[1]) : 0;

		test2();
	}

	static void test1()
	{
		int numCells = g.getFaceCount();
		if (numIters == 0)
			numIters = 200000000/numCells;

		System.out.println("Geometry: " + g.toString());
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

	static void test2()
	{
		for (int i = 0; i < 12; i++) {
			for (int j = 0; j < 5; j++) {
				Cursor c = new Cursor(i, j);
				System.out.print("From "+c);

				int count = 0;
				do {
					count++;
					g.stepCursor(c);
					assert c.location != i;

					g.rotateCursor(c, 3);
				}
				while (c.location >= 12);

				System.out.print(" walked "+count+" steps to "+c);

				for (int k = 0; k < count; k++) {
					g.rotateCursor(c, -3);
					g.stepCursor(c);
				}

				assert c.location == i;
				System.out.println();
			}
		}
	}
}
