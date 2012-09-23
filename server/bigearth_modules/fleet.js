// FLEET DATA STRUCTURE
//  type - the unit type of this fleet
//  location - where the fleet is
//  population - the number of people
//  inBattle - identifies a battle that this fleet is involved in
//  inBattleGroup - identifies which team, on the battle, the fleet is on
//  messages [] - array of message objects
//  map - identifies the map that this fleet uses
//  activity - name of activity the fleet is currently doing
//  stock {} - commodities this fleet has in its possession
//

var Scheduler = require('./scheduler.js');
var Location = require('../../html/location.js');
var Settler = require('./settler.js');
var Terrain = require('./terrain.js');
var City = require('./city.js');
var Commodity = require('./commodity.js');
var Lobby = require('./lobby.js');
var Battle = require('./battle.js');

function fleetMessage(fleetId, message)
{
	var fleet = G.fleets[fleetId];
	if (!fleet)
	{
		throw new Error("fleet "+fleetId+" not found");
	}

	if (!fleet.messages)
	{
		fleet.messages = [];
	}

	fleet.messages.unshift({
		message: message,
		time: Scheduler.time
		});
	while (fleet.messages.length > 12)
		fleet.messages.pop();

	fleetChanged(fleetId, fleet);
}

function fleetActivityError(fleetId, fleet, errorMessage)
{
	fleetMessage(fleetId, "Unable to complete orders: " + errorMessage);
}

function fleetCooldown(fleetId, fleet, delay)
{
	fleet._coolDownTimer = Scheduler.schedule(function() {
		fleet._coolDownTimer = null;
		fleetActivity(fleetId);
		}, delay);
}

function getMap(fleetId, fleet)
{
	if (fleet.map)
	{
		return G.maps[fleet.map];
	}

	// create a temporary empty map to return
	var tempMap = {
		cells: {}, edges: {}
		};
	return tempMap;
}

function setFleetActivityFlag(fleetId, fleet, newActivity)
{
	if (newActivity)
	{
		fleet.activity = newActivity;
		fleet.currentActivityInfo = {};
		fleet.currentActivityInfo.started = Scheduler.time;
	}
	else
	{
		delete fleet.activity;
	}

	return fleetChanged(fleetId, fleet);
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

	if (!fleet.hadTerrainEffect)
	{
		return fleetTerrainEffect(fleetId);
	}

	if (!fleet.population)
	{
		// fleet has died off
		return destroyFleet(fleetId, 'died-off');
	}

	if (!fleet.orders || fleet.orders.length == 0)
	{
		// this fleet does not have any orders
		return fleetMessage(fleetId, "Orders complete.");
	}

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
	else if (currentOrder.command == 'gather-wood')
	{
		return fleetGatherWood(fleetId, fleet, currentOrder);
	}
	else if (currentOrder.command == 'goto')
	{
		return moveFleetTowards(fleetId, currentOrder.location);
	}
	else if (currentOrder.command == 'hunt')
	{
		return fleetHunt(fleetId, fleet, currentOrder);
	}
	else if (currentOrder.command == 'build-city')
	{
		return Settler.buildCity(fleetId, fleet, currentOrder);
	}
	else if (currentOrder.command == 'auto-settle')
	{
		return Settler.autoSettle(fleetId, fleet, currentOrder);
	}
	else if (currentOrder.command == 'disband')
	{
		return Settler.disbandInCity(fleetId, fleet, currentOrder);
	}
	else if (currentOrder.command == 'drop')
	{
		return fleetDropResource(fleetId, fleet, currentOrder);
	}
	else if (currentOrder.command == 'take')
	{
		return fleetTakeResource(fleetId, fleet, currentOrder);
	}
	else
	{
		return fleetActivityError(fleetId, fleet, "Unrecognized command");
	}
}

function fleetChanged(fleetId, fleet)
{
	notifyPlayer(fleet.owner, {
		event: 'fleet-updated',
		fleet: fleetId,
		data: getFleetInfoForPlayer(fleetId, fleet.owner)
		});
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

	if (fleet.inBattle)
	{
		Battle.removeFleet(fleet.inBattle, fleetId, fleet.inBattleGroup);
	}

	var terrain = getTerrainLocation(fleet.location);
	delete terrain.fleets[fleetId];

	delete G.fleets[fleetId];
}

function fleetEncumbrance(fleet)
{
	var totalWeight = 0;
	var totalLivestock = 0;

	for (var commod in fleet.stock)
	{
		var commodInfo = Commodity.getCommodityTypeInfo(commod);
		var amt = fleet.stock[commod];

		if (commodInfo.isLivestock)
		{
			totalLivestock += amt;
		}
		else
		{
			totalWeight += (commodInfo.weight || 10) * amt;
		}
	}

	return totalWeight + totalLivestock * 20;
}

function fleetEncumbranceLevel(fleet)
{
	var encumb = fleetEncumbrance(fleet);
	var carryingCapacity = fleet.population * 20;

	if (carryingCapacity > 0)
		return encumb / carryingCapacity;
	else
		return Infinity;
}

function fleetSpeedModifier(fleet)
{
	var encumbLevel = fleetEncumbranceLevel(fleet);
	return 1-1/(1+Math.exp(3*(1.75-encumbLevel)));
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
			orders: f.orders,
			population: f.population
		};
		if (f.activity)
			_fleet.activity = f.activity;
		if (f.inBattle)
		{
			_fleet.inBattle = f.inBattle;
			_fleet.inBattleGroup = f.inBattleGroup;
		}
		if (f.messages)
		{
			_fleet.messages = f.messages;
			_fleet.message = _fleet.messages[0].message;
		}

		if (fleetCanSettle(f))
		{
			_fleet.canSettle = true;
			_fleet.settlementFitness = Settler.getSettlementFitness(
				getMap(fleetId, f), Location.toCellId(f.location));
		}

		var terrainCell = G.terrain.cells[Location.toCellId(f.location)];
		if (terrainCell && terrainCell.hasWildSheep && terrainCell.hasWildSheep >= 25)
		{
			_fleet.sheepBeHere = true;
		}
		if (terrainCell && terrainCell.hasWildPig && terrainCell.hasWildPig >= 25)
		{
			_fleet.pigBeHere = true;
		}

		_fleet.stock = {};
		if (f.stock)
		{
			for (var resourceType in f.stock)
			{
				_fleet.stock[resourceType] = f.stock[resourceType];
			}
		}

		var encumbLevel = fleetEncumbranceLevel(f);
		_fleet.encumbranceCategory = (
			encumbLevel <= 1   ? "Unencumbered" :
			encumbLevel <= 1.5 ? "Burdened" :
			encumbLevel <= 2   ? "Stressed" :
			encumbLevel <= 2.5 ? "Strained" :
			encumbLevel <= 3   ? "Overtaxed" :
			"Overloaded"
			);

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
		if (f.inBattle)
		{
			_fleet.inBattle = f.inBattle;
			_fleet.inBattleGroup = f.inBattleGroup;
		}
		return _fleet;
	}
	else
	{
		return null;
	}
}

function fleetDropResource(fleetId, fleet, currentOrder)
{
	var cell = G.terrain.cells[Location.toCellId(fleet.location)];
	var cityId = null;
	if (cell.city)
	{
		cityId = cell.city;
		cell = G.cities[cityId];
	}
	if (!cell)
	{
		return fleetActivityError(fleetId, fleet, 'invalid location');
	}

	var resourceType = currentOrder.resourceType;
	var amountToDrop = currentOrder.amount;

	var avail = fleet.stock ? (fleet.stock[resourceType] || 0) : 0;
	if (avail == 0 || avail < amountToDrop)
	{
		return fleetActivityError(fleetId, fleet, 'not enough of that resource in your possession');
	}

	fleet.stock[resourceType] -= amountToDrop;
	if (!fleet.stock[resourceType])
	{
		delete fleet.stock[resourceType];
	}

	if (!cell.stock)
		cell.stock = {};
	cell.stock[resourceType] = (cell.stock[resourceType] || 0) + amountToDrop;

	if (cityId)
	{
		City.cityChanged(cityId);
	}

	return fleetCurrentCommandFinished(fleetId, fleet);
}

function fleetTakeResource(fleetId, fleet, currentOrder)
{
	var cell = G.terrain.cells[Location.toCellId(fleet.location)];
	var cityId = null;
	if (cell.city)
	{
		cityId = cell.city;
		cell = G.cities[cityId];
	}
	if (!cell)
	{
		return fleetActivityError(fleetId, fleet, 'invalid location');
	}

	var resourceType = currentOrder.resourceType;
	var amountWanted = currentOrder.amount;

	var avail = cell.stock ? (cell.stock[resourceType] || 0) : 0;
	if (avail == 0)
	{
		return fleetActivityError(fleetId, fleet, 'that resource not available here');
	}

	if (amountWanted > avail)
	{
		return fleetActivityError(fleetId, fleet, 'not enough of that resource here');
	}

	cell.stock[resourceType] -= amountWanted;
	if (!cell.stock[resourceType])
	{
		delete cell.stock[resourceType];
	}
	fleet.stock[resourceType] = (fleet.stock[resourceType] || 0) + amountWanted;

	if (cityId)
	{
		City.cityChanged(cityId);
	}

	return fleetCurrentCommandFinished(fleetId, fleet);
}

function fleetGatherWood(fleetId, fleet, currentOrder)
{
	var requiredTime = (1/G.world.woodPerWoodGatherer) * 2000;

	if (fleet.activity != 'gather-wood')
	{
		setFleetActivityFlag(fleetId, fleet, 'gather-wood');
		fleetCooldown(fleetId, fleet, requiredTime);
		return;
	}

	if (!fleet.stock)
		fleet.stock = {};
	fleet.stock.wood = (fleet.stock.wood || 0) + 1;

	setFleetActivityFlag(fleetId, fleet, null);
	return fleetCurrentCommandFinished(fleetId, fleet);
}

function fleetHunt(fleetId, fleet, currentOrder)
{
	var cell = G.terrain.cells[Location.toCellId(fleet.location)];
	var huntingRate = Terrain.calculateHuntingRate(cell, fleet.population);
	var requiredTime = ((1/G.world.foodPerAnimal) / huntingRate) * 20000;

	if (fleet.activity != 'hunt')
	{
		setFleetActivityFlag(fleetId, fleet, 'hunt');
		fleetCooldown(fleetId, fleet, requiredTime);
		return;
	}

	if (!fleet.stock)
		fleet.stock = {};

	var r = Math.random();
	if (cell.hasWildSheep && r < 5/cell.hasWildSheep)
	{
		fleet.stock.sheep = (fleet.stock.sheep || 0) + 1;
		cell.hasWildSheep -= 1;
		if (cell.hasWildSheep < 1)
			delete cell.hasWildSheep;
	}
	else if (cell.hasWildPig && r < 5 / cell.hasWildPig)
	{
		fleet.stock.pig = (fleet.stock.pig || 0) + 1;
		cell.hasWildPig -= 1;
		if (cell.hasWildPig < 1)
			delete cell.hasWildPig;
	}
	else
	{
		fleet.stock.meat = (fleet.stock.meat || 0) + 1;
		cell.wildlifeHunted += 1/G.world.foodPerAnimal;
	}

	setFleetActivityFlag(fleetId, fleet, null);
	return fleetCurrentCommandFinished(fleetId, fleet);
}

function fleetCanSettle(fleet)
{
	return fleet.type == 'settler';
}

function fleetTerrainEffect_deaths(fleetId, fleet, deathCount, explanation)
{
	fleet.population = Math.floor(fleet.population - deathCount);
	if (fleet.population > 0)
	{
		fleetMessage(fleetId, (deathCount > 1 ? "Some" : "One") + " of your party " + explanation + ".");
		return fleetCooldown(fleetId, fleet,
			deathCount > 3 ? 1500 : (500*deathCount));
	}
	else
	{
		fleet.population = 0;
		fleetMessage(fleetId, 'Your party ' + explanation + '.');
		return fleetCooldown(fleetId, fleet, 1500);
	}
}

function fleetTerrainEffect_lossOfStock(fleetId, fleet, portion, explanation)
{
	if (!fleet.stock)
		return fleetCooldown(fleetId, fleet, 0);

	var countGoods = 0;
	for (var commod in fleet.stock)
	{
		countGoods += fleet.stock[commod];
	}

	var toLose = Math.ceil(countGoods * portion);
	var actualLoss = 0;
	for (var commod in fleet.stock)
	{
		if (countGoods < 1) break;
		if (toLose < 1) break;

		var toLoseThis = Math.round(fleet.stock[commod] * toLose / countGoods);
		if (toLoseThis > 0)
		{
			if (toLoseThis < fleet.stock[commod])
				fleet.stock[commod] -= toLoseThis;
			else
				delete fleet.stock[commod];

			toLose -= toLoseThis;
			countGoods -= toLoseThis;
			actualLoss += toLoseThis;
		}
	}

	if (actualLoss > 0)
	{
		fleetMessage(fleetId, actualLoss + ' ' + (actualLoss == 1 ? 'good' : 'goods') + ' ' + explanation);
		return fleetCooldown(fleetId, fleet, 1500);
	}
	else
	{
		return fleetCooldown(fleetId, fleet, 0);
	}
}

function fleetTerrainEffect(fleetId)
{
	var fleet = G.fleets[fleetId];
	fleet.hadTerrainEffect = true;

	if (fleet.crossedRiver)
	{
		var encumbLevel = fleetEncumbranceLevel(fleet);
		if (encumbLevel <= 1)
		{
			if (Math.random() < 0.1)
			{
				return fleetTerrainEffect_lossOfStock(fleetId, fleet, 0.10, 'lost while crossing a river');
			}
		}
		else if (encumbLevel <= 1.5)
		{
			if (Math.random() < 0.25)
			{
				return fleetTerrainEffect_lossOfStock(fleetId, fleet, 0.10, 'lost while crossing a river');
			}
		}
		else if (encumbLevel <= 2)
		{
			if (Math.random() < 0.5)
			{
				return fleetTerrainEffect_lossOfStock(fleetId, fleet, 0.25, 'lost while crossing a river');
			}
		}
		else if (encumbLevel <= 2.5)
		{
			return fleetTerrainEffect_lossOfStock(fleetId, fleet,
				(Math.random() < 0.75 ? 0.5 : 0.1),
				'lost while crossing a river');
		}
		else
		{
			return fleetTerrainEffect_lossOfStock(fleetId, fleet, 0.75, 'lost while crossing a river');
		}

		// if no goods were lost, still a small chance that someone will drown crossing the river
		if (Math.random() < 0.15)
		{
			return fleetTerrainEffect_deaths(fleetId, fleet, 1, 'drowned crossing a river');
		}
	}

	var location = fleet.location;
	var terrainCell = G.terrain.cells[Location.toCellId(location)];
	if (terrainCell)
	{
		var terrainType = terrainCell.terrain;
		if (terrainType == 'tundra' && Math.random() < 0.25)
		{
			return fleetTerrainEffect_deaths(fleetId, fleet, 5, 'died of exposure');
		}

		if (terrainType == 'glacier' && Math.random() < 0.5)
		{
			return fleetTerrainEffect_deaths(fleetId, fleet, 7, 'died of exposure');
		}

		if ((terrainType == 'jungle' || terrainType == 'swamp') && Math.random() < 0.25)
		{
			return fleetTerrainEffect_deaths(fleetId, fleet, 5, 'died of malaria');
		}

		if (terrainCell.wildlife >= 10 &&
			Math.random() < .001*terrainCell.wildlife)
		{
			return fleetTerrainEffect_deaths(fleetId, fleet, 1, 'killed by a wild animal');
		}
	}

	// no effect
	return fleetActivity(fleetId);
}

var UNIT_MOVEMENT_RULES = {
	explorer: {
		plains:  600,
		grassland: 600,
		desert: 600,
		forest: 1200,
		swamp: 2400,
		ocean: 15000,
		tundra: 1200,
		jungle: 2400,
		glacier: 1200,
		hills: 1800,
		mountains: 2400,
		across_river: 2000,
		other_terrain: 600
		},
	trieme: {
		ocean: 600,
		other_terrain: 15000
		},
	'*': {
		ocean: 15000,
		across_river: 3600,
		'other_terrain': 3600
		},
	};

function isNavigableByMap(map, fleet, location)
{
	var unitType = fleet.type;
	var UM = UNIT_MOVEMENT_RULES[unitType] || UNIT_MOVEMENT_RULES['*'];
	var c = map.cells[Location.toCellId(location)];
	if (!c)
		return false;

	var cost = UM[c.terrain] || UM.other_terrain;
	return cost < 15000;
}

function getMovementCost_byMap(fleet, oldLoc, newLoc, map)
{
	var unitType = fleet.type;
	var encumbLevel = fleetEncumbranceLevel(fleet);
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

	var eId = BE.geometry._makeEdge(oldLoc, newLoc);
	var e = map.edges[eId];
	var riverCrossing = false;
	if (e && e.feature && e.feature == 'river')
	{
		riverCrossing = true;

		// consider the extra time required to cross the river
		cost += (UM.across_river || 0);

		// consider also the risk of losing goods crossing the river
		var RISK_LOST_GOODS_PENALTY = 4000;
		var extraPenalty = RISK_LOST_GOODS_PENALTY*Math.pow(encumbLevel,2);
		cost += extraPenalty;
	}

	return { delay: cost };
}

function getMovementCost_real(fleet, oldLoc, newLoc)
{
	var unitType = fleet.type;
	var locationInfo = function(loc)
	{
		if (Location.isCell(loc))
		{
			return { locationType: 'cell', location: +loc };
		}
		else if (Location.isEdge(loc))
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

		// consider cost of crossing a river, if there is one.
		var eId = BE.geometry._makeEdge(ol.location, ne.location);
		var e = G.terrain.edges[eId];
		var riverCrossing = false;
		if (e && e.feature && e.feature == 'river')
		{
			riverCrossing = true;
			cost += (UM.across_river || 0);
		}

		cost /= fleetSpeedModifier(fleet);

		return { delay: cost, crossedRiver: riverCrossing };
	}
	return { delay: Infinity };
}

function maybeAdvertise(fleetId, fleet)
{
	if (!BE.serverBaseUrl)
		throw new Error("cannot advertise before http server is listening");

	if (fleet.owner == null)
	{
		Lobby.postAdvertisement({
			url: BE.serverBaseUrl + '/login?role=' + fleetId,
			description: fleet.type
			});
	}
}

function moveFleetOneStep(fleetId, newLoc)
{
	var fleet = G.fleets[fleetId];
	var oldLoc = fleet.location;

	if (fleet.inBattle)
	{
		Battle.removeFleet(fleet.inBattle, fleetId, fleet.inBattleGroup);
		delete fleet.inBattle;
		delete fleet.inBattleGroup;
	}

	fleet.lastLocation = oldLoc;
	fleet.location = newLoc;
	delete fleet.hadTerrainEffect;
	delete fleet.crossedRiver;

	{
		var oldLocTerrain = getTerrainLocation(oldLoc);
		if (oldLocTerrain.fleets)
			delete oldLocTerrain.fleets[fleetId];

		var newLocTerrain = getTerrainLocation(newLoc);
		if (!newLocTerrain.fleets)
			newLocTerrain.fleets = {};
		newLocTerrain.fleets[fleetId] = true;
	}

	var movementCostInfo = getMovementCost_real(fleet, oldLoc, newLoc);
	var costOfMovement = movementCostInfo.delay;
	console.log("cost is " + Math.round(costOfMovement));

	if (movementCostInfo.crossedRiver)
		fleet.crossedRiver = movementCostInfo.crossedRiver;

	fleetMoved(fleetId, fleet, oldLoc, newLoc);
	discoverCell(fleet.map, Location.toCellId(newLoc), "full-sight");
	discoverCellBorder(fleet.map, Location.toCellId(newLoc), "full-sight");

	notifyPlayer(fleet.owner, {
		event: 'fleet-movement',
		fleet: fleetId,
		fromLocation: oldLoc,
		toLocation: newLoc,
		delay: Math.round(costOfMovement),
		inBattle: fleet.inBattle,
		inBattleGroup: fleet.inBattleGroup
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

function moveFleetAlongCoast(fleetId)
{
	var fleet = G.fleets[fleetId];
	var oldLoc = fleet.location;

	var nn = BE.geometry.getNeighbors(Location.toCellId(oldLoc));
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

	// moving fleet from oldLoc to targetLocation

	var map = getMap(fleetId, fleet);
	if (fleet.path && fleet.path.length >= 1)
	{
		// using memoized path

		var nextLoc = fleet.path.shift();
		if (isNavigableByMap(map, fleet, nextLoc))
			return moveFleetOneStep(fleetId, nextLoc);
		delete fleet.path;
	}

	// perform a shortest path search for the destination

	fleet.path = shortestPathByMap(map, fleet, oldLoc, targetLocation);

	// only memoize 50 steps at a time
	if (fleet.path.length > 50)
		fleet.path.splice(0,50);

	var nextLoc = fleet.path.shift();
	if (nextLoc && isNavigableByMap(map, fleet, nextLoc))
	{
		return moveFleetOneStep(fleetId, nextLoc);
	}
	else // our map does not tell us how to get to the destination
	{
		// rediscover the current and surrounding cells, just in case the problem
		// is lack of a map
		discoverCell(fleet.map, Location.toCellId(fleet.location), "full-sight");
		discoverCellBorder(fleet.map, Location.toCellId(fleet.location), "full-sight");

		// report the error
		return fleetActivityError(fleetId, fleet, "Cannot reach destination");
	}
}

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

		if (seen[curLoc])
			continue;
		seen[curLoc] = cur[1];

		if (curLoc == toLoc)
		{
			// success!
			return buildPath(toLoc);
		}
		else if (++countIterations > 500)
		{
			// failure...
			console.log("shortestPath taking too long, aborting");
			break;
		}

		var nn = BE.geometry.getNeighbors(curLoc);
		if (!baseDist)
			baseDist = BE.geometry.distanceBetween(curLoc, nn[0]);

		for (var i = 0, l = nn.length; i < l; i++)
		{
			if (seen[nn[i]])
				continue;

			var costInfo = getMovementCost_byMap(fleet, curLoc, nn[i], map);
			var accumDist = cur[2] + costInfo.delay;
			var estRemainDistSteps = nn[i] == toLoc ? 0 : BE.geometry.distanceBetween(nn[i], toLoc) / baseDist;
			var estRemainDist = estRemainDistSteps * 3000;

			Q.push([ nn[i], curLoc, accumDist, estRemainDist ]);
		}
		Q.sort(function(a,b) {

			return (a[2] + a[3]) - (b[2] + b[3]);
			});
	}

	// ran out of options to try
	if (bestSoFar)
		return buildPath(bestSoFar);
	else
		return [];
}

global.fleetMessage = fleetMessage;
global.fleetActivityError = fleetActivityError;
global.fleetCooldown = fleetCooldown;
global.setFleetActivityFlag = setFleetActivityFlag;
global.fleetCurrentCommandFinished = fleetCurrentCommandFinished;
global.fleetHasCapability = fleetHasCapability;
global.fleetActivity = fleetActivity;
global.fleetChanged = fleetChanged;
global.destroyFleet = destroyFleet;
global.getFleetInfoForPlayer = getFleetInfoForPlayer;

exports.isNavigableByMap = isNavigableByMap;
exports.getMovementCost_byMap = getMovementCost_byMap;
exports.getMovementCost_real = getMovementCost_real;
exports.maybeAdvertise = maybeAdvertise;
exports.getMap = getMap;
exports.moveFleetOneStep = moveFleetOneStep;
