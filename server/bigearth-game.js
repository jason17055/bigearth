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

	if (!map.cells[location-1])
	{
		map.cells[location-1] = {};
		isNew = true;
	}
	map.cells[location-1].terrain = G.globalMap.cells[location-1].terrain;

	if (isNew)
	{
		postEvent({
			event: 'map-update',
			location: location,
			locationType: 'cell',
			data: map.cells[location-1]
			});
	}

	var ee = G.globalMap.geometry.getEdgesAdjacentToCell(location);
	for (var i = 0; i < ee.length; i++)
	{
		var eId = ee[i];
		
	}
}

function moveFleetRandomly(fleetId)
{
	var fleet = G.fleets[fleetId];
	var nn = G.globalMap.geometry.getNeighbors(fleet.location);
	var newLoc = nn[Math.floor(Math.random()*nn.length)];

	var oldLoc = fleet.location;
	fleet.location = newLoc;

	discoverCell(1, newLoc);
		
	postEvent({
		event: 'fleet-movement',
		fleet: fleetId,
		fromLocation: oldLoc,
		toLocation: newLoc
		});
}

function addTraveler()
{
	G.fleets = {};
	G.fleets[1] = {
		location: 1,
		type: 'explorer'
		};

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
