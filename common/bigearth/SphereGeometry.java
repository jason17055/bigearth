package bigearth;

import javax.vecmath.*;

public class SphereGeometry implements Geometry
{
	int size;

	/// Describes the special pentagon cells of this geometry.
	/// The 12 pentagons correspond to the 12 points of an icosahedron.
	static int [][] PENT_INFO = {
		{ 5, 4, 3, 2, 1 },
		{ 1, 6, 11, 20, 10 },
		{ 2, 7, 13, 12, 6 },
		{ 3, 8, 15, 14, 7 },
		{ 4, 9, 17, 16, 8 },
		{ 5, 10, 19, 18, 9 },
		{ 11, 12, 21, 30, 25 },
		{ 13, 14, 22, 26, 21 },
		{ 15, 16, 23, 27, 22 },
		{ 17, 18, 24, 28, 23 },
		{ 19, 20, 25, 29, 24 },
		{ 26, 27, 28, 29, 30 }
		};

	/// Describes the ridges of this geometry. Each ridge is an edge
	/// of an icosahedron. Each row corresponds to one of the 30 ridges.
	/// The first two fields are the endpoints (pentagon regions) of the ridge
	/// (in lexigraphic order). 3rd and 4th fields are face numbers.
	static int [][] EDGE_INFO = {
		{ 1, 2, 5, 1 },
		{ 1, 3, 1, 2 },
		{ 1, 4, 2, 3 },
		{ 1, 5, 3, 4 },
		{ 1, 6, 4, 5 },
		{ 2, 3, 6, 1 },
        	{ 3, 4, 8, 2 },
        	{ 4, 5, 10, 3 },
        	{ 5, 6, 12, 4 },
        	{ 2, 6, 5, 14 },
        	{ 2, 7, 15, 6 },
        	{ 3, 7, 6, 7 },
        	{ 3, 8, 7, 8 },
        	{ 4, 8, 8, 9},
        	{ 4, 9, 9, 10 },
        	{ 5, 9, 10, 11 },
        	{ 5, 10, 11, 12 },
        	{ 6, 10, 12, 13 },
        	{ 6, 11, 13, 14 },
        	{ 2, 11, 14, 15 },
        	{ 7, 8, 16, 7 },
        	{ 8, 9, 17, 9 },
        	{ 9, 10, 18, 11 },
        	{ 10, 11, 19, 13 },
        	{ 7, 11, 15, 20 },
        	{ 8, 12, 16, 17 },
        	{ 9, 12, 17, 18 },
        	{ 10, 12, 18, 19 },
        	{ 11, 12, 19, 20 },
        	{ 7, 12, 20, 16 }
		};

	/// Describes the "faces" of this geometry. (Faces are those areas
	/// composed entirely of hexagonal regions and on a plane.)
	/// Each row corresponds to one of the 20 triangular faces of an
	/// icosahedron. The first three numbers in each row identify the
	/// pentagonal regions that are the corners of this "face".
	/// Order is always counter-clockwise; lowest number is listed first.
	/// The 4th, 5th, and 6th numbers in each row identify the edges
	/// of the face: base, right, left, respectively.
	static int [][] FACE_INFO = {
        	{ 1,2,3,      1,  6,  2 },
        	{ 1,3,4,      2,  7,  3 },
        	{ 1,4,5,      3,  8,  4 },
        	{ 1,5,6,      4,  9,  5 },
        	{ 1,6,2,      5, 10,  1 },
        	{ 2,7,3,     11, 12,  6 },
        	{ 3,7,8,     12, 21, 13 },
        	{ 3,8,4,     13, 14,  7 },
        	{ 4,8,9,     14, 22, 15 },
        	{ 4,9,5,    15, 16,  8 },
        	{ 5,9,10,   16, 23, 17 },
        	{ 5,10,6,   17, 18,  9 },
        	{ 6,10,11,  18, 24, 19 },
        	{ 2,6,11,   10, 19, 20 },
        	{ 2,11,7,   20, 25, 11 },
        	{ 7,12,8,   30, 26, 21 },
        	{ 8,12,9,   26, 27, 22 },
        	{ 9,12,10,  27, 28, 23 },
        	{ 10,12,11, 28, 29, 24 },
        	{ 7,11,12,  25, 29, 30 }
		};

	public SphereGeometry(int size)
	{
		this.size = size;
	}

	//implements Geometry
	public int getCellCount()
	{
		return (10*size+20)*size + 12;
	}

	private int getEdgeCell(int edgeId, int idx)
	{
		assert edgeId >= 1 && edgeId <= EDGE_INFO.length;

		if (idx == 0)
			return EDGE_INFO[edgeId-1][0];
		else if (idx == size+1)
			return EDGE_INFO[edgeId-1][1];
		else
			return 13 + size * (edgeId-1) + (idx-1);
	}

	private int getFaceCell(int faceId, int row, int idx)
	{
		assert faceId >= 1 && faceId <= FACE_INFO.length;

		int [] fi = FACE_INFO[faceId-1];
		if (row == 0)
		{
			// the "base" row of this triangle is a ridge
			return getEdgeCell( fi[3] , idx );
		}
		else if (idx == 0)
		{
			// the "left" edge of this triangle
			int leftEdgeId = fi[5];
			int [] ei = EDGE_INFO[leftEdgeId-1];
			return getEdgeCell( leftEdgeId,
				ei[0] == fi[0] ? row : (size + 1 - row));
		}
		else if (idx == size + 1 - row)
		{
			// the "right" edge of this triangle
			int rightEdgeId = fi[4];
			int [] ei = EDGE_INFO[rightEdgeId-1];
			return getEdgeCell( rightEdgeId,
				ei[0] == fi[1] ? row : (size + 1 - row));
		}
		else
		{
			int n = size - row;
			int b = (size * (size-1) - n * (n+1)) / 2;
			int i = b + idx - 1;

			int eachFaceSize = size * (size-1) / 2;
			return 13 + 30 * size + eachFaceSize * (faceId-1) + i;
		}
	}

	//implements Geometry
	public int [] getNeighbors(int cellId)
	{
		if (cellId < 1)
		{
			throw new Error("Invalid argument : "+cellId);
		}
		if (cellId <= 12)
		{
			int [] pi = PENT_INFO[cellId-1];
			int [] rv = new int[5];
			for (int i = 0; i < 5; i++)
			{
				int [] ei = EDGE_INFO[pi[i]-1];
				rv[i] = getEdgeCell(pi[i], ei[0]==cellId ? 1 : size);
			}
			return rv;
		}
		else if (cellId <= 30 * size + 12)
		{
			int edgeId = (cellId-13) / size + 1;
			int idx = (cellId-13) % size + 1;
			int [] rv = new int[] {
				getEdgeCell(edgeId, idx + 1),
				0,
				0,
				getEdgeCell(edgeId, idx - 1),
				0,
				0 };

			int [] ei = EDGE_INFO[edgeId-1];
			int faceR = ei[2];
			int [] faceRI = FACE_INFO[faceR-1];
			if (faceRI[3] == edgeId)
			{
				// this edge is the face's "base" side
				rv[1] = getFaceCell(faceR, 1, size-idx);
				rv[2] = getFaceCell(faceR, 1, size+1-idx);
			}
			else if (faceRI[4] == edgeId)
			{
				// this edge is the face's "right" side
				rv[1] = getFaceCell(faceR, size-idx, idx);
				rv[2] = getFaceCell(faceR, size+1-idx, idx-1);
			}
			else
			{
				// this edge is the face's "left" side
				rv[1] = getFaceCell(faceR, idx, 1);
				rv[2] = getFaceCell(faceR, idx-1, 1);
			}

			int faceL = ei[3];
			int [] faceLI = FACE_INFO[faceL-1];
			if (faceLI[3] == edgeId)
			{
				// this edge is the face's "base" side
				rv[4] = getFaceCell(faceL, 1, idx-1);
				rv[5] = getFaceCell(faceL, 1, idx);
			}
			else if (faceLI[4] == edgeId)
			{
				// this edge is the face's "right" side
				rv[4] = getFaceCell(faceL, idx-1, size-(idx-1));
				rv[5] = getFaceCell(faceL, idx, size-idx);
			}
			else
			{
				// this edge is the face's "left" side
				rv[4] = getFaceCell(faceL, size+1-idx, 1);
				rv[5] = getFaceCell(faceL, size-idx, 1);
			}
			return rv;
		}
		else
		{
			int baseAllFaces = 13 + 30 * size;
			int eachFaceSize = size * (size-1) / 2;

			int faceId = (cellId-baseAllFaces) / eachFaceSize + 1;
			int i = (cellId-baseAllFaces) % eachFaceSize;
			int j = eachFaceSize - i;

			int row = size-1 - (int)Math.floor((-1 + Math.sqrt(8 * (j-1) + 1)) / 2);
			int n = size - row;
			int b = (size * (size - 1) - n * (n+1)) / 2;
			int idx = i - b + 1;

			int [] rv = new int[] {
				getFaceCell(faceId, row, idx+1),
				getFaceCell(faceId, row-1, idx+1),
				getFaceCell(faceId, row-1, idx),
				getFaceCell(faceId, row, idx-1),
				getFaceCell(faceId, row+1, idx-1),
				getFaceCell(faceId, row+1, idx)
				};
			return rv;
		}
	}

	private class MyVertexId implements VertexId
	{
		int cell1;
		int cell2;
		int cell3;

		MyVertexId(int cell1, int cell2, int cell3)
		{
			assert cell1 < cell2 && cell1 < cell3;
			this.cell1 = cell1;
			this.cell2 = cell2;
			this.cell3 = cell3;
		}

		//implements Geometry.VertexId
		public int [] getAdjacentCells()
		{
			return new int[] { cell1, cell2, cell3 };
		}
	}

	private class MyEdgeId implements EdgeId
	{
		int cell1;
		int cell2;

		MyEdgeId(int cell1, int cell2)
		{
			assert cell1 < cell2;
			this.cell1 = cell1;
			this.cell2 = cell2;
		}

		//implements Geometry.EdgeId
		public int [] getAdjacentCells()
		{
			return new int[] { cell1, cell2 };
		}

		//implements Geometry.EdgeId
		public VertexId [] getEndpoints()
		{
			return getEdgeEndpoints(this);
		}
	}

	//implements Geometry
	public EdgeId getEdgeBetween(int cell1, int cell2)
	{
		if (cell1 < cell2)
			return new MyEdgeId(cell1, cell2);
		else
			return new MyEdgeId(cell2, cell1);
	}

	VertexId getVertexBetween(int cell1, int cell2, int cell3)
	{
		if (cell1 < cell2 && cell1 < cell3)
			return new MyVertexId(cell1, cell2, cell3);
		else if (cell2 < cell1 && cell2 < cell3)
			return new MyVertexId(cell2, cell3, cell1);
		else
			return new MyVertexId(cell3, cell1, cell2);
	}

	VertexId [] getEdgeEndpoints(MyEdgeId edgeId)
	{
		int [] adj = getNeighbors(edgeId.cell1);
		for (int i = 0, len = adj.length; i < len; i++)
		{
			if (adj[i] == edgeId.cell2)
			{
				return new VertexId[] {
					getVertexBetween(edgeId.cell1, edgeId.cell2, adj[(i+1)%len]),
					getVertexBetween(edgeId.cell2, edgeId.cell1, adj[(i+len-1)%len])
					};
			}
		}
		throw new Error("Invalid edge : " + edgeId);
	}

	//implements Geometry
	public VertexId [] getSurroundingVertices(int cellId)
	{
		int [] nn = getNeighbors(cellId);
		int len = nn.length;

		VertexId [] rv = new VertexId[len];
		for (int i = 0; i < len; i++)
		{
			rv[i] = getVertexBetween(cellId, nn[i], nn[(i+1)%len]);
		}
		return rv;
	}

	//implements Geometry
	public EdgeId [] getSurroundingEdges(int cellId)
	{
		int [] nn = getNeighbors(cellId);
		int len = nn.length;

		EdgeId [] rv = new EdgeId[len];
		for (int i = 0; i < len; i++)
		{
			rv[i] = getEdgeBetween(cellId, nn[i]);
		}
		return rv;
	}

	//implements Geometry
	public Point3d getCenterPoint(int cellIdx)
	{
		if (cellIdx <= 12)
		{
			//pent cell
			if (cellIdx == 1)
				return fromPolar(0, Math.PI/2);
			else if (cellIdx <= 6)
				return fromPolar(
					-(Math.PI * 2.0/5.0 * (cellIdx-1)),
					ATAN12);
			else if (cellIdx <= 11)
				return fromPolar(
					-(Math.PI * 2.0/5.0 * (cellIdx-5.5)),
					-ATAN12);
			else
				return fromPolar(0, -Math.PI/2);
		}
		else if (cellIdx <= 30 * size + 12)
		{
			//edge cell

			int edgeId = (cellIdx-13) / size + 1;
			int idx = (cellIdx-13) % size + 1;

			Vector3d beginPt = new Vector3d(getCenterPoint(EDGE_INFO[edgeId-1][0]));
			Vector3d endPt = new Vector3d(getCenterPoint(EDGE_INFO[edgeId-1][1]));

			double dp = beginPt.dot(endPt);
			double angl = Math.acos(dp);

			Vector3d proj = new Vector3d(beginPt);
			proj.scale(dp);

			Vector3d orth = new Vector3d(endPt);
			orth.sub(proj);
			orth.normalize();

			double desireAngl = idx * angl / (size+1);
			beginPt.scale(Math.cos(desireAngl));
			orth.scale(Math.sin(desireAngl));
			beginPt.add(orth);
			return new Point3d(beginPt);
		}
		else
		{
			// face cell

			int baseAllFaces = 13 + 30 * size;
			int eachFaceSize = size * (size-1) / 2;

			int faceId = (cellIdx - baseAllFaces) / eachFaceSize + 1;
			int i = (cellIdx - baseAllFaces) % eachFaceSize;
			int j = eachFaceSize - i;

			int row = size - 1 - (int)Math.floor((-1 + Math.sqrt(8 * (j-1) + 1))/2);
			int n = size - row;
			int b = (size * (size - 1) - n * (n+1)) / 2;
			int idx = i - b + 1;

			int refCell1 = getFaceCell(faceId, row, 0);
			int refCell2 = getFaceCell(faceId, row, size + 1 - row);

			Vector3d beginPt = new Vector3d(getCenterPoint(refCell1));
			Vector3d endPt = new Vector3d(getCenterPoint(refCell2));

			double dp = beginPt.dot(endPt);
			double angl = Math.acos(dp);

			Vector3d proj = new Vector3d(beginPt);
			proj.scale(dp);

			Vector3d orth = new Vector3d(endPt);
			orth.sub(proj);
			orth.normalize();

			double desireAngl = idx * angl / (size+1-row);
			beginPt.scale(Math.cos(desireAngl));
			orth.scale(Math.sin(desireAngl));
			beginPt.add(orth);
			return new Point3d(beginPt);
		}
	}

	private static final double ATAN12 = Math.atan(0.5);

	//implements Geometry
	public int findCell(Point3d pt)
	{
		int best = 1;
		double bestDist = Double.POSITIVE_INFINITY;
		Vector3d v = new Vector3d();

		for (int i = 0, numCells = getCellCount(); i < numCells; i++)
		{
			Point3d p = getCenterPoint(i+1);
			v.sub(pt,p);
			double x = v.length();
			if (x<bestDist)
			{
				best=i+1;
				bestDist=x;
			}
		}
		return best;
	}

	public static Point3d fromPolar(double lgt, double lat)
	{
		double zz = Math.cos(lat);
		return new Point3d(
			Math.cos(lgt) * zz,
			Math.sin(lgt) * zz,
			Math.sin(lat)
			);
	}

	public static double getLatitude(Point3d pt)
	{
		return Math.asin(pt.z);
	}

	public static double getLongitude(Point3d pt)
	{
		return Math.atan2(pt.y, pt.x);
	}

	public static String getGeographicCoordinateString(Point3d pt)
	{
		double lat = getLatitude(pt);
		double lgt = getLongitude(pt);
		return String.format("%.2f%s%s, %.2f%s%s",
			Math.abs(Math.toDegrees(lat)),
			"deg",
			lat > 0 ? "N" : lat < 0 ? "S" : "",
			Math.abs(Math.toDegrees(lgt)),
			"deg",
			lgt > 0 ? "E" : lgt < 0 ? "W" : ""
			);
	}
}
