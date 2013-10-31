package bigearth;

import javax.vecmath.*;
import bigearth.geom.Cursor;

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
	/// 5th and 6th fields indicate the orientation within the pentagon
	/// regions of the edge.
	static int [][] EDGE_INFO = {
	//edge 1..10
		{  1, 2,    5, 1,    4, 0 },
		{  1, 3,    1, 2,    3, 0 },
		{  1, 4,    2, 3,    2, 0 },
		{  1, 5,    3, 4,    1, 0 },
		{  1, 6,    4, 5,    0, 0 },
		{  2, 3,    6, 1,    1, 4 },
        	{  3, 4,    8, 2,    1, 4 },
        	{  4, 5,   10, 3,    1, 4 },
        	{  5, 6,   12, 4,    1, 4 },
        	{  2, 6,    5,14,    4, 1 },
	//edge 11..20
        	{  2, 7,   15, 6,    2, 0 },
        	{  3, 7,    6, 7,    3, 1 },
        	{  3, 8,    7, 8,    2, 0 },
        	{  4, 8,    8, 9,    3, 1 },
        	{  4, 9,    9,10,    2, 0 },
        	{  5, 9,   10,11,    3, 1 },
        	{  5,10,   11,12,    2, 0 },
        	{  6,10,   12,13,    3, 1 },
        	{  6,11,   13,14,    2, 0 },
        	{  2,11,   14,15,    3, 1 },
	//edge 21..30
        	{  7, 8,   16, 7,    2, 4 },
        	{  8, 9,   17, 9,    2, 4 },
        	{  9,10,   18,11,    2, 4 },
        	{ 10,11,   19,13,    2, 4 },
        	{  7,11,   15,20,    4, 2 },
        	{  8,12,   16,17,    3, 0 },
        	{  9,12,   17,18,    3, 1 },
        	{ 10,12,   18,19,    3, 2 },
        	{ 11,12,   19,20,    3, 3 },
        	{  7,12,   20,16,    3, 4 }
		};

	private static boolean checkEdgeBackAttitudes()
	{
		for (int i = 0; i < PENT_INFO.length; i++) {
			for (int j = 0; j < 5; j++) {
				int eid = PENT_INFO[i][j] - 1;
				if (EDGE_INFO[eid][0] == i+1) {
					if (EDGE_INFO[eid][4] != j) {
						throw new Error("wrong back-link for edge "+eid+".0");
					}
				}
				else if (EDGE_INFO[eid][1] == i+1) {
					if (EDGE_INFO[eid][5] != j) {
						throw new Error("wrong back-link for edge "+eid+".1");
					}
				}
				else {
					return false;
				}
			}
		}
		return true;
	}
	static
	{
		assert checkEdgeBackAttitudes();
	}

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

	@Override
	public String toString()
	{
		return "sphere:"+size;
	}

	//implements Geometry
	public int getFaceCount()
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
	public int getNeighborCount(int cellId)
	{
		return cellId <= 12 ? 5 : 6;
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

	private static class MyVertexId implements VertexId
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

		@Override
		public boolean equals(Object obj)
		{
			if (obj instanceof MyVertexId)
			{
				MyVertexId rhs = (MyVertexId) obj;
				return this.cell1 == rhs.cell1 &&
					this.cell2 == rhs.cell2 &&
					this.cell3 == rhs.cell3;
			}
			return false;
		}

		@Override
		public int hashCode()
		{
			return cell1 + 257 * (cell2 + 257 * (cell3));
		}

		@Override
		public String toString()
		{
			return String.format("V<%d,%d,%d>", cell1, cell2, cell3);
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

		@Override
		public boolean equals(Object obj)
		{
			if (obj instanceof MyEdgeId)
			{
				MyEdgeId rhs = (MyEdgeId) obj;
				return this.cell1 == rhs.cell1 &&
					this.cell2 == rhs.cell2;
			}
			return false;
		}

		@Override
		public int hashCode()
		{
			return cell1 + 257 * cell2;
		}

		@Override
		public String toString()
		{
			return String.format("E<%d,%d>", cell1, cell2);
		}
	}

	//implements Geometry
	public EdgeId getEdgeByEndpoints(VertexId fromVertex, VertexId toVertex)
	{
		int [] cc = fromVertex.getAdjacentCells();
		int [] dd = toVertex.getAdjacentCells();

		// assumption- two of the cell ids in cc[] match two of
		// the cell ids in dd[]. We will look for the cell id in
		// cc[] that does not match any in dd[], and conclude
		// that the other two ids in cc[] are the matching ones.

		if (cc[0] != dd[0] && cc[0] != dd[1] && cc[0] != dd[2])
		{
			return getEdgeBetween(cc[1], cc[2]);
		}
		else if (cc[1] != dd[0] && cc[1] != dd[1] && cc[1] != dd[2])
		{
			return getEdgeBetween(cc[0], cc[2]);
		}
		else
		{
			assert(cc[2] != dd[0] && cc[2] != dd[1] && cc[2] != dd[2]);
			return getEdgeBetween(cc[0], cc[1]);
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

	//implements Geometry
	public VertexId getVertex(int cell1, int cell2, int cell3)
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
					getVertex(edgeId.cell1, edgeId.cell2, adj[(i+1)%len]),
					getVertex(edgeId.cell2, edgeId.cell1, adj[(i+len-1)%len])
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
			rv[i] = getVertex(cellId, nn[i], nn[(i+1)%len]);
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
	public VertexId [] getNearbyVertices(VertexId vertex)
	{
		int [] cc = vertex.getAdjacentCells();
		VertexId [] result = new VertexId[cc.length];
		for (int i = 0; i < cc.length; i++)
		{
			int x = cc[(i+1)%cc.length];
			int [] nn = getNeighbors(cc[i]);
			for (int j = 0; j < nn.length; j++)
			{
				if (nn[(j+1)%nn.length] == x)
				{
					result[i] = getVertex(cc[i], nn[j], x);
				}
			}
			assert result[i] != null;
		}

		return result;
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

	public Point3d [] getCellBoundary(int cellId)
	{
		Point3d myCp = getCenterPoint(cellId);
		int [] nn = getNeighbors(cellId);
		Point3d [] neighborCp = new Point3d[nn.length];
		for (int i = 0; i < nn.length; i++)
		{
			neighborCp[i] = getCenterPoint(nn[i]);
		}

		Point3d [] result = new Point3d[nn.length];
		Vector3d v = new Vector3d();
		for (int i = 0; i < nn.length; i++)
		{
			v.set(myCp);
			v.add(neighborCp[i]);
			v.add(neighborCp[(i+1)%nn.length]);
			v.normalize();
			result[i] = new Point3d(v);
		}
		return result;
	}

	private static final double ATAN12 = Math.atan(0.5);

	//implements Geometry
	public int findCell(Point3d pt)
	{
		int best = 1;
		double bestDist = Double.POSITIVE_INFINITY;
		Vector3d v = new Vector3d();

		for (int i = 0, numCells = getFaceCount(); i < numCells; i++)
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

	//implements Geometry
	public Point3d getPoint(Location loc)
	{
		if (loc instanceof SimpleLocation)
		{
			SimpleLocation sloc = (SimpleLocation) loc;
			return getCenterPoint(sloc.regionId);
		}
		else if (loc instanceof MyEdgeId)
		{
			MyEdgeId eloc = (MyEdgeId) loc;
			Vector3d v = new Vector3d();
			v.set(getCenterPoint(eloc.cell1));
			v.add(getCenterPoint(eloc.cell2));
			v.normalize();
			return new Point3d(v);
		}
		else if (loc instanceof MyVertexId)
		{
			MyVertexId vloc = (MyVertexId) loc;
			Vector3d v = new Vector3d();
			v.set(getCenterPoint(vloc.cell1));
			v.add(getCenterPoint(vloc.cell2));
			v.add(getCenterPoint(vloc.cell3));
			v.normalize();
			return new Point3d(v);
		}
		else
		{
			throw new IllegalArgumentException("invalid location");
		}
	}

	//implements Geometry(new)
	public void rotateCursor(Cursor c, int adjust)
	{
		int k = c.location < 12 ? 5 : 6;
		c.orientation = (c.orientation + k + (adjust%k)) % k;
	}

	private void _E_cell(Cursor c, int mEdge, int idx, int att)
	{
		if (idx == 0) {

			assert att == EAST;

			c.location = EDGE_INFO[mEdge][0]-1;
			c.orientation = EDGE_INFO[mEdge][4];
		}
		else if (idx == size+1) {

			assert att == WEST;

			c.location = EDGE_INFO[mEdge][1]-1;
			c.orientation = EDGE_INFO[mEdge][5];
		}
		else {
			assert idx >= 1 && idx <= size;

			c.location = 12 + size * mEdge + (idx-1);
			c.orientation = att;
		}
	}

	private void _F_cell(Cursor c, int mFace, int row, int idx, int att)
	{
		assert mFace >= 0 && mFace < FACE_INFO.length;

		int [] fi = FACE_INFO[mFace];
		if (row == 0)
		{
			// the "base" row of this triangle is a ridge
			int mEdge = fi[3] - 1;
			if (EDGE_INFO[mEdge][0] == fi[0]) {
				assert EDGE_INFO[mEdge][1] == fi[1];
				_E_cell(c, mEdge, idx, att);
			}
			else {
				assert EDGE_INFO[mEdge][0] == fi[1];
				assert EDGE_INFO[mEdge][1] == fi[0];
				_E_cell(c, mEdge, size+1-idx, (att+3)%6);
neverReached();
			}
		}
		else if (idx == 0)
		{
			// the "left" edge of this triangle
			int mEdge = fi[5] - 1;
			if (EDGE_INFO[mEdge][0] == fi[0]) {
				assert EDGE_INFO[mEdge][1] == fi[2];
				_E_cell(c, mEdge, row, (att+1)%6);
			}
			else {
				assert EDGE_INFO[mEdge][0] == fi[2];
				assert EDGE_INFO[mEdge][1] == fi[0];
				_E_cell(c, mEdge, size+1-row, (att+4)%6);
neverReached();
			}
		}
		else if (idx == size + 1 - row)
		{
			// the "right" edge of this triangle
			int mEdge = fi[4] - 1;
			if (EDGE_INFO[mEdge][0] == fi[1]) {
				assert EDGE_INFO[mEdge][1] == fi[2];
				_E_cell(c, mEdge, row, (att+2)%6);
			}
			else {
				assert EDGE_INFO[mEdge][0] == fi[2];
				assert EDGE_INFO[mEdge][1] == fi[1];
				_E_cell(c, mEdge, size+1-row, (att+5)%6);
			}
		}
		else
		{
			int n = size - row;
			int b = (size * (size-1) - n * (n+1)) / 2;
			int i = b + idx - 1;

			int eachFaceSize = size * (size-1) / 2;
			c.location = 12 + 30 * size + eachFaceSize * mFace + i;
			c.orientation = att;
		}
	}

private void neverReached() { throw new Error("reached"); }

	//implements Geometry(new)
	public void stepCursor(Cursor c)
	{
		assert size >= 1;

		int cellId = c.location;
		if (cellId < 12)
		{
			assert c.orientation >= 0 && c.orientation < 5;

			int [] pi = PENT_INFO[cellId];
			int mEdge = pi[c.orientation]-1;
			int [] ei = EDGE_INFO[mEdge];
			if (ei[0] == cellId+1) {
				_E_cell(c, mEdge, 1, WEST);
			}
			else {
				assert(ei[1] == cellId+1);
				_E_cell(c, mEdge, size, EAST);
			}
			return;
		}
		else if (cellId - 12 < 30 * size)
		{
			int mEdge = (cellId-12) / size;
			int idx = 1 + (cellId-12) % size;
			switch (c.orientation) {
			case EAST:
				_E_cell(c, mEdge, idx+1, WEST);
				return;

			case NORTHEAST: {
				int mFace = EDGE_INFO[mEdge][2]-1;
				if (FACE_INFO[mFace][3] == mEdge+1) {
					// our edge is the "top" of mFace
					_F_cell(c, mFace, 1, size-idx, SOUTHEAST);
neverReached();
				}
				else if (FACE_INFO[mFace][4] == mEdge+1) {
					// our edge is the "right" side of mFace
					_F_cell(c, mFace, size-idx, idx, SOUTHEAST);
				}
				else {
					// our edge is the "left" side of mFace
					_F_cell(c, mFace, idx, 1, WEST);
				}
				return;
				}

			case NORTHWEST: {
				int mFace = EDGE_INFO[mEdge][2]-1;
				if (FACE_INFO[mFace][3] == mEdge+1) {
					// our edge is the "top" of mFace
					_F_cell(c, mFace, 1, size+1-idx, SOUTHEAST);
neverReached();
				}
				else if (FACE_INFO[mFace][4] == mEdge+1) {
					// our edge is the "right" side of mFace
					_F_cell(c, mFace, size+1-idx, idx-1, EAST);
				}
				else {
					// our edge is the "left" side of mFace
					_F_cell(c, mFace, idx-1, 1, SOUTHWEST);
				}
				return;
				}

			case WEST:
				_E_cell(c, mEdge, idx-1, EAST);
				return;

			case SOUTHWEST: {
				int mFace = EDGE_INFO[mEdge][3]-1;
				if (FACE_INFO[mFace][3] == mEdge+1) {
					// our edge is the "top" of mFace
					_F_cell(c, mFace, 1, idx-1, NORTHEAST);
				}
				else if (FACE_INFO[mFace][4] == mEdge+1) {
					// our edge is the "right" side of mFace
					_F_cell(c, mFace, idx-1, size-(idx-1), SOUTHEAST);
				}
				else {
					// our edge is the "left" side of mFace
					_F_cell(c, mFace, size+1-idx, 1, WEST);
neverReached();
				}
				return;
				}

			case SOUTHEAST: {
				int mFace = EDGE_INFO[mEdge][3]-1;
				if (FACE_INFO[mFace][3] == mEdge+1) {
					// our edge is the "top" of mFace
					_F_cell(c, mFace, 1, idx, NORTHWEST);
				}
				else if (FACE_INFO[mFace][4] == mEdge+1) {
					// our edge is the "right" side of mFace
					_F_cell(c, mFace, idx, size-idx, EAST);
				}
				else {
					// our edge is the "left" side of mFace
					_F_cell(c, mFace, size-idx, 1, NORTHWEST);
neverReached();
				}
				return;
				}

			default:
				throw new Error("not implemented");
			}
		}
		else
		{
			int baseAllFaces = 12 + 30 * size;
			int eachFaceSize = size * (size-1) / 2;

			int mFace = (cellId - baseAllFaces) / eachFaceSize;
			int i = (cellId - baseAllFaces) % eachFaceSize;
			int j = eachFaceSize - i;

			int row = size - 1 - (int)Math.floor((-1 + Math.sqrt(8 * (j-1) + 1))/2);
			int n = size - row;
			int b = (size * (size - 1) - n * (n+1)) / 2;
			int idx = i - b + 1;

			switch (c.orientation) {
			case EAST:
				_F_cell(c, mFace, row, idx+1, WEST);
				return;
			case NORTHEAST:
				_F_cell(c, mFace, row-1, idx+1, SOUTHWEST);
				return;
			case NORTHWEST:
				_F_cell(c, mFace, row-1, idx, SOUTHEAST);
				return;
			case WEST:
				_F_cell(c, mFace, row, idx-1, EAST);
				return;
			case SOUTHWEST:
				_F_cell(c, mFace, row+1, idx-1, NORTHEAST);
				return;
			case SOUTHEAST:
				_F_cell(c, mFace, row+1, idx, NORTHWEST);
				return;
			default:
				throw new Error("not implemented");
			}
		}
	}

	static final int EAST = 0;
	static final int NORTHEAST = 1;
	static final int NORTHWEST = 2;
	static final int WEST = 3;
	static final int SOUTHWEST = 4;
	static final int SOUTHEAST = 5;

//	private int _makeEVertex(int mEdge, int idx, int b)
//	{
//		assert mEdge >= 0 && mEdge < 30;
//		assert idx >= 1 && idx <= size;
//		assert b == 0 || b == 1;
//
//	}
//
//	private int _makeVVertex(int mSpecial, int corner)
//	{
//		assert mSpecial >= 0 && mSpecial < 12;
//		assert corner >= 0 && corner < 5;
//
//		return mSpecial*5 + corner;
//	}
//
//	//implements Geometry(new)
//	public int getVertex(Cursor c)
//	{
//		assert size >= 1;
//
//		int cellId = c.location;
//		if (cellId <= 12)
//		{
//			return _makeVVertex(cellId-1, c.orientation%5);
//		}
//
//		else if (cellId <= 30 * size + 12)
//		{
//			int mEdge = (cellId-13) / size;
//			int idx = ((cellId-13) % size) + 1;
//
//			/*             *****     next E cell
//			 *           3_______2
//			 *           /       \
//			 *          /         \
//			 *        4/           \1
//			 *         \           /
//			 *          \         /
//			 *           \_______/
//			 *           5       0
//			 *             *****     previous E cell
//			 */
//
//			int vtx = c.orientation;
//			if (vtx == 0) {
//				idx--;
//				vtx = 2;
//			} else if (vtx == 5) {
//				idx--;
//				vtx = 3;
//			}
//
//			if (vtx == 2) {
//				return _makeEVertex(mEdge, idx, 0);
//			}
//			else if (vtx == 3) {
//				return _makeEVertex(mEdge, idx, 1);
//			}
//			throw new Error("not implemented");
//		}
//
//		throw new Error("not implemented");
//	}
//
//	//implements Geometry(new)
//	public Cursor fromVertex(int vertex)
//	{
//		assert size >= 1;
//
//		if (vertex < 60) {
//			return new Cursor(vertex/5, vertex%5);
//		}
//
//		vertex -= 60;
//		if (vertex < (size-1)*2*30) {
//			assert size-1 >= 1;
//			int mEdge = vertex / ((size-1)*2);
//			int idx = (vertex % ((size-1)*2)) / 2;
//			int jdx = vertex % 2;
//
//			int b = getEdgeCell(mEdge+1, idx+1);
//			return new Cursor(b, jdx);
//		}
//	}
}
