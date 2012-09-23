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
	G.battles[battleId] = {
	startTime: Scheduler.time,
	location: location,
	groups: groups
	};

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

	return battleId;
}

function fireBattleNotification(battleId, eventData)
{
	var battle = G.battles[battleId];

	var recipients = {};
	for (var side in battle.groups)
	{
		for (var fid in battle.groups[side])
		{
			var fleet = G.fleets[fid];
			if (fleet.owner)
				recipients[fleet.owner] = true;
		}
	}

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

	//TODO- check whether that completes the battle
}

exports.newBattle = newBattle;
exports.endBattle = endBattle;
exports.addFleet = addFleet;
exports.removeFleet = removeFleet;
