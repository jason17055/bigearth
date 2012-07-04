
var cradle = require('cradle');


var worldName = process.argv[2];
console.log("Creating world "+worldName);

var DB = new(cradle.Connection)().database(worldName);

function createDatabase()
{
	DB.create(function(err,res) {
		if (err) {
			console.log("ERROR", err);
		} else {
			console.log("Database created.");
			createSchema();
		}
		});
}

function createSchema()
{
	var remaining = 0;
	var count = 0;
	var callback = function(err,res) {
			if (err) {
				console.log("ERROR",err);
			} else {
				console.log("Schema object "+(++count)+" created.");
				remaining--;
				if (remaining == 0)
					createTerrain();
			}
		};

	remaining++;
	DB.save('_design/cells', {
		language: 'javascript',
		views: {
		all: { map: function(doc) {
			if (doc._id.match(/^terrain\/(\d+)$/))
			{
				emit(+RegExp.$1, doc);
			}
			} },
		by_coordinates: { map: function(doc) {
			if (doc._id.match(/^terrain\/(\d+)$/))
			{
				var lat = Math.asin(doc.pt.z);
				var lon = Math.atan2(doc.pt.y, doc.pt.x);
				var band = Math.floor(lat / (Math.PI/12));
				emit([band,lon,lat], doc);
			}
			} }
		}
		}, callback);

	remaining++;
	DB.save('_design/mapdata', {
		language: 'javascript',
		views: {
		byMap: { map: function(doc) {
			if (doc._id.match(/^map\/(\d+)\/(.*)$/))
			{
				emit(RegExp.$1,doc);
			}
			} }
		}
		}, callback);

	remaining++;
	DB.save('_design/fleets', {
		language: 'javascript',
		views: {
		byPlayer: { map: function(doc) {
			if (doc.orders && doc.owner)
			{
				emit(""+doc.owner,doc);
			}
			} }
		}
		}, callback);
}

function createTerrain()
{
	var remaining = 0;
	var count = 0;
	var callback = function(err,res) {
			console.log("Terrain object "+(++count)+" created.");
			if (err) {
				console.log("ERROR",err);
			} else {
				remaining--;
			}
		};

	var SG = require('../html/sphere-geometry.js');
	var MG = require('../html/mapgen.js');

	console.log("Generating terrain...");
	var geometry = new SG.SphereGeometry(22);
	var map = MG.makeMap(geometry);
	var coords = MG.makeCoords(geometry);
	map.geometry = geometry;
	map.size = geometry.size;
	MG.generateTerrain(map, coords);

	for (var cellIdx = 1, numCells = geometry.getCellCount();
		cellIdx <= numCells; cellIdx++)
	{
		var c = map.cells[cellIdx-1];
		var co = coords.cells[cellIdx];

		var x = {
		terrain: c.terrain,
		height: c.height,
		temperature: c.temperature,
		moisture: c.moisture,
		pt: co.pt,
		neighbors: geometry.getNeighbors(cellIdx)
		};

		remaining++;
		DB.save('terrain/'+cellIdx, x, callback);
	}

	for (var eId in map.edges)
	{
		var e = map.edges[eId];
		if (!e.feature)
			continue;

		remaining++;
		DB.save('terrain/'+eId, { feature: e.feature }, callback);
	}

	remaining++;
	DB.save('world', {
		geometry: 'sphere',
		size: geometry.size
		}, callback);
}

createDatabase();
