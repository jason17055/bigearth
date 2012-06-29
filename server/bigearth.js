if (typeof require !== 'undefined')
{
//require('../html/trains-common.js');
}

var G = {
	map: {},
	players: {},
	nextPlayerId: 1
	};

function addTraveler()
{
	G.fleets = {};
	G.fleets[1] = {
		location: 1,
		type: 'explorer'
		};
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
