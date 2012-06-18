if (typeof require !== 'undefined')
{
//require('../html/trains-common.js');
}

var G = {
	map: {},
	players: {},
	nextPlayerId: 1
	};

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
}
