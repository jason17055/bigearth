function newMap()
{
	var mapId = G.world.nextMapId++;
	while (G.maps[mapId])
		mapId = G.world.nextMapId++;

	G.maps[mapId] = {
		cells: {},
		edges: {}
		};
	return mapId;
}

exports.newMap = newMap;
