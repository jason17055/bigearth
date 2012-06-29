/**
 * sz=0 ; 4 rows, 12 cells
 *        /-----1-----\
 *       /   /  |  \   \
 *      2---3---4---5---6---
 *     / \ / \ / \ / \ / \
 *     ---7---8---9--10--11
 *         \   \  |  /  /
 *          \----12----/
 *
 * always 12 pentagon faces, regardless of size (cell ids 1-12)
 * 30*sz hexagon faces along the 30 edges (cell ids 13-?)
 * 20*(sz)(sz-1)/2 hexagon faces along the 20 triangle faces
 *
 * numCells = 12 + 30*sz + 20*sz*(sz-1)/2
 *          = 12 + 30*sz + 10*sz*sz - 10*sz
 *          = 12 + 20*sz + 10*sz^2
 */
function SphereGeometry(size)
{
	this.size = size;
}

SphereGeometry.prototype.getCellCount = function()
{
	var sz = this.size;
	return (10 * sz + 20) * sz + 12;
};

// there are 12 special pentagon cells; this array describes the
// edges connecting to each of the pentagon cells
//SphereGeometry.prototype.PENT_INFO = {
//	1: [ 
//	};

// first two indexes are the endpoints of the edge (in lexigraphic order)
// 3rd and 4th values are face ids (right, then left)
SphereGeometry.prototype.EDGE_INFO = {
	1: [ 1, 2, 5, 1 ],
	2: [ 1, 3, 1, 2 ],
	3: [ 1, 4, 2, 3 ],
	4: [ 1, 5, 3, 4 ],
	5: [ 1, 6, 4, 5 ],
	6: [ 2, 3, 6, 1 ],
	7: [ 3, 4, 8, 2 ],
	8: [ 4, 5, 10, 3 ],
	9: [ 5, 6, 12, 4 ],
	10: [ 2, 6, 5, 14 ],
	11: [ 2, 7, 15, 6 ],
	12: [ 3, 7, 6, 7 ],
	13: [ 3, 8, 7, 8 ],
	14: [ 4, 8, 8, 9],
	15: [ 4, 9, 9, 10 ],
	16: [ 5, 9, 10, 11 ],
	17: [ 5, 10, 11, 12 ],
	18: [ 6, 10, 12, 13 ],
	19: [ 6, 11, 13, 14 ],
	20: [ 2, 11, 14, 15 ],
	21: [ 7, 8, 16, 7 ],
	22: [ 8, 9, 17, 9 ],
	23: [ 9, 10, 18, 11 ],
	24: [ 10, 11, 19, 13 ],
	25: [ 7, 11, 15, 20 ],
	26: [ 8, 12, 16, 17 ],
	27: [ 9, 12, 17, 18 ],
	28: [ 10, 12, 18, 19 ],
	29: [ 11, 12, 19, 20 ],
	30: [ 7, 12, 20, 16 ]
	};

// For each triangular face, these are the Pentagon cells that make
// up its corners, and the edges that make the sides. The
// lexigraphically-smallest pentagon cell is designated the first
// corner. Order is counter-clockwise.
SphereGeometry.prototype.FACE_INFO = {
	1: [ 1,2,3,      1,  6,  2 ],
	2: [ 1,3,4,      2,  7,  3 ],
	3: [ 1,4,5,      3,  8,  4 ],
	4: [ 1,5,6,      4,  9,  5 ],
	5: [ 1,6,2,      5, 10,  1 ],
	6: [ 2,7,3,     11, 12,  6 ],
	7: [ 3,7,8,     12, 21, 13 ],
	8: [ 3,8,4,     13, 14,  7 ],
	9: [ 4,8,9,     14, 22, 15 ],
	10: [ 4,9,5,    15, 16,  8 ],
	11: [ 5,9,10,   16, 23, 17 ],
	12: [ 5,10,6,   17, 18,  9 ],
	13: [ 6,10,11,  18, 24, 19 ],
	14: [ 2,6,11,   10, 19, 20 ],
	15: [ 2,11,7,   20, 25, 11 ],
	16: [ 7,12,8,   30, 26, 21 ],
	17: [ 8,12,9,   26, 27, 22 ],
	18: [ 9,12,10,  27, 28, 23 ],
	19: [ 10,12,11, 28, 29, 24 ],
	20: [ 7,11,12,  25, 29, 30 ]
	};

SphereGeometry.prototype.getEdgeCell = function(edgeId, idx)
{
	var sz = this.size;
	if (idx == 0) {
		return this.EDGE_INFO[edgeId][0];
	}
	else if (idx == sz+1) {
		return this.EDGE_INFO[edgeId][1];
	}
	else {
		return 13 + sz * (edgeId-1) + (idx-1);
	}
};

SphereGeometry.prototype.getFaceCell = function(faceId, row, idx)
{
	var sz = this.size;
	var fi = this.FACE_INFO[faceId];
	if (row == 0)
	{
		var baseEdge = fi[3];
		return this.getEdgeCell(baseEdge, idx);
	}
	else if (idx == 0)
	{
		var leftEdge = fi[5];
		var ei = this.EDGE_INFO[leftEdge];
		return this.getEdgeCell(leftEdge, ei[0]==fi[0] ? row : (sz+1-row));
	}
	else if (idx == sz + 1 - row)
	{
		var rightEdge = fi[4];
		var ei = this.EDGE_INFO[rightEdge];
		return this.getEdgeCell(rightEdge, ei[0]==fi[1] ? row : (sz+1-row));
	}
	else
	{
		var n = sz - row;
		var b = (sz * (sz-1) - n * (n+1)) / 2;
		var i = b + idx - 1;

		var eachFaceSize = sz * (sz-1) / 2;
		return 13 + 30 * sz + eachFaceSize * (faceId-1) + i;
	}
};

SphereGeometry.prototype.getNeighbors = function(cellIdx)
{
	var sz = this.size;

	if (cellIdx <= 12)
	{
		switch (cellIdx) {
		case 1:
			return [
			this.getEdgeCell(5, 1),
			this.getEdgeCell(4, 1),
			this.getEdgeCell(3, 1),
			this.getEdgeCell(2, 1),
			this.getEdgeCell(1, 1) ];
		case 2:
			return [
			this.getEdgeCell(1, sz),
			this.getEdgeCell(6, 1),
			this.getEdgeCell(11, 1),
			this.getEdgeCell(20, 1),
			this.getEdgeCell(10, 1) ];
		case 3:
			return [
			this.getEdgeCell(2, sz),
			this.getEdgeCell(7, 1),
			this.getEdgeCell(13, 1),
			this.getEdgeCell(12, 1),
			this.getEdgeCell(6, sz) ];
		case 4:
			return [
			this.getEdgeCell(3, sz),
			this.getEdgeCell(8, 1),
			this.getEdgeCell(15, 1),
			this.getEdgeCell(14, 1),
			this.getEdgeCell(7, sz) ];
		case 5:
			return [
			this.getEdgeCell(4, sz),
			this.getEdgeCell(9, 1),
			this.getEdgeCell(17, 1),
			this.getEdgeCell(16, 1),
			this.getEdgeCell(8, sz) ];
		case 6:
			return [
			this.getEdgeCell(5, sz),
			this.getEdgeCell(10, sz),
			this.getEdgeCell(19, 1),
			this.getEdgeCell(18, 1),
			this.getEdgeCell(9, sz) ];
		case 7:
			return [
			this.getEdgeCell(11, sz),
			this.getEdgeCell(12, sz),
			this.getEdgeCell(21, 1),
			this.getEdgeCell(30, 1),
			this.getEdgeCell(25, 1) ];
		case 8:
			return [
			this.getEdgeCell(13, sz),
			this.getEdgeCell(14, sz),
			this.getEdgeCell(22, 1),
			this.getEdgeCell(26, 1),
			this.getEdgeCell(21, sz) ];
		case 9:
			return [
			this.getEdgeCell(15, sz),
			this.getEdgeCell(16, sz),
			this.getEdgeCell(23, 1),
			this.getEdgeCell(27, 1),
			this.getEdgeCell(22, sz) ];
		case 10:
			return [
			this.getEdgeCell(17, sz),
			this.getEdgeCell(18, sz),
			this.getEdgeCell(24, 1),
			this.getEdgeCell(28, 1),
			this.getEdgeCell(23, sz) ];
		case 11:
			return [
			this.getEdgeCell(19, sz),
			this.getEdgeCell(20, sz),
			this.getEdgeCell(25, sz),
			this.getEdgeCell(29, 1),
			this.getEdgeCell(24, sz) ];
		case 12:
			return [
			this.getEdgeCell(26, sz),
			this.getEdgeCell(27, sz),
			this.getEdgeCell(28, sz),
			this.getEdgeCell(29, sz),
			this.getEdgeCell(30, sz) ];
		}
		alert("should not get here");
	}
	else if (cellIdx <= 30 * sz + 12)
	{
		var edgeId = Math.floor((cellIdx - 13) / sz) + 1;
		var idx = (cellIdx - 13) % sz + 1;
		var rv = [
			this.getEdgeCell(edgeId, idx+1),
			0,
			0,
			this.getEdgeCell(edgeId, idx-1),
			0,
			0 ];

		var ei = this.EDGE_INFO[edgeId];
		var faceR = ei[2];
		var faceRI = this.FACE_INFO[faceR];
		if (faceRI[3] == edgeId)
		{
			// this edge is the face's "base" side
			rv[1] = this.getFaceCell(faceR, 1, sz-idx);
			rv[2] = this.getFaceCell(faceR, 1, sz+1-idx);
		}
		else if (faceRI[4] == edgeId)
		{
			// this edge is the face's "right" side
			rv[1] = this.getFaceCell(faceR, sz-idx, idx);
			rv[2] = this.getFaceCell(faceR, sz+1-idx, idx-1);
		}
		else
		{
			// this edge is the face's "left" side
			rv[1] = this.getFaceCell(faceR, idx, 1);
			rv[2] = this.getFaceCell(faceR, idx-1, 1);
		}

		var faceL = ei[3];
		var faceLI = this.FACE_INFO[faceL];
		if (faceLI[3] == edgeId)
		{
			// this edge is the face's "base" side
			rv[4] = this.getFaceCell(faceL, 1, idx-1);
			rv[5] = this.getFaceCell(faceL, 1, idx);
		}
		else if (faceLI[4] == edgeId)
		{
			// this edge is the face's "right" side
			rv[4] = this.getFaceCell(faceL, idx-1, sz-(idx-1));
			rv[5] = this.getFaceCell(faceL, idx, sz-idx);
		}
		else
		{
			// this edge is the face's "left" side
			rv[4] = this.getFaceCell(faceL, sz+1-idx, 1);
			rv[5] = this.getFaceCell(faceL, sz-idx, 1);
		}
		return rv;
	}
	else
	{
		var baseAllFaces = 13 + 30 * sz;
		var eachFaceSize = sz * (sz-1) / 2;

		var faceId = Math.floor((cellIdx - baseAllFaces) / eachFaceSize) + 1;
		var i = (cellIdx - baseAllFaces) % eachFaceSize;
		var j = eachFaceSize - i;

		var row = sz - 1 - Math.floor((-1 + Math.sqrt(8*(j-1)+1))/2);
		var n = sz - row;
		var b = (sz * (sz-1) - n * (n+1)) / 2;
		var idx = i - b + 1;

		var rv = [
			this.getFaceCell(faceId, row, idx+1),
			this.getFaceCell(faceId, row-1, idx+1),
			this.getFaceCell(faceId, row-1, idx),
			this.getFaceCell(faceId, row, idx-1),
			this.getFaceCell(faceId, row+1, idx-1),
			this.getFaceCell(faceId, row+1, idx)
			];
		return rv;
	}
};

// Edges are identified as <C1,C2>, i.e. the ids of the
// two adjoining cells. The lexigraphically smallest cell id
// is listed first.
//
SphereGeometry.prototype._makeEdge = function(cell1, cell2)
{
	if (cell1 < cell2)
	{
		return cell1+"-"+cell2;
	}
	else
	{
		return cell2+"-"+cell1;
	}
};

// Vertices are always identified as <C1,C2,C3>, i.e. the ids of
// the three adjoining cells. The lexigraphically smallest cell id
// is first. The other two are chosen so that the three cells are
// in clockwise order around the vertex being represented.
//
SphereGeometry.prototype._makeVertex = function(cell1, cell2, cell3)
{
	if (cell1 < cell2 && cell1 < cell3)
	{
		return cell1+","+cell2+","+cell3;
	}
	else if (cell2 < cell1 && cell2 < cell3)
	{
		return cell2+","+cell3+","+cell1;
	}
	else // cell3 is smallest
	{
		return cell3+","+cell1+","+cell2;
	}
};

SphereGeometry.prototype.getCellsAdjacentToEdge = function(edgeId)
{
	var cc = edgeId.split(/-/);
	return [
		+cc[0],
		+cc[1]
		];
};

SphereGeometry.prototype.getVerticesAdjacentToEdge = function(edgeId)
{
	var cc = edgeId.split(/-/);
	var c = parseInt(cc[0]);
	var d = parseInt(cc[1]);

	var adj = this.getNeighbors(c);
	for (var i = 0, l = adj.length; i < l; i++)
	{
		if (adj[i] == d)
		{
			return [
			this._makeVertex(c,d,adj[(i+1)%l]),
			this._makeVertex(d,c,adj[(i+l-1)%l])
			];
		}
	}
	//unexpected
	return null;
};

SphereGeometry.prototype.getVerticesAdjacentToCell = function(cellIdx)
{
	var n = this.getNeighbors(cellIdx);
	var a = new Array();

	var l = n.length;
	for (var i = 0; i < l; i++)
	{
		a.push(this._makeVertex(cellIdx, n[i], n[(i+1)%l]));
	}
	return a;
};

SphereGeometry.prototype.getEdgesAdjacentToCell = function(cellIdx)
{
	var n = this.getNeighbors(cellIdx);
	var a = [];

	var l = n.length;
	for (var i = 0; i < l; i++)
	{
		a.push(this._makeEdge(cellIdx, n[i]));
	}
	return a;
};

SphereGeometry.prototype.getCellsAdjacentToVertex = function(vertexId)
{
	var a = vertexId.split(',');
	return [
		parseInt(a[0]),
		parseInt(a[1]),
		parseInt(a[2])
		];
};

SphereGeometry.prototype.getVerticesAdjacentToVertex = function(vertexId)
{
	var cc = this.getCellsAdjacentToVertex(vertexId);
	var rv = new Array();
	for (var i = 0; i < 3; i++)
	{
		var x = cc[(i+1)%3];
		var n = this.getNeighbors(cc[i]);
		var f = null;
		for (var j = 0, l = n.length; j < l; j++)
		{
			if (n[(j+1)%l] == x)
			{
				f = this._makeVertex(cc[i],n[j],x);
			}
		}
		rv.push(f);
	}

	return rv;
};

function fromPolar(lgt, lat)
{
	var zz = Math.cos(lat);
	return {
	x: Math.cos(lgt) * zz,
	y: Math.sin(lgt) * zz,
	z: Math.sin(lat)
	};
}

SphereGeometry.prototype.makeEdgeFromEndpoints = function(vertex1, vertex2)
{
	var cc = this.getCellsAdjacentToVertex(vertex1);
	var dd = this.getCellsAdjacentToVertex(vertex2);

	// assumption- two of the cell ids in cc match two of
	// the cell ids in dd. We will look for the cell id in
	// cc that does not match any in dd, and conclude that the
	// other two ids in cc are the matching ones.

	if (cc[0] != dd[0] && cc[0] != dd[1] && cc[0] != dd[2])
	{
		return this._makeEdge(cc[1],cc[2]);
	}
	else if (cc[1] != dd[0] && cc[1] != dd[1] && cc[1] != dd[2])
	{
		return this._makeEdge(cc[0],cc[2]);
	}
	else
	{
		return this._makeEdge(cc[0],cc[1]);
	}
};

SphereGeometry.ATAN12 = Math.atan(0.5);

SphereGeometry.prototype.getSpherePoint = function(cellIdx)
{
	var sz = this.size;

	if (cellIdx <= 12)
	{
		// pent cell

		if (cellIdx == 1)
			return fromPolar(0, Math.PI/2);
		else if (cellIdx <= 6)
			return fromPolar(Math.PI * 2 / 5 * (cellIdx-1), SphereGeometry.ATAN12);
		else if (cellIdx <= 11)
			return fromPolar(Math.PI * 2 / 5 * (cellIdx-5.5), -SphereGeometry.ATAN12);
		else
			return fromPolar(0, -Math.PI/2);
	}
	else if (cellIdx <= 30 * sz + 12)
	{
		// edge cell

		var edgeId = Math.floor((cellIdx - 13) / sz) + 1;
		var idx = (cellIdx - 13) % sz + 1;

		var beginPt = this.getSpherePoint(this.EDGE_INFO[edgeId][0]);
		var endPt = this.getSpherePoint(this.EDGE_INFO[edgeId][1]);

		var dp = dotProduct(beginPt, endPt);
		var angl = Math.acos(dp);

		var proj = scaleVector(beginPt, dp);
		var orth = subtractVector(endPt, proj);
		orth = makeUnitVector(orth);

		var desireAngl = idx * angl / (sz+1)
		return addVector(scaleVector(beginPt, Math.cos(desireAngl)),
			scaleVector(orth, Math.sin(desireAngl)));
	}
	else
	{
		// face cell

		var baseAllFaces = 13 + 30 * sz;
		var eachFaceSize = sz * (sz-1) / 2;

		var faceId = Math.floor((cellIdx - baseAllFaces) / eachFaceSize) + 1;
		var i = (cellIdx - baseAllFaces) % eachFaceSize;
		var j = eachFaceSize - i;

		var row = sz - 1 - Math.floor((-1 + Math.sqrt(8*(j-1)+1))/2);
		var n = sz - row;
		var b = (sz * (sz-1) - n * (n+1)) / 2;
		var idx = i - b + 1;
		
		var refCell1 = this.getFaceCell(faceId, row, 0);
		var refCell2 = this.getFaceCell(faceId, row, sz+1-row);

		var beginPt = this.getSpherePoint(refCell1);
		var endPt = this.getSpherePoint(refCell2);

		var dp = dotProduct(beginPt, endPt);
		var angl = Math.acos(dp);

		var proj = scaleVector(beginPt, dp);
		var orth = subtractVector(endPt, proj);
		orth = makeUnitVector(orth);

		var desireAngl = idx * angl / (sz+1-row)
		return addVector(scaleVector(beginPt, Math.cos(desireAngl)),
			scaleVector(orth, Math.sin(desireAngl)));
	}
};

function subtractVector(v1, v2)
{
	return {
	x: v1.x - v2.x,
	y: v1.y - v2.y,
	z: v1.z - v2.z
	};
}

function addVector(v1, v2)
{
	return {
	x: v1.x + v2.x,
	y: v1.y + v2.y,
	z: v1.z + v2.z
	};
}

function scaleVector(v, f)
{
	return {
	x: v.x * f,
	y: v.y * f,
	z: v.z * f
	};
}

function vectorLength(v)
{
	return Math.sqrt(
		Math.pow(v.x,2) + Math.pow(v.y,2) + Math.pow(v.z,2)
		);
}

function makeUnitVector(v)
{
	return scaleVector(v, 1/vectorLength(v));
}

function dotProduct(v1, v2)
{
	return v1.x * v2.x + v1.y * v2.y + v1.z * v2.z;
}


if (typeof exports !== 'undefined')
{
	exports.SphereGeometry = SphereGeometry;
}
