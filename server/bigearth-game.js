if (typeof require !== 'undefined')
{
//require('../html/trains-common.js');
}

var G = {
	map: {},
	players: {},
	nextPlayerId: 1
	};
G.players[1] = { primaryMap: { cells: [], edges: {}, vertices: {} } };

function discoverCell(playerId, location)
{
	var map = G.players[playerId].primaryMap;
	var isNew = false;

	G.DB.get('terrain/'+location, function(err,doc) {

		if (err)
		{
			console.log("ERROR: terrain cell " + location + " not found");
			return;
		}

	var refCell = doc;
	if (!map.cells[location-1])
	{
		map.cells[location-1] = {};
		map.cells[location-1].terrain = refCell.terrain;
		isNew = true;
	}
	else if (map.cells[location-1].terrain != refCell.terrain)
	{
		map.cells[location-1].terrain = refCell.terrain;
		isNew = true;
	}

	if (isNew)
	{
		postEvent({
			event: 'map-update',
			location: location,
			locationType: 'cell',
			data: map.cells[location-1]
			});
	}

	var nn = refCell.neighbors;
	for (var i = 0; i < nn.length; i++)
	{
		if (map.cells[nn[i]-1])
		{
			var eId = G.geometry._makeEdge(location, nn[i]);
			discoverEdge(playerId, eId);
		}
	}

		});
}

function discoverEdge(playerId, eId)
{
	var map = G.players[playerId].primaryMap;
	var isNew = false;

	if (!map.edges[eId])
	{
		G.DB.get('terrain/'+eId, function(err,doc) {

		var e = {};
		if (doc && doc.feature)
		{
			e.feature = doc.feature;
		}

		map.edges[eId] = e;
		postEvent({
			event: 'map-update',
			location: eId,
			locationType: 'edge',
			data: e
			});

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

function moveFleetTowards(fleetId, targetLocation)
{
	var fleet = G.fleets[fleetId];
	var oldLoc = fleet.location;

console.log("moving fleet "+fleetId+" from " + oldLoc + " to " + targetLocation);

	if (oldLoc == targetLocation)
	{
		return setFleetOrder(fleetId, "stop");
	}

	var nn = G.geometry.getNeighbors(oldLoc);
	var best = nn[0];
	var bestDist = Infinity;
	for (var i = 0, l = nn.length; i < l; i++)
	{
		var candidateLoc = nn[i];
		var d = G.geometry.distanceBetween(candidateLoc, targetLocation);
		if (d < bestDist)
		{
			best = candidateLoc;
			bestDist = d;
		}
	}
console.log("  chose " + best);

	return moveFleetOneStep(fleetId, best);
}

function moveFleetRandomly(fleetId)
{
	var fleet = G.fleets[fleetId];
	var oldLoc = fleet.location;
	var nn = G.geometry.getNeighbors(fleet.location);

	var map = G.players[1].primaryMap;
	var getTerrain = function(loc) {
		return map.cells[loc-1] ?
			map.cells[loc-1].terrain : null;
	};

	var candidates1 = [];
	var candidates2 = [];
	for (var i = 0; i < nn.length; i++)
	{
		if (getTerrain(oldLoc) == 'ocean'
			|| getTerrain(nn[i]) != 'ocean')
		{
			if (!fleet.recent[nn[i]])
				candidates1.push(nn[i]);
			candidates2.push(nn[i]);
		}
	}
	if (candidates1.length == 0)
	{
		fleet.recent = {};
		candidates1 = candidates2;
	}

	if (candidates1.length > 0)
	{
		var newLoc = candidates1[Math.floor(Math.random()*candidates1.length)];
		fleet.recent[newLoc] = true;
		moveFleetOneStep(fleetId, newLoc);
	}
}

function moveFleetOneStep(fleetId, newLoc)
{
	var fleet = G.fleets[fleetId];
	var oldLoc = fleet.location;
	fleet.location = newLoc;

	discoverCell(1, newLoc);
	discoverCellBorder(1, newLoc);
		
	postEvent({
		event: 'fleet-movement',
		fleet: fleetId,
		fromLocation: oldLoc,
		toLocation: newLoc
		});
	fleet.coolDownTimer = setTimeout(function() {
		fleet.coolDownTimer = null;
		fleetActivity(fleetId);
		}, 1200);
}

function newPlayer(playerId, andThen)
{
	G.DB.save('player/'+playerId, {
		type: 'player'
		}, function(err,res) {


	if (err) {
		console.log("Cannot create player " + playerId, err);
	}
	else {

		if (andThen) andThen();
	}
		});
}

function addExplorer(playerId)
{
	G.fleets = {};
	G.fleets[1] = {
		location: 1,
		type: 'explorer',
		recent: {}
		};
	discoverCell(1,1);
	discoverCellBorder(1,1);

	setFleetOrder(1, "wander");
}

function setFleetOrder(fleetId, newOrder, extraInfo)
{
	var fleet = G.fleets[fleetId];
	fleet.currentOrder = newOrder;
	if (newOrder == 'goto')
	{
		fleet.targetLocation = extraInfo.location;
	}
	fleetActivity(fleetId);
}

function fleetActivity(fleetId)
{
	var fleet = G.fleets[fleetId];
	if (!fleet) return;
	if (fleet.coolDownTimer) return;

	if (fleet.currentOrder == 'wander')
	{
		console.log("traveler moves!");
		return moveFleetRandomly(fleetId);
	}
	if (fleet.currentOrder == 'goto')
	{
		return moveFleetTowards(fleetId, fleet.targetLocation);
	}
}

function getGameState()
{
	var pp = {};
	for (var pid in G.players)
	{
		var P = G.players[pid];

		pp[pid] = {
		};
	}

	var ff = {};
	for (var fid in G.fleets)
	{
		var F = G.fleets[fid];

		ff[fid] = {
		location: F.location,
		type: F.type,
		currentOrder: F.currentOrder
		};
	}

	return {
	map: "/map/1",
	mapSize: G.globalMap.size,
	fleets: ff,
	players: pp
	};
}

function doExpose(requestData, remoteUser)
{
	console.log("in doExpose");

	if (requestData.cell)
	{
		discoverCell(1, requestData.cell);
	}
}

function doOrder(requestData, remoteUser)
{
	console.log("in doOrder");

	var fleetId = +(requestData.fleet);
	if (!G.fleets[fleetId])
		return;

	setFleetOrder(fleetId, requestData.order, requestData);
}

function getMapFragment(mapId, callback)
{
	var map = G.players[mapId].primaryMap;
	var result = {};
	for (var i = 1, l = G.geometry.getCellCount();
		i <= l; i++)
	{
		if (map.cells[i-1] && map.cells[i-1].terrain)
			result[i] = map.cells[i-1];
	}
	for (var eId in map.edges)
	{
		if (map.edges[eId] && map.edges[eId].feature)
			result[eId] = map.edges[eId];
	}
	callback(result);

//	G.DB.view('maps/byName',
//		{ key: mapId },
//		function(err, res){
//
//		var result = {};
//		res.forEach(function(row) {
//
//		result.push(row);
//		});
//
//		callback(result);
//	});
}
exports.getMapFragment = getMapFragment;


var actionHandlers = {
	expose: doExpose,
	order: doOrder
	};

if (typeof global !== 'undefined')
{
	global.G = G;
	global.getGameState = getGameState;
	global.actionHandlers = actionHandlers;
	global.addExplorer = addExplorer;
	global.newPlayer = newPlayer;
}
