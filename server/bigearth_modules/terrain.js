// TERRAIN DATA STRUCTURE
//  terrain - the type of terrain
//  fleets - set of fleet ids, listing anyone that is in this cell
//  battle - optional, identifies the battle taking place in this cell
//

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
var WILDLIFE_LIFESPAN = 5;
var WILDLIFE_EMIGRATION_RATE = 0.25;

var PEASANTS_QUOTA = {
	'glacier': 0,
	'desert': 0,
	'tundra': 0,
	'mountain': 100,
	'plains': 1000,
	'hills': 1000,
	'grasslands': 1000,
	'forest': 500,
	'swamp': 100,
	'jungle': 100,
	'ocean': 0,
	other_terrain: 0
	};
var PEASANTS_LIFESPAN = 30;
var PEASANTS_EMIGRATION_RATE = 0.10;


function hasRiver(edgeId)
{
	var e = G.terrain.edges[edgeId];
	return (e && e.feature == 'river')
}

function terrainEndOfYear_prepare(cellId, cell)
{
	cell.wildlifeImmigrants = 0;
	cell.wildlifeHunted = 0;
	cell.peasantImmigrants = 0;
}

//prereqs: wildlifeHunted should be calculated before this function is called
//
function terrainEndOfYear_pass1(cellId, cell)
{
	var terrainType = cell.terrain;
	var wildlifeQuota = WILDLIFE_QUOTA[terrainType] || WILDLIFE_QUOTA.other_terrain || 0;
	var curCount = cell.wildlife || 0;

	if (!(cell.wildlifeHunted >= 0 && cell.wildlifeHunted <= curCount))
	{
		console.log("WARNING! wildlifeHunted for "+cellId+" is impossible ("+cell.wildlifeHunted+"/"+curCount+")");
		cell.wildlifeHunted = curCount;
	}

	cell.wildlifeBirths = Randomizer((wildlifeQuota/WILDLIFE_LIFESPAN) * Math.pow(0.5 - 0.5*Math.cos(Math.PI * Math.sqrt(curCount / wildlifeQuota)), 2.0));

	var deaths = Randomizer(curCount / WILDLIFE_LIFESPAN);
	cell.wildlifeDeaths = cell.wildlifeHunted > deaths ? 0 :
			deaths > curCount ? curCount - cell.wildlifeHunted :
			deaths - cell.wildlifeHunted;

	var adjustedCount = curCount - (cell.wildlifeHunted + cell.wildlifeDeaths);
	var emigrantsBase = WILDLIFE_EMIGRATION_RATE * adjustedCount * (adjustedCount / wildlifeQuota);
	cell.wildlifeEmigrants = 0;

	var nn = BE.geometry.getNeighbors(cellId);
	for (var i = 0, l = nn.length; i < l; i++)
	{
		var n = G.terrain.cells[nn[i]];

		var emigrants = emigrantsBase / l;
		if (cell.terrain == 'ocean' && n.terrain == 'ocean')
		{
			// ocean-to-ocean migration, twice as fluid
			emigrants *= 2;
		}
		else if (cell.terrain == 'ocean' || n.terrain == 'ocean')
		{
			emigrants = 0;
		}
		else		// land-to-land emigration
		{
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

	//
	// now, do the same thing but for peasants
	//

	var peasantsQuota = PEASANTS_QUOTA[terrainType] || PEASANTS_QUOTA.other_terrain || 0;
	var curCount = cell.peasants || 0;
	cell.peasantsBirths = Randomizer((peasantsQuota/PEASANTS_LIFESPAN) * Math.pow(0.5 - 0.5*Math.cos(Math.PI * Math.sqrt(curCount/peasantsQuota)), 2.0));

	var deaths = Randomizer(curCount / PEASANTS_LIFESPAN);
	cell.peasantsDeaths = deaths > curCount ? curCount :
			deaths;

	var adjustedCount = curCount - cell.peasantDeaths;
	var emigrantsBase = PEASANTS_EMIGRATION_RATE * adjustedCount * (adjustedCount / peasantsQuota);
	cell.peasantsEmigrants = 0;

	for (var i = 0, l = nn.length; i < l; i++)
	{
		var n = G.terrain.cells[nn[i]];

		var emigrants = emigrantsBase / l;
		if (cell.terrain == 'ocean' || n.terrain == 'ocean')
		{
			emigrants = 0;
		}
		else if (hasRiver(BE.geometry._makeEdge(cellId, nn[i])))
		{
			// no migration across rivers
			emigrants = 0;
		}
			
		cell.peasantsEmigrants += emigrants;
		n.peasantsImmigrants += emigrants;
	}
}

function terrainEndOfYear_cleanup(cellId, cell)
{
	cell.wildlife += cell.wildlifeBirths - cell.wildlifeDeaths - cell.wildlifeHunted +
			cell.wildlifeImmigrants - cell.wildlifeEmigrants;
	if (cell.wildlife < 0)
		cell.wildlife = 0;

	cell.peasants += cell.peasantsBirths - cell.peasantsDeaths +
			cell.peasantsImmigrants - cell.peasantsEmigrants;
	if (cell.peasants < 0)
		cell.peasants = 0;
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

var ZONE_TYPES = {
	'mud-cottages': { description: 'about 20 primitive mud houses',
		maxHousing: 200,
		builders: 50,
		productionCost: 100
		},
	'wood-cottages': { description: 'about 20 primitive wooden houses',
		maxHousing: 200,
		builders: 60,
		productionCost: 120,
		resourceCost: { wood: 10 }
		},
	'stone-cottages': { description: 'about 20 primitive stone houses',
		maxHousing: 200,
		builders: 60,
		productionCost: 180,
		resourceCost: { 'stone-block': 30 }
		},
	'farm': { description: 'land cultivated for growing wheat',
		builders: 25,
		productionCost: 50
		},
	'natural': { description: 'the natural land type for this terrain' },
	'forest': { },  //unused
	};

function getLandTypeInfo(landType)
{
	return ZONE_TYPES[landType];
}

function getHousing(cellId)
{
	var c = G.terrain.cells[cellId];
	if (!c) return 0;

	var sum = 0;
	for (var zoneType in c.zones)
	{
		var zi = ZONE_TYPES[zoneType];
		if (zi && zi.maxHousing)
			sum += c.zones[zoneType] * zi.maxHousing;
	}
	return sum;
}

global.terrainEndOfYear_prepare = terrainEndOfYear_prepare;
global.terrainEndOfYear_pass1 = terrainEndOfYear_pass1;
global.terrainEndOfYear_cleanup = terrainEndOfYear_cleanup;

exports.calculateHuntingRate = calculateHuntingRate;
exports.getHousing = getHousing;
exports.getLandTypeInfo = getLandTypeInfo;
