// BATTLE DATA STRUCTURE
//  startTime - this is the year the battle was created
//  location - the location of the battle (cannot change)
//  groups.a - set of fleet ids, listing participants in the battle
//  groups.b - set of fleet ids, listing participants in the battle
//
// Backpointers to the Battle data structure exist
//   on each participating Fleet (fleet.inBattle)
//   on the terrain cell for the battle location
//

var Scheduler = require('./scheduler.js');

function newBattle(location, side1, side2)
{
	var groups = {
	a: side1,
	b: side2
	};

	var battleId = location;
	var battle = {
	startTime: Scheduler.time,
	location: location,
	groups: groups
	};
	G.battles[battleId] = battle;

	for (var fid in side1)
	{
		G.fleets[fid].inBattle = battleId;
		G.fleets[fid].inBattleGroup = 'a';
	}
	for (var fid in side2)
	{
		G.fleets[fid].inBattle = battleId;
		G.fleets[fid].inBattleGroup = 'b';
	}

	var terrain = getTerrainLocation(location);
	terrain.battle = battleId;

	fireBattleNotification(battleId,
		{
			event: 'battle-created',
			battle: battleId,
			location: location,
			groups: groups
		});

	battle._skirmishTimer = Scheduler.schedule(function() { onSkirmish(battleId); }, 2000);

	return battleId;
}

function reviveBattle(battleId, battle)
{
	if (!battle._skirmishTimer)
	{
		battle._skirmishTimer = Scheduler.schedule(
			function() { onSkirmish(battleId); }, 2000);
	}
}

function fireBattleNotification(battleId, eventData)
{
	var battle = G.battles[battleId];

	var recipients = allPlayersWhoCanSee(battle.location);
	for (var playerId in recipients)
	{
		notifyPlayer(playerId, eventData);
	}
}

function endBattle(battleId)
{
	var battle = G.battles[battleId];
	for (var side in battle.groups)
	{
		for (var fid in battle.groups[side])
		{
			delete G.fleets[fid].inBattle;
			delete G.fleets[fid].inBattleGroup;
		}
	}

	var terrain = getTerrainLocation(battle.location);
	delete terrain.battle;

	if (battle._skirmishTimer)
	{
		Scheduler.cancel(battle._skirmishTimer);
		delete battle._skirmishTimer;
	}

	delete G.battles[battleId];
	return;
}

function addFleet(battleId, fleetId, side)
{
	var battle = G.battles[battleId];
	var fleet = G.fleets[fleetId];

	if (fleet.inBattle && fleet.inBattle != battleId)
	{
		removeFleet(fleet.inBattle, fleetId, fleet.inBattleGroup);
		delete fleet.inBattle;
		delete fleet.inBattleGroup;
	}

	if (! battle.groups[side] )
		battle.groups[side] = {};
	battle.groups[side][fleetId] = true;

	fleet.inBattle = battleId;
	fleet.inBattleGroup = side;

	fireBattleNotification(battleId,
		{
			event: 'battle-updated',
			battle: battleId,
			location: battle.location,
			groups: battle.groups
		});
}

function removeFleet(battleId, fleetId, side)
{
	var battle = G.battles[battleId];
	delete battle.groups[side][fleetId];

	var fleet = G.fleets[fleetId];
	if (fleet)
	{
		delete fleet.inBattle;
		delete fleet.inBattleGroup;

		//TODO - notify owner of this fleet
		//fleetChanged(...)
	}

	// check whether that completes the battle
	var countSides = 0;
	var victorGroup = null;
	for (var side in battle.groups)
	{
		var count = 0;
		for (var fid in battle.groups[side])
		{
			count++;
		}

		if (count == 0)
		{
			delete battle.groups[side];
		}
		else
		{
			victorGroup = side;
			countSides++;
		}
	}

	if (countSides <= 1)
	{
		fireBattleNotification(battleId,
		{
			event: 'battle-terminated',
			battle: battleId,
			location: battle.location,
			victorsGroup: victorGroup,
			victors: battle.groups[victorGroup]
		});

		endBattle(battleId);
	}
	return;
}

function playerCanNowSee(battleId, playerId)
{
	var battle = G.battles[battleId];

	notifyPlayer(playerId,
		{
			event: 'battle-created',
			battle: battleId,
			location: location,
			groups: groups
		});
}

function playerCanNoLongerSee(battleId, playerId)
{
	var battle = G.battles[battleId];

	notifyPlayer(playerId, {
		event: 'battle-terminated',
		battle: battleId,
		location: battle.location,
		disposition: 'out-of-sight'
		});
}

function onSkirmish(battleId)
{
	var battle = G.battles[battleId];
	delete battle._skirmishTimer;

	// pick an attacker
	var candidates = [];
	for (var side in battle.groups)
	{
		for (var fid in battle.groups[side])
		{
			candidates.push({ id: fid, group: side });
		}
	}
	var pick = Math.floor(Math.random() * candidates.length);
	var attackerId = candidates[pick].id;
	var attackerSide = candidates[pick].group;

	// pick a defender
	candidates = [];
	for (var side in battle.groups)
	{
		if (side == attackerSide)
			continue;

		for (var fid in battle.groups[side])
		{
			candidates.push({ id: fid, group: side });
		}
	}
	var pick = Math.floor(Math.random() * candidates.length);
	var defenderId = candidates[pick].id;

	// TODO- deal out damage, etc.

	fireBattleNotification(battleId,
	{
		event: 'battle-attack',
		battle: battleId,
		location: battle.location,
		attacker: attackerId,
		attackerDamageSustained: 5,
		defender: defenderId,
		defenderDamageSustained: 10
	});

	battle._skirmishTimer = Scheduler.schedule(function() { onSkirmish(battleId); }, 5000);
}

exports.newBattle = newBattle;
exports.endBattle = endBattle;
exports.addFleet = addFleet;
exports.removeFleet = removeFleet;
exports.playerCanNowSee = playerCanNowSee;
exports.playerCanNoLongerSee = playerCanNoLongerSee;
exports.reviveBattle = reviveBattle;
