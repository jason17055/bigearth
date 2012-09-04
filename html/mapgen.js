function normalize(pt)
{
	var l = Math.sqrt(
		Math.pow(pt.x, 2.0)
		+Math.pow(pt.y, 2.0)
		+Math.pow(pt.z, 2.0)
		);
	return {
	x: pt.x / l,
	y: pt.y / l,
	z: pt.z / l
	};
}

function makeCoords(geometry)
{
	var cellCoords = {};
	var vertexCoords = {};
	var edgeCoords = {};

	var numCells = geometry.getCellCount();
	for (var cellIdx = 1; cellIdx <= numCells; cellIdx++)
	{
		cellCoords[cellIdx] = {
		pt: geometry.getSpherePoint(cellIdx)
		};
	}

	for (var cellIdx = 1; cellIdx <= numCells; cellIdx++)
	{
		var c = cellCoords[cellIdx];
		var adj = geometry.getNeighbors(cellIdx);
		var pts = new Array();
		for (var j = 0, l = adj.length; j < l; j++)
		{
			var d = cellCoords[adj[j]];
			var e = cellCoords[adj[(j+1)%l]];

			var avg = {
			x: (c.pt.x + d.pt.x + e.pt.x) / 3,
			y: (c.pt.y + d.pt.y + e.pt.y) / 3,
			z: (c.pt.z + d.pt.z + e.pt.z) / 3
			};
			avg = normalize(avg);
			pts.push(avg);

			var eId = geometry._makeEdge(cellIdx,adj[j]);
			if (!(eId in edgeCoords))
			{
				var a1 = {
				x: (c.pt.x + d.pt.x) / 2,
				y: (c.pt.y + d.pt.y) / 2,
				z: (c.pt.z + d.pt.z) / 2
				};
				a1 = normalize(a1);
				edgeCoords[eId] = { pt: a1 };
			}

			var vId = geometry._makeVertex(cellIdx,adj[j],adj[(j+1)%l]);
			if (!(vId in vertexCoords))
			{
				vertexCoords[vId] = {
				pt: avg
				};
			}
		}
		cellCoords[cellIdx].pts = pts;
	}

	return {
	cells: cellCoords,
	vertices: vertexCoords,
	edges: edgeCoords
	};
}

if (typeof exports !== 'undefined')
{
	exports.makeCoords = makeCoords;
}
