if (typeof require !== 'undefined')
{
//require('../html/trains-common.js');
}

var Scheduler = require('./bigearth_modules/scheduler.js');
var city_module = require('./bigearth_modules/city.js');

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

		if (discoverCity(refCell.city, city, mapCell.city, playerId))
		{
			isNew = true;
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

// updates building information on a map to reflect the actual building
function discoverBuilding(realBuilding, mapBuilding, playerId)
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
}

// updates city information on a map to reflect the actual city
function discoverCity(cityId, realCity, mapCity, playerId)
{
	var isNew = false;

	//compatibility checks
	delete mapCity.fuel;
	delete mapCity.wheat;
	delete mapCity.wood;
	delete mapCity.meat;
	delete mapCity.clay;
	delete mapCity.food;
	delete mapCity.stone;
	delete mapCity['stone-block'];
	delete mapCity['stone-weapon'];

	var props = {
		name: "public",
		size: "public",
		owner: "public",
		population: "private floor",
		children: "private floor",
		activity: "private",
		activityTime: "private",
		activityComplete: "private",
		activitySpeed: "private"
		};

	for (var p in props)
	{
		if (props[p].match(/private/) && realCity.owner != playerId)
			continue;

		var refValue = realCity[p];
		if (props[p].match(/floor/))
			refValue = Math.floor(refValue);

		if (mapCity[p] != refValue)
		{
			isNew = true;
			mapCity[p] = refValue;
		}
	}

	if (realCity.owner == playerId)
	{
		if (!mapCity.workers)
			mapCity.workers = {};

		var ww = roundWorkers(realCity.workers);
		addAvailableJobs(cityId, ww);
		for (var j in mapCity.workers)
		{
			if (!(j in ww))
			{
				isNew = true;
				delete mapCity.workers[j];
			}
		}
		for (var j in ww)
		{
			if (mapCity.workers[j] != ww[j])
			{
				isNew = true;
				mapCity.workers[j] = ww[j];
			}
		}

		if (!mapCity.buildings)
		{
			mapCity.buildings = {};
		}
		delete mapCity.buildingOrders;

		// check for buildings which no longer exist
		for (var bt in mapCity.buildings)
		{
			if (!realCity.buildings[bt])
			{
				isNew = true;
				delete mapCity.buildings[bt];
			}
		}

		// check for buildings that are new or changed
		for (var bt in realCity.buildings)
		{
			if (!mapCity.buildings[bt])
			{
				mapCity.buildings[bt] = {};
			}
			if (discoverBuilding(realCity.buildings[bt], mapCity.buildings[bt], playerId))
			{
				isNew = true;
			}
		}

		// discover stock held by this city
		if (!mapCity.stock)
			mapCity.stock = {};
		for (var t in mapCity.stock)
		{
			if (!realCity.stock[t])
			{
				isNew = true;
				delete mapCity.stock[t];
			}
		}
		for (var t in realCity.stock)
		{
			var refValue = Math.floor(+realCity.stock[t]);
			if (refValue != mapCity.stock[t])
			{
				isNew = true;
				mapCity.stock[t] = refValue;
			}
		}
	}

	return isNew;
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

function fleetChanged(fleetId, fleet)
{
	notifyPlayer(fleet.owner, {
		event: 'fleet-updated',
		fleet: fleetId,
		data: getFleetInfoForPlayer(fleetId, fleet.owner)
		});
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

	return fleetChanged(fleetId, fleet);
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
	fleet.message = "Unable to complete orders: " + errorMessage;
	fleetChanged(fleetId, fleet);
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
		city_addWorkersAny(cityId, city, fleet.population);
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

		var city = newCity(fleet.location, fleet.owner);
		city.wheat = 100;
		city.wood = 50;
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
		fleet.message = 'Orders complete';
		fleetChanged(fleetId, fleet);
		return;
	}

	fleet.message = null;
	fleetChanged(fleetId, fleet);

	var currentOrder = fleet.orders[0];
	if (currentOrder.command == 'wander')
	{
		console.log("traveler moves!");
		return moveFleetRandomly(fleetId);
	}
	else if (currentOrder.command == 'follow-coast')
	{
		return moveFleetAlongCoast(fleetId);
	}
	else if (currentOrder.command == 'goto')
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
	else
	{
		return fleetActivityError(fleetId, fleet, "Unrecognized command");
	}
}

function getGameState(request)
{
	if (request.remote_player)
	{
		return {
		role: "player",
		gameYear: Scheduler.time,
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

function developLand(location, type, amount)
{
	var c = G.terrain.cells[location];
	c.subcells.natural -= amount;
	c.subcells[type] += amount;
	terrainChanged(location);
}

function getYear()
{
	var t = new Date().getTime();
	return G.world.age + (t-G.world.realWorldTime)/G.world.oneYear;
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
		if (f.message)
			_fleet.message = f.message;
		return _fleet;
	}
	else if (playerCanSee(playerId, f.location))
	{
		var _fleet = {
			type: f.type,
			location: f.location,
			owner: f.owner
		};
		if (f.activity)
			_fleet.activity = f.activity;
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


var actionHandlers = {
	orders: doOrders,
	'rename-city': city_module.cmd_rename_city,
	'test-city': city_module.cmd_test_city,
	'reassign-workers': city_module.cmd_reassign_workers,
	'equip-unit': city_module.cmd_equip_unit,
	'build-improvement': city_module.cmd_build_improvement,
	'build-building': city_module.cmd_build_building,
	'building-orders': city_module.cmd_building_orders
	};

if (typeof global !== 'undefined')
{
	global.G = G;
	global.getGameState = getGameState;
	global.actionHandlers = actionHandlers;
	global.newPlayer = newPlayer;
	global.startGame = startGame;
	global.getYear = getYear;
	global.nextFleetId = nextFleetId;
	global.terrainChanged = terrainChanged;
	global.updateFleetSight = updateFleetSight;
	global.developLand = developLand;
	global.createUnit = createUnit;
}
