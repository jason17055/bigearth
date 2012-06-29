if (typeof require !== 'undefined')
{
//require('../html/trains-common.js');
}

var G = {
	map: {},
	players: {},
	nextPlayerId: 1
	};

function moveFleetRandomly(fleetId)
{
	var fleet = G.fleets[fleetId];
	var nn = G.globalMap.geometry.getNeighbors(fleet.location);
	var newLoc = nn[Math.floor(Math.random()*nn.length)];

	var oldLoc = fleet.location;
	fleet.location = newLoc;

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
	map: G.globalMap,
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
