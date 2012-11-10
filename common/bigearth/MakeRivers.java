package bigearth;

import java.util.*;

public class MakeRivers
{
	MakeWorld world;
	List<Geometry.VertexId> todo;
	Set<Geometry.VertexId> remaining;
	Map<Geometry.VertexId, Geometry.VertexId> drainage;
	Map<Geometry.EdgeId, RiverInfo> rivers;
	Map<Geometry.VertexId, LakeInfo> lakes;

	public MakeRivers(MakeWorld world)
	{
		this.world = world;
		this.todo = new ArrayList<Geometry.VertexId>();
		this.remaining = new HashSet<Geometry.VertexId>();
		this.drainage = new HashMap<Geometry.VertexId, Geometry.VertexId>();
		this.rivers = new HashMap<Geometry.EdgeId, RiverInfo>();
	}

	void initialize()
	{
		todo.clear();
		remaining.clear();
		drainage.clear();
		rivers.clear();

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

		RiverInfo ri = new RiverInfo();
		ri.upstreamVertex = toVertex;
		rivers.put(world.g.getEdgeByEndpoints(fromVertex, toVertex), ri);
		drainage.put(toVertex, fromVertex);

		return true;
	}

	/**
	 * @return true if a river was added, false if no more rivers to add
	 */
	private boolean drainageStep()
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
		while (drainageStep());

		//
		// determine volume of rivers
		//

		lakes = new HashMap<Geometry.VertexId,LakeInfo>();
		for (Geometry.VertexId vtx : drainage.keySet())
		{
			int [] cells = vtx.getAdjacentCells();
			int water = 0;

			for (int cellId : cells)
			{
				water += world.annualRains[cellId-1];
			}
			water = (water - 100) / 30;
			if (water <= 0)
				continue;

			Geometry.VertexId v = vtx;
			while (drainage.containsKey(v))
			{
				Geometry.VertexId nextVtx = drainage.get(v);
				Geometry.EdgeId eId = world.g.getEdgeByEndpoints(v, nextVtx);
				RiverInfo r = rivers.get(eId);
				assert r != null;

				r.volume += water;
				v = nextVtx;
			}

			if (!lakes.containsKey(v))
			{
				lakes.put(v, new LakeInfo());
			}
			lakes.get(v).volume += water;
		}

		//
		// process the lakes
		//

		for (Geometry.VertexId lakeVertex : lakes.keySet())
		{
			// see if we can carve a path to a neighboring separate river system
			Geometry.VertexId [] candidates = world.g.getNearbyVertices(lakeVertex);
			Geometry.VertexId best = null;
			double bestV = Double.POSITIVE_INFINITY;

			double lakeHeight = getVertexHeight(lakeVertex);
			int water = lakes.get(lakeVertex).volume;

			for (Geometry.VertexId neighborVertex : candidates)
			{
				Geometry.VertexId termVtx = neighborVertex;
				while (drainage.containsKey(termVtx))
				{
					termVtx = drainage.get(termVtx);
				}

				// the neighboring river we pick must terminate at some
				// vertex other than our own
				if (termVtx == lakeVertex) 
					continue;

				// the neighboring river we pick must terminate at a
				// lower-elevation vertex
				if (getVertexHeight(termVtx) >= lakeHeight)
					continue;

				// assuming the above criteria are met, pick the
				// neighboring vertex which is lowest in elevation

				double h = getVertexHeight(neighborVertex);
				if (h < bestV)
				{
					bestV = h;
					best = neighborVertex;
				}
			}

			if (best != null)
			{
				lakes.get(lakeVertex).type = LakeType.NONTERMINAL;

				RiverInfo ri = new RiverInfo();
				ri.upstreamVertex = lakeVertex;
				ri.volume = water;
				rivers.put(world.g.getEdgeByEndpoints(lakeVertex, best), ri);
				drainage.put(lakeVertex, best);

				// add this lake's volume to the downstream river
				Geometry.VertexId v = best;
				while (drainage.containsKey(v))
				{
					Geometry.VertexId nextVtx = drainage.get(v);
					Geometry.EdgeId eId = world.g.getEdgeByEndpoints(v, nextVtx);
					RiverInfo r = rivers.get(eId);
					assert r != null;

					r.volume += water;
					v = nextVtx;
				}
			}
			else
			{
	System.out.println("nowhere to take lake at "+lakeVertex);
			}
		}

		//
		// place rivers
		//

		for (Geometry.EdgeId eId : rivers.keySet())
		{
			RiverInfo r = rivers.get(eId);
			int [] cc = r.upstreamVertex.getAdjacentCells();
			int [] dd = eId.getAdjacentCells();

			int aRegion;
			int bRegion;
			if (cc[0] != dd[0] && cc[0] != dd[1])
			{
				aRegion = cc[1];
				bRegion = cc[2];
			}
			else if (cc[1] != dd[0] && cc[1] != dd[1])
			{
				aRegion = cc[2];
				bRegion = cc[0];
			}
			else
			{
				aRegion = cc[0];
				bRegion = cc[1];
			}

			RegionDetail region = world.regions[bRegion-1];
			region.setRiver(aRegion,
				r.volume > 500 ? RegionSideDetail.SideFeature.RIVER :
				r.volume > 100 ? RegionSideDetail.SideFeature.CREEK :
				RegionSideDetail.SideFeature.BROOK);
		}

		//
		// place lakes
		//

		for (Geometry.VertexId vId : lakes.keySet())
		{
			LakeInfo lake = lakes.get(vId);
			int [] cc = vId.getAdjacentCells();

			int aRegion = cc[0];
			RegionDetail region = world.regions[aRegion-1];
			region.setLake(vId,
				lake.type == LakeType.TERMINAL ? RegionCornerDetail.PointFeature.LAKE :
				RegionCornerDetail.PointFeature.POND
				);
		}
	}

	static class RiverInfo
	{
		int volume;
		Geometry.VertexId upstreamVertex;
	}

	static enum LakeType
	{
		TERMINAL,
		NONTERMINAL;
	}

	static class LakeInfo
	{
		int volume;
		LakeType type;

		LakeInfo() { type = LakeType.TERMINAL; }
	}
}
