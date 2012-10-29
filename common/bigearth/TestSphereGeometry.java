package bigearth;

public class TestSphereGeometry
{
	public static void main(String [] args)
	{
		Geometry g = new SphereGeometry(13);
		System.out.println("Number of cells: " + g.getCellCount());

		for (int i = 1, l = g.getCellCount(); i <= l; i++)
		{
			int [] nn = g.getNeighbors(i);
			System.out.print(i+": "+nn[0]);
			for (int j = 1; j < nn.length; j++)
			{
				System.out.print(", "+nn[j]);
			}
			System.out.println();
		}
	}
}
