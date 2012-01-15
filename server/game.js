require('../html/trains-common.js');

var G = {
	rails: {},
	map: {},
	players: {}
	};

function getGameState()
{
	return {
	rails: G.rails,
	map: G.map,
	players: {}
	};
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
			var best = G.map.terrain.length + CELLS_PER_ROW;
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

if (global)
{
	global.autoCreateDemands = autoCreateDemands;
	global.getGameState = getGameState;
}
