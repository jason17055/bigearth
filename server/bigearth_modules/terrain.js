var WILDLIFE_QUOTA = {
	'glacier': 10,
	'desert': 20,
	'tundra': 15,
	'mountain': 30,
	'plains': 50,
	'hills': 50,
	'grasslands': 75,
	'forest': 100,
	'swamp': 100,
	'jungle': 100,
	'ocean': 100,
	other_terrain: 10
	};

function hasRiver(edgeId)
{
	var e = G.terrain.edges[edgeId];
	return (e && e.feature == 'river')
}

function terrainEndOfYear_prepare(cellId, cell)
{
	cell.wildlifeImmigrants = 0;
	cell.wildlifeHunted = 0;
}

//prereqs: wildlifeHunted should be calculated before this function is called
//
function terrainEndOfYear_pass1(cellId, cell)
{
	var terrainType = cell.terrain;
	var quota = WILDLIFE_QUOTA[terrainType] || WILDLIFE_QUOTA.other_terrain || 0;
	var curCount = cell.wildlife || 0;

	if (!(cell.wildlifeHunted >= 0 && cell.wildlifeHunted <= curCount))
	{
		console.log("WARNING! wildlifeHunted for "+cellId+" is impossible ("+cell.wildlifeHunted+"/"+curCount+")");
		cell.wildlifeHunted = curCount;
	}

	var births = Randomizer((quota/5) * Math.pow(0.5 - 0.5*Math.cos(Math.PI * Math.sqrt(curCount / quota)), 2.0));
	cell.wildlifeBirths = births;

	var deaths = Randomizer(curCount / 5);
	cell.wildlifeDeaths = cell.wildlifeHunted > deaths ? 0 :
			deaths > curCount ? curCount - cell.wildlifeHunted :
			deaths - cell.wildlifeHunted;

	var adjustedCount = curCount - (cell.wildlifeHunted + cell.wildlifeDeaths);
	var emigrantsBase = 0.5 * adjustedCount * (adjustedCount / quota);
	cell.wildlifeEmigrants = 0;

	var nn = BE.geometry.getNeighbors(cellId);
	for (var i = 0, l = nn.length; i < l; i++)
	{
		var n = G.terrain.cells[nn[i]];

		var emigrants = emigrantsBase / l;
		if (cell.terrain == 'ocean' && n.terrain == 'ocean')
		{
			emigrants *= 1;
		}
		else if (cell.terrain == 'ocean' || n.terrain == 'ocean')
		{
			emigrants = 0;
		}
		else
		{
			// land-to-land emigration
			emigrants *= 0.5;

			// check for presence of a river
			var eId = BE.geometry._makeEdge(cellId, nn[i]);
			if (hasRiver(eId))
			{
				emigrants = 0;
			}
		}

		cell.wildlifeEmigrants += emigrants;
		n.wildlifeImmigrants += emigrants;
	}
}

function terrainEndOfYear_cleanup(cellId, cell)
{
	if (cell.wildlifeHunted)
	{
		console.log("cell "+cellId);
		console.log("  wildlife count:     "+cell.wildlife);
		console.log("  wildlife births:    "+cell.wildlifeBirths);
		console.log("  wildlife hunted:    "+cell.wildlifeHunted);
		console.log("  wildlife deaths:    "+cell.wildlifeDeaths);
		console.log("  wildlife immigrants:"+cell.wildlifeImmigrants);
		console.log("  wildlife emigrants: "+cell.wildlifeEmigrants);
	}

	cell.wildlife += cell.wildlifeBirths - cell.wildlifeDeaths - cell.wildlifeHunted +
			cell.wildlifeImmigrants - cell.wildlifeEmigrants;
	if (cell.wildlife < 0)
		cell.wildlife = 0;
}

function Randomizer(x)
{
	var t = Math.random();
	if (t == 0) return 0;
	return x * Math.exp( -Math.log((1/t)-1) / 15 );
}

function calculateHuntingRate(terrainCell, numWorkers)
{
	var numWildlife = terrainCell.wildlife || 0;
	if (numWildlife == 0)
		return 0;

	var s = 0.5 * Math.sqrt(numWildlife / 40);
	return numWildlife - numWildlife * Math.exp(-s * numWorkers / numWildlife);
}

global.terrainEndOfYear_prepare = terrainEndOfYear_prepare;
global.terrainEndOfYear_pass1 = terrainEndOfYear_pass1;
global.terrainEndOfYear_cleanup = terrainEndOfYear_cleanup;

exports.calculateHuntingRate = calculateHuntingRate;
