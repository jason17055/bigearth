var WILDLIFE_QUOTA = [
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
	];

function terrainEndOfYear_prepare(loc, cell)
{
	cell.wildlifeImmigrants = 0;
	cell.wildlifeHunted = 0;
}

function terrainEndOfYear_pass1(loc, cell)
{
	var terrainType = cell.terrain;
	var quota = WILDLIFE_QUOTA[terrainType] || WILDLIFE_QUOTA.other_terrain || 0;
	var curCount = cell.wildlife || 0;

	var births = Randomizer((quota/5) * Math.pow(0.5 - 0.5*Math.cos(Math.PI * Math.sqrt(curCount / quota)), 2.0));
	cell.wildlifeBirths = births;

	var deaths = Randomizer(curCount / 5);
	cell.wildlifeDeaths = cell.wildlifeHunted > deaths ? 0 :
			deaths > curCount ? curCount - cell.wildlifeHunted :
			deaths - cell.wildlifeHunted;

	var adjustedCount = curCount - (cell.wildlifeHunted + cell.wildlifeDeaths);
	var emigrantsBase = 0.5 * adjustedCount * (adjustedCount / quota);
	cell.wildlifeEmigrants = 0;

	var nn = G.geometry.getNeighbors(loc);
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
			var eId = G.geometry._makeEdge(cellId, nn[i]);
			if (hasRiver(eId))
			{
				emigrants = 0;
			}
		}

		cell.wildlifeEmigrants += emigrants;
		n.wildlifeImmigrants += emigrants;
	}
}

function terrainEndOfYear_cleanup(loc, cell)
{
	cell.wildlife += cell.wildlifeBirths - cell.wildlifeDeaths - cell.wildlifeHunted +
			cell.wildlifeImmigrants - cell.wildlifeEmigrants;
}

function Randomizer(x)
{
	var t = Math.random();
	if (t == 0) return 0;
	return x * Math.exp( -Math.log((1/t)-1) / 15 );
}

global.terrainEndOfYear_prepare = terrainEndOfYear_prepare;
global.terrainEndOfYear_pass1 = terrainEndOfYear_pass1;
global.terrainEndOfYear_cleanup = terrainEndOfYear_cleanup;
