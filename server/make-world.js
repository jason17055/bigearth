var fs = require('fs');

var worldName = process.argv[2];
console.log("Creating world "+worldName);


function createDatabase()
{
	fs.mkdirSync(worldName);
}

function createTerrain()
{
	var SG = require('../html/sphere-geometry.js');
	var MG = require('../html/mapgen.js');

	console.log("Generating terrain...");
	var geometry = new SG.SphereGeometry(22);
	var map = MG.makeMap(geometry);
	var coords = MG.makeCoords(geometry);
	MG.generateTerrain(map, coords);

	var worldFile = worldName + '/world.txt';
	fs.writeFileSync(worldFile,
		JSON.stringify({
		nextPlayerId: 1,
		nextFleetId: 1
		}));

	var filename = worldName+'/terrain.txt';
	var _map = {
		cells: map.cells,
		edges: map.edges,
		vertices: map.vertices,
		size: map.size,
		geometry: map.geometry.name
		};
	fs.writeFileSync(filename, JSON.stringify(_map));
}

createDatabase();
createTerrain();
