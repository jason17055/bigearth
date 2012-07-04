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
	var mapCellId = 'map/'+playerId+'/'+location;
	var isNew = false;

	// fetch the actual terrain and the player's map cell
	G.DB.get( [
		'terrain/'+location,
		mapCellId
		],
	function(err,res) {

		if (err)
		{
			console.log("DB ERROR", err);
			return;
		}

	// the first result is the actual terrain
	var refCell = res[0].doc;
	if (!refCell) {
		console.log("Oops, terrain "+location+" not found in DB");
		return;
	}

	// the second result (which may be {error:"not_found"}, is
	// what's on the player's map)

	var mapCell = res[1].doc;
	if (!mapCell)
	{
		mapCell = {};
		mapCell.terrain = refCell.terrain;
		isNew = true;
	}
	else if (mapCell.terrain != refCell.terrain)
	{
		mapCell.terrain = refCell.terrain;
		isNew = true;
	}

	if (isNew)
	{
console.log("posting "+mapCellId,mapCell);
		G.DB.save(mapCellId, mapCell, function(err1,res1) {});
		postEvent({
			event: 'map-update',
			location: location,
			locationType: 'cell',
			data: mapCell
			});
	}

	var nn = refCell.neighbors;
	for (var i = 0; i < nn.length; i++)
	{
		//if (map.cells[nn[i]-1])
		//{
			var eId = G.geometry._makeEdge(location, nn[i]);
			discoverEdge(playerId, eId);
		//}
	}

		}); //end of callback
}

function discoverEdge(playerId, eId)
{
	var mapEdgeId = 'map/'+playerId+'/'+eId;
	var isNew = false;

	// fetch the actual terrain and the player's map data
	G.DB.get( [
		'terrain/'+eId,
		mapEdgeId
		],
	function(err,res) {

		if (err)
		{
			console.log("DB ERROR", err);
			return;
		}

	// the first result is the actual terrain
	var refEdge = res[0].doc || {};

	// the second result (which may be {error:"not_found"}, is
	// what's on the player's map

	var mapEdge = res[1].doc;
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
		G.DB.save(mapEdgeId, mapEdge, function(err1,res1) {});
		postEvent({
			event: 'map-update',
			location: eId,
			locationType: 'edge',
			data: mapEdge
			});
	}
		}); //end of callback
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
	//broken
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
	G.DB.get('player/'+playerId, function(err,res) {

		if (res)
		{
			if (andThen) andThen();
		}


	G.DB.save('player/'+playerId, {
		type: 'player'
		}, function(err,res) {


	if (err) {
		console.log("Cannot create player " + playerId, err);
	}
	else {

		addExplorer(playerId, andThen);
	}
		});

		});
}

function addExplorer(playerId, andThen)
{
	var f = {
		owner: playerId,
		location: 2,
		type: 'explorer',
		orders: []
		};
	G.DB.save(f, function(err,res) {

		if (err)
		{
			console.log("DB ERROR", err);
			return;
		}

		var fid = res.id;
		discoverCell(playerId,f.location);
		discoverCellBorder(playerId,f.location);

		if (andThen) andThen();

		});
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
	var positionFromId = function(id) {
		if (id.match(/\/([^\/]+)$/))
			return RegExp.$1;
		else
			return id;
	};
	var pruneProperties = function(doc) {
		var x = {};
		for (var k in doc)
		{
			if (k.substr(0,1)!= '_')
				x[k]=doc[k];
		}
		return x;
	};
		

	G.DB.view('mapdata/byMap',
		{ key: (""+mapId) },
		function(err, res){

		if (err) {
		console.log("DB ERROR", err);
		return;
		}

		var result = {};
		var count = 0;
		res.forEach(function(row) {

			var location = positionFromId(row._id);
			result[location] = pruneProperties(row);
			count++;
		});

		callback(result);
	});
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
