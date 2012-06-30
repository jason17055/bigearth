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
		map.edges[eId].river = G.globalMap.edges[eId].river;

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

	var moveTraveler;
	moveTraveler = function() {
		console.log("traveler moves!");
		moveFleetRandomly(1);
		setTimeout(moveTraveler, 600);
		};
	setTimeout(moveTraveler, 600);
}

function getGameState()
{
	var p = {};
	for (var pid in G.players)
	{
		var pp = G.players[pid];
		p[pid] = pp;
	}

	return {
	map: G.players[1].primaryMap,
	mapSize: G.globalMap.size,
	fleets: G.fleets,
	players: p
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

var actionHandlers = {
	};

if (typeof global !== 'undefined')
{
	global.G = G;
	global.getGameState = getGameState;
	global.actionHandlers = actionHandlers;
	global.addTraveler = addTraveler;
}
