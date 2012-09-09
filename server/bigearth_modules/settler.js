var Location = require('../../html/location.js');
var City = require('./city.js');

function autoSettle(fleetId, fleet, currentOrder)
{
	var seen = {};
	var curRing = {};
	var fleetCellId = Location.toCellId(fleet.location);
	seen[fleetCellId] = true;
	curRing[fleetCellId] = true;

	var map = G.maps[fleet.owner];

	var bestValue = getSettlementFitness(map, fleetCellId);
	var best = fleet.location;
	for (var dist = 0; ; dist++)
	{
		var nextRing = {};
		for (var cid in curRing)
		{
			var v = getSettlementFitness(map, cid);
			if (v > bestValue)
			{
				bestValue = v;
				best = cid;
			}

			var nn = BE.geometry.getNeighbors(cid);
			for (var j = 0; j < nn.length; j++)
			{
				var nid = nn[j];
				if (!seen[nid])
				{
					seen[nid] = true;
					nextRing[nid] = true;
				}
			}
		}
		curRing = nextRing;

		if (dist >= 5 && bestValue >= 3.0)
		{
			fleet.orders.shift();
			fleet.orders.unshift({
				command: 'build-city'
				});
			fleet.orders.unshift({
				command: 'goto',
				location: best
				});
			return fleetActivity(fleetId, fleet);
		}
	}

	return fleetActivityError(fleetId, fleet, "Unable to find a suitable city location.")
}

function getSettlementFitness(map, cellId)
{
	var thisCellFitness = 0;

	var c = map.cells[cellId];
	if (!c || c.city)
	{
		return 0;
	}

	if (c.terrain == 'grassland' ||
		c.terrain == 'plains' ||
		c.terrain == 'forest' ||
		c.terrain == 'hills')
	{
		thisCellFitness = 1;
	}

	var numGoodNeighbors = 0;
	var numRivers = 0;
	var nn = BE.geometry.getNeighbors(cellId);
	for (var i = 0; i < nn.length; i++)
	{
		var n = map.cells[nn[i]];
		if (!n) continue;
		if (n.terrain == 'grassland' ||
			n.terrain == 'plains' ||
			n.terrain == 'forest' ||
			n.terrain == 'hills')
		{
			numGoodNeighbors++;
		}

		var eId = BE.geometry._makeEdge(cellId, nn[i]);
		var e = map.edges[eId];
		if (e && e.feature == 'river')
		{
			numRivers++;
		}
	}

	var neighborsFitness = numGoodNeighbors / 5 - 0.3 * Math.abs(numRivers - 2);

	// look up to five cells away for another city
	var dist = distanceToNearestCity(map, cellId, 5);
	var distCityFitness = 2 - 4/dist;
	return thisCellFitness + neighborsFitness + distCityFitness;
}

function distanceToNearestCity(map, cellId, maxDist)
{
	var seen = {};
	var ring = {};
	ring[cellId] = true;
	seen[cellId] = true;

	for (var dist = 0; dist <= maxDist; dist++)
	{
		var nextRing = {};
		for (var cid in ring)
		{
			seen[cid] = true;
			var c = map.cells[cid];
			if (c && c.city)
				return dist;

			var nn = BE.geometry.getNeighbors(cid);
			for (var j = 0; j < nn.length; j++)
			{
				var nid = nn[j];
				if (!seen[nid])
				{
					seen[nid] = true;
					nextRing[nid] = true;
				}
			}
		}
		ring = nextRing;
	}

	return maxDist+1;
}

var CITY_NAMES_DICTIONARY = [
	'Beijing', 'Bogota', 'Buenos Aires', 'Cairo', 'Delhi', 'Dhaka',
	'Guangzhou', 'Istanbul', 'Jakarta', 'Karachi', 'Kinshasa', 'Kolkata',
	'Lagos', 'Lima', 'London', 'Los Angeles', 'Manila', 'Mexico City',
	'Moscow', 'Mumbai', 'New York', 'Osaka', 'Rio de Janeiro', 'Sao Paulo',
	'Seoul', 'Shanghai', 'Shenzhen', 'Tehran', 'Tianjin', 'Tokyo'
	];

function pickCityName(map)
{
	var taken = {};
	for (var cid in map.cells)
	{
		var c = map.cells[cid];
		if (c.city && c.city.name)
		{
			taken[c.city.name] = true;
		}
	}

	var l = CITY_NAMES_DICTIONARY.length;
	while (l>0)
	{
		var i = Math.floor(Math.random() * l);
		var name = CITY_NAMES_DICTIONARY[i];

		if (!taken[name])
			return name;

		CITY_NAMES_DICTIONARY[i] = CITY_NAMES_DICTIONARY[l-1];
		CITY_NAMES_DICTIONARY[l-1] = name;
		l--;
	}

	return null;
}

function buildCity(fleetId, fleet, currentOrder)
{
	if (!fleetHasCapability(fleet, 'build-city'))
		return fleetActivityError(fleetId, fleet, "Cannot build city with this unit type");

	if (fleet.activity == 'build-city')
	{
		if (!Location.isCell(fleet.location))
		{
			throw new Error("cannot build city on non-cell terrain; don't know what to do");
		}

		var terrainCell = G.terrain.cells[Location.toCellId(fleet.location)];
		if (terrainCell.city)
		{
			return fleetActivityError(fleetId, fleet, "There is already a city here.");
		}

		var city = newCity(fleet.location, fleet.owner);
		var tid = city._id;

		var map = G.maps[fleet.owner];
		city.name = pickCityName(G.maps[fleet.owner]);

		terrainCell.city = tid;
		developLand(fleet.location, 'hamlet', 1);
		updateFleetSight(tid, city);

		setFleetActivityFlag(fleetId, fleet, null);
		return fleetDisbandInCity(fleetId, fleet);
	}
	else
	{
		setFleetActivityFlag(fleetId, fleet, 'build-city');
		fleetCooldown(fleetId, fleet, 5000);
	}
}

function disbandInCity(fleetId, fleet, currentOrder)
{
	var location = fleet.location;
	var cellId = Location.toCellId(location);

	var cityId = G.terrain.cells[cellId].city;
	var city = G.cities[cityId];
	if (!city && G.terrain.cells[cellId].terrain == 'ocean')
	{
		// try to find a city in an adjoining cell
		var nn = BE.geometry.getNeighbors(cellId);
		for (var i = 0; i < nn.length; i++)
		{
			var c = G.terrain.cells[nn[i]];
			if (c.city)
			{
				cityId = c.city;
				city = G.cities[cityId];
				break;
			}
		}
	}
	if (!city)
	{
		return fleetActivityError(fleetId, fleet, "No city at this location");
	}

	if (fleet.population)
	{
		city_addWorkersAny(cityId, city, fleet.population);
	}

	destroyFleet(fleetId, 'disband-in-city');
}

exports.buildCity = buildCity;
exports.autoSettle = autoSettle;
exports.disbandInCity = disbandInCity;
