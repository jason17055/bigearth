package bigearth;

import java.util.*;

public class MakeRivers
{
	MakeWorld world;
	ArrayList<Geometry.VertexId> todo;
	HashSet<Geometry.VertexId> remaining;
	Map<Geometry.VertexId, Geometry.VertexId> drainage;
	Map<Geometry.EdgeId, RiverInfo> rivers;

	public MakeRivers(MakeWorld world)
	{
		this.world = world;
		this.todo = new ArrayList<Geometry.VertexId>();
		this.remaining = new HashSet<Geometry.VertexId>();
		this.drainage = new HashMap<Geometry.VertexId, Geometry.VertexId>();
	}

	void initialize()
	{
		todo.clear();
		remaining.clear();
		drainage.clear();

		int numCells = world.g.getCellCount();
		for (int i = 1; i <= numCells; i++)
		{
			for (Geometry.VertexId v : world.g.getSurroundingVertices(i))
			{
				remaining.add(v);
			}
		}
	}

	private double getVertexHeight(Geometry.VertexId vtx)
	{
		double sum = 0.0;
		int [] cells = vtx.getAdjacentCells();
		for (int cellId : cells)
		{
			sum += world.elevation[cellId-1];
		}
		return sum / cells.length;
	}

	/**
	 * @return true if a river was added, false if no more rivers to add
	 */
	private boolean addRiverAt(Geometry.VertexId fromVertex)
	{
		double myHeight = getVertexHeight(fromVertex);
		ArrayList<Geometry.VertexId> candidates = new ArrayList<Geometry.VertexId>();
		for (Geometry.VertexId neighborVertex : world.g.getNearbyVertices(fromVertex))
		{
			if (!remaining.contains(neighborVertex))
				continue;

			double neighborHeight = getVertexHeight(neighborVertex);
			if (neighborHeight < myHeight)
				continue; // never create a river coming from lower elevation

			candidates.add(neighborVertex);
		}

		if (candidates.isEmpty())
			return false;

		int i = (int) Math.floor(Math.random() * candidates.size());
		Geometry.VertexId toVertex = candidates.get(i);

		remaining.remove(toVertex);
		todo.add(toVertex);

		rivers.put(world.g.getEdgeByEndpoints(fromVertex, toVertex), new RiverInfo());
		drainage.put(fromVertex, toVertex);

		return true;
	}

	/**
	 * @return true if a river was added, false if no more rivers to add
	 */
	private boolean step()
	{
		for (;;)
		{
			if (todo.isEmpty())
			{
				// find a starting spot
				ArrayList<Geometry.VertexId> candidates = new ArrayList<Geometry.VertexId>();
				double bestH = Double.POSITIVE_INFINITY;

				for (Geometry.VertexId v : remaining)
				{
					double h = getVertexHeight(v);
					if (h < bestH)
					{
						bestH = h;
						candidates.clear();
						candidates.add(v);
					}
					else if (h == bestH)
					{
						candidates.add(v);
					}
				}

				if (candidates.isEmpty())
					return false; //no more rivers to add

				int i = (int) Math.floor(Math.random() * candidates.size());
				Geometry.VertexId choice = candidates.get(i);
				remaining.remove(choice);
				todo.add(choice);
			}

			// choose from TODO array randomly
			int i = (int) Math.floor(Math.random() * todo.size());
			Geometry.VertexId v = todo.get(i);
			if (addRiverAt(v))
				return true;

			todo.remove(i);
		}
	}

	public void generateRivers()
	{
		initialize();
		while (step());
	}

	static class RiverInfo
	{
	}
}
