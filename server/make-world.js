var fs = require('fs');

var worldName = process.argv[2];
console.log("Creating world "+worldName);


global.BE = {};

function createDatabase()
{
	fs.mkdirSync(worldName);
}

function makeMap(geometry)
{
	var cells = {};
	var vertices = {};
	var edges = {};

	var numCells = geometry.getCellCount();
	for (var cellIdx = 1; cellIdx <= numCells; cellIdx++)
	{
		var c = {
			height: 0,
			water: 0,
			summerRains: 0,
			winterRains: 0
			};
		cells[cellIdx] = c;

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
	edges: edges,
	size: geometry.size,
	geometry: geometry
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

	for (var cid in map.cells)
	{
		var c = map.cells[cid];
		var co = coords.cells[cid];
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

function findRiverThreshold(map, desiredRiverCount)
{
	var countRiversAt = function(threshold)
	{
		var numRivers = 0;
		for (var eId in map.edges)
		{
			var edge = map.edges[eId];
			if ((edge.riverVolume || 0) >= threshold)
				numRivers++;
		}
		return numRivers;
	};

	var low = Infinity;
	var high = -Infinity;
	for (var eId in map.edges)
	{
		var edge = map.edges[eId];
		if ((edge.riverVolume || 0) < low)
			low = edge.riverVolume || 0;
		if ((edge.riverVolume || 0) > high)
			high = edge.riverVolume || 0;
	}

	while (high - low > 0.5)
	{
		var t = (high + low) / 2;
		var count = countRiversAt(t);
		if (count >= desiredRiverCount)
			low = t;
		else
			high = t;
	}
	return (high+low)/2;
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

function blurMoisture(map, coords, moistureKey)
{
	var newValues = {};
	for (var cid in map.cells)
	{
		newValues[cid] = map.cells[cid][moistureKey] || 0;
	}

	for (var cid in map.cells)
	{
		var c = map.cells[cid];
		var c_lat = Math.asin(coords.cells[cid].pt.z);
		var c_lgt = Math.atan2(coords.cells[cid].pt.y, coords.cells[cid].pt.x);
		var winds = Math.cos(c_lat*4);

		var nn = BE.geometry.getNeighbors(cid);
		for (var i = 0, l = nn.length; i < l; i++)
		{
			var nid = nn[i];
			var n = map.cells[nid];
			var n_lat = Math.asin(coords.cells[nid].pt.z);
			var n_lgt = Math.atan2(coords.cells[nid].pt.y, coords.cells[nid].pt.x);
			var n_dir = Math.atan2(n_lat-c_lat, n_lgt-c_lgt);
			var diff = (c[moistureKey] || 0) - (n[moistureKey] || 0);
			if (diff > 0)
			{
				var c_height = c.height > 0 ? c.height : 0;
				var n_height = n.height > 0 ? n.height : 0;
				var height_diff = c_height - n_height;

				var wind_aid = Math.cos(n_dir) * winds;

				var xfer = 0.2 * diff * (LogisticFunction(height_diff/2 + wind_aid));
				newValues[cid] -= xfer;
				newValues[nid] += xfer;
			}
		}
	}

	for (var cid in map.cells)
	{
		map.cells[cid][moistureKey] = newValues[cid];
	}
}

function rainfallStats(map, moistureKey)
{
	var minSeen = Infinity;
	var maxSeen = -Infinity;
	var count = 0;
	var sum = 0;

	for (var cid in map.cells)
	{
		var m = map.cells[cid][moistureKey];
		if (m < minSeen)
			minSeen = m;
		if (m > maxSeen)
			maxSeen = m;
		sum += m;
		count++;
	}

	console.log(moistureKey + " avg", sum/count);
	console.log(moistureKey + " min", minSeen);
	console.log(moistureKey + " max", maxSeen);
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
	for (var cid in map.cells)
	{
		var lat = Math.asin(coords.cells[cid].pt.z);
		map.cells[cid].temperature = 24 - 20 * Math.pow(lat,2);
	}
	//  then apply some random noise to those numbers
	for (var i = 0; i < 30; i++)
		bumpMap(map, coords, i%2 ? 2 : -2, "temperature");

	//
	// determine soil quality
	//
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

	//
	// determine rainfall levels
	//
	// assign initial moisture numbers based on ocean and temperature
	for (var cid in map.cells)
	{
		var c = map.cells[cid];
		var lat = Math.asin(coords.cells[cid].pt.z);

		if (c.height < 1)
			c.winterRains = 30*Math.pow(.5,(24-c.temperature)/12);
		else
			c.winterRains = 0;

		// middle latitudes will get seasonal variation in rains
		c.summerRains = c.winterRains + 6*Math.sin(lat/2);
	}
	for (var i = 0; i < 50; i++)
	{
		blurMoisture(map, coords, 'summerRains');
		blurMoisture(map, coords, 'winterRains');
	}
	for (var i = 0; i < 20; i++)
	{
		bumpMap(map, coords, i%2 ? 2 : -2, "summerRains");
		bumpMap(map, coords, i%2 ? 2 : -2, "winterRains");
	}
	// normalize rainfall levels
	var minRainfall = Infinity;
	for (var cid in map.cells)
	{
		var c = map.cells[cid];
		if (c.winterRains < minRainfall)
			minRainfall = c.winterRains;
		if (c.summerRains < minRainfall)
			minRainfall = c.summerRains;
	}
	for (var cid in map.cells)
	{
		var c = map.cells[cid];
		c.winterRains -= minRainfall;
		c.summerRains -= minRainfall;
	}
	// rainfall stats
	rainfallStats(map, "summerRains");
	rainfallStats(map, "winterRains");

	//
	// make rivers
	//
	var RF = new RiverFactory(map);
	RF.generateRivers();

	var lakes = {};
	var cellCount = BE.geometry.getCellCount();
	for (var i = 0; i < 30; i++)
	{
		for (var cellIdx in map.cells)
		{

			var cellsAlongThisRiver = {};
			var addMoistureToCell = function(cid, water)
			{
				if (!cellsAlongThisRiver[cid])
				{
					cellsAlongThisRiver[cid] = true;
					map.cells[cid].riverMoisture = (map.cells[cid].riverMoisture||0)+water;
				}
			};

			var water = (map.cells[cellIdx].summerRains + map.cells[cellIdx].winterRains)/300;
			var vv = BE.geometry.getVerticesAdjacentToCell(cellIdx);
			var vId = vv[i % vv.length];
			while (vId)
			{
				var nextVId = RF.nextVertex[vId];
				if (!nextVId)
				{
					lakes[vId] = (lakes[vId]||0)+1;
					break;
				}

				var eId = BE.geometry.makeEdgeFromEndpoints(vId, nextVId);
				var edge = map.edges[eId];

				var cc = BE.geometry.getCellsAdjacentToEdge(eId);
				if (map.cells[cc[0]].height < 1)
					break;
				if (map.cells[cc[1]].height < 1)
					break;

				addMoistureToCell(cc[0], water);
				addMoistureToCell(cc[1], water);

				edge.riverVolume = (edge.riverVolume||0)+water;
				vId = nextVId;
			}

		}
	}
	for (var i = 0; i < 25; i++)
	{
		blurMoisture(map, coords, 'riverMoisture');
	}
	rainfallStats(map, "riverMoisture");

	var riverThreshold = findRiverThreshold(map, cellCount*0.125);
	var numRivers = 0;
	for (var eId in map.edges)
	{
		var edge = map.edges[eId];
		if (edge.riverVolume >= riverThreshold)
		{
			edge.feature = 'river';
			numRivers++;
		}
		delete edge.riverVolume;
	}
	console.log("created "+numRivers+" rivers");

	var countTerrains = {};
	for (var cid in map.cells)
	{
		var c = map.cells[cid];
		var nn = BE.geometry.getNeighbors(cid);
		var sumVar = 0;
		for (var j = 0; j < nn.length; j++)
		{
			var n = map.cells[nn[j]];
			sumVar += Math.pow(n.height-c.height,2);
		}
		sumVar /= nn.length;

		var totalWater = c.summerRains + c.winterRains + (c.riverMoisture||0);
		var lesserRain = c.summerRains < c.winterRains ? c.summerRains : c.winterRains;

		if (c.height < 1)
			c.terrain = "ocean";
		else if (c.temperature < 0)
			c.terrain = "glacier";
		else if (sumVar - c.soil >= 3.0)
			c.terrain = "mountains";
		else if (sumVar >= 1.8)
			c.terrain = "hills";
		else if (totalWater < 20 && c.temperature >= 13)
			c.terrain = "desert";
		else if (totalWater < 30 && c.temperature < 10)
			c.terrain = "tundra";
		else if (lesserRain >= 17 && c.temperature >= 18)
			c.terrain = "jungle";
		else if (lesserRain >= 15 && c.height < 5)
			c.terrain = "swamp";
		else if (lesserRain >= 12)
			c.terrain = "forest";
		else if (c.temperature > 13)
			c.terrain = "plains";
		else
			c.terrain = "grassland";

		countTerrains[c.terrain] = (countTerrains[c.terrain]||0)+1;
	}
	for (var terrainType in countTerrains)
	{
		console.log("total "+terrainType, countTerrains[terrainType]);
	}
}

function putWildlife(map)
{
	for (var cid in map.cells)
	{
		var cell = map.cells[cid];
		cell.wildlife = 65;

		if ((cell.terrain == 'plains' || cell.terrain == 'grassland'))
		{
			if (Math.random() < 0.3)
			{
				cell.hasWildSheep = Math.round(10 + 40 * Math.random());
			}
		}
		if (cell.terrain == 'hills')
		{
			if (Math.random() < 0.6)
			{
				cell.hasWildSheep = Math.round(15 + 50 * Math.random());
			}
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
	var cc = BE.geometry.getCellsAdjacentToVertex(vId);
	return (
		map.cells[cc[0]].height +
		map.cells[cc[1]].height +
		map.cells[cc[2]].height) / 3;
};

RiverFactory.prototype.addRiverAt = function(vId)
{
	var h = this.getVertexHeight(vId);
	var vv = BE.geometry.getVerticesAdjacentToVertex(vId);
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
	var eId = BE.geometry.makeEdgeFromEndpoints(vId, otherV);

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

function createTerrain()
{
	var SG = require('../html/sphere-geometry.js');
	var MG = require('../html/mapgen.js');

	console.log("Generating terrain...");
	BE.geometry = new SG.SphereGeometry(22);
	var map = makeMap(BE.geometry);
	BE.coords = MG.makeCoords(BE.geometry);
	generateTerrain(map, BE.coords);

	putWildlife(map);

	var worldFile = worldName + '/world.txt';
	fs.writeFileSync(worldFile,
		JSON.stringify({
		nextPlayerId: 1,
		nextFleetId: 1,
		age: 0,
		oneYear: 60000
		}));

	var filename = worldName+'/terrain.txt';
	var _map = {
		cells: map.cells,
		edges: map.edges,
		vertices: map.vertices,
		size: map.size,
		geometry: BE.geometry.name
		};
	fs.writeFileSync(filename, JSON.stringify(_map));
}

createDatabase();
createTerrain();
