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

exports.newMap = newMap;
exports.copyMap = copyMap;
