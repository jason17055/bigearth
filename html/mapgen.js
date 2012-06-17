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

function makeMap(geometry)
{
	var cells = new Array();
	var vertices = {};

	var numCells = geometry.getCellCount();
	for (var cellIdx = 1; cellIdx <= numCells; cellIdx++)
	{
		var c = {
			pt: geometry.getSpherePoint(cellIdx),
			height: 0,
			water: 0
			};
		cells.push(c);
	}

	for (var i in cells)
	{
		var cellIdx = parseInt(i)+1;
		var c = cells[i];

		var adj = geometry.getNeighbors(cellIdx);
		c.adjacent = new Array();
		for (var j = 0; j < adj.length; j++)
		{
			c.adjacent.push(cells[adj[j]-1]);
		}

		var pts = new Array();
		for (var j = 0, l = c.adjacent.length; j < l; j++)
		{
			var d = c.adjacent[j];
			var e = c.adjacent[(j+1)%l];

			var avg = {
			x: (c.pt.x + d.pt.x + e.pt.x) / 3,
			y: (c.pt.y + d.pt.y + e.pt.y) / 3,
			z: (c.pt.z + d.pt.z + e.pt.z) / 3
			};
			avg = normalize(avg);
			pts.push(avg);

			var vId = geometry._makeVertex(cellIdx,adj[j],adj[(j+1)%l]);
			if (!(vId in vertices))
			{
				vertices[vId] = {
				pt: avg
				};
			}
		}
		c.pts = pts;
	}
	return {
	cells: cells,
	vertices: vertices
	};
}

function bumpMap(map)
{
	var M = Math.sqrt(1/3);
	var v = {
	x: Math.random() * 2 * M - M,
	y: Math.random() * 2 * M - M,
	z: Math.random() * 2 * M - M
	};
	var d = Math.random() >= 0.5 ? 1 : -1;

	for (var i in map.cells)
	{
		var c = map.cells[i];
		var u = {
		x: c.pt.x - v.x,
		y: c.pt.y - v.y,
		z: c.pt.z - v.z
		};
		var dp = u.x * v.x + u.y * v.y + u.z * v.z;
		if (dp > 0)
		{
			c.height += d;
		}
	}
}

if (typeof exports !== 'undefined')
{
	exports.makeMap = makeMap;
	exports.bumpMap = bumpMap;
}
