var Location = require('../../html/location.js');

function newMap()
{
	G.world.nextMapId = (G.world.nextMapId || 0);

	var mapId = G.world.nextMapId++;
	while (G.maps[mapId])
		mapId = G.world.nextMapId++;

	G.maps[mapId] = {
		cells: {},
		edges: {}
		};
	return mapId;
}

function copyMap(mapId)
{
	return mapId;
}

function getNeighbors(mapId, location)
{
	var cellId = Location.toCellId(location);
	return BE.geometry.getNeighbors(cellId);
}

exports.newMap = newMap;
exports.copyMap = copyMap;
exports.getNeighbors = getNeighbors;
