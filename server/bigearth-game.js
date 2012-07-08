if (typeof require !== 'undefined')
{
//require('../html/trains-common.js');
}

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

		var props = {
		name: "public",
		size: "public",
		owner: "public",
		population: "private floor",
		food: "private floor",
		fuel: "private floor",
		children: "private floor"
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
			for (var j in mapCell.city.workers)
			{
				if (!ww[j])
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
		}
	}

	if (isNew)
	{
		map.cells[location] = mapCell;
		postEvent({
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
		postEvent({
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

	postEvent({
		event: 'fleet-activity',
		fleet: fleetId,
		activity: fleet.activity
		});
	
}

function playerCanSee(playerId, cellId)
{
	var cells_to_check = [cellId];
	var nn = G.geometry.getNeighbors(cellId);
	for (var i = 0; i < nn.length; i++)
	{
		cells_to_check.push(nn[i]);
	}

	for (var i = 0; i < cells_to_check.length; i++)
	{
		var tid = G.terrain.cells[cells_to_check[i]].city;
		if (tid)
		{
			if (G.cities[tid].owner == playerId)
				return true;
		}
	}

	for (var fid in G.fleets)
	{
		var f = G.fleets[fid];
		if (f.owner == playerId)
		{
			for (var i = 0; i < cells_to_check.length; i++)
			{
				if (f.location == cells_to_check[i])
					return true;
			}
		}
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
	if (!city)
		return fleetActivityError(fleetId, fleet, "No city at this location");

	//TODO - add this fleet's numbers to the city

	delete G.fleets[fleetId];
	postEvent({
		event: 'fleet-terminated',
		fleet: fleetId,
		location: location,
		disposition: 'disband-in-city'
		});
}

function tryToBuildCity(fleetId, fleet)
{
	if (!fleetHasCapability(fleet, 'build-city'))
		return fleetCurrentCommandFinished(fleetId, fleet);

	if (fleet.activity == 'build-city')
	{
		var tid = nextFleetId();
		var city = {
			owner: fleet.owner,
			location: fleet.location,
			food: 100,
			fuel: 50,
			workers: {},
			production: {},
			population: 0,
			children: 0,
			childrenByAge: [],
			lastUpdate: G.year
			};
		addWorkers(city, fleet.population/2, "procreate");
		addWorkers(city, fleet.population/2, "hunt");
		city.population = fleet.population || 100;
		G.cities[tid] = city;
		G.terrain.cells[fleet.location].city = tid;
		terrainChanged(fleet.location);

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

	fleet.path = shortestPath(oldLoc, targetLocation, fleet);

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
function shortestPath(fromLoc, toLoc, fleet)
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

			var accumDist = cur[2] + getFleetMovementCost(fleet, curLoc, nn[i]);
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
		'other_terrain': 3600
		},
	};

function isNavigableByMap(map, fleet, location)
{
	var unitType = fleet.type;
	var UM = UNIT_MOVEMENT_RULES[unitType] || UNIT_MOVEMENT_RULES['*'];
	var c = map.cells[location];

	var cost = UM[c.terrain] || UM.other_terrain;
	return cost < 15000;
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

function getFleetMovementCost(fleet, oldLoc, newLoc)
{
	return getUnitMovementCost(fleet.type, oldLoc, newLoc);
}

function moveFleetOneStep(fleetId, newLoc)
{
	var fleet = G.fleets[fleetId];
	var oldLoc = fleet.location;
	fleet.lastLocation = oldLoc;
	fleet.location = newLoc;

	var costOfMovement = getFleetMovementCost(fleet, oldLoc, newLoc);
	console.log("cost is " + Math.round(costOfMovement));

	discoverCell(fleet.owner, newLoc);
	discoverCellBorder(fleet.owner, newLoc);

	postEvent({
		event: 'fleet-movement',
		fleet: fleetId,
		fromLocation: oldLoc,
		toLocation: newLoc,
		delay: Math.round(costOfMovement)
		});
	fleetCooldown(fleetId, fleet, Math.round(costOfMovement));
}

function fleetCooldown(fleetId, fleet, delay)
{
	fleet._coolDownTimer = setTimeout(function() {
		fleet._coolDownTimer = null;
		G.year = getYear();
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
	createUnit(playerId, "settler", loc);
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
	postEvent({
		event: 'fleet-spawned',
		fleet: fid,
		data: getFleetInfoForPlayer(fid, playerId)
		});
	
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
}

function getGameState(request)
{
	if (request.remote_player)
	{
		return {
		role: "player",
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

	if (!city.workers[fromJob])
		return;

	if (quantity + 1 < (+city.workers[fromJob]))
	{
		city.workers[fromJob] -= quantity;
		console.log('still have ' + city.workers[fromJob] + ' workers left');
	}
	else
	{
		quantity = (+city.workers[fromJob]);
		delete city.workers[fromJob];
	}

	city.workers[toJob] = +(city.workers[toJob] || 0) + quantity;
	terrainChanged(city.location);
}

function doCityBuildUnit(requestData, queryString, remoteUser)
{
	if (!queryString.match(/^city=(.*)$/))
	{
		console.log("build-settler: invalid query string");
		return;
	}

	var cityId = RegExp.$1;
	var city = G.cities[cityId];
	if (!city)
	{
		console.log("build-settler: city " + cityId + " not found");
		return;
	}

	if (city.owner != remoteUser)
	{
		console.log("build-settler: city " + cityId + " not owned by player " + remoteUser);
		return;
	}

	if (requestData.type == 'settler')
	{
		return tryBuildSettler(cityId, city);
	}
	else if (requestData.type == 'trieme')
	{
		return tryBuildTrieme(cityId, city);
	}
}

function tryBuildTrieme(cityId, city)
{
	if (city.population < 150)
	{
		console.log("build-unit: city " + cityId + " not large enough to build trieme");
		return;
	}

	stealWorkers(cityId, city, 50, 'trieme');
	createUnit(city.owner, "trieme", city.location, {
		population: 50
		});
	removeWorkers(cityId, city, 50, 'trieme');
	terrainChanged(city.location);
}

// adds new people to the city, given a particular job
//
function addWorkers(city, quantity, toJob)
{
	if (quantity < 0)
		throw new Error("invalid argument for addWorkers");

	if (quantity != 0)
		city.workers[toJob] = (city.workers[toJob] || 0) + quantity;
	city.population += quantity;
}

function removeWorkers(cityId, city, quantity, fromJob)
{
	if (city.workers[fromJob] > quantity)
	{
		city.population -= quantity;
		city.workers[fromJob] -= quantity;
		return quantity;
	}
	else if (city.workers[fromJob])
	{
		quantity = city.workers[fromJob];
		city.population -= quantity;
		delete city.workers[fromJob];
		return quantity;
	}
	else
	{
		return 0;
	}
}

function tryBuildSettler(cityId, city)
{
	if (city.population < 200)
	{
		console.log("build-unit: city " + cityId + " not large enough to build settler");
		return;
	}

	
	var numSettlers = city.workers.settle || 0;
console.log("numSettlers is " + numSettlers);
	if (numSettlers < 100)
		stealWorkers(cityId, city, 100-numSettlers, 'settle');

	if (!city.tasks)
		city.tasks = [];
	city.tasks.push({
		task: 'build',
		type: 'settler'
		});

console.log("city now has " + city.workers.settle + " workers");
	terrainChanged(city.location);
	cityActivity(cityId, city);
}

function cityActivity(cityId, city)
{
	setTimeout(function() {

	var numSettlers = city.workers.settle || 0;
	createUnit(city.owner, "settler", city.location, {
		population: numSettlers
		});
	delete city.workers.settle;
	terrainChanged(city.location);

		}, 5000);
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
	terrainChanged(city.location);
}

function doCityTest(requestData, queryString, remoteUser)
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

	updateCityProperties(cityId, city);
	terrainChanged(city.location);
}

function getYear()
{
	var t = new Date().getTime();
	return G.world.age + (t-G.world.realWorldTime)/G.world.oneYear;
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

var FREE_PROFESSIONS = {
	"hunt": true,
	"procreate": true
	};

function countFreeWorkers(cityId, city)
{
	var sum = 0;
	for (var k in city.workers)
	{
		if (FREE_PROFESSIONS[k])
			sum += city.workers[k];
	}
	return sum;
}

//caller should call fire-city-update-notification
//
function stealWorkers(cityId, city, quantity, toJob)
{
	var sumFreeWorkers = countFreeWorkers(cityId, city);
	if (sumFreeWorkers < quantity)
		throw new Error("oops not enough free workers");

	for (var k in city.workers)
	{
		if (FREE_PROFESSIONS[k])
		{
			city.workers[k] -= quantity*city.workers[k]/sumFreeWorkers;
			if (city.workers[k] == 0)
				delete city.workers[k];
		}
	}

	addWorkers(city, quantity, toJob);
}

function cityEndOfYear(cityId, city)
{
	console.log("city "+city.name+": end of year");

	updateCityProperties(cityId, city);

	var ADULT_AGE = G.world.childYears;
	var LIFE_EXPECTANCY = G.world.lifeExpectancy;

	// calculate births and bring forward growing children
	var newAdults = city.childrenByAge[ADULT_AGE-1] || 0;
	var childCareDemand = 0;
	for (var i = ADULT_AGE-1; i > 0; i--)
	{
		city.childrenByAge[i] = (city.childrenByAge[i-1] || 0);
		childCareDemand += city.childrenByAge[i];
	}
	city.childrenByAge[0] = 0;
	city.children -= newAdults;

	// TODO- consider childCareDemand... if not enough workers
	// assigned to procreate, then kill off children since
	// they is inadequate caretakers.

	if (city.production.procreate)
	{
		var pts = city.production.procreate;
		delete city.production.procreate;

		var births = pts/ADULT_AGE;
		city.childrenByAge[0] = births;
		city.children = (city.children || 0) + births;
		city.births += births;
	}

	// food production
	if (city.production.hunt)
	{
		var pts = city.production.hunt;
		delete city.production.hunt;

		var numWildlife = 80;
		var s = 0.5*Math.sqrt(numWildlife/40);
		var numHarvested = numWildlife - numWildlife * Math.exp(-s * pts / numWildlife);

		//TODO- subtract numHarvested from this cell's
		//wildlife counter.

		var foodYield = numHarvested * G.world.foodPerAnimal;
		city.food += foodYield;

		console.log("  hunters brought in " + foodYield + " food");
	}

	// feed the population
	var foodDemand = city.hunger || 0;
	var sustenance;
	if (city.food >= foodDemand)
	{
		// ok, enough to satisfy everyone
		sustenance = 1;
		city.food -= foodDemand;
	}
	else   // city.food < foodDemand
	{
		// not enough food, some people gonna die
		sustenance = Math.sqrt(city.food / foodDemand);
		city.food = 0;
	}
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
	}
	city.population += netPopChange;

	// notify interested parties
	terrainChanged(city.location);

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

function updateCityProperties(cityId, city)
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
			var productionPoints = RndProductionPoints(yearsElapsed * city.workers[job]);
			city.production[job] = (city.production[job] || 0) +
				productionPoints;
		}

		// calculate hunger
		var foodRequired = (FOOD_PER_ADULT * city.population + FOOD_PER_CHILD * city.children) * yearsElapsed;
		city.hunger = (city.hunger || 0) + foodRequired;

		// finished
		city.lastUpdate = aTime;
	};

	if (!G.year)
		throw new Error("invalid year ("+G.year+", "+G.world.lastYear+")");

	bringForward(G.year);
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
	if (G.year < G.world.lastYear)
		G.year = G.world.lastYear;

	// do this year's processing
	console.log("processing year " + G.world.lastYear);

	// process cities
	for (var tid in G.cities)
	{
		cityEndOfYear(tid, G.cities[tid]);
	}

	// schedule for next end-of-year
	var nextYearDelay = (Math.round(G.world.lastYear+1) - getYear()) * G.world.oneYear;
	setTimeout(endOfYear, nextYearDelay > 0 ? nextYearDelay : 0);
}

function startGame()
{
	checkWorldParameters();
	G.world.realWorldTime = new Date().getTime();
	G.year = G.world.age;

	for (var tid in G.cities)
	{
		checkCity(tid, G.cities[tid]);
	}

	var nextYearDelay = (Math.round(G.world.lastYear+1) - G.world.age) * G.world.oneYear;
	setTimeout(endOfYear, nextYearDelay > 0 ? nextYearDelay : 0);

	for (var fid in G.fleets)
	{
		checkFleet(fid);
		fleetActivity(fid, G.fleets[fid]);
	}
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
}

// inspect properties of a single fleet.
// add any that are missing,
// fix any whose semantics have changed.
//
function checkFleet(fleetId, fleet)
{
}

// inspect properties of a single city.
// add any that are missing,
// fix any whose semantics have changed.
//
function checkCity(cityId, city)
{
	if (!('fuel' in city))
		city.fuel = 0;
	if (!('food' in city))
		city.food = 0;
	if (!city.workers)
	{
		city.workers = {
			hunt: 50,
			procreate: 50
			};
	}
	city.population = 0;
	for (var j in city.workers)
	{
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
		city.children += (+(city.childrenByAge[i] || 0));
	}
	
	if (!city.lastUpdate)
		city.lastUpdate = G.world.age;
	if (!city.birth)
		city.birth = city.lastUpdate;
}


var actionHandlers = {
	expose: doExpose,
	orders: doOrders,
	'rename-city': doRenameCity,
	'test-city': doCityTest,
	'reassign-workers': doReassignWorkers,
	'build-unit': doCityBuildUnit
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
