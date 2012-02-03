require('../html/trains-common.js');

var G = {
	rails: {},
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
	rails: G.rails,
	map: G.map,
	players: p
	};
}

function setMap(mapName, map)
{
	G.mapName = mapName;
	G.map = map;
	G.map.geometry = loadGeometry(map.terrain[0].length, map.terrain.length);
}

function autoCreateDemands()
{
	var allResourceTypes = {};
	for (var cityId in G.map.cities)
	{
		var c = G.map.cities[cityId];
		for (var i in c.offers)
		{
			if (!allResourceTypes[c.offers[i]])
				allResourceTypes[c.offers[i]] = {};
			allResourceTypes[c.offers[i]][cityId]=true;
		}
	}

	G.map.futureDemands = new Array();
	G.map.pastDemands = new Array();
	for (var cityId in G.map.cities)
	{
		var c = G.map.cities[cityId];
		var here = {};
		for (var i in c.offers)
		{
			here[c.offers[i]] = true;
		}

		for (var rt in allResourceTypes)
		{
			if (here[rt])
				continue;

			// find nearest source of this resource
			var best = 1E10;
			for (var s in allResourceTypes[rt])
			{
				var d = simpleDistance(cityId, s);
				if (d < best) { best = d; }
			}

			var value = Math.round(best * .5);
			if (value >= 1)
			{
				G.map.futureDemands.push(
					[cityId, rt, value]
					);
			}
		}
	}
	shuffleArray(G.map.futureDemands);
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

function doJoin(joinData, remoteUser)
{
	var pid = newPlayer();
	G.players[pid].identity = remoteUser;
	console.log("remote user is " + remoteUser);

	var np = {};
	np[pid] = G.players[pid];

	postEvent({
		event: "new-player",
		newPlayers: np
		});
	return { pid: pid };
}

var actionHandlers = {
	join: doJoin
	};

if (typeof global !== 'undefined')
{
	global.autoCreateDemands = autoCreateDemands;
	global.getGameState = getGameState;
	global.setMap = setMap;
	global.actionHandlers = actionHandlers;
}
