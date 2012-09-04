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

function createTerrain()
{
	var SG = require('../html/sphere-geometry.js');
	var MG = require('../html/mapgen.js');

	console.log("Generating terrain...");
	BE.geometry = new SG.SphereGeometry(22);
	var map = makeMap(BE.geometry);
	BE.coords = MG.makeCoords(BE.geometry);
	MG.generateTerrain(map, BE.coords);

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
