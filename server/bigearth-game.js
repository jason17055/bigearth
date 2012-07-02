if (typeof require !== 'undefined')
{
//require('../html/trains-common.js');
}

var G = {
	map: {},
	players: {},
	nextPlayerId: 1
	};
G.players[1] = { primaryMap: { cells: [], edges: {}, vertices: {} } };

function discoverCell(playerId, location)
{
	var map = G.players[playerId].primaryMap;
	var isNew = false;

	var refCell = G.globalMap.cells[location-1];
	if (!map.cells[location-1])
	{
		map.cells[location-1] = {};
		map.cells[location-1].terrain = refCell.terrain;
		isNew = true;
	}
	else if (map.cells[location-1].terrain != refCell.terrain)
	{
		map.cells[location-1].terrain = refCell.terrain;
		isNew = true;
	}

	if (isNew)
	{
		postEvent({
			event: 'map-update',
			location: location,
			locationType: 'cell',
			data: map.cells[location-1]
			});
	}
}

function discoverEdge(playerId, eId)
{
	var map = G.players[playerId].primaryMap;
	var isNew = false;

	if (!map.edges[eId])
	{
		map.edges[eId] = {};
		map.edges[eId].feature = G.globalMap.edges[eId].feature;

		postEvent({
			event: 'map-update',
			location: eId,
			locationType: 'edge',
			data: map.edges[eId]
			});
	}
}

function discoverCellBorder(playerId, cellIdx)
{
	var ee = G.globalMap.geometry.getEdgesAdjacentToCell(cellIdx);
	for (var i = 0; i < ee.length; i++)
	{
		discoverEdge(playerId, ee[i]);
	}

	var nn = G.globalMap.geometry.getNeighbors(cellIdx);
	for (var i = 0; i < nn.length; i++)
	{
		discoverCell(playerId, nn[i]);
	}
}

function moveFleetRandomly(fleetId)
{
	var fleet = G.fleets[fleetId];
	var oldLoc = fleet.location;
	var nn = G.globalMap.geometry.getNeighbors(fleet.location);

	var map = G.players[1].primaryMap;

	var candidates1 = [];
	var candidates2 = [];
	for (var i = 0; i < nn.length; i++)
	{
		if (map.cells[oldLoc-1].terrain == 'ocean'
			|| map.cells[nn[i]-1].terrain != 'ocean')
		{
			if (!fleet.recent[nn[i]])
				candidates1.push(nn[i]);
			candidates2.push(nn[i]);
		}
	}
	if (candidates1.length == 0)
	{
		fleet.recent = {};
		candidates1 = candidates2;
	}

	if (candidates1.length > 0)
	{
		var newLoc = candidates1[Math.floor(Math.random()*candidates1.length)];
		fleet.location = newLoc;
		fleet.recent[newLoc] = true;

		discoverCell(1, newLoc);
		discoverCellBorder(1, newLoc);
		
		postEvent({
			event: 'fleet-movement',
			fleet: fleetId,
			fromLocation: oldLoc,
			toLocation: newLoc
			});
	}
}

function addTraveler()
{
	G.fleets = {};
	G.fleets[1] = {
		location: 1,
		type: 'explorer',
		recent: {}
		};
	discoverCell(1,1);
	discoverCellBorder(1,1);

	setFleetOrder(1, "wander");
}

function setFleetOrder(fleetId, newOrder)
{
	var fleet = G.fleets[fleetId];
	fleet.currentOrder = newOrder;
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
		moveFleetRandomly(fleetId);
		fleet.coolDownTimer = setTimeout(function() {
			fleet.coolDownTimer = null;
			fleetActivity(fleetId);
			}, 1200);
	}
}

function getGameState()
{
	var pp = {};
	for (var pid in G.players)
	{
		pp[pid] = G.players[pid];
	}

	var ff = {};
	for (var fid in G.fleets)
	{
		ff[fid] = {
		location: fid,
		type: G.fleets[fid].type
		};
	}

	return {
	map: G.players[1].primaryMap,
	mapSize: G.globalMap.size,
	fleets: ff,
	players: pp
	};
}

function newPlayer()
{
	var pid = G.nextPlayerId++;
	G.players[pid] = {
		money: 50,
		demands: G.map.futureDemands.splice(0,5)
		};
	return pid;
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

	setFleetOrder(fleetId, requestData.order);
}

var actionHandlers = {
	expose: doExpose,
	order: doOrder
	};

if (typeof global !== 'undefined')
{
	global.G = G;
	global.getGameState = getGameState;
	global.actionHandlers = actionHandlers;
	global.addTraveler = addTraveler;
}
