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

function makeMap(geometry)
{
	var cells = new Array();
	var vertices = {};
	var edges = {};

	var numCells = geometry.getCellCount();
	for (var cellIdx = 1; cellIdx <= numCells; cellIdx++)
	{
		var c = {
			height: 0,
			water: 0,
			moisture: 0
			};
		cells.push(c);

		var adj = geometry.getNeighbors(cellIdx);
		for (var j = 0, l = adj.length; j < l; j++)
		{
			var other1 = adj[j];
			var other2 = adj[(j+1)%l];

			var eId = geometry._makeEdge(cellIdx,other1);
			if (!(eId in edges))
			{
				edges[eId] = {};
			}

			var vId = geometry._makeVertex(cellIdx,other1,other2);
			if (!(vId in vertices))
			{
				vertices[vId] = {};
			}
		}
	}

	return {
	cells: cells,
	vertices: vertices,
	edges: edges
	};
}

function bumpMap(map, coords, d, attrName)
{
	var M = Math.sqrt(1/3);
	var v = {
	x: Math.random() * 2 * M - M,
	y: Math.random() * 2 * M - M,
	z: Math.random() * 2 * M - M
	};

	if (!d) {
		d = Math.random() >= 0.5 ? 1 : -1;
	}

	if (!attrName) {
		attrName = 'height';
	}

	for (var i in map.cells)
	{
		var c = map.cells[i];
		var co = coords.cells[parseInt(i)+1];
		var u = {
		x: co.pt.x - v.x,
		y: co.pt.y - v.y,
		z: co.pt.z - v.z
		};
		var dp = u.x * v.x + u.y * v.y + u.z * v.z;
		if (dp > 0)
		{
			c[attrName] += d;
		}
	}
}

function findSeaLevel(map, desiredSeaCoverage)
{
	var getSeaCoverageAt = function(lev)
	{
		var countAbove = 0, countBelow = 0;
		for (var cid in map.cells)
		{
			var c = map.cells[cid];
			if (c.height > lev)
				countAbove++;
			else
				countBelow++;
		}
		return countBelow / (countAbove+countBelow);
	};

	var minLevel = -40; // highest level known to have too much land
	var maxLevel = 40;  // lowest level known to have too much sea

	while (minLevel + 1 < maxLevel)
	{
		var i = Math.floor((minLevel+maxLevel+1)/2);
		var cov = getSeaCoverageAt(i);
		if (cov < desiredSeaCoverage)
		{
			//too much land, not enough sea
			minLevel = i;
		}
		else
		{
			//too much sea, not enough land
			maxLevel = i;
		}
	}

	return minLevel;
}

function LogisticFunction(t)
{
	return 1/(1+Math.exp(-t));
}

function blurMoisture(map)
{
	var newValues = {};
	for (var cid in map.cells)
	{
		newValues[cid] = map.cells[cid].moisture;
	}

	for (var cid in map.cells)
	{
		var c = map.cells[cid];
		var cellIdx = +cid+1;

		var nn = map.geometry.getNeighbors(cellIdx);
		for (var i = 0, l = nn.length; i < l; i++)
		{
			var nid = nn[i]-1;
			var n = map.cells[nid];
			var diff = c.moisture - n.moisture;
			if (diff > 0)
			{
				var c_height = c.height > 0 ? c.height : 0;
				var n_height = n.height > 0 ? n.height : 0;
				var height_diff = c_height - n_height;
				var xfer = 0.2 * diff * LogisticFunction(height_diff/2);
				newValues[cid] -= xfer;
				newValues[nid] += xfer;
			}
		}
	}

	for (var cid in map.cells)
	{
		map.cells[cid].moisture = newValues[cid];
		//console.log("moisture at "+cid+" is "+Math.round(newValues[cid]));
	}
}

function generateTerrain(map, coords)
{
	// randomly generate height map for entire sphere
	for (var i = 0; i < 100; i++)
		bumpMap(map, coords);

	// find the level that makes sea coverage close to 60 percent
	var seaLevel = findSeaLevel(map, 0.6);
	for (var cid in map.cells)
	{
		map.cells[cid].height -= seaLevel;
	}

	// generate temperature map...
	//  start by calculating temperature based on latitude
	for (var i = 1, l = map.geometry.getCellCount(); i <= l; i++)
	{
		var lat = Math.asin(coords.cells[i].pt.z);
		map.cells[i-1].temperature = 24 - 20 * Math.pow(lat,2);
	}
	//  then apply some random noise to those numbers
	for (var i = 0; i < 30; i++)
		bumpMap(map, coords, i%2 ? 2 : -2, "temperature");

	// assign initial moisture numbers based on ocean and temperature
	for (var cid in map.cells)
	{
		var c = map.cells[cid];
		if (c.height < 1)
			c.moisture = 30*Math.pow(.5,(24-c.temperature)/12);
		else
			c.moisture = 0;
	}
	for (var i = 0; i < 50; i++)
		blurMoisture(map);
	for (var i = 0; i < 10; i++)
		bumpMap(map, coords, i%2 ? 1 : -1, "moisture");

	for (var cid in map.cells)
	{
		map.cells[cid].soil = 0;
	}
	for (var i = 0; i < 21; i++)
		bumpMap(map, coords, i%2 ? 1 : -1, "soil");
	for (var cid in map.cells)
	{
		map.cells[cid].soil = LogisticFunction(map.cells[cid].soil);
	}

	for (var i = 1, l = map.geometry.getCellCount(); i <= l; i++)
	{
		var c = map.cells[i-1];
		var nn = map.geometry.getNeighbors(i);
		var sumVar = 0;
		for (var j = 0; j < nn.length; j++)
		{
			var n = map.cells[nn[j]-1];
			sumVar += Math.pow(n.height-c.height,2);
		}
		sumVar /= nn.length;

		if (c.height < 1)
			c.terrain = "ocean";
		else if (c.temperature < 0)
			c.terrain = "glacier";
		else if (sumVar - c.soil >= 3.5)
			c.terrain = "mountains";
		else if (sumVar >= 2)
			c.terrain = "hills";
		else if (c.moisture < 0.5 && c.temperature > 15)
			c.terrain = "desert";
		else if (c.moisture < 0.5)
			c.terrain = "tundra";
		else if (c.moisture >= 6 && c.height >= 2 && c.soil >= 0.65)
			c.terrain = "swamp";
		else if (c.soil >= 0.65)
			c.terrain = "forest";
		else if (c.moisture < 2)
			c.terrain = "plains";
		else
			c.terrain = "grassland";
	}

	var RF = new RiverFactory(map);
	RF.generateRivers();

	var numRivers = 0;
	var cellCount = map.geometry.getCellCount();
	while (numRivers < 180)
	{
		var cellIdx = 1+Math.floor(Math.random()*cellCount);
		var vv = map.geometry.getVerticesAdjacentToCell(cellIdx);
		var vId = vv[Math.floor(Math.random()*vv.length)];
		while (vId)
		{
			var nextVId = RF.nextVertex[vId];
			if (!nextVId)
				break;

			var eId = map.geometry.makeEdgeFromEndpoints(vId, nextVId);
			if (map.edges[eId].feature)
				break;

			var cc = map.geometry.getCellsAdjacentToEdge(eId);
			if (map.cells[cc[0]-1].height < 1)
				break;
			if (map.cells[cc[1]-1].height < 1)
				break;

			map.edges[eId].feature = "river";
			numRivers++;
			vId = nextVId;
		}
	}
}

function RiverFactory(map)
{
	this.map = map;
	this.todo = new Array();
	this.visitedCount = 0;

	var r = {};
	for (var vId in this.map.vertices)
	{
		r[vId] = true;
	}
	this.remaining = r;

	this.nextVertex = {}; //indexed by vertex id
	this.rivers = {};     //indexed by edge id
}

RiverFactory.prototype.getVertexHeight = function(vId)
{
	var map = this.map;
	var cc = map.geometry.getCellsAdjacentToVertex(vId);
	return (
		map.cells[cc[0]-1].height +
		map.cells[cc[1]-1].height +
		map.cells[cc[2]-1].height) / 3;
};

RiverFactory.prototype.addRiverAt = function(vId)
{
	var h = this.getVertexHeight(vId);
	var vv = this.map.geometry.getVerticesAdjacentToVertex(vId);
	var candidates = new Array();
	for (var i = 0, l = vv.length; i < l; i++)
	{
		if (!this.remaining[vv[i]])
			continue;

		var hh = this.getVertexHeight(vv[i]);
		if (hh < h)
			continue;

		candidates.push(vv[i]);
	}

	if (candidates.length == 0)
		return false;

	var i = Math.floor(Math.random() * candidates.length);
	var otherV = candidates[i];

	delete this.remaining[otherV];
	this.visitedCount++;
	this.todo.push(otherV);
	var eId = this.map.geometry.makeEdgeFromEndpoints(vId, otherV);

	this.rivers[eId] = true;
	this.nextVertex[otherV] = vId;

	return true;
};

RiverFactory.prototype.step = function()
{
	for (;;)
	{
		if (this.todo.length == 0)
		{
			// find a starting spot
			var candidates = new Array();
			var bestH = Infinity;
			for (var vId in this.remaining)
			{
				var h = this.getVertexHeight(vId);
				if (h < bestH)
				{
					bestH = h;
					candidates = [vId];
				}
				else if (h == bestH)
				{
					candidates.push(vId);
				}
			}

			if (candidates.length == 0)
				return false;

			var i = Math.floor(Math.random()*candidates.length);
			delete this.remaining[candidates[i]];
			this.todo.push(candidates[i]);
		}

		var i = Math.floor(Math.random()*this.todo.length);
		var vId = this.todo[i];
		if (this.addRiverAt(vId))
			return true;

		this.todo.splice(i,1);
	}
};

RiverFactory.prototype.generateRivers = function()
{
	while (this.step());
};

if (typeof exports !== 'undefined')
{
	exports.makeMap = makeMap;
	exports.makeCoords = makeCoords;
	exports.bumpMap = bumpMap;
	exports.generateTerrain = generateTerrain;
}
