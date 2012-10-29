package bigearth;

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
}
