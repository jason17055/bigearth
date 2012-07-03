
var cradle = require('cradle');


var worldName = process.argv[2];
console.log("Creating world "+worldName);

var DB = new(cradle.Connection)().database(worldName);

DB.save('_design/mapcells', {
	language: 'javascript',
	views: {
	all: { map: function(doc) {
			if (doc._id.match(/^map\/(\d+)$/))
			{
				emit(+RegExp.$1, doc);
			}
		} }
	}
	}, function(err,res) {
		console.log("DONE");
		});

DB.create(function(err,res) {

	if (err) {
		console.log("ERROR", err);
	} else {


	var remaining = 0;
	var callback = function(err,res) {
		if (err) {
			console.log("ERROR",err);
		} else {
			remaining--;
		}
		};

	var SG = require('../html/sphere-geometry.js');
	var MG = require('../html/mapgen.js');

	console.log("generating terrain...");
	var geometry = new SG.SphereGeometry(22);
	var map = MG.makeMap(geometry);
	var coords = MG.makeCoords(geometry);
	map.geometry = geometry;
	map.size = geometry.size;
	MG.generateTerrain(map, coords);

	console.log("uploading cell data");
	for (var cellIdx = 1, numCells = geometry.getCellCount();
		cellIdx <= numCells; cellIdx++)
	{
		var c = map.cells[cellIdx-1];
		var co = coords.cells[cellIdx];

		var x = {
		terrain: c.terrain,
		height: c.height,
		pt: co.pt,
		neighbors: geometry.getNeighbors(cellIdx)
		};

		remaining++;
		DB.save('map/'+cellIdx, x, callback);
	}

	remaining++;
	DB.save('map', {
		geometry: 'sphere',
		size: geometry.size
		}, callback);
}
});
