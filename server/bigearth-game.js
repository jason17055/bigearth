if (typeof require !== 'undefined')
{
//require('../html/trains-common.js');
}

var G = {
	world: {},
	terrain: {},
	players: {},
	maps: {},
	fleets: {}
	};

function discoverCell(playerId, location)
{
	var isNew = false;
	var refCell = G.terrain.cells[location];

	var map = G.maps[playerId];
	var mapCell = map.cells[location];
	if (!mapCell)
	{
		isNew = true;
		mapCell = {};
		mapCell.terrain = refCell.terrain;
	}
	else if (mapCell.terrain != refCell.terrain)
	{
		isNew = true;
		mapCell.terrain = refCell.terrain;
	}

	if (isNew)
	{
		map.cells[location] = mapCell;
		postEvent({
			event: 'map-update',
			location: location,
			locationType: 'cell',
			data: mapCell
			});
	}

	var nn = G.geometry.getNeighbors(location);
	for (var i = 0; i < nn.length; i++)
	{
		if (map.cells[nn[i]])
		{
			var eId = G.geometry._makeEdge(location, nn[i]);
			discoverEdge(playerId, eId);
		}
	}
}

function discoverEdge(playerId, eId)
{
	var isNew = false;

	var refEdge = G.terrain.edges[eId] || {};
	var map = G.maps[playerId];
	var mapEdge = map.edges[eId];

	if (!mapEdge)
	{
		mapEdge = {};
		if (refEdge.feature)
			mapEdge.feature = refEdge.feature;
		isNew = true;
	}
	else if (mapEdge.feature != refEdge.feature)
	{
		mapEdge.feature = refEdge.feature;
		isNew = true;
	}

	if (isNew)
	{
		map.edges[eId] = mapEdge;
		postEvent({
			event: 'map-update',
			location: eId,
			locationType: 'edge',
			data: mapEdge
			});
	}
}

function discoverCellBorder(playerId, cellIdx)
{
	var ee = G.geometry.getEdgesAdjacentToCell(cellIdx);
	for (var i = 0; i < ee.length; i++)
	{
		discoverEdge(playerId, ee[i]);
	}

	var nn = G.geometry.getNeighbors(cellIdx);
	for (var i = 0; i < nn.length; i++)
	{
		discoverCell(playerId, nn[i]);
	}
}

function moveFleetTowards(fleetId, targetLocation)
{
	var fleet = G.fleets[fleetId];
	var oldLoc = fleet.location;

console.log("moving fleet "+fleetId+" from " + oldLoc + " to " + targetLocation);

	if (oldLoc == targetLocation)
	{
		return setFleetOrder(fleetId, "stop");
	}

	var nn = G.geometry.getNeighbors(oldLoc);
	var best = nn[0];
	var bestDist = Infinity;
	for (var i = 0, l = nn.length; i < l; i++)
	{
		var candidateLoc = nn[i];
		var d = G.geometry.distanceBetween(candidateLoc, targetLocation);
		if (d < bestDist)
		{
			best = candidateLoc;
			bestDist = d;
		}
	}
console.log("  chose " + best);

	return moveFleetOneStep(fleetId, best);
}

function moveFleetRandomly(fleetId)
{
	//broken
}

function moveFleetOneStep(fleetId, newLoc)
{
	var fleet = G.fleets[fleetId];
	var oldLoc = fleet.location;
	fleet.location = newLoc;

	discoverCell(fleet.owner, newLoc);
	discoverCellBorder(fleet.owner, newLoc);
		
	postEvent({
		event: 'fleet-movement',
		fleet: fleetId,
		fromLocation: oldLoc,
		toLocation: newLoc
		});
	fleet.coolDownTimer = setTimeout(function() {
		fleet.coolDownTimer = null;
		fleetActivity(fleetId);
		}, 1200);
}

function newPlayer(playerId, andThen)
{
	if (G.players[playerId])
	{
		if (andThen) andThen();
		return;
	}

	G.players[playerId] = {
		type: 'player'
		};
	G.maps[playerId] = {
		cells: {},
		edges: {}
		};
	addExplorer(playerId, andThen);
}

function nextFleetId()
{
	return G.world.nextFleetId++;
}

function addExplorer(playerId, andThen)
{
	var f = {
		owner: playerId,
		location: 2,
		type: 'explorer',
		orders: []
		};
	var fid = nextFleetId();

	G.fleets[fid] = f;
	discoverCell(playerId,f.location);
	discoverCellBorder(playerId,f.location);

	if (andThen) andThen();
}

function setFleetOrder(fleetId, newOrder, extraInfo)
{
	var fleet = G.fleets[fleetId];
	fleet.currentOrder = newOrder;
	if (newOrder == 'goto')
	{
		fleet.targetLocation = extraInfo.location;
	}
	fleetActivity(fleetId);
}

function fleetActivity(fleetId)
{
	var fleet = G.fleets[fleetId];
	if (!fleet) return;
	if (fleet.coolDownTimer) return;

	if (fleet.currentOrder == 'wander')
	{
		console.log("traveler moves!");
		return moveFleetRandomly(fleetId);
	}
	if (fleet.currentOrder == 'goto')
	{
		return moveFleetTowards(fleetId, fleet.targetLocation);
	}
}

function getGameState(request)
{
	if (request.remote_player)
	{
		return {
		role: "player",
		map: "/map/"+request.remote_player,
		mapSize: G.terrain.size,
		fleets: "/fleets/"+request.remote_player,
		identity: request.remote_player
		};
	}
	else
	{
		return {
		role: "observer",
		};
	}
}

function doExpose(requestData, remoteUser)
{
	console.log("in doExpose");

	if (requestData.cell)
	{
		discoverCell(1, requestData.cell);
	}
}

function doOrder(requestData, remoteUser)
{
	console.log("in doOrder");

	var fleetId = +(requestData.fleet);
	if (!G.fleets[fleetId])
		return;

	setFleetOrder(fleetId, requestData.order, requestData);
}

function getFleets(playerId, callback)
{
	var result = {};
	for (var fid in G.fleets)
	{
		var f = G.fleets[fid];
		if (f.owner == playerId)
		{
			result[fid] = f;
		}
	}

	callback(result);
}
exports.getFleets = getFleets;

function getMapFragment(mapId, callback)
{
console.log("in getMapFragment");
	var result = {};
	var map = G.maps[mapId];
	if (!map)
	{
		console.log("Warning: map '"+mapId+"' not found");
		return callback(result);
	}

	for (var cid in map.cells)
	{
		result[cid] = map.cells[cid];
	}
	for (var eid in map.edges)
	{
		result[eid] = map.edges[eid];
	}
	console.log("map is "+JSON.stringify(result));
	return callback(result);
}
exports.getMapFragment = getMapFragment;


var actionHandlers = {
	expose: doExpose,
	order: doOrder
	};

if (typeof global !== 'undefined')
{
	global.G = G;
	global.getGameState = getGameState;
	global.actionHandlers = actionHandlers;
	global.addExplorer = addExplorer;
	global.newPlayer = newPlayer;
}
