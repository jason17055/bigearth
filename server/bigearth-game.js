if (typeof require !== 'undefined')
{
//require('../html/trains-common.js');
}

var Scheduler = require('./bigearth_modules/scheduler.js');
var city_module = require('./bigearth_modules/city.js');
var mod_terrain = require('./bigearth_modules/terrain.js');
var Location = require('../html/location.js');
var Settler = require('./bigearth_modules/settler.js');
var Fleet = require('./bigearth_modules/fleet.js');
var Lobby = require('./bigearth_modules/lobby.js');
var Map = require('./bigearth_modules/map.js');
var Battle = require('./bigearth_modules/battle.js');

var G = {
	world: {},
	terrain: {},
	players: {},
	maps: {},
	fleets: {}
	};

function discoverCell(mapId, cellId, sightLevel)
{
	if (!mapId)
	{
		console.log("Warning: discoverCell called on null map");
		return;
	}

	var isNew = false;
	var refCell = G.terrain.cells[cellId];

	var map = G.maps[mapId];
	var mapCell = map.cells[cellId];
	if (!mapCell)
	{
		isNew = true;
		mapCell = {};
	}

	//compatibility checks
	delete mapCell.subcells;

	if (mapCell.terrain != refCell.terrain)
	{
		isNew = true;
		mapCell.terrain = refCell.terrain;
	}

	for (var subcellType in refCell.zones)
	{
		if (subcellType == 'natural')
			continue;

		if (!mapCell.zones)
			mapCell.zones = {};

		if (refCell.zones[subcellType] != mapCell.zones[subcellType])
		{
			mapCell.zones[subcellType] = refCell.zones[subcellType];
			isNew = true;
		}
	}
	if (mapCell.zones)
	{
		for (var subcellType in mapCell.zones)
		{
			if (!mapCell.zones[subcellType])
			{
				delete mapCell.zones[subcellType];
				isNew = true;
			}
		}
	}

	if (mapCell.city && !refCell.city)
	{
		isNew = true;
		delete mapCell.city;
		delete mapCell.cityName;
		delete mapCell.cityOwner;
	}
	else if (refCell.city)
	{
		if (!mapCell.city || mapCell.city != refCell.city || typeof mapCell.city !== 'Number')
		{
			isNew = true;
			mapCell.city = refCell.city;
		}

		var realCity = G.cities[refCell.city];
		if (!realCity)
			throw new Error("Unexpected- city "+refCell.city+" does not exist");

		if (discoverCity(mapCell, refCell.city, realCity, sightLevel))
		{
			isNew = true;
		}
	}

	if (isNew)
	{
		map.cells[cellId] = mapCell;
		fireMapUpdate(mapId, Location.fromCellId(cellId), mapCell);
	}

	var nn = BE.geometry.getNeighbors(cellId);
	for (var i = 0; i < nn.length; i++)
	{
		if (map.cells[nn[i]])
		{
			var edgeId = BE.geometry._makeEdge(cellId, nn[i]);
			discoverEdge(mapId, edgeId);
		}
	}
}

function fireMapUpdate(mapId, location, data)
{
	// find the player(s) that use this map
	for (var playerId in G.players)
	{
		if (G.players[playerId].map == mapId)
		{
			notifyPlayer(playerId, {
				event: 'map-update',
				location: location,
				data: data
				});
		}
	}
}

// updates city information on a map to reflect the actual city
function discoverCity(mapCell, cityId, realCity, sightLevel)
{
	var isNew = false;

	if (realCity.name != mapCell.cityName)
	{
		isNew = true;
		mapCell.cityName = realCity.name;
	}

	if (realCity.owner != mapCell.cityOwner)
	{
		isNew = true;
		mapCell.cityOwner = realCity.owner;
	}

	return isNew;
}

function discoverEdge(mapId, edgeId)
{
	if (!mapId)
	{
		console.log("Warning: discoverCell called on null map");
		return;
	}

	var isNew = false;

	var refEdge = G.terrain.edges[edgeId] || {};
	var map = G.maps[mapId];
	var mapEdge = map.edges[edgeId];

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
		map.edges[edgeId] = mapEdge;
		fireMapUpdate(mapId, Location.fromEdgeId(edgeId), mapEdge);
	}
}

function discoverCellBorder(playerId, cellId, sightLevel)
{
	var ee = BE.geometry.getEdgesAdjacentToCell(cellId);
	for (var i = 0; i < ee.length; i++)
	{
		discoverEdge(playerId, ee[i]);
	}

	var nn = BE.geometry.getNeighbors(cellId);
	for (var i = 0; i < nn.length; i++)
	{
		discoverCell(playerId, nn[i], sightLevel);
	}
}

function playerCanSee(playerId, location)
{
	var terrainCell = getTerrainLocation(location);
	if (!terrainCell.seenBy)
		return false;

	for (var fid in terrainCell.seenBy)
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
	var terrainCell = G.terrain.cells[cellId];
	if (!terrainCell.seenBy)
		return;

	var done = {};
	for (var fid in terrainCell.seenBy)
	{
		var f = G.fleets[fid] || G.cities[fid];
		if (!f)
			throw new Error("unexpected- invalid fleet or city id");

		if (f.map && G.maps[f.map])
			discoverCell(f.map, cellId, "full-sight");
	}
}

function allPlayersWhoCanSee(location)
{
	var terrainCell = getTerrainLocation(location);
	if (!terrainCell.seenBy)
		return {};

	var seenByPid = {};
	for (var fid in terrainCell.seenBy)
	{
		var fleet = G.fleets[fid] || G.cities[fid];
		if (fleet.owner)
		{
			seenByPid[fleet.owner] = true;
		}
	}
	return seenByPid;
}

function moveFleetRandomly(fleetId)
{
	//broken
}

function getTerrainLocation(location)
{
	return G.terrain.cells[Location.toCellId(location)];
}

function addPlayerCanSee(playerId, location)
{
	// notify user of any fleets found at this location
	var terrain = getTerrainLocation(location);

	for (var fid in terrain.fleets)
	{
		var fleet = G.fleets[fid];
		if (fleet && fleet.owner != playerId)
		{
			notifyPlayer(playerId, {
				event: 'fleet-spawned',
				fleet: fid,
				data: getFleetInfoForPlayer(fid, playerId)
				});
		}
	}

	// notify user if there is a battle going on here
	if (terrain.battle)
	{
		Battle.playerCanNowSee(terrain.battle, playerId);
	}
}

function removePlayerCanSee(playerId, location)
{
	// notify user that they lost sight of any battle going on here
	var terrain = getTerrainLocation(location);
	if (terrain.battle)
	{
		Battle.playerCanNoLongerSee(terrain.battle, playerId);
	}

	// notify user that they lost sight of any fleets at this location

	for (var fid in terrain.fleets)
	{
		var fleet = G.fleets[fid];
		if (fleet.owner != playerId)
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

	var terrainCell = getTerrainLocation(location);
	if (!terrainCell.seenBy)
		terrainCell.seenBy = {};
	terrainCell.seenBy[fleetId] = true;

	if (!couldSeeBefore)
	{
		addPlayerCanSee(fleet.owner, location);
	}
}

function removeFleetCanSee(fleetId, fleet, location)
{
	delete fleet.canSee[location];

	var terrainCell = getTerrainLocation(location);
	if (terrainCell.seenBy)
		delete terrainCell.seenBy[fleetId];

	if (!playerCanSee(fleet.owner, location))
	{
		removePlayerCanSee(fleet.owner, location);
	}
}

function updateFleetSight(fid, fleet)
{
	var newVisibility = {};

	newVisibility[fleet.location] = true;
	var nn = BE.geometry.getNeighbors(Location.toCellId(fleet.location));
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

function checkForBattle(location)
{
	var terrain = getTerrainLocation(location);

	var lions = {};
	var countLions = 0;
	var nonLions = {};
	var countNonLions = 0;
	for (var fid in terrain.fleets)
	{
		if (G.fleets[fid].type == 'lion')
		{
			lions[fid] = true;
			countLions++;
		}
		else
		{
			nonLions[fid] = true;
			countNonLions++;
		}
	}

	if (countLions == 0 || countNonLions == 0)
	{
		// no battle
		return;
	}

	if (!terrain.battle)
	{
		Battle.newBattle(location, lions, nonLions);
	}
	else
	{
		// anyone not in the battle should join
		for (var fid in lions)
		{
			if (!G.fleets[fid].inBattle != terrain.battle)
				Battle.addFleet(terrain.battle, fid, 'a');
		}
		for (var fid in nonLions)
		{
			if (!G.fleets[fid].inBattle != terrain.battle)
				Battle.addFleet(terrain.battle, fid, 'b');
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
	checkForBattle(newLoc);
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

		if (c.city)
			continue;

		var v = (c.temperature / 20) * ((c.summerRains || c.moisture || 0) / 0.8);
		if (v > 1) v = 1;

		var nn = BE.geometry.getNeighbors(cid);
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
			var eId = BE.geometry._makeEdge(cid,nn[i]);
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

function newPlayer(requestedRole, playerId)
{
	if (G.players[playerId])
		return;

	var playerStruct = {
		type: 'player'
		};
	G.players[playerId] = playerStruct;

	if (requestedRole)
	{
		var fleet = G.fleets[requestedRole];
		if (fleet && !fleet.owner)
		{
			fleet.owner = playerId;
			fleet.map = fleet.map || Map.newMap();
			playerStruct.map = fleet.map;
		}
		else
		{
			console.log("newPlayer: requested fleet already claimed");
			return;
		}
	}
	else
	{
		// create a new map
		var mapId = Map.newMap();
		playerStruct.map = mapId;

		// pick a location to be this player's home location
		var loc = findSuitableStartingLocation();
		createUnit(playerId, "settler", loc, {
				population: 100
				});
		createUnit(playerId, "explorer", BE.geometry.getNeighbors(loc)[0]);

		if (playerId == 'god')
		{
			for (var cid in G.terrain.cells)
			{
				discoverCell(mapId, cid, "full-sight");
			}
		}
	}
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

	if (unitType == 'lion')
	{
		f.owner = null;
	}

	if (f.owner && !f.map)
	{
		f.map = G.players[f.owner].map;
	}
	if (!f.map)
	{
		f.map = Map.newMap();
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
	
	if (f.owner)
	{
		discoverCell(f.map, Location.toCellId(f.location), "full-sight");
		discoverCellBorder(f.map, Location.toCellId(f.location), "full-sight");
	}
	else
	{
		// post an advertisement
		Lobby.postAdvertisement({
			url: BE.serverBaseUrl + '/login?role=' + fid,
			description: 'Take the role of a dangerous lion, preying on weak humans and undefended livestock.'
			});
	}

	return fid;
}

function getGameState(request)
{
	return {
		role: "player",
		gameYear: Scheduler.time,
		gameSpeed: Scheduler.ticksPerYear,
		map: "/map/"+request.remote_player,
		mapSize: G.terrain.size,
		cities: "/cities/"+request.remote_player,
		fleets: "/fleets/"+request.remote_player,
		identity: request.remote_player
		};
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
	delete fleet.message;
	fleetActivity(fleetId);
}

function developLand(location, type, amount)
{
	var cellId = Location.toCellId(location);
	var c = G.terrain.cells[cellId];
	c.zones.natural -= amount;
	c.zones[type] = (c.zones[type] || 0) + amount;
	terrainChanged(cellId);
}

function getYear()
{
	var t = new Date().getTime();
	return G.world.age + (t-G.world.realWorldTime)/G.world.oneYear;
}

function getCities(playerId, callback)
{
	var result = {};
	for (var cityId in G.cities)
	{
		if (G.cities[cityId].owner == playerId)
		{
			result[cityId] = city_module.getCityInfoForOwner(cityId);
		}
	}

	callback(result);
}
exports.getCities = getCities;

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

function getMapFragment(playerId, callback)
{
	var result = {};

	var playerStruct = G.players[playerId];
	if (!playerStruct)
	{
		// specified player not found
		return callback(result);
	}

	var mapId = playerStruct.map;
	var map = G.maps[mapId];
	if (!map)
	{
		console.log("Warning: map '"+mapId+"' for player '"+playerId+"' not found");
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
	return callback(result);
}
exports.getMapFragment = getMapFragment;

function endOfYear()
{
	G.world.lastYear++;
	G.world.totalPopulation = 0;

	// do this year's processing
	console.log("processing year " + G.world.lastYear);

	// prepare phase
	for (var cid in G.terrain.cells)
	{
		terrainEndOfYear_prepare(cid, G.terrain.cells[cid]);
	}

	// process cities
	for (var tid in G.cities)
	{
		cityEndOfYear(tid, G.cities[tid]);
	}

	// process terrains
	for (var cid in G.terrain.cells)
	{
		// terrain processing expects city processing to be done already

		terrainEndOfYear_pass1(cid, G.terrain.cells[cid]);
	}

	// cleanup phase
	for (var cid in G.terrain.cells)
	{
		terrainEndOfYear_cleanup(cid, G.terrain.cells[cid]);
	}
	for (var tid in G.cities)
	{
		// city cleanup depends on terrain cleanup already being done

		cityEndOfYear_cleanup(tid, G.cities[tid]);
		G.world.totalPopulation += G.cities[tid].population + G.cities[tid].children;
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
		checkFleet(fid, G.fleets[fid]);
		Fleet.maybeAdvertise(fid, G.fleets[fid]);
		Fleet.reviveFleet(fid, G.fleets[fid]);
	}

	for (var pid in G.players)
	{
		checkPlayer(pid, G.players[pid]);
	}

	for (var bid in G.battles)
	{
		Battle.reviveBattle(bid, G.battles[bid]);
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
		G.world.woodPerWoodGatherer = 0.05;
	if (!G.world.clayPerClayGatherer)
		G.world.clayPerClayGatherer = 0.05;
	if (!G.world.stonePerStoneGatherer)
		G.world.stonePerStoneGatherer = 0.05;
}

// inspect properties of Player struct
//
function checkPlayer(pid, player)
{
	if (!player.map)
	{
		if (G.maps[pid])
			player.map = pid;
		else
			player.map = Map.newMap();
	}

	player.canSee = {};
	for (var tid in G.cities)
	{
		var city = G.cities[tid];
		if (city.owner != pid)
			continue;

		player.canSee[city.location] = true;
		var nn = BE.geometry.getNeighbors(Location.toCellId(city.location));
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
		var nn = BE.geometry.getNeighbors(Location.toCellId(fleet.location));
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
	if (fleet.owner && !fleet.map)
	{
		if (G.maps[fleet.owner])
			fleet.map = fleet.owner;
	}
}

function checkTerrainCell(cid, cell)
{
	if (cell.subcells)
	{
		cell.zones = cell.subcells;
		delete cell.subcells;
	}

	if (!cell.zones)
	{
		cell.zones = { natural: 64 };
		if (cell.city)
		{
			var city = G.cities[cell.city];
			if (city && city.farms)
			{
				cell.zones.natural -= city.farms;
				cell.zones.farm = city.farms;
				delete city.farms;
			}
			if (city)
			{
				cell.zones.natural -= 1;
				cell.zones['mud-cottages'] = 1;
			}
		}
	}

	if (cell.zones.farms)
	{
		cell.zones.farm = cell.zones.farms;
		delete cell.zones.farms;
	}

	if (cell.zones.hamlet)
	{
		cell.zones['mud-cottages'] = cell.zones.hamlet;
		delete cell.zones.hamlet;
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

	if (cell.fleets)
	{
		for (var fid in cell.fleets)
		{
			if (!G.fleets[fid])
				delete cell.fleets[fid];
		}
	}

	if (!('wildlife' in cell))
	{
		cell.wildlife = 80;
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
	global.playerCanSee = playerCanSee;
	global.allPlayersWhoCanSee = allPlayersWhoCanSee;
	global.removeFleetCanSee = removeFleetCanSee;
	global.getTerrainLocation = getTerrainLocation;
	global.discoverCell = discoverCell;
	global.discoverCellBorder = discoverCellBorder;
	global.fleetMoved = fleetMoved;
}
