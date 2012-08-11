if (typeof require !== 'undefined')
{
//require('../html/trains-common.js');
}

var Scheduler = require('./bigearth_modules/scheduler.js');

var G = {
	world: {},
	terrain: {},
	players: {},
	maps: {},
	fleets: {}
	};

function discoverCell(playerId, location)
{
	var isNew = false;
	var refCell = G.terrain.cells[location];

	var map = G.maps[playerId];
	var mapCell = map.cells[location];
	if (!mapCell)
	{
		isNew = true;
		mapCell = {};
	}
	if (mapCell.terrain != refCell.terrain)
	{
		isNew = true;
		mapCell.terrain = refCell.terrain;
	}

	for (var subcellType in refCell.subcells)
	{
		if (subcellType == 'natural')
			continue;

		if (!mapCell.subcells)
			mapCell.subcells = {};

		if (refCell.subcells[subcellType] != mapCell.subcells[subcellType])
		{
			mapCell.subcells[subcellType] = refCell.subcells[subcellType];
			isNew = true;
		}
	}
	if (mapCell.subcells)
	{
		for (var subcellType in mapCell.subcells)
		{
			if (!mapCell.subcells[subcellType])
			{
				delete mapCell.subcells[subcellType];
				isNew = true;
			}
		}
	}

	if (mapCell.city && !refCell.city)
	{
		isNew = true;
		delete mapCell.city;
	}
	else if (refCell.city)
	{
		var city = G.cities[refCell.city] || {};
		if (!mapCell.city || mapCell.city.id != refCell.city)
		{
			isNew = true;
			mapCell.city = { id: refCell.city };
		}

		if ('fuel' in mapCell.city)
		{
			//compatibility fix
			isNew = true;
			delete mapCell.city.fuel;
		}

		var props = {
		name: "public",
		size: "public",
		owner: "public",
		population: "private floor",
		food: "private floor",
		clay: "private floor",
		meat: "private floor",
		stone: "private floor",
		wheat: "private floor",
		wood: "private floor",
		'stone-block': "private floor",
		'stone-weapon': "private floor",
		children: "private floor",
		activity: "private",
		activityTime: "private",
		activityComplete: "private",
		activitySpeed: "private"
		};

		for (var p in props)
		{
			if (props[p].match(/private/) && city.owner != playerId)
				continue;

			var refValue = city[p];
			if (props[p].match(/floor/))
				refValue = Math.floor(refValue);

			if (mapCell.city[p] != refValue)
			{
				isNew = true;
				mapCell.city[p] = refValue;
			}
		}

		if (city.owner == playerId)
		{
			if (!mapCell.city.workers)
				mapCell.city.workers = {};

			var ww = roundWorkers(city.workers);
			addAvailableJobs(refCell.city, ww);
			for (var j in mapCell.city.workers)
			{
				if (!(j in ww))
				{
					isNew = true;
					delete mapCell.city.workers[j];
				}
			}
			for (var j in ww)
			{
				if (mapCell.city.workers[j] != ww[j])
				{
					isNew = true;
					mapCell.city.workers[j] = ww[j];
				}
			}

			if (!mapCell.city.buildings)
			{
				mapCell.city.buildings = {};
			}
			delete mapCell.city.buildingOrders;

var discoverBuilding = function(realBuilding, mapBuilding, playerId)
{
	var isNew = false;

	if (realBuilding.buildingType != mapBuilding.buildingType)
	{
		isNew = true;
		mapBuilding.buildingType = realBuilding.buildingType;
	}
	if (realBuilding.size != mapBuilding.size)
	{
		isNew = true;
		mapBuilding.size = realBuilding.size;
	}
	if (realBuilding.orders != mapBuilding.orders)
	{
		isNew = true;
		mapBuilding.orders = realBuilding.orders
	}
	return isNew;
};

			// check for buildings which no longer exist
			for (var bt in mapCell.city.buildings)
			{
				if (!city.buildings[bt])
				{
					isNew = true;
					delete mapCell.city.buildings[bt];
				}
			}

			// check for buildings that are new or changed
			for (var bt in city.buildings)
			{
				if (!mapCell.city.buildings[bt])
				{
					mapCell.city.buildings[bt] = {};
				}
				if (discoverBuilding(city.buildings[bt], mapCell.city.buildings[bt], playerId))
				{
					isNew = true;
				}
			}
		}
	}

	if (isNew)
	{
		map.cells[location] = mapCell;
		notifyPlayer(playerId, {
			event: 'map-update',
			location: location,
			locationType: 'cell',
			data: mapCell
			});
	}

	var nn = G.geometry.getNeighbors(location);
	for (var i = 0; i < nn.length; i++)
	{
		if (map.cells[nn[i]])
		{
			var eId = G.geometry._makeEdge(location, nn[i]);
			discoverEdge(playerId, eId);
		}
	}
}

function addAvailableJobs(cityId, jobs)
{
	var city = G.cities[cityId];
	var cell = G.terrain.cells[city.location];

	if (!jobs.hunt)
		jobs.hunt = 0;
	if (!jobs.childcare)
		jobs.childcare = 0;

	if (!jobs['gather-wood'] && cell.terrain == 'forest')
		jobs['gather-wood'] = 0;

	if (!jobs['gather-clay'] && (
			cell.terrain == 'grassland' ||
			cell.terrain == 'hills' ||
			cell.terrain == 'plains' ||
			cell.terrain == 'mountains' ||
			cell.terrain == 'swamp'))
		jobs['gather-clay'] = 0;

	if (!jobs['gather-stone'] && (
			cell.terrain == 'desert' ||
			cell.terrain == 'forest' ||
			cell.terrain == 'grassland' ||
			cell.terrain == 'hills' ||
			cell.terrain == 'mountains' ||
			cell.terrain == 'plains' ||
			cell.terrain == 'swamp' ||
			cell.terrain == 'tundra'))
		jobs['gather-stone'] = 0;

	if (!jobs.farm && cell.subcells.farm)
		jobs.farm = 0;

	for (var bid in city.buildings)
	{
		var b = city.buildings[bid];
		if (b.orders && b.orders.match(/^make-/))
		{
			if (!jobs[b.orders])
				jobs[b.orders] = 0;
		}
	}
}

// given an associative array, return a new associative array with
// all the numbers rounded up, in such a way that the sum of the
// numbers in the result equals the floored sum of the input.
//
function roundWorkers(aa)
{
	var err = 0;
	var sum = 0;
	var jj = [];
	for (var k in aa)
	{
		jj.push([k,aa[k]]);
		sum += aa[k];
	}
	var target = Math.floor(sum);

	if (jj.length == 0)
		return {};

	// sort smallest to largest
	jj.sort(function(a,b) {
		return a[1]-b[1];
		});

	var result = {};
	for (var i = 0; i + 1 < jj.length; i++)
	{
		var x = jj[i];
		var v = Math.ceil(x[1]-err);
		err += v - x[1];
		sum -= x[1];
		target -= v;
		result[x[0]] = v;
	}
	result[jj[jj.length-1][0]] = target;

	return result;
}

function discoverEdge(playerId, eId)
{
	var isNew = false;

	var refEdge = G.terrain.edges[eId] || {};
	var map = G.maps[playerId];
	var mapEdge = map.edges[eId];

	if (!mapEdge)
	{
		mapEdge = {};
		if (refEdge.feature)
			mapEdge.feature = refEdge.feature;
		isNew = true;
	}
	else if (mapEdge.feature != refEdge.feature)
	{
		mapEdge.feature = refEdge.feature;
		isNew = true;
	}

	if (isNew)
	{
		map.edges[eId] = mapEdge;
		notifyPlayer(playerId, {
			event: 'map-update',
			location: eId,
			locationType: 'edge',
			data: mapEdge
			});
	}
}

function discoverCellBorder(playerId, cellIdx)
{
	var ee = G.geometry.getEdgesAdjacentToCell(cellIdx);
	for (var i = 0; i < ee.length; i++)
	{
		discoverEdge(playerId, ee[i]);
	}

	var nn = G.geometry.getNeighbors(cellIdx);
	for (var i = 0; i < nn.length; i++)
	{
		discoverCell(playerId, nn[i]);
	}
}

function fleetCurrentCommandFinished(fleetId, fleet)
{
	fleet.orders.shift();
	fleetActivity(fleetId);
}

function fleetHasCapability(fleet, capability)
{
	return (fleet.type == 'settler');
}

function setFleetActivityFlag(fleetId, fleet, newActivity)
{
	if (newActivity)
	{
		fleet.activity = newActivity;
	}
	else
	{
		delete fleet.activity;
	}

	notifyPlayer(fleet.owner, {
		event: 'fleet-activity',
		fleet: fleetId,
		activity: fleet.activity
		});
	
}

function playerCanSee(playerId, location)
{
	var cell = getTerrainLocation(location);
	if (!cell.seenBy)
		return false;

	for (var fid in cell.seenBy)
	{
		var f = G.fleets[fid] || G.cities[fid];
		if (!f)
			throw new Error("unexpected: cell "+location+" has seenby of "+fid+" but this is neither a fleet nor a city");
		if (f.owner == playerId)
			return true;
	}
	return false;
}

function terrainChanged(cellId)
{
	for (var mapId in G.maps)
	{
		var mapOwnerId = mapId;
		if (playerCanSee(mapOwnerId, cellId))
		{
			discoverCell(mapOwnerId, cellId);
		}
	}
}

function fleetActivityError(fleetId, fleet, errorMessage)
{
	console.log("fleet #"+fleetId + " error: " + errorMessage);
}

function fleetDisbandInCity(fleetId, fleet)
{
	var location = fleet.location;
	var cityId = G.terrain.cells[location].city;
	var city = G.cities[cityId];
	if (!city && G.terrain.cells[location].terrain == 'ocean')
	{
		// try to find a city in an adjoining cell
		var nn = G.geometry.getNeighbors(location);
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
		lockCityStruct(city);
		addWorkers(cityId, city, fleet.population/2, 'hunt');
		addWorkers(cityId, city, fleet.population/2, 'childcare');
		unlockCityStruct(cityId, city);
	}

	destroyFleet(fleetId, 'disband-in-city');
}

function allPlayersWhoCanSee(location)
{
	var cell = getTerrainLocation(location);
	if (!cell.seenBy)
		return {};

	var seenByPid = {};
	for (var fid in cell.seenBy)
	{
		var fleet = G.fleets[fid] || G.cities[fid];
		if (fleet.owner)
		{
			seenByPid[fleet.owner] = true;
		}
	}
	return seenByPid;
}

function destroyFleet(fleetId, disposition)
{
	var fleet = G.fleets[fleetId];
	var location = fleet.location;

	notifyPlayer(fleet.owner, {
		event: 'fleet-terminated',
		fleet: fleetId,
		location: location,
		disposition: disposition
		});

	var pp = allPlayersWhoCanSee(location);
	for (var pid in pp)
	{
		if (pid != fleet.owner)
		{
			// notify foreign player that can also see this fleet
			notifyPlayer(pid, {
				event: 'fleet-terminated',
				fleet: fleetId,
				location: location,
				disposition: disposition
				});
		}
	}

	if (fleet.canSee)
	{
		for (var loc in fleet.canSee)
		{
			removeFleetCanSee(fleetId, fleet, loc);
		}
	}
	delete G.fleets[fleetId];
}

function tryToBuildCity(fleetId, fleet)
{
	if (!fleetHasCapability(fleet, 'build-city'))
		return fleetCurrentCommandFinished(fleetId, fleet);

	if (fleet.activity == 'build-city')
	{
		if (G.terrain.cells[fleet.location].city)
		{
			return fleetActivityError(fleetId, fleet, "There is already a city here.");
		}

		var tid = nextFleetId();
		var city = {
			owner: fleet.owner,
			location: fleet.location,
			wheat: 100,
			wood: 50,
			workers: {},
			workerRates: {},
			production: {},
			population: 0,
			children: 0,
			childrenByAge: [],
			lastUpdate: Scheduler.time
			};
		G.cities[tid] = city;
		G.terrain.cells[fleet.location].city = tid;
		terrainChanged(fleet.location);
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

function moveFleetAlongCoast(fleetId)
{
	var fleet = G.fleets[fleetId];
	var oldLoc = fleet.location;

	var nn = G.geometry.getNeighbors(oldLoc);
	var lastLoc = fleet.lastLocation;

	var i;
	for (i = 0; i < nn.length; i++)
	{
		if (lastLoc && nn[i] == lastLoc)
			break;
	}

	var foundSea = false;
	var map = G.maps[fleet.owner];
	for (var j = 0; j < nn.length + 6; j++)
	{
		var nid = nn[(i+1+j)%nn.length];
		var c = map.cells[nid];
		if (!c)
			continue;
		if (foundSea && c.terrain != 'ocean')
		{
			return moveFleetOneStep(fleetId, nid);
		}
		else if (c.terrain == 'ocean')
		{
			foundSea=true;
		}
	}

	// don't know where to go
	return fleetCurrentCommandFinished(fleetId, fleet);
}

function moveFleetTowards(fleetId, targetLocation)
{
	var fleet = G.fleets[fleetId];
	var oldLoc = fleet.location;

	if (!targetLocation || oldLoc == targetLocation)
	{
		return fleetCurrentCommandFinished(fleetId, fleet);
	}

console.log("moving fleet "+fleetId+" from " + oldLoc + " to " + targetLocation);

	if (fleet.path && fleet.path.length >= 1)
	{
console.log("using memoized path (length " + fleet.path.length + ")");

		var nextLoc = fleet.path.shift();
		if (isNavigableByMap(G.maps[fleet.owner], fleet, nextLoc))
			return moveFleetOneStep(fleetId, nextLoc);
		delete fleet.path;
	}

// perform a shortest path search for the destination

console.log("must perform a shortest path search");

	fleet.path = shortestPathByMap(G.maps[fleet.owner], fleet, oldLoc, targetLocation);

console.log("shortest path is", fleet.path);
if (fleet.path.length > 50)
	fleet.path.splice(0,50);

	var nextLoc = fleet.path.shift();
	if (nextLoc && isNavigableByMap(G.maps[fleet.owner], fleet, nextLoc))
		return moveFleetOneStep(fleetId, nextLoc);
	else
		return fleetActivityError(fleetId, fleet, "Cannot reach destination");
}

//FIXME- this function accesses server-side terrain data to
//find the best route; obviously that is not desirable.
//
function shortestPathByMap(map, fleet, fromLoc, toLoc)
{
	var baseDist = null;

	var seen = {};
	var buildPath = function(loc)
	{
		var path = [];
		while (loc != fromLoc)
		{
			path.unshift(loc);
			loc = seen[loc];
			if (!loc)
				throw new Error("unexpected");
		}
		return path;
	};

	// each entry in Q is [ cellId, lastCell, accumDist, estRemainDist ];

	var Q = [];
	Q.push([ fromLoc, 0, 0, Infinity ]);

	var countIterations = 0;
	var bestSoFar;               //in case we stop early
	var bestSoFarScore = Infinity;

	while (Q.length)
	{
		var cur = Q.shift();
		var curLoc = cur[0];

		if (cur[3] < bestSoFarScore)
		{
			bestSoFar = curLoc;
			bestSoFarScore = cur[3];
		}

		if (++countIterations > 500)
		{
			console.log("shortestPath taking too long, aborting");
			
			return buildPath(bestSoFar);
		}

		if (seen[curLoc])
			continue;
		seen[curLoc] = cur[1];

		var nn = G.geometry.getNeighbors(curLoc);
		if (!baseDist)
			baseDist = G.geometry.distanceBetween(curLoc, nn[0]);

		for (var i = 0, l = nn.length; i < l; i++)
		{
			if (seen[nn[i]])
				continue;

			if (nn[i] == toLoc)
			{
				seen[nn[i]] = curLoc;
				return buildPath(toLoc);
			}

			var accumDist = cur[2] + getFleetMovementCostByMap(fleet, curLoc, nn[i], map);
			var estRemainDistSteps = G.geometry.distanceBetween(nn[i], toLoc) / baseDist;
			var estRemainDist = estRemainDistSteps * 3000;

			Q.push([ nn[i], curLoc, accumDist, estRemainDist ]);
		}
		Q.sort(function(a,b) {

			return (a[2] + a[3]) - (b[2] + b[3]);
			});
	}

	// ran out of options to try
	return [];
}

function moveFleetRandomly(fleetId)
{
	//broken
}

var UNIT_MOVEMENT_RULES = {
	explorer: {
		plains:  600,
		grassland: 600,
		desert: 600,
		forest: 1200,
		swamp: 1200,
		ocean: 15000,
		tundra: 1200,
		glacier: 1200,
		hills: 1800,
		mountains: 2400,
		across_river: 1200,
		other_terrain: 600
		},
	trieme: {
		ocean: 800,
		other_terrain: 15000
		},
	'*': {
		ocean: 15000,
		'other_terrain': 3600
		},
	};

function isNavigableByMap(map, fleet, location)
{
	var unitType = fleet.type;
	var UM = UNIT_MOVEMENT_RULES[unitType] || UNIT_MOVEMENT_RULES['*'];
	var c = map.cells[location];
	if (!c)
		return false;

	var cost = UM[c.terrain] || UM.other_terrain;
	return cost < 15000;
}

function getUnitMovementCostByMap(unitType, oldLoc, newLoc, map)
{
	var UM = UNIT_MOVEMENT_RULES[unitType] || UNIT_MOVEMENT_RULES['*'];

	var oldLocCell = map.cells[oldLoc];
	var newLocCell = map.cells[newLoc];

	var UNKNOWN_TERRAIN_COST = 1100;
	var costOldCell = oldLocCell && oldLocCell.terrain ?
		((UM[oldLocCell.terrain] || UM.other_terrain) / 2) :
		UNKNOWN_TERRAIN_COST;
;
	var costNewCell = newLocCell && newLocCell.terrain ?
		((UM[newLocCell.terrain] || UM.other_terrain) / 2) :
		UNKNOWN_TERRAIN_COST;

	var cost = costOldCell + costNewCell;

	// consider cost of crossing a river, if there is one.

	var eId = G.geometry._makeEdge(oldLoc, newLoc);
	var e = map.edges[eId];
	var riverCrossing = false;
	if (e && e.feature && e.feature == 'river')
	{
		riverCrossing = true;
		cost += (UM.across_river || 0);
	}

	return cost;
}

function getUnitMovementCost(unitType, oldLoc, newLoc)
{
	var locationInfo = function(loc)
	{
		if ((""+loc).match(/^(\d+)$/))
		{
			return { locationType: 'cell', location: +loc };
		}
		else if ((""+loc).match(/^(\d+)-(\d+)$/))
		{
			return { locationType: 'edge', location: loc };
		}
		else
		{
			return { locationType: 'vertex', location: loc };
		}
	};

	var ol = locationInfo(oldLoc);
	var ne = locationInfo(newLoc);
	var UM = UNIT_MOVEMENT_RULES[unitType] || UNIT_MOVEMENT_RULES['*'];

	if (ol.locationType == 'cell' && ne.locationType == 'cell')
	{
		//CASE 1 : from center hex to center of neighboring hex

		var costOldCell = (UM[G.terrain.cells[ol.location].terrain] || UM.other_terrain) / 2;
		var costNewCell = (UM[G.terrain.cells[ne.location].terrain] || UM.other_terrain) / 2;

		var cost = costOldCell + costNewCell;

		// TODO - consider cost of crossing a river, if there is
		// one.
		var eId = G.geometry._makeEdge(ol.location, ne.location);
		var e = G.terrain.edges[eId];
		var riverCrossing = false;
		if (e && e.feature && e.feature == 'river')
		{
			riverCrossing = true;
			cost += (UM.across_river || 0);
		}

		console.log("from " + G.terrain.cells[ol.location].terrain + " to " + G.terrain.cells[ne.location].terrain +
			(riverCrossing ? " (w/ river)" : "") +
			" cost is " + cost);

		return cost;
	}
	return Infinity;
}

function getFleetMovementCostByMap(fleet, oldLoc, newLoc, map)
{
	return getUnitMovementCostByMap(fleet.type, oldLoc, newLoc, map);
}

function getFleetMovementCost(fleet, oldLoc, newLoc)
{
	return getUnitMovementCost(fleet.type, oldLoc, newLoc);
}

function getTerrainLocation(location)
{
	return G.terrain.cells[location];
}

function addPlayerCanSee(playerId, location)
{
	// notify user of any fleets found at this location

	for (var fid in G.fleets)
	{
		var fleet = G.fleets[fid];
		if (fleet.location == location && fleet.owner != playerId)
		{
			notifyPlayer(playerId, {
				event: 'fleet-spawned',
				fleet: fid,
				data: getFleetInfoForPlayer(fid, playerId)
				});
		}
	}
}

function removePlayerCanSee(playerId, location)
{
	// notify user that they lost sight of any fleets at this location

	for (var fid in G.fleets)
	{
		var fleet = G.fleets[fid];
		if (fleet.location == location && fleet.owner != playerId)
		{
			notifyPlayer(playerId, {
				event: 'fleet-terminated',
				fleet: fid,
				location: location,
				disposition: "out-of-sight"
				});
		}
	}
}

function addFleetCanSee(fleetId, fleet, location)
{
	var couldSeeBefore = playerCanSee(fleet.owner, location);

	fleet.canSee[location] = true;

	var cell = getTerrainLocation(location);
	if (!cell.seenBy)
		cell.seenBy = {};
	cell.seenBy[fleetId] = true;

	if (!couldSeeBefore)
	{
		addPlayerCanSee(fleet.owner, location);
	}
}

function removeFleetCanSee(fleetId, fleet, location)
{
	delete fleet.canSee[location];

	var cell = getTerrainLocation(location);
	if (cell.seenBy)
		delete cell.seenBy[fleetId];

	if (!playerCanSee(fleet.owner, location))
	{
		removePlayerCanSee(fleet.owner, location);
	}
}

function updateFleetSight(fid, fleet)
{
	var newVisibility = {};

	newVisibility[fleet.location] = true;
	var nn = G.geometry.getNeighbors(fleet.location);
	for (var i = 0; i < nn.length; i++)
	{
		var n = nn[i];
		newVisibility[n] = true;
	}

	if (!fleet.canSee)
		fleet.canSee = {};

	for (var loc in fleet.canSee)
	{
		if (!newVisibility[loc])
		{
			removeFleetCanSee(fid, fleet, loc);
		}
	}

	for (var loc in newVisibility)
	{
		if (!fleet.canSee[loc])
		{
			addFleetCanSee(fid, fleet, loc);
		}
	}
}

// called after fleet has moved
// this function is responsible for fleets being able to see each
// other, and detecting when a fleet can no longer be seen
//
function fleetMoved(fleetId, fleet, oldLoc, newLoc)
{
	updateFleetSight(fleetId, fleet);
}

function moveFleetOneStep(fleetId, newLoc)
{
	var fleet = G.fleets[fleetId];
	var oldLoc = fleet.location;
	fleet.lastLocation = oldLoc;
	fleet.location = newLoc;

	{
		var oldLocStruct = getTerrainLocation(oldLoc);
		if (oldLocStruct.fleets)
			delete oldLocStruct.fleets[fleetId];

		var newLocStruct = getTerrainLocation(newLoc);
		if (!newLocStruct.fleets)
			newLocStruct.fleets = {};
		newLocStruct.fleets[fleetId] = true;
	}

	fleetMoved(fleetId, fleet, oldLoc, newLoc);

	var costOfMovement = getFleetMovementCost(fleet, oldLoc, newLoc);
	console.log("cost is " + Math.round(costOfMovement));

	discoverCell(fleet.owner, newLoc);
	discoverCellBorder(fleet.owner, newLoc);

	notifyPlayer(fleet.owner, {
		event: 'fleet-movement',
		fleet: fleetId,
		fromLocation: oldLoc,
		toLocation: newLoc,
		delay: Math.round(costOfMovement)
		});

	var observersOldLoc = allPlayersWhoCanSee(oldLoc);
	var observersNewLoc = allPlayersWhoCanSee(newLoc);
	for (var pid in observersOldLoc)
	{
		if (pid == fleet.owner)
			continue;

		if (observersNewLoc[pid])
		{
			// ordinary movement
			notifyPlayer(pid, {
				event: 'fleet-movement',
				fleet: fleetId,
				fromLocation: oldLoc,
				toLocation: newLoc,
				delay: Math.round(costOfMovement)
				});
		}
		else
		{
			// observer of fleet at old location, but cannot
			// see the new location
			notifyPlayer(pid, {
				event: 'fleet-terminated',
				fleet: fleetId,
				location: oldLoc,
				newLocation: newLoc,
				disposition: 'moved-out-of-sight'
				});
		}
	}
	for (var pid in observersNewLoc)
	{
		if (pid == fleet.owner)
			continue;

		if (!observersOldLoc[pid])
		{
			// observer who can see new location, but not
			// able to see the old location
			notifyPlayer(pid, {
				event: 'fleet-spawned',
				fleet: fleetId,
				fromLocation: oldLoc,
				data: getFleetInfoForPlayer(fleetId, pid)
				});
		}
	}

	fleetCooldown(fleetId, fleet, Math.round(costOfMovement));
}

function fleetCooldown(fleetId, fleet, delay)
{
	fleet._coolDownTimer = Scheduler.schedule(function() {
		fleet._coolDownTimer = null;
		fleetActivity(fleetId);
		}, delay);
}

function findSuitableStartingLocation()
{
	var best = 1;
	var bestV = -Infinity;
	for (var cid in G.terrain.cells)
	{
		var c = G.terrain.cells[cid];
		if (c.terrain != 'grassland' &&
			c.terrain != 'plains' &&
			c.terrain != 'forest' &&
			c.terrain != 'hills')
			continue;

		var v = (c.temperature / 20) * (c.moisture / 0.8);
		if (v > 1) v = 1;

		var nn = G.geometry.getNeighbors(cid);
		var numGoodNeighbors = 0;
		var numRivers = 0;
		for (var i = 0; i < nn.length; i++)
		{
			var n = G.terrain.cells[nn[i]];
			if (n.terrain == 'grassland' ||
				n.terrain == 'plains' ||
				n.terrain == 'forest' ||
				n.terrain == 'hills')
			{
				numGoodNeighbors++;
			}
			var eId = G.geometry._makeEdge(cid,nn[i]);
			var e = G.terrain.edges[eId];
			if (e && e.feature == 'river')
			{
				numRivers++;
			}
		}

		v += numGoodNeighbors / 5;
		v -= 0.3 * Math.abs(numRivers - 2);
	
		if (v > bestV)
		{
			best = cid;
			bestV = v;
		}
	}
	return best;
}

function newPlayer(playerId)
{
	if (G.players[playerId])
		return;

	G.players[playerId] = {
		type: 'player'
		};
	G.maps[playerId] = {
		cells: {},
		edges: {}
		};

	// pick a location to be this player's home location
	var loc = findSuitableStartingLocation();
	createUnit(playerId, "settler", loc, {
			population: numSettlers
			});
	createUnit(playerId, "explorer", G.geometry.getNeighbors(loc)[0]);
}

function nextFleetId()
{
	return G.world.nextFleetId++;
}

function createUnit(playerId, unitType, initialLocation, extraProperties)
{
	var f = {
		owner: playerId,
		location: initialLocation,
		type: unitType,
		population: 50,
		orders: []
		};
	if (extraProperties)
	{
		for (var k in extraProperties)
		{
			f[k] = extraProperties[k];
		}
	}
	var fid = nextFleetId();

	G.fleets[fid] = f;
	notifyPlayer(playerId, {
		event: 'fleet-spawned',
		fleet: fid,
		data: getFleetInfoForPlayer(fid, playerId)
		});

	var pp = allPlayersWhoCanSee(initialLocation);
	for (var pid in pp)
	{
		if (pid == playerId)
			continue;

		notifyPlayer(pid, {
			event: 'fleet-spawned',
			fleet: fid,
			data: getFleetInfoForPlayer(fid, pid)
			});
	}
	
	discoverCell(playerId,f.location);
	discoverCellBorder(playerId,f.location);
}

function fleetActivity(fleetId)
{
	var fleet = G.fleets[fleetId];
	if (fleet._coolDownTimer)
	{
		// this fleet is still performing last step.
		// not to worry, this function will be called automatically
		// when the fleet finishes its current task
		return;
	}

	if (!fleet.orders || fleet.orders.length == 0)
	{
		// this fleet does not have any orders
		return;
	}

	var currentOrder = fleet.orders[0];
	if (!currentOrder)
	{
		console.log("invalid first command", fleet.orders);
		return;
	}

	if (currentOrder.command == 'wander')
	{
		console.log("traveler moves!");
		return moveFleetRandomly(fleetId);
	}
	if (currentOrder.command == 'follow-coast')
	{
		return moveFleetAlongCoast(fleetId);
	}
	if (currentOrder.command == 'goto')
	{
		return moveFleetTowards(fleetId, currentOrder.location);
	}
	else if (currentOrder.command == 'build-city')
	{
		return tryToBuildCity(fleetId, fleet);
	}
	else if (currentOrder.command == 'disband')
	{
		return fleetDisbandInCity(fleetId, fleet);
	}
}

function getGameState(request)
{
	if (request.remote_player)
	{
		return {
		role: "player",
		gameYear: Scheduler.timer,
		gameSpeed: Scheduler.ticksPerYear,
		map: "/map/"+request.remote_player,
		mapSize: G.terrain.size,
		fleets: "/fleets/"+request.remote_player,
		identity: request.remote_player
		};
	}
	else
	{
		return {
		role: "observer",
		};
	}
}

function doExpose(requestData, queryString, remoteUser)
{
	// TODO- delete this function

	if (requestData.cell)
	{
		discoverCell(1, requestData.cell);
	}
}

function doOrders(requestData, queryString, remoteUser)
{
	if (!queryString.match(/^fleet=(.*)$/))
	{
		console.log("doOrders: invalid query string");
		return;
	}

	var fleetId = RegExp.$1;
	var fleet = G.fleets[fleetId];
	if (!fleet)
	{
		console.log("Warning: fleet "+fleetId+" not found");
		return;
	}

	if (fleet.owner != remoteUser)
	{
		console.log("Warning: fleet "+fleetId+" not owned by player "+remoteUser);
		return;
	}

	if (!(requestData instanceof Array))
	{
		console.log("doOrders: request content not a JSON array");
		return;
	}
	for (var i = 0; i < requestData.length; i++)
	{
		if (!requestData[i].command)
		{
			console.log("doOrders: one or more orders without a 'command'");
			return;
		}
	}

	fleet.orders = requestData;
	fleetActivity(fleetId);
}

function doReassignWorkers(requestData, queryString, remoteUser)
{
	if (!queryString.match(/^city=(.*)$/))
	{
		console.log("reassign-workers: invalid query string");
		return;
	}

	var cityId = RegExp.$1;
	var city = G.cities[cityId];
	if (!city)
	{
		console.log("reassign-workers: city " + cityId + " not found");
		return;
	}

	if (city.owner != remoteUser)
	{
		console.log("reassign-workers: city " + cityId + " not owned by player " + remoteUser);
		return;
	}

	var fromJob = requestData.fromJob;
	var toJob = requestData.toJob;
	var quantity = +requestData.amount;

	lockCityStruct(city);

	if (quantity + 1 >= +(city.workers[fromJob] || 0))
		quantity = quantity+1;

	quantity = removeWorkers(cityId, city, quantity, fromJob);
	addWorkers(cityId, city, quantity, toJob);

	unlockCityStruct(cityId, city);
}

function doCityBuildUnit(requestData, queryString, remoteUser)
{
	if (!queryString.match(/^city=(.*)$/))
	{
		console.log("build-unit: invalid query string");
		return;
	}

	var cityId = RegExp.$1;
	var city = G.cities[cityId];
	if (!city)
	{
		console.log("build-unit: city " + cityId + " not found");
		return;
	}

	if (city.owner != remoteUser)
	{
		console.log("build-unit: city " + cityId + " not owned by player " + remoteUser);
		return;
	}

	lockCityStruct(city);

	if (!city.tasks)
		city.tasks = [];
	city.tasks.push({
		task: 'build',
		type: requestData.type
		});

	unlockCityStruct(cityId, city);
}

function doCityBuildImprovement(requestData, queryString, remoteUser)
{
	if (!queryString.match(/^city=(.*)$/))
	{
		console.log("build-improvement: invalid query string");
		return;
	}

	var cityId = RegExp.$1;
	var city = G.cities[cityId];
	if (!city)
	{
		console.log("build-improvement: city " + cityId + " not found");
		return;
	}

	if (city.owner != remoteUser)
	{
		console.log("build-improvement: city " + cityId + " not owned by player " + remoteUser);
		return;
	}

	lockCityStruct(city);

	if (!city.tasks)
		city.tasks = [];
	city.tasks.push({
		task: 'improvement',
		type: requestData.improvement
		});

	unlockCityStruct(cityId, city);
}

function doCityBuildBuilding(requestData, queryString, remoteUser)
{
	if (!queryString.match(/^city=(.*)$/))
	{
		console.log("build-building: invalid query string");
		return;
	}

	var cityId = RegExp.$1;
	var city = G.cities[cityId];
	if (!city)
	{
		console.log("build-building: city " + cityId + " not found");
		return;
	}

	if (city.owner != remoteUser)
	{
		console.log("build-building: city " + cityId + " not owned by player " + remoteUser);
		return;
	}

	lockCityStruct(city);

	if (!city.tasks)
		city.tasks = [];
	city.tasks.push({
		task: 'building',
		type: requestData.building
		});

	unlockCityStruct(cityId, city);
}

function doCityBuildingOrders(requestData, queryString, remoteUser)
{
	var QS = require('querystring');
	var args = QS.parse(queryString);

	if (!args.city || !args.building)
	{
		console.log("building-orders: invalid query string");
		return;
	}

	var city = G.cities[args.city];
	if (!city)
	{
		console.log("building-orders: city " + args.city + " not found");
		return;
	}

	if (city.owner != remoteUser)
	{
		console.log("building-orders: city " + args.city + " not owned by player " + remoteUser);
		return;
	}

	var building = city.buildings[args.building];
	if (!building)
	{
		console.log("building-orders: building " + args.building + " not found in city " + args.city);
		return;
	}
		
	lockCityStruct(city);

	building.orders = requestData.orders;

	unlockCityStruct(args.city, city);
}

function addBuilding(cityId, city, buildingType)
{
	city.buildings[buildingType] = (city.buildings[buildingType] || 0) + 1;
}

function developLand(location, type, amount)
{
	var c = G.terrain.cells[location];
	c.subcells.natural -= amount;
	c.subcells[type] += amount;
	terrainChanged(location);
}

function onFarmCompleted(cityId, city)
{
	stealWorkers(cityId, city, 15, 'farm');
}

// [0]: number of builders to assign when task starts
// [1]: production cost
// [2]: function to call after land has been developed (can be null)
//
var LAND_TYPE_COSTS = {
	farm:   [ 25, 50, onFarmCompleted ],
	hamlet: [ 50, 100, null ]
	};

function tryDevelopLand(cityId, city, landType)
{
	var ltc = LAND_TYPE_COSTS[landType];
	if (!ltc)
	{
		return cityActivityError(cityId, city, "invalid land type: "+landType);
	}

	var builders = ltc[0];
	var cost = ltc[1];
	var activityName = 'build-'+landType;

	if (city.activity == activityName && city.production.build >= cost)
	{
		delete city.production.build;
		freeWorkers(cityId, city, 'build');

		developLand(city.location, landType, 1);
		cityActivityComplete(cityId, city);

		if (ltc[2])
		{
			ltc[2](cityId, city);
		}

		return;
	}
	else
	{
		setCityActivity(cityId, city, activityName, builders, cost);
	}
}

// [0]: number of builders to assign when task starts
// [1]: production cost
// [2]: function to call after building has been completed (can be null)
//
var BUILDING_TYPE_COSTS = {
	'stone-workshop':   [ 25, 50, null ]
	};

function tryMakeBuilding(cityId, city, buildingType)
{
	var costInfo = BUILDING_TYPE_COSTS[buildingType];
	if (!costInfo)
	{
		return cityActivityError(cityId, city, "invalid building type: "+buildingType);
	}

	var builders = costInfo[0];
	var cost = costInfo[1];
	var activityName = 'build-'+buildingType;

	if (city.activity == activityName && city.production.build >= cost)
	{
		delete city.production.build;
		freeWorkers(cityId, city, 'build');

		addBuilding(cityId, city, buildingType);
		cityActivityComplete(cityId, city);

		if (costInfo[2])
		{
			costInfo[2](cityId, city);
		}

		return;
	}
	else
	{
		setCityActivity(cityId, city, activityName, builders, cost);
	}
}

function tryBuildTrieme(cityId, city)
{
	var builders = 400;   // number of workers required to build the boat
	var cost = G.world.triemeCost || 400;

	if (city.activity == 'build-trieme' && city.production.build >= cost)
	{
		freeWorkers(cityId, city, 'build');

		if (city.population < 150)
		{
			return cityActivityError(cityId, city, "not large enough to build trieme");
		}

		stealWorkers(cityId, city, 50, 'trieme');
		delete city.production.build;
		var numSailers = removeWorkers(cityId, city, Infinity, 'trieme');
		createUnit(city.owner, "trieme", city.location, {
			population: numSailers
			});

		cityActivityComplete(cityId, city);
		return;
	}
	else
	{
		setCityActivity(cityId, city, 'build-trieme', builders, cost);
		return;
	}
}

// adds new people to the city, given a particular job
//
function addWorkers(cityId, city, quantity, toJob)
{
	if (quantity < 0)
		throw new Error("invalid argument for addWorkers");
	if (quantity > 0)
	{
		if (city.lastUpdate != Scheduler.time)
			throw new Error("not ready for addWorkers");

		city.workers[toJob] = (city.workers[toJob] || 0) + quantity;
		city.population += quantity;
		cityNewWorkerRate(city, toJob);
	}
}

var cityWorkerRatesSpecial = {

	farm: function(city, baseProduction) {
		var cell = G.terrain.cells[city.location];
		var numFarms = cell.subcells.farm || 0;
		var maxYield = numFarms * 30;
		var z = maxYield - maxYield * Math.exp(-1 * baseProduction / maxYield);
		return z;
		},

	hunt: function(city, baseProduction) {
		var numWildlife = 80;
		var s = 0.5 * Math.sqrt(numWildlife / 40);
		return numWildlife - numWildlife * Math.exp(-s * baseProduction / numWildlife);
		}
	};

function cityNewWorkerRate(city, job)
{
	var baseProduction = (city.workers[job] || 0) * nextRandomWorkerRate();
	var f = cityWorkerRatesSpecial[job];
	city.workerRates[job] = f ?
			f(city, baseProduction) :
			baseProduction;
}

function removeWorkers(cityId, city, quantity, fromJob)
{
	if (quantity < 0)
		throw new Error("invalid argument for removeWorkers");

	if (quantity > 0)
	{
		if (city.lastUpdate != Scheduler.time)
			throw new Error("not ready for addWorkers");

		if (city.workers[fromJob] > quantity)
		{
			city.population -= quantity;
			city.workers[fromJob] -= quantity;
			cityNewWorkerRate(city, fromJob);
		}
		else if (city.workers[fromJob])
		{
			quantity = city.workers[fromJob];
			city.population -= quantity;
			delete city.workers[fromJob];
			delete city.workerRates[fromJob];
		}
		else
		{
			quantity = 0;
		}
	}
	return quantity;
}

function cityActivityError(cityId, city, message)
{
	console.log("city " + cityId + ": " + message);
	return;
}

function setCityActivity(cityId, city, activity, builders, cost)
{
	if (city.activity != activity)
	{
		city.activity = activity;

		var targetWorkerCount = builders;
		if (targetWorkerCount > city.population - 100 &&
				city.population - 100 > 0)
		{
			targetWorkerCount = city.population - 100;
		}

		var curWorkerCount = city.workers.build || 0;
		if (curWorkerCount < targetWorkerCount)
		{
			stealWorkers(cityId, city,
				targetWorkerCount-curWorkerCount, 'build');
		}
	}

	var rate = city.workerRates.build || 0;
	var partComplete = city.production.build || 0;

	city.activityTime = Scheduler.time;
	city.activityComplete = (partComplete / cost);
	city.activitySpeed = (rate / cost);
	cityChanged(cityId);

	if (rate > 0)
	{
		var remaining = cost - partComplete;
		var timeRemaining = remaining / rate;
		if (timeRemaining < 0.001)
		{
			console.log("warning: city "+cityId+" ("+city.name+") activity "+activity+" has "+(city.production.build||0)+" of "+cost+" already");
			timeRemaining = 0.001;
		}
		return cityActivityWakeup(cityId, city, timeRemaining);
	}
}

function freeWorkers(cityId, city, job)
{
	var num = city.workers[job] || 0;

	delete city.workers[job];
	delete city.workerRates[job];
	city.population -= num;

	var p = city.population;
	for (var job in city.workers)
	{
		var q = num * city.workers[job] / p;
		addWorkers(cityId, city, q, job);
	}
}

function tryBuildSettler(cityId, city)
{
	var builders = 50;   // number of workers to build settlers
	var cost = G.world.settlerCost || 200;

	if (city.activity == 'build-settler' && city.production.build >= cost)
	{
		freeWorkers(cityId, city, 'build');

		if (city.population < 200)
		{
			return cityActivityError(cityId, city, "not large enough to build settler");
		}

		stealWorkers(cityId, city, 100, 'settle');
		delete city.production.build;
		var numSettlers = removeWorkers(cityId, city, Infinity, 'settle');
		createUnit(city.owner, "settler", city.location, {
			population: numSettlers
			});

		cityActivityComplete(cityId, city);
		return;
	}
	else
	{
		setCityActivity(cityId, city, 'build-settler', builders, cost);
		return;
	}
	
}

function cityActivityWakeup(cityId, city, yearsRemaining)
{
	if (city._wakeupTimer)
	{
		Scheduler.cancel(city._wakeupTimer);
		delete city._wakeupTimer;
	}

	var targetTime = Scheduler.time + yearsRemaining;
	if (targetTime < G.world.lastYear+1)
	{
		city._wakeupTimer = Scheduler.scheduleYears(function() {
				lockCityStruct(city);
				unlockCityStruct(cityId, city);
			}, yearsRemaining);
	}
	// otherwise, just wait until cityEndOfYear is called...
}

function cityChanged(cityId)
{
	var city = G.cities[cityId];
	if (!city)
		throw new Error("oops! city "+cityId+" not found");

	terrainChanged(city.location);
}

function cityActivityComplete(cityId, city)
{
	delete city.activity;

	city.tasks.shift();
	if (city.tasks.length == 0)
	{
		delete city.tasks;
		cityChanged(cityId);
	}
	else
	{
		return cityActivity(cityId, city);
	}
}

function cityActivity(cityId, city)
{
console.log("in cityActivity");

	if (!city.tasks || city.tasks.length == 0)
	{
		// this city does not have any orders
		return;
	}

	var currentTask = city.tasks[0];
	if (!currentTask)
		throw new Error("invalid first task", city.tasks);

console.log("current task is " + currentTask.task);

	if (currentTask.task == 'build')
	{
		if (currentTask.type == 'settler')
			return tryBuildSettler(cityId, city);
		else if (currentTask.type == 'trieme')
			return tryBuildTrieme(cityId, city);
	}
	else if (currentTask.task == 'improvement')
	{
		return tryDevelopLand(cityId, city, currentTask.type);
	}
	else if (currentTask.task == 'building')
	{
		return tryMakeBuilding(cityId, city, currentTask.type);
	}

	return cityActivityError(cityId, city, "unrecognized task " + currentTask.task);
}

function doRenameCity(requestData, queryString, remoteUser)
{
	if (!queryString.match(/^city=(.*)$/))
	{
		console.log("doRenameCity: invalid query string");
		return;
	}

	var cityId = RegExp.$1;
	var city = G.cities[cityId];
	if (!city)
	{
		console.log("doRenameCity: city " + cityId + " not found");
		return;
	}

	if (city.owner != remoteUser)
	{
		console.log("doRenameCity: city " + cityId + " not owned by player " + remoteUser);
		return;
	}

	city.name = requestData.name;
	cityChanged(cityId);
}

function doCityTest(requestData, queryString, remoteUser)
{
	if (!queryString.match(/^city=(.*)$/))
	{
		console.log("doCityTest: invalid query string");
		return;
	}

	var cityId = RegExp.$1;
	var city = G.cities[cityId];
	if (!city)
	{
		console.log("doCityTest: city " + cityId + " not found");
		return;
	}

	throw new Error("not implemented");
}

function getYear()
{
	var t = new Date().getTime();
	return G.world.age + (t-G.world.realWorldTime)/G.world.oneYear;
}

function nextRandomWorkerRate()
{
	var t = Math.random();
	if (t == 0) return 0;
	return Math.exp( -Math.log((1/t)-1) / 15 );
}

//TODO- ensure that the following two distributions are equivalent:
//   RndProductionPoints(x)
//  and
//   RndProductionPoints(x/2) + RndProductionPoints(x/2)
//
function RndProductionPoints(x)
{
	var t = Math.random();
	if (t == 0) return 0;
	return x * Math.exp( -Math.log((1/t)-1) / 15 );
}

//caller should call fire-city-update-notification
//
function stealWorkers(cityId, city, quantity, toJob)
{
	var sumAvailable = 0;
	for (var job in city.workers)
	{
		if (job != toJob)
			sumAvailable += city.workers[job];
	}

	if (sumAvailable < quantity)
		throw new Error("oops, not enough available workers (city "+city.name+", want "+quantity+" for job " + toJob+")");

	for (var job in city.workers)
	{
		if (job != toJob)
		{
			var amt = quantity * city.workers[job]/sumAvailable;
			city.workers[job] -= amt;
			city.population -= amt;
			if (city.workers[job] > 0)
			{
				cityNewWorkerRate(city, job);
			}
			else
			{
				delete city.workers[job];
				delete city.workerRates[job];
			}
		}
	}

	addWorkers(cityId, city, quantity, toJob);
}

function getCityPopulationCapacity(city)
{
	var c = G.terrain.cells[city.location];
	return (c.subcells.hamlet || 0) * 200;
}

var FOOD_TYPES = [ 'food', 'meat', 'wheat' ];

function getTotalFood(city)
{
	var sum = 0;
	for (var i = 0; i < FOOD_TYPES.length; i++)
	{
		var ft = FOOD_TYPES[i];
		sum += (city[ft] || 0);
	}
	return sum;
}

function subtractFood(city, amount)
{
	for (var i = 0; i < FOOD_TYPES.length; i++)
	{
		var ft = FOOD_TYPES[i];
		if (city[ft])
		{
			if (city[ft] > amount)
			{
				city[ft] -= amount;
				return true;
			}
			amount -= city[ft];
			delete city[ft];
		}
	}
	return false;
}

function cityEndOfYear(cityId, city)
{
	console.log("city "+city.name+": end of year");

	lockCityStruct(city);

	var ADULT_AGE = G.world.childYears;
	var LIFE_EXPECTANCY = G.world.lifeExpectancy;

	// check child care
	var childCareDemand = city.children;
	var childCareSupply = 2*(city.production.childcare || 0);
	if (childCareSupply < childCareDemand)
	{
		// not enough child care, kill off some children
		var caredForPortion = childCareSupply / childCareDemand;
		var survivalRate = 0.60 + 0.40 * Math.pow(caredForPortion,2);

		if (survivalRate > 1) //sanity check
			survivalRate = 1;

		var newSum = 0.0;
		for (var i = 0; i < ADULT_AGE; i++)
		{
			city.childrenByAge[i] = (city.childrenByAge[i] || 0) * survivalRate;
			newSum += city.childrenByAge[i];
		}
		city.children = newSum;
	}

	// bring forward growing children
	var newAdults = city.childrenByAge[ADULT_AGE-1] || 0;
	for (var i = ADULT_AGE-1; i > 0; i--)
	{
		city.childrenByAge[i] = (city.childrenByAge[i-1] || 0);
	}
	city.childrenByAge[0] = 0;
	city.children -= newAdults;
	if (city.children < 0) { city.children = 0; }

	// calculate new births
	{
		// determine how much room there is for growth
		var cityCapacity = getCityPopulationCapacity(city);
		if (cityCapacity < 1) cityCapacity = 1;
		var housingUsage = (city.population + city.children) / cityCapacity;

		var birthRate = 0.125 / (1 + Math.exp(-(0.85-housingUsage)*12));
		var births = city.population * birthRate;

		city.childrenByAge[0] = births;
		city.children = (city.children || 0) + births;
		city.births += births;
	}

	if (city.production['gather-wood'])
	{
		var pts = city.production['gather-wood'];
		delete city.production['gather-wood'];

		var woodYield = pts * G.world.woodPerWoodGatherer;
		city.wood = (city.wood || 0) + woodYield;

		console.log("  wood gatherers brought in " + woodYield + " wood");
	}

	if (city.production['gather-clay'])
	{
		var pts = city.production['gather-clay'];
		delete city.production['gather-clay'];

		var clayYield = pts * G.world.clayPerClayGatherer;
		city.clay = (city.clay || 0) + clayYield;

		console.log('  clay gatherers brought in ' + clayYield + " clay");
	}

	if (city.production['gather-stone'])
	{
		var pts = city.production['gather-stone'];
		delete city.production['gather-stone'];

		var stoneYield = pts * G.world.stonePerStoneGatherer;
		city.stone = (city.stone || 0) + stoneYield;

		console.log('  stone gatherers brought in ' + stoneYield + " stone");
	}

	// food production
	if (city.production.hunt)
	{
		var pts = city.production.hunt;
		delete city.production.hunt;

		var numHarvested = pts;

		//TODO- subtract numHarvested from this cell's
		//wildlife counter.

		var foodYield = numHarvested * G.world.foodPerAnimal;
		city.meat = (city.meat || 0) + foodYield;

		console.log("  hunters brought in " + foodYield + " meat");
	}

	if (city.production.farm)
	{
		var pts = city.production.farm;
		delete city.production.farm;

		var foodYield = pts * G.world.foodPerFarmer;
		city.wheat = (city.wheat || 0) + foodYield;

		console.log("  farmers brought in " + foodYield + " wheat");
	}

	// feed the population
	var foodDemand = city.hunger || 0;
	var foodSupply = getTotalFood(city);
	var foodConsumed;
	if (foodSupply >= foodDemand)
	{
		// ok, enough to satisfy everyone
		foodConsumed = foodDemand;
	}
	else   // city.food < foodDemand
	{
		// not enough food, some people gonna die
		foodConsumed = foodSupply;
	}
	subtractFood(city, foodConsumed);
	var sustenance = foodDemand > 0 ? Math.sqrt(foodConsumed / foodDemand) : 1;
	city.hunger = 0;

	// calculate deaths
	var sustainRate = 1 - 1/LIFE_EXPECTANCY;
	sustainRate *= sustenance;
	var deathRate = 1 - sustainRate;
	var deaths = city.population * deathRate;
	city.deaths += deaths;

	// distribute net change in adults evenly
	var netPopChange = newAdults - deaths;
	for (var job in city.workers)
	{
		var portion = city.workers[job] / city.population;
		city.workers[job] += portion * netPopChange;
		cityNewWorkerRate(city, job);
	}
	city.population += netPopChange;

	// notify interested parties
	// done changing the city properties
	unlockCityStruct(cityId, city);

	// record stats
	var fs = require('fs');
	var fd = fs.appendFileSync(G.worldName+'/city'+cityId+'.log',
		[ G.world.lastYear,
		city.population,
		city.children,
		city.food,
		city.births,
		city.deaths,
		newAdults ].join(',') + "\n");

	console.log("  population: adults: " + city.population +
		", children: " + city.children);
	console.log("  food: " + city.food);
	console.log("  births: " + city.births);
	console.log("  deaths: " + city.deaths);
	console.log("  new adults: " + newAdults);
	city.births = 0;
	city.deaths = 0;
}

var FACTORY_RECIPES = {
	'stone-weapon': { 'rate': 0.01, 'input': { 'stone': 0.25 } },
	'stone-block': { 'rate': 0.01,  'input': { 'stone': 1.00 } }
	};

function processManufacturingOutput(city)
{
	var ppByOutput = {};
	for (var job in city.workers)
	{
		if (job.match(/^make-(.*)$/))
		{
			var outputResource = RegExp.$1;
			ppByOutput[outputResource] = city.production[job];
			delete city.production[job];
		}
	}

	for (var outputResource in ppByOutput)
	{
		var recipe = FACTORY_RECIPES[outputResource];
		if (!recipe)
			continue;

		var idealOutput = ppByOutput[outputResource] * (recipe.rate || 0.01);
		var actualOutput = idealOutput;
		for (var inputType in recipe.input)
		{
			var demand = actualOutput * recipe.input[inputType];
			var supply = city[inputType] || 0;
			if (supply < 0) { supply = 0; }

			if (demand > supply)
			{
				actualOutput *= supply / demand;
			}
		}

		for (var inputType in recipe.input)
		{
			var demand = actualOutput * recipe.input[inputType];
			var newSupply = (city[inputType] || 0) - demand;
			if (newSupply > 0)
				city[inputType] = newSupply;
			else
				delete city[inputType];
		}

		city[outputResource] = (city[outputResource] || 0) + actualOutput;
	}
}

// prepare a City struct for manipulation; each call to lockCityStruct()
// should be paired with a call to unlockCityStruct().
// nesting the calls is ok.
// the global value Scheduler.time should be properly set when calling
// lockCityStruct().
//
function lockCityStruct(city)
{
	if (!city.lastUpdate)
		city.lastUpdate = 0;

	var FOOD_PER_CHILD = G.world.hungerPerChild;
	var FOOD_PER_ADULT = G.world.hungerPerAdult;

	var bringForward = function(aTime)
	{
		if (city.lastUpdate >= aTime)
			return;

		var yearsElapsed = aTime - city.lastUpdate;

		// calculate worker production
		for (var job in city.workers)
		{
			var rate = city.workerRates[job] || 0;
			var productionPoints = yearsElapsed * rate;
			city.production[job] = (city.production[job] || 0) +
				productionPoints;
		}

		// manufacturing jobs
		processManufacturingOutput(city);

		// calculate hunger
		var foodRequired = (FOOD_PER_ADULT * city.population + FOOD_PER_CHILD * city.children) * yearsElapsed;
		city.hunger = (city.hunger || 0) + foodRequired;

		// finished
		city.lastUpdate = aTime;
	};

	if (!Scheduler.time)
		throw new Error("invalid year ("+Scheduler.time+", "+G.world.lastYear+")");

	if (city._lockLevel)
	{
		if (city._lockedWhen != Scheduler.time)
			throw new Error("lockCityStruct() called on city already locked with a different timestamp");

		city._lockLevel++;
	}
	else
	{
		city._lockLevel = 1;
		city._lockedWhen = Scheduler.time;
		bringForward(Scheduler.time);
	}
}

function unlockCityStruct(cityId, city)
{
	if (city._lockLevel > 1)
	{
		city._lockLevel--;
	}
	else
	{
		if (!city._inActivity)
		{
			city._inActivity = true;
			cityActivity(cityId, city);
			cityChanged(cityId);
			delete city._inActivity;
		}
		delete city._lockLevel;
		delete city._lockedWhen;
	}
}

function getFleetInfoForPlayer(fleetId, playerId)
{
	var f = G.fleets[fleetId];
	if (f.owner == playerId)
	{
		var _fleet = {
			type: f.type,
			location: f.location,
			owner: f.owner,
			orders: f.orders
		};
		if (f.activity)
			_fleet.activity = f.activity;
		return _fleet;
	}
	else if (playerCanSee(playerId, f.location))
	{
		var _fleet = {
			type: f.type,
			location: f.location,
			owner: f.owner
		};
		return _fleet;
	}
	else
	{
		return null;
	}
}

function getFleets(playerId, callback)
{
	var result = {};
	for (var fid in G.fleets)
	{
		var fi = getFleetInfoForPlayer(fid, playerId);
		if (fi)
		{
			result[fid] = fi;
		}
	}

	callback(result);
}
exports.getFleets = getFleets;

function getMapFragment(mapId, callback)
{
console.log("in getMapFragment");
	var result = {};
	var map = G.maps[mapId];
	if (!map)
	{
		console.log("Warning: map '"+mapId+"' not found");
		return callback(result);
	}

	for (var cid in map.cells)
	{
		result[cid] = map.cells[cid];
	}
	for (var eid in map.edges)
	{
		result[eid] = map.edges[eid];
	}
	console.log("map is "+JSON.stringify(result));
	return callback(result);
}
exports.getMapFragment = getMapFragment;

function endOfYear()
{
	G.world.lastYear++;

	// do this year's processing
	console.log("processing year " + G.world.lastYear);

	// process cities
	for (var tid in G.cities)
	{
		cityEndOfYear(tid, G.cities[tid]);
	}

	// schedule for next end-of-year
	Scheduler.scheduleAtYear(endOfYear, G.world.lastYear + 1);
}

function startGame()
{
	checkWorldParameters();
	G.world.realWorldTime = new Date().getTime();
	Scheduler.time = G.world.age;
	Scheduler.ticksPerYear = G.world.oneYear;

	for (var cid in G.terrain.cells)
	{
		checkTerrainCell(cid, G.terrain.cells[cid]);
	}

	for (var tid in G.cities)
	{
		checkCity(tid, G.cities[tid]);
	}

	for (var fid in G.fleets)
	{
		checkFleet(fid);
		fleetActivity(fid, G.fleets[fid]);
	}

	for (var pid in G.players)
	{
		checkPlayer(pid, G.players[pid]);
	}

	// schedule for next end-of-year
	Scheduler.scheduleAtYear(endOfYear, G.world.lastYear + 1);
}

// inspect properties from world.txt,
// add any that are missing,
// fix any whose semantics have changed.
//
function checkWorldParameters()
{
	// real-world-time this file was last saved
	if (!G.world.realWorldTime)
		G.world.realWorldTime = new Date().getTime();

	// how many game-years this world has been run
	if (!G.world.age)
		G.world.age = 0;

	// age of world when the last end-of-year was performed
	if (!G.world.lastYear)
		G.world.lastYear = Math.floor(G.world.age);

	// how many real-world-milliseconds correspond to one game-year
	// (this parameter may change between executions of the server)
	if (!G.world.oneYear)
		G.world.oneYear = 60000;

	if (!G.world.childYears)
		G.world.childYears = 10;

	if (!G.world.lifeExpectancy)
		G.world.lifeExpectancy = 60;

	if (!G.world.hungerPerChild)
		G.world.hungerPerChild = 0.01;

	if (!G.world.hungerPerAdult)
		G.world.hungerPerAdult = 0.01;

	if (!G.world.foodPerAnimal)
		G.world.foodPerAnimal = 0.1;

	if (!G.world.foodPerFarmer)
		G.world.foodPerFarmer = 0.1;

	if (!G.world.woodPerWoodGatherer)
		G.world.woodPerWoodGatherer = 0.01;
	if (!G.world.clayPerClayGatherer)
		G.world.clayPerClayGatherer = 0.01;
	if (!G.world.stonePerStoneGatherer)
		G.world.stonePerStoneGatherer = 0.01;
}

// inspect properties of Player struct
//
function checkPlayer(pid, player)
{
	player.canSee = {};
	for (var tid in G.cities)
	{
		var city = G.cities[tid];
		if (city.owner != pid)
			continue;

		player.canSee[city.location] = true;
		var nn = G.geometry.getNeighbors(city.location);
		for (var i = 0; i < nn.length; i++)
		{
			var n = nn[i];
			player.canSee[n] = true;
		}
	}

	for (var fid in G.fleets)
	{
		var fleet = G.fleets[fid];
		if (fleet.owner != pid)
			continue;

		player.canSee[fleet.location] = true;;
		var nn = G.geometry.getNeighbors(fleet.location);
		for (var i = 0; i < nn.length; i++)
		{
			var n = nn[i];
			player.canSee[n] = true;
		}
	}
}

// inspect properties of a single fleet.
// add any that are missing,
// fix any whose semantics have changed.
//
function checkFleet(fleetId, fleet)
{
}

function checkTerrainCell(cid, cell)
{
	if (!cell.subcells)
	{
		cell.subcells = { natural: 64 };
		if (cell.city)
		{
			var city = G.cities[cell.city];
			if (city && city.farms)
			{
				cell.subcells.natural -= city.farms;
				cell.subcells.farm = city.farms;
				delete city.farms;
			}
			if (city)
			{
				cell.subcells.natural -= 1;
				cell.subcells.hamlet = 1;
			}
		}
	}

	if (cell.subcells.farms)
	{
		cell.subcells.farm = cell.subcells.farms;
		delete cell.subcells.farms;
	}

	if (cell.seenBy)
	{
		for (var fid in cell.seenBy)
		{
			var fleetOrCity = G.fleets[fid] || G.cities[fid];
			if (!fleetOrCity)
				delete cell.seenBy[fid];
		}
	}
}

// inspect properties of a single city.
// add any that are missing,
// fix any whose semantics have changed.
//
function checkCity(cityId, city)
{
	if ('fuel' in city)
	{
		city.wood = city.fuel;
		delete city.fuel;
	}
	if (!('food' in city))
		city.food = 0;
	if (!city.workers)
	{
		city.workers = {};
		city.workers.hunt = 50;
		city.workers.childcare = 50;
	}
	if (!city.workerRates)
		city.workerRates = {};
	if (city.workers.procreate)
	{
		city.workers['raise-children'] = city.workers.procreate;
		city.workerRates['raise-children'] = city.workerRates.procreate;
		delete city.workers.procreate;
		delete city.workerRates.procreate;
	}
	if (city.workers['raise-children'])
	{
		city.workers.childcare = city.workers['raise-children'];
		city.workerRates.childcare = city.workerRates['raise-children'];
		delete city.workers['raise-children'];
		delete city.workerRates['raise-children'];
	}
	city.population = 0;
	for (var j in city.workers)
	{
		if (isNaN(city.workers[j]))
			city.workers[j] = 0;
		city.population += (+city.workers[j]);
	}
	if (!city.production)
	{
		city.production = {};
	}
	if (!city.childrenByAge)
		city.childrenByAge = [];
	city.children = 0;
	for (var i = 0; i < city.childrenByAge.length; i++)
	{
		if (isNaN(city.childrenByAge[i]))
			city.childrenByAge[i] = 0;
		city.children += (+(city.childrenByAge[i] || 0));
	}

	if (!city.lastUpdate)
		city.lastUpdate = G.world.age;
	if (!city.birth)
		city.birth = city.lastUpdate;

	if (!city.buildings)
		city.buildings = {};
	delete city.buildingOrders;

	for (var bt in city.buildings)
	{
		if ((typeof city.buildings[bt]) == 'number')
		{
			var sz = city.buildings[bt];
			city.buildings[bt] = {
				buildingType: bt,
				size: sz
				};
		}
	}

	updateFleetSight(cityId, city);
}


var actionHandlers = {
	expose: doExpose,
	orders: doOrders,
	'rename-city': doRenameCity,
	'test-city': doCityTest,
	'reassign-workers': doReassignWorkers,
	'build-unit': doCityBuildUnit,
	'build-improvement': doCityBuildImprovement,
	'build-building': doCityBuildBuilding,
	'building-orders': doCityBuildingOrders
	};

if (typeof global !== 'undefined')
{
	global.G = G;
	global.getGameState = getGameState;
	global.actionHandlers = actionHandlers;
	global.newPlayer = newPlayer;
	global.startGame = startGame;
	global.getYear = getYear;
}
