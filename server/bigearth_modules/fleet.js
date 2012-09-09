var Scheduler = require('./scheduler.js');
var Location = require('../../html/location.js');

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
	else if (currentOrder.command == 'goto')
	{
		return moveFleetTowards(fleetId, currentOrder.location);
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
	delete G.fleets[fleetId];
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
		if (f.messages)
		{
			_fleet.messages = f.messages;
			_fleet.message = _fleet.messages[0].message;
		}

		if (fleetCanSettle(f))
		{
			_fleet.canSettle = true;
			_fleet.settlementFitness = Settler.getSettlementFitness(
				G.maps[f.owner], Location.toCellId(f.location));
		}

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

function fleetCanSettle(fleet)
{
	return fleet.type == 'settler';
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
