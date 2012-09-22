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

	return battleId;
}

exports.newBattle = newBattle;
