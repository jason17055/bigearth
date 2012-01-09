var mapData;
var theTrain = null;
var mapFeatures = {};
var isBuilding = null;
var isPlanning = null;
var isEditing = null;
var curPlayer = {
	demands: new Array()
	};

// each cell has the following shape
//                        _
//            /\          
//           /  \          20px
//          /    \        _
//          |    |
//          |    |         36px
//          |    |        _
//          \    /        
//           \  /          20px
//            \/          _

var CELLS_PER_ROW = 1;
var CELL_WIDTH = 64;
var CELL_HEIGHT;
var CELL_ASCENT;
var CELL_DESCENT;
var MAP_ORIGIN_X;
var MAP_ORIGIN_Y;
function updateMapMetrics()
{
	CELL_HEIGHT = 2*Math.round(CELL_WIDTH*(56/64)/2);
	CELL_ASCENT = Math.round(CELL_HEIGHT * 36/56);
	CELL_DESCENT = CELL_HEIGHT - CELL_ASCENT;
}
updateMapMetrics();
MAP_ORIGIN_X = CELL_WIDTH/2;
MAP_ORIGIN_Y = CELL_ASCENT/2;

var terrainImages = {};
$(function() {
	var img = new Image();
	img.onload = function() {
			terrainImages.mountain = img;
			repaint();
		};
	img.src = "terrain_mountain.png";
});

function getPlayerId()
{
	if (location.hash)
	{
		return location.hash.substr(1);
	}
	return "1";
}

function getCellRow(cellIdx)
{
	return Math.floor(cellIdx / CELLS_PER_ROW);
}

function getCellColumn(cellIdx)
{
	return cellIdx % CELLS_PER_ROW;
}

function getCell(row, column)
{
	return row * CELLS_PER_ROW + column;
}

function isCellAdjacent(cell1, cell2)
{
	return cell2 == getAdjacentW(cell1) ||
		cell2 == getAdjacentNW(cell1) ||
		cell2 == getAdjacentNE(cell1) ||
		cell2 == getAdjacentE(cell1) ||
		cell2 == getAdjacentSE(cell1) ||
		cell2 == getAdjacentSW(cell1);
}

function getAdjacentCell(cellIdx, dir)
{
	switch (dir)
	{
	case 0: return getAdjacentW(cellIdx);
	case 1: return getAdjacentNW(cellIdx);
	case 2: return getAdjacentNE(cellIdx);
	case 3: return getAdjacentE(cellIdx);
	case 4: return getAdjacentSE(cellIdx);
	case 5: return getAdjacentSW(cellIdx);
	}
	return null;
}

function getAdjacentW(cellIdx)
{
	return cellIdx - 1;
}

function getAdjacentNW(cellIdx)
{
	var origRow = getCellRow(cellIdx);
	if (origRow % 2 == 0)
	{
		return cellIdx - CELLS_PER_ROW;
	}
	else
	{
		return cellIdx - CELLS_PER_ROW - 1;
	}
}

function getAdjacentNE(cellIdx)
{
	var origRow = getCellRow(cellIdx);
	if (origRow % 2 == 0)
	{
		return cellIdx - CELLS_PER_ROW + 1;
	}
	else
	{
		return cellIdx - CELLS_PER_ROW;
	}
}

function getAdjacentE(cellIdx)
{
	return cellIdx + 1;
}

function getAdjacentSE(cellIdx)
{
	var origRow = getCellRow(cellIdx);
	if (origRow % 2 == 0)
	{
		return cellIdx + CELLS_PER_ROW + 1;
	}
	else
	{
		return cellIdx + CELLS_PER_ROW;
	}
}

function getAdjacentSW(cellIdx)
{
	var origRow = getCellRow(cellIdx);
	if (origRow % 2 == 0)
	{
		return cellIdx + CELLS_PER_ROW;
	}
	else
	{
		return cellIdx + CELLS_PER_ROW - 1;
	}
}

function isCity(cellIdx)
{
	return mapData.cities[cellIdx];
}

// dir: 0 == west, 1 == northwest, 2 == northeast,
//      3 == east, 4 == southeast, 5 == southwest
//
function hasTrackAtDir(cellIdx, dir)
{
	var trackIdx = getTrackIndex(cellIdx, dir);
	if (mapData.rails[trackIdx] == getPlayerId())
		return 1;
	else if (isBuilding && isBuilding.rails[trackIdx])
		return 2;
	else if (mapData.rails[trackIdx])
		return 3;
	else
		return null;
}

function getTrackIndex(cellIdx, dir)
{
	if (dir == 3)
	{
		return getTrackIndex(getAdjacentE(cellIdx), 0);
	}
	else if (dir == 4)
	{
		return getTrackIndex(getAdjacentSE(cellIdx), 1);
	}
	else if (dir == 5)
	{
		return getTrackIndex(getAdjacentSW(cellIdx), 2);
	}
	else
	{
		return cellIdx * 3 + (dir + 1);
	}
}

function hasTrackAt(cellIdx)
{
	return hasTrackAtDir(cellIdx, 0) ||
		hasTrackAtDir(cellIdx, 1) ||
		hasTrackAtDir(cellIdx, 2) ||
		hasTrackAtDir(cellIdx, 3) ||
		hasTrackAtDir(cellIdx, 4) ||
		hasTrackAtDir(cellIdx, 5);
}

function drawCell(ctx, pt, c, w, nw, ne)
{
	var getColor = function(cc)
	{
		if (mapFeatures.hideTerrain)
		{
			return cc == "w" ? "#1155ff" : "#ffffff";
		}

		return cc == "." ? "#99dd55" :
			cc == "M" ? "#884400" :
			cc == "w" ? "#1155ff" :
			"#ffffff";
	};

	ctx.fillStyle = getColor(c);
	if (c != nw || c != ne)
	{
		ctx.beginPath();
		ctx.moveTo(pt.x, pt.y);
		ctx.lineTo(pt.x + CELL_WIDTH / 2, pt.y - CELL_DESCENT);
		ctx.lineTo(pt.x + CELL_WIDTH + 1, pt.y);
		ctx.lineTo(pt.x + CELL_WIDTH + 1, pt.y + CELL_HEIGHT + 1);
		ctx.lineTo(pt.x, pt.y + CELL_HEIGHT + 1);
		ctx.closePath();
		ctx.fill();
	}
	else
	{
		ctx.fillRect(
			pt.x, pt.y,
			CELL_WIDTH+1, CELL_HEIGHT+1
			);
	}

	if (c == "M" && terrainImages.mountain && !mapFeatures.hideTerrain
		&& CELL_WIDTH >= 24)
	{
		var imageSize = CELL_WIDTH * .8;
		ctx.drawImage(terrainImages.mountain,
			pt.x + CELL_WIDTH/2 - imageSize/2,
			pt.y + CELL_ASCENT/2 - imageSize/2,
			imageSize, imageSize);
	}
}

function drawRivers(ctx, pt, cellIdx)
{
	ctx.save();
	ctx.strokeStyle = '#1155ff';
	ctx.lineWidth =
		CELL_WIDTH >= 48 ? 5 :
		CELL_WIDTH >= 20 ? 3 :
		2;

	var drawRiverHelper = function()
	{
		ctx.beginPath();
		ctx.moveTo(0, -CELL_ASCENT / 2);
		ctx.lineTo(0, CELL_ASCENT / 2);
		ctx.stroke();
	};

	var t;
	if (t = mapData.rivers[cellIdx * 3]) //west
	{
		ctx.save();
		ctx.translate(pt.x, pt.y + CELL_ASCENT/2);
		drawRiverHelper(t);
		ctx.restore();
	}
	if (t = mapData.rivers[cellIdx * 3 + 1]) //northwest
	{
		ctx.save();
		ctx.translate(pt.x + CELL_WIDTH / 4, pt.y - CELL_DESCENT / 2);
		ctx.rotate(Math.PI / 3);
		drawRiverHelper(t);
		ctx.restore();
	}
	if (t = mapData.rivers[cellIdx * 3 + 2]) //northeast
	{
		ctx.save();
		ctx.translate(pt.x + 3 * CELL_WIDTH / 4, pt.y - CELL_DESCENT / 2);
		ctx.rotate(Math.PI * 2 / 3);
		drawRiverHelper(t);
		ctx.restore();
	}

	ctx.restore();
}

function drawRails(ctx, pt, cellIdx)
{
	var t;
	if (t = hasTrackAtDir(cellIdx, 0)) //West
	{
		if (trackVisible(getTrackIndex(cellIdx, 0)))
		{
		ctx.save();
		ctx.translate(pt.x, pt.y + CELL_ASCENT/2);
		drawRailsHelper(ctx, t);
		ctx.restore();
		}
	}
	if (t = hasTrackAtDir(cellIdx, 1)) //Northwest
	{
		if (trackVisible(getTrackIndex(cellIdx, 1)))
		{
		ctx.save();
		ctx.translate(pt.x + CELL_WIDTH / 4, pt.y - CELL_DESCENT / 2);
		ctx.rotate(Math.PI / 3);
		drawRailsHelper(ctx, t);
		ctx.restore();
		}
	}
	if (t = hasTrackAtDir(cellIdx, 2)) //Northeast
	{
		if (trackVisible(getTrackIndex(cellIdx, 2)))
		{
		ctx.save();
		ctx.translate(pt.x + 3 * CELL_WIDTH / 4, pt.y - CELL_DESCENT / 2);
		ctx.rotate(Math.PI * 2 / 3);
		drawRailsHelper(ctx, t);
		ctx.restore();
		}
	}
}

function drawRailsHelper(ctx, owner)
{
	var RAIL_WIDTH = CELL_WIDTH / 16;
	var TIE_LENGTH = CELL_WIDTH / 10;

	if (owner == 2)
	{
		ctx.strokeStyle = "#009999";
		ctx.lineWidth = 2;
	}
	else if (owner == 3)
	{
		ctx.strokeStyle = '#990099';
		ctx.lineWidth = 1;
	}
	else
	{
		ctx.strokeStyle = "#000000";
		ctx.lineWidth = 1;
	}

	if (CELL_WIDTH < 20)
	{
		ctx.lineWidth = 3;
		ctx.beginPath();
		ctx.moveTo(-CELL_WIDTH / 2, 0);
		ctx.lineTo(CELL_WIDTH / 2, 0);
		ctx.stroke();
		return;
	}

	ctx.beginPath();
	ctx.moveTo(-CELL_WIDTH / 2, -RAIL_WIDTH);
	ctx.lineTo(CELL_WIDTH / 2, -RAIL_WIDTH);
	ctx.stroke();
	ctx.beginPath();
	ctx.moveTo(-CELL_WIDTH / 2, RAIL_WIDTH);
	ctx.lineTo(CELL_WIDTH / 2, RAIL_WIDTH);
	ctx.stroke();
	for (var i = -2; i <= 2; i++)
	{
		ctx.beginPath();
		ctx.moveTo(CELL_WIDTH * i / 5, -TIE_LENGTH);
		ctx.lineTo(CELL_WIDTH * i / 5, TIE_LENGTH);
		ctx.stroke();
	}
}

function cityVisible(cityId)
{
	return !mapFeatures.filterCities ||
		mapFeatures.filterCities[cityId];
}

function cityColor(cityId)
{
	if (mapFeatures.highlightCities
		&& mapFeatures.highlightCities[cityId])
	{
		return "#ffff44";
	}
	else
	{
		return "#ff4444";
	}
}

function trackVisible(trackId)
{
	return !mapFeatures.filterTrack ||
		mapFeatures.filterTrack[trackId];
}

// pt: the desired *center* point of the dot, in screen coordinates
//
function drawCityDot(ctx, pt, cityId)
{
	ctx.fillStyle = cityColor(cityId);
	ctx.beginPath();
	ctx.arc(pt.x, pt.y, CELL_HEIGHT * .36, 0, Math.PI * 2, true);
	ctx.closePath();
	ctx.fill();
}

function repaint()
{
	var canvas = document.getElementById('theCanvas');
	var ctx = canvas.getContext('2d');

	ctx.fillStyle = "#ffffff";
	ctx.fillRect(0,0,canvas.width,canvas.height);

	if (!mapData)
		return;

	for (var y = 0; y < mapData.terrain.length; y++)
	{
		for (var x = 0; x < mapData.terrain[y].length; x++)
		{
			var c = mapData.terrain[y].charAt(x);
			var w = x > 0 ? mapData.terrain[y].charAt(x-1) : c;
			var n = y > 0 ? mapData.terrain[y-1].charAt(x) : c;
			var nw,ne;
			if (y % 2 == 0)
			{
				nw = n;
				ne = y > 0 && x+1 < mapData.terrain[y-1].length ?
					mapData.terrain[y-1].charAt(x+1) : n;
			}
			else
			{
				nw = y > 0 && x > 0 ? mapData.terrain[y-1].charAt(x-1) : n;
				ne = n;
			}

			var pt = {
				x: x * CELL_WIDTH + (y % 2 == 0 ? CELL_WIDTH / 2 : 0) - MAP_ORIGIN_X,
				y: y * CELL_HEIGHT - MAP_ORIGIN_Y
				};
			drawCell(ctx, pt, c, w, nw, ne);

			var cellIdx = getCell(y,x);
			drawRivers(ctx, pt, cellIdx);

			if (mapData.cities[cellIdx] && cityVisible(cellIdx))
			{
				drawCityDot(ctx, {
				x: pt.x + CELL_WIDTH / 2,
				y: pt.y + CELL_ASCENT / 2
				}, cellIdx);
			}

			drawRails(ctx, pt, cellIdx);
		}
	}

	ctx.save();
	ctx.fillStyle = "#333333";
	ctx.font = CELL_WIDTH >= 24 ?
		"24px sans-serif" :
		"16px sans-serif";
	for (var cityLoc in mapData.cities)
	{
		if (cityVisible(cityLoc))
		{
			var cityName = mapData.cities[cityLoc].name;
			var p = getCellPoint(cityLoc);
			ctx.fillText(cityName,
			p.x + Math.round(CELL_WIDTH/2 + CELL_HEIGHT*.36)-2,
			p.y + CELL_ASCENT);
		}
	}
	ctx.restore();
}

function onResize()
{
	var canvas = document.getElementById('theCanvas');
	canvas.width = window.innerWidth - 0;
	canvas.height = window.innerHeight - $('#buttonBar').outerHeight();

	$('#contentArea').css({
		width: canvas.width+"px",
		height: canvas.height+"px"
		});

	repaint();
}
window.onresize = onResize;
$(onResize);

function beginLoadMap(mapName)
{
	var onSuccess = function(data,status)
	{
		mapData = data;
		if (!mapData.rivers)
		{
			mapData.rivers = {};
		}
		if (!mapData.rails)
		{
			mapData.rails = {};
		}
		CELLS_PER_ROW = mapData.terrain[0].length;
		autoCreateDemands();
		zoomShowAll();
	};

	$.ajax({
	url: "maps/"+mapName+".txt",
	success: onSuccess,
	dataType: "json"
	});
}

var serverState;
function fetchGameState()
{
	var fetchBeginTime = new Date().getTime();

	var onSuccess = function(data,status)
	{
		var fetchEndTime = new Date().getTime();
		serverState = data;
		serverState.basisTime = fetchBeginTime +
			Math.round((fetchEndTime - fetchBeginTime) / 2) -
			serverState.serverTime;
		onGameState();
	};
	var onError = function(xhr, status, errorThrown)
	{
		//FIXME, report an error, I suppose
	};

	$.ajax({
		url: "/gamestate",
		success: onSuccess,
		error: onError,
		dataType: "json"
		});
}
$(fetchGameState);

function getGameTime()
{
	if (serverState)
		return new Date().getTime() - serverState.basisTime;
	else
		return 0;
}

var eventsListenerEnabled = false;
var nextEventId = null;

function onGameEvent(evt)
{
	if (evt.rails)
	{
		for (var i in evt.rails)
		{
			mapData.rails[i] = evt.rails[i];
		}
	}
	if (evt.playerMoney)
	{
		for (var pid in evt.playerMoney)
		{
			var p = serverState.players[pid];
			if (p)
			{
				var newBalance = evt.playerMoney[pid];
				p.money = newBalance;
			}
		}
	}

	onGameState();
}

function startEventsListener()
{
	var rdm = new Date().getTime();
	var errorCounter = 0;

	var fetchNextEvent;
	var onSuccess = function(data,status)
	{
		errorCounter = 0;
		onGameEvent(data);
		serverState.nextEvent = data.nextEvent;
		fetchNextEvent();
	};
	var onError = function(xhr, status, errorThrown)
	{
		errorCounter++;
		if (errorCounter < 3)
		{
			fetchNextEvent();
			return;
		}
		alert("fetchEvent error " + status + " " + errorThrown);
	};

	fetchNextEvent = function()
	{
		$.ajax({
		url: "/event/" + serverState.nextEvent + "?r="+rdm,
		success: onSuccess,
		error: onError,
		dataType: "json"
		});
	};

	fetchNextEvent();
	eventsListenerEnabled = true;
}

function onGameState()
{
	var firstLoad = (CELLS_PER_ROW == 1);

	if (serverState.map)
	{
		mapData = serverState.map;
		if (!mapData.rivers)
			mapData.rivers = {};
		if (!mapData.rails)
			mapData.rails = {};
		CELLS_PER_ROW = mapData.terrain[0].length;
	}
	if (mapData && serverState.rails)
		mapData.rails = serverState.rails;
	if (serverState.players)
	{
		var p = serverState.players[getPlayerId()];
		if (p)
		{
			if (p.demands)
			{
				curPlayer.demands = p.demands;
			}
		}
	}

	if (!eventsListenerEnabled)
	{
		startEventsListener();
	}

	if (firstLoad)
		zoomShowAll();
	else
		repaint();

	var pid = getPlayerId();
	var p = serverState.players[pid];
	if (p)
	{
		$('#cashIndicator').text(p.money);
	}
	else
	{
		$('#cashIndicator').text("");
	}
}

function getCellPoint(cellIdx)
{
	var x = getCellColumn(cellIdx);
	var y = getCellRow(cellIdx);

	return {
		x: (y % 2 == 0 ? CELL_WIDTH / 2 : 0) + CELL_WIDTH * x - MAP_ORIGIN_X,
		y: CELL_HEIGHT * y - MAP_ORIGIN_Y
		};
}

function getEdgeFromPoint(x, y)
{
	x += MAP_ORIGIN_X;
	y += MAP_ORIGIN_Y;

	var iy = Math.floor(y / CELL_HEIGHT);
	var ia = Math.floor(x / (CELL_WIDTH/2)) - (1 - iy % 2);

	var ry = y - iy * CELL_HEIGHT;
	if (ry < CELL_ASCENT)
	{
		var col = Math.floor((ia+1) / 2);
		return getCell(iy, col) * 3;
	}
	else if (ia % 2 == 0)
	{
		var col = Math.floor(ia / 2);
		return getAdjacentSW(getCell(iy, col)) * 3 + 2;
	}
	else
	{
		var col = Math.floor(ia / 2);
		return getAdjacentSE(getCell(iy, col)) * 3 + 1;
	}
}

function getCellFromPoint(x, y)
{
	x += MAP_ORIGIN_X;
	y += MAP_ORIGIN_Y;

	var iy = Math.floor(y / CELL_HEIGHT);
	var ix = Math.floor((x - (iy % 2 == 0 ? CELL_WIDTH/2 : 0)) / CELL_WIDTH);
	return getCell(iy, ix);
}

function updateWaypointSpritePosition(sprite)
{
	var pt = getCellPoint(sprite.loc);

	var $t = sprite.el;
	var p = $('#theCanvas').position();
	$t.css({
		left: (p.left + pt.x + CELL_WIDTH/2 - $t.outerWidth()/2) + "px",
		top: (p.top + pt.y + CELL_ASCENT/2 - $t.outerHeight()) + "px"
		});
}

function updateTrainSpritePosition(train)
{
	var pt = getCellPoint(train.loc);

	if (train.tick && train.route && train.route[0])
	{
		var pt1 = getCellPoint(train.route[0]);
		pt.x += (pt1.x - pt.x) * train.tick / 12.0;
		pt.y += (pt1.y - pt.y) * train.tick / 12.0;
	}

	var $t = train.el;
	var origin_pt = $('#theCanvas').position();
	$t.css({
		left: (origin_pt.left + pt.x + CELL_WIDTH/2 - 15) + "px",
		top: (origin_pt.top + pt.y + CELL_ASCENT/2 - 15) + "px"
		});
}

function train_cargoChanged(train)
{
	if (isPlanning && isPlanning.train == train)
	{
		reloadPlan();
	}
}

function nextDemand()
{
	if (mapData.futureDemands.length == 0)
	{
		mapData.futureDemands = mapData.pastDemands;
		mapData.pastDemands = new Array();
		shuffleArray(mapData.futureDemands);
	}

	if (mapData.futureDemands.length > 0)
	{
		var d = mapData.futureDemands.shift();
		curPlayer.demands.push(d);
	}
}

function train_deliver(train, resource_type)
{
	if (!train.cargo)
		return;

	var found = false;
	for (var i in train.cargo)
	{
		if (train.cargo[i] == resource_type)
		{
			train.cargo.splice(i,1);
			train_cargoChanged(train);
			found = true;
			break;
		}
	}

	if (found)
	{
		for (var i in curPlayer.demands)
		{
			var d = curPlayer.demands[i];
			if (d[0] == train.loc && d[1] == resource_type)
			{
				adjustPlayerCash(d[2]);
				curPlayer.demands.splice(i,1);
				mapData.pastDemands.push(d);
				nextDemand();
				break;
			}
		}
	}

	return;
}

function train_pickup(train, resource_type)
{
	if (!train.cargo)
		train.cargo = new Array();
	train.cargo.push(resource_type);

	train_cargoChanged(train);
	return;
}

// do whatever's next on the train's plan
function train_next(train)
{
	if (train.timer)
		return;
	if (!train.plan)
	{
		alert("unexpected: train has no plan");
		return stopTrain(train);
	}

	var p = train.plan[0];
	if (!p)
	{
		alert("unexpected: train has no plan for current location");
		return stopTrain(train);
	}

	reloadPlan();

	if (p.location == train.loc)
	{
		if (p.deliver && p.deliver.length)
		{
			train.timer = setTimeout(
				function()
				{
					delete train.timer;
					var resource_type = p.deliver.shift();
					train_deliver(train, resource_type);
					train_next(train);
				}, 500);
			return;
		}
		if (p.pickup && p.pickup.length)
		{
			train.timer = setTimeout(
				function()
				{
					delete train.timer;
					var resource_type = p.pickup.shift();
					train_pickup(train, resource_type);
					train_next(train);
				}, 500);
			return;
		}

		if (train.plan.length >= 2)
		{
			train.plan.shift();
			var oldSelection = $('#planPane').attr('selected-waypoint');
			if (oldSelection != null)
			{
				if (oldSelection >= 1)
				{
					$('#planPane').attr('selected-waypoint', oldSelection-1);
				}
				else
				{
					$('#planPane').attr('selected-waypoint', "");
					$('#waypointPane').fadeOut();
				}
			}
			return train_next(train);
		}
		else
		{
			return stopTrain(train);
		}
	}

	train.route = new Array();
	findBestPath(train.loc, p.location, train.route);
	animateTrain(train);
}

function onTrainLocationChanged(train)
{
	$('#planPane tr[waypoint-number=0] .waypointEta').text(
		train.route && train.route.length > 0 ? train.route.length
		: "");
}

function animateTrain(train)
{
	if (train.tick == null)
	{
		train.tick = 0;
	}
	else
	{
		train.tick++;
	}

	while (train.tick >= 12 && train.route.length >= 1)
	{
		train.loc = train.route.shift();
		onTrainLocationChanged(train);
		train.tick -= 12;
	}

	updateTrainSpritePosition(train);
	if (train.route.length >= 1)
	{
		train.timer =
		setTimeout(function() { animateTrain(train) }, 150);
	}
	else
	{
		delete train.timer;

		if (train.running)
			return train_next(train);
	}
}

var waypointSprites = {};
function addWaypointSprite(waypointId, loc)
{
	if (!waypointSprites[waypointId])
	{
		var $t = $('<div class="waypointSprite"></div>');
		$t.text(waypointId);
		$('#contentArea').append($t);

		waypointSprites[waypointId] = {
			id: waypointId,
			el: $t
			};
	}
	var sprite = waypointSprites[waypointId];
	sprite.loc = loc;
	updateWaypointSpritePosition(sprite);
}

function removeWaypointSprite(waypointId)
{
	if (waypointSprites[waypointId])
	{
		waypointSprites[waypointId].el.remove();
		delete waypointSprites[waypointId];
	}
}

function addTrainSprite(trainLoc)
{
	var $t = $('<div class="trainSprite">T</div>');
	$t.css({
		backgroundColor: "#ffff00"
	});
	$('#contentArea').append($t);

	var train = {
		el: $t,
		loc: trainLoc,
		plan: new Array(),
		route: new Array(),
		brandNew: true
		};
	updateTrainSpritePosition(train);
	updateTrainSpriteSensitivity(train);

	return train;
}

function updateTrainSpriteSensitivity(train)
{
	if (isBuilding)
	{
		train.el.get(0).onclick = null;
	}
	else
	{
		train.el.get(0).onclick = function() { onTrainClicked(train); };
	}
}

var isDragging = null;
var isPanning = null;
function onMouseDown(evt)
{
	if (evt.which != 1) return;

	evt.preventDefault();
	onTouchStart(evt);
}

function onTouchStart(evt)
{
	var p = $('#theCanvas').position();
	var pt = {
		x: evt.clientX - p.left,
		y: evt.clientY - p.top
		};
	var cellIdx = getCellFromPoint(pt.x, pt.y);

	if (cellIdx == 0)
		return;
	var cellP = getCellPoint(cellIdx);

	if (isCity(cellIdx) && isPlanning)
	{
		addCityToPlan(cellIdx);
		return;
	}

	var canBuild = false;
	if (isCity(cellIdx) || hasTrackAt(cellIdx))
		canBuild = true;

	if (isBuilding && canBuild)
	{
		onMouseDown_build(cellIdx);
		return;
	}

	if (isEditing && getRadioButtonValue(document.editMapForm.tool) != "hand")
	{
		onMouseDown_editTerrain(cellIdx, pt);
		return;
	}

	// begin pan of whole map
	isPanning = {
		originX: pt.x,
		originY: pt.y
		};
	return;
}

function getRadioButtonValue(radioObj)
{
	var l = radioObj.length;
	for (var i = 0; i < l; i++)
	{
		if (radioObj[i].checked)
			return radioObj[i].value;
	}
}

function onMouseDown_editTerrain(cellIdx, oPt)
{
	var r = getCellRow(cellIdx);
	var col = getCellColumn(cellIdx);

	var t = getRadioButtonValue(document.editMapForm.tool);
	if (t == "city")
	{
		showEditCityPane(cellIdx);
		return;
	}
	else if (t == "rivers")
	{
		var edgeIdx = getEdgeFromPoint(oPt.x, oPt.y);
		if (mapData.rivers[edgeIdx])
			delete mapData.rivers[edgeIdx];
		else
			mapData.rivers[edgeIdx] = 1;
		repaint();
		return;
	}

	var c = t == "grass" ? "." :
		t == "mountain" ? "M" :
		t == "sea" ? "w" :
		" ";

	var s = mapData.terrain[r];
	mapData.terrain[r] = s.substr(0,col) + c + s.substr(col+1);
	repaint();
}

function onMouseDown_build(cellIdx)
{
	var pt = getCellPoint(cellIdx);
	var canvas = document.getElementById('theCanvas');
	var ctx = canvas.getContext('2d');
	ctx.beginPath();
	ctx.fillStyle = '#009999';
	ctx.arc(pt.x + CELL_WIDTH / 2, pt.y + CELL_ASCENT / 2, 6, 0, Math.PI * 2, true);
	ctx.closePath();
	ctx.fill();

	isDragging = {
	start: cellIdx
	};
	isBuilding.curSegmentCount = 0;
	isBuilding.erasing = false;
}

function onMouseUp(evt)
{
	if (evt.which != 1) return;
	onTouchEnd(evt);
}

function onTouchEnd(evt)
{
	if (isBuilding)
	{
		if (evt.preventDefault) { evt.preventDefault(); }
		repaint();
		isDragging = null;
	}
	if (isPanning)
	{
		if (evt.preventDefault) { evt.preventDefault(); }
		repaint();
		isPanning = null;
	}
}

function onMouseMove(evt)
{
	var p = $('#theCanvas').position();
	var pt = {
		x: evt.clientX - p.left,
		y: evt.clientY - p.top
		};

	if (isDragging)
	{
		var cellIdx = getCellFromPoint(pt.x, pt.y);
		var cellPt = getCellPoint(cellIdx);
		var cellCenterPt = {
			x: cellPt.x + CELL_WIDTH / 2,
			y: cellPt.y + CELL_ASCENT / 2
			};

		if (cellIdx != isDragging.start
			&& Math.abs(cellCenterPt.x - pt.x) < 16
			&& Math.abs(cellCenterPt.y - pt.y) < 16
			&& isCellAdjacent(isDragging.start, cellIdx))
		{
			track_addSegment(isDragging.start, cellIdx);
			isDragging.start = cellIdx;
		}
	}
	else if (isPanning)
	{
		var dx = pt.x - isPanning.originX;
		var dy = pt.y - isPanning.originY;

		var canvas = document.getElementById('theCanvas');
		var rect = {
			left: 0 + (dx < 0 ? -dx : 0),
			right: canvas.width + (dx > 0 ? -dx : 0),
			top: 0 + (dy < 0 ? -dy : 0),
			bottom: canvas.height + (dy > 0 ? -dy : 0)
			};

		var ctx = canvas.getContext('2d');
		ctx.drawImage(canvas, rect.left, rect.top,
			(rect.right-rect.left), (rect.bottom-rect.top),
			rect.left + dx, rect.top + dy,
			(rect.right-rect.left), (rect.bottom-rect.top)
			);

		MAP_ORIGIN_X -= dx;
		MAP_ORIGIN_Y -= dy;
		updateAllSpritePositions();

		isPanning.originX = pt.x;
		isPanning.originY = pt.y;
	}
}

function track_addSegment(fromIdx, toIdx)
{
	// is this track already in the plan?
	var cellIdx;
	var dir;
	if (fromIdx == getAdjacentW(toIdx))
	{
		cellIdx = toIdx;
		dir = 0;
	}
	else if (fromIdx == getAdjacentNW(toIdx))
	{
		cellIdx = toIdx;
		dir = 1;
	}
	else if (fromIdx == getAdjacentNE(toIdx))
	{
		cellIdx = toIdx;
		dir = 2;
	}
	else if (fromIdx == getAdjacentE(toIdx))
	{
		cellIdx = fromIdx;
		dir = 0;
	}
	else if (fromIdx == getAdjacentSE(toIdx))
	{
		cellIdx = fromIdx;
		dir = 1;
	}
	else if (fromIdx == getAdjacentSW(toIdx))
	{
		cellIdx = fromIdx;
		dir = 2;
	}
	else
	{
		return;
	}
	var trackIdx = cellIdx * 3 + dir + 1;

	// check if this track is already build
	if (mapData.rails[trackIdx])
		return;

	// check if this track is already in the plan
	if (isBuilding.rails[trackIdx] && isBuilding.curSegmentCount == 0)
	{
		// start erasing
		isBuilding.erasing = true;
	}

	var canvas = document.getElementById('theCanvas');
	var ctx = canvas.getContext('2d');

	var p0 = getCellPoint(fromIdx);
	var p1 = getCellPoint(toIdx);

	ctx.save();
	ctx.beginPath();
	ctx.strokeStyle = isBuilding.erasing ? '#ffffff' : '#009999';
	ctx.lineWidth = 4;
	ctx.moveTo(p0.x + CELL_WIDTH/2, p0.y + CELL_ASCENT/2);
	ctx.lineTo(p1.x + CELL_WIDTH/2, p1.y + CELL_ASCENT/2);
	ctx.stroke();
	ctx.restore();

	if (isBuilding.erasing)
	{
		delete isBuilding.rails[trackIdx];
	}
	else
	{
		isBuilding.rails[trackIdx] = true;
		isBuilding.curSegmentCount++;
	}

	updateBuildingCost();
}

function updateBuildingCost()
{
	if (!isBuilding)
		return;
	if (!isBuilding.rails)
		return;

	var cost = 0;
	for (var i in isBuilding.rails)
	{
		var trackIdx = isBuilding.rails[i];
		cost += 1;
	}

	$('#buildTrackCost').text(cost);
}

function onMouseWheel(evt)
{
	var pt = {
		x: evt.clientX,
		y: evt.clientY
		};

	if (evt.detail)
	{
		if (evt.detail > 0)
			zoomOut(pt);
		else
			zoomIn(pt);
	}
}

function onTouchStart_r(evt)
{
	evt.preventDefault();
	if (evt.touches && evt.touches.length == 1)
	{
		onTouchStart({
		clientX: evt.touches[0].clientX,
		clientY: evt.touches[0].clientY
		});
	}
}

function onTouchMove_r(evt)
{
	if (evt.touches && evt.touches.length == 1)
	{
		onMouseMove({
		which: 1,
		clientX: evt.touches[0].clientX,
		clientY: evt.touches[0].clientY
		});
	}
}

function onTouchEnd_r(evt)
{
	if (evt.touches && evt.touches.length == 0)
	{
		onTouchEnd({
		which: 1
		});
	}
}

$(function() {
	document.getElementById('theCanvas').addEventListener('mousedown', onMouseDown, false);
	$(document).mouseup(onMouseUp);
	$(document).mousemove(onMouseMove);
	document.getElementById('theCanvas').addEventListener('DOMMouseScroll',
			onMouseWheel, false);

	document.getElementById('theCanvas').addEventListener('touchstart', onTouchStart_r, false);
	document.addEventListener('touchmove', onTouchMove_r, false);
	document.addEventListener('touchend', onTouchEnd_r, false);
});

function setZoomLevel(w, basisPt)
{
	if (!basisPt)
	{
		var canvas = document.getElementById('theCanvas');
		basisPt = {
		x: canvas.width / 2,
		y: canvas.height / 2
		};
	}

	var newCellWidth = 2 * Math.round(w/2);
	if (newCellWidth > 64)
		newCellWidth = 64;
	if (newCellWidth < 12)
		newCellWidth = 12;

	var relX = (basisPt.x + MAP_ORIGIN_X) / CELL_WIDTH;
	var relY = (basisPt.y + MAP_ORIGIN_Y) / CELL_WIDTH;

	CELL_WIDTH = newCellWidth;
	MAP_ORIGIN_X = relX * CELL_WIDTH - basisPt.x;
	MAP_ORIGIN_Y = relY * CELL_WIDTH - basisPt.y;

	updateMapMetrics();
	repaint();
	updateAllSpritePositions();
}

function zoomIn(basisPt)
{
	setZoomLevel(CELL_WIDTH * 4/3, basisPt);
}

function zoomOut(basisPt)
{
	setZoomLevel(CELL_WIDTH * 3/4, basisPt);
}

function zoomShowAll()
{
	var canvas = document.getElementById('theCanvas');
	var cw1 = canvas.width / CELLS_PER_ROW;
	var cw2 = (canvas.height / mapData.terrain.length) * 64/56;

	CELL_WIDTH = 2*Math.round((cw1 < cw2 ? cw1 : cw2) / 2);
	updateMapMetrics();
	MAP_ORIGIN_X = CELL_WIDTH/2;
	MAP_ORIGIN_Y = CELL_ASCENT/2;
	repaint();
	updateAllSpritePositions();
}

function beginBuilding()
{
	isBuilding = {
	rails: {}
	};
	updateBuildingCost();
	$('#buildTrackInfo').fadeIn();

	if (theTrain)
	{
		updateTrainSpriteSensitivity(theTrain);
	}
}

function adjustPlayerCash(delta)
{
	var money = parseFloat($('#cashIndicator').text());
	money += delta;
	$('#cashIndicator').text(money);
}

function sendRequest(verb, data)
{
	var onSuccess = function()
	{
	};
	var onError = function(xhr, status, errorThrown)
	{
		alert("request error " + errorThrown);
	};

	$.ajax({
	type: "POST",
	url: ("/request/" + verb),
	data: data,
	success: onSuccess,
	error: onError,
	dataType: "json"
	});
}

function commitBuilding()
{
	if (!isBuilding)
		return;

	var cost = parseFloat($('#buildTrackCost').text());
	var money = parseFloat($('#cashIndicator').text());
	money -= cost;

	if (money < 0)
	{
		alert("You do not have enough money.");
		return;
	}

	var a = new Array();
	for (var i in isBuilding.rails)
	{
		a.push(i);
	}

	sendRequest("build",
		{
		player: getPlayerId(),
		cost: cost,
		rails: a.join(' ')
		});

	isBuilding = null;
	$('#buildTrackInfo').fadeOut();

	if (theTrain)
	{
		updateTrainSpriteSensitivity(theTrain);
	}

	repaint();
}

function abandonBuilding()
{
	if (!isBuilding)
		return;

	isBuilding = null;
	$('#buildTrackInfo').fadeOut();

	if (theTrain)
	{
		updateTrainSpriteSensitivity(theTrain);
	}

	repaint();
}

function fixWidgetDimensions($widget)
{
	var h = $widget.innerHeight();
	h -= $('.widgetHeader', $widget).outerHeight();
	h -= $('.widgetFooter', $widget).outerHeight();
	$('.widgetContent', $widget).css('height', h + "px");
}

function onTrainClicked(train)
{
	showPlan(train);
}

function addLocomotive()
{
	showPlan(theTrain);
}

function showPlan(train)
{
	if (isPlanning && isPlanning.train == train)
		return;

	isPlanning = {
		train: train
		};
	reloadPlan();
	$('#planPane').fadeIn();

	if (train)
	{
		// update map for planning
		mapFeatures.hideTerrain = true;
		filterMapToReachable(isPlanning.train);
		repaint();
	}
	else
	{
		$('#brandNewInstructions').show();
	}

	var $widget = $('#planPane');
	fixWidgetDimensions($widget);
}

function dismissPlan()
{
	if (!isPlanning)
		return;

	if (isPlanning.train && isPlanning.train.brandNew)
	{
		isPlanning.train.el.remove();
		delete isPlanning.train;
		theTrain = null;
	}

	isPlanning = null;
	mapFeatures.hideTerrain = false;
	delete mapFeatures.filterCities;
	delete mapFeatures.filterTrack;
	repaint();

	$('#planPane').fadeOut();
	$('#waypointPane').fadeOut();
}

function filterMapToReachable(train)
{
	var queue = new Array();
	queue.push(train.loc);

	var visited = {};
	var reachableCities = {};
	var reachableTrack = {};

	while (queue.length)
	{
		var l = queue.pop();
		visited[l] = true;

		if (mapData.cities[l])
		{
			reachableCities[l] = true;
		}

		for (var dir = 0; dir < 6; dir++)
		{
			var ti = getTrackIndex(l, dir);
			if (mapData.rails[ti])
			{
				reachableTrack[ti] = true;
				var adjCellIdx = getAdjacentCell(l, dir);
				if (adjCellIdx && !visited[adjCellIdx])
				{
					queue.push(adjCellIdx);
				}
			}
		}
	}

	mapFeatures.filterCities = reachableCities;
	mapFeatures.filterTrack = reachableTrack;
}

function addCityToPlan(cellIdx)
{
	if (!isPlanning)
		return;

	if (!isPlanning.train)
	{
		// player is designating their starting location
		theTrain = addTrainSprite(cellIdx);
		isPlanning.train = theTrain;

		// update map for planning
		mapFeatures.hideTerrain = true;
		filterMapToReachable(isPlanning.train);
		repaint();

		// now that a starting point is being selected,
		// we can remove the "Click city to set this starting point"
		// instructions.
		$('#brandNewInstructions').hide();
	}

	var priorWaypoint = null;
	if (isPlanning.train.plan.length > 0)
	{
		priorWaypoint = isPlanning.train.plan[isPlanning.train.plan.length - 1];
	}

	if (priorWaypoint && priorWaypoint.location == cellIdx)
	{
		// user clicked the city that's the last waypoint
		selectWaypoint(isPlanning.train.plan.length - 1);
		return;
	}

	var waypoint = {
		class: "waypoint",
		location: cellIdx
		};
	if (priorWaypoint)
	{
		var r = new Array();
		findBestPath(priorWaypoint.location, waypoint.location, r);
		waypoint.distanceHint = r.length;
	}

        isPlanning.train.plan.push(waypoint);
	reloadPlan();
	selectWaypoint(isPlanning.train.plan.length-1);
}

function updateAllSpritePositions()
{
	if (theTrain)
	{
		updateTrainSpritePosition(theTrain);
	}
	for (var i in waypointSprites)
	{
		updateWaypointSpritePosition(waypointSprites[i]);
	}
}

function selectWaypoint(newSelection)
{
	var oldSelection = $('#planPane').attr('selected-waypoint');
	if (oldSelection)
	{
		$('#planPane tr[waypoint-number='+oldSelection+']').removeClass('selected');
	}

	$('#planPane tr[waypoint-number='+newSelection+']').addClass('selected');
	$('#planPane').attr('selected-waypoint', newSelection);

	var waypoint = isPlanning.train.plan[newSelection];

	var cityName = mapData.cities[waypoint.location].name;
	$('#waypointPane .widgetHeader').text(cityName);

	reloadWaypoint(waypoint);

	$('#waypointPane').fadeIn();
	var $pw = $('#planPane');
	$('#waypointPane').css({
		top: ($pw.position().top + $pw.outerHeight() + 10) + "px"
		});
	fixWidgetDimensions($('#waypointPane'));
}

function getTrainManifestAtWaypoint(train, targetWaypoint)
{
	if (!train)
	{
		train = isPlanning.train;
	}

	var found = new Array();
	for (var i in train.cargo)
	{
		found.push(train.cargo[i]);
	}

	for (var planIdx in train.plan)
	{
		var waypoint = train.plan[planIdx];

		if (waypoint.deliver)
		{
			for (var i in waypoint.deliver)
			{
				var rsrc = waypoint.deliver[i];
				for (var j in found)
				{
					if (found[j] == rsrc)
					{
						found.splice(j,1);
						break;
					}
				}
			}
		}

		if (waypoint == targetWaypoint)
			break;

		if (waypoint.pickup)
		{
			for (var i in waypoint.pickup)
			{
				found.push(waypoint.pickup[i]);
			}
		}
	}
	return found;
}

function reloadWaypoint(waypoint)
{
	$('#waypointPane .insertedRow').remove();

	var deliverHere = waypoint.deliver;
	if (deliverHere && deliverHere.length > 0)
	{
		$('#deliverHeader').show();
		for (var i in deliverHere)
		{
			var resource_type = deliverHere[i];
			var $row = $('#deliverTemplate').clone();

			$row.addClass('insertedRow');
			$('img.resource_icon', $row).attr('src', 'resource_icons/' + resource_type + '.png');
			$('.resource_name', $row).text(resource_type);

			var deliverReward = 0;
			for (var j in curPlayer.demands)
			{
				var dem = curPlayer.demands[j];
				if (dem[1] == resource_type)
				{
					if (dem[0] == waypoint.location)
					{
						deliverReward = dem[2];
					}
				}
			}
			$('.wantedInfo', $row).text("Deliver for $" + deliverReward);

			$('#deliverTemplate').before($row);
			$row.show();

			with({ index: i })
			{
				$('button', $row).click(function() {
					waypoint.deliver.splice(index,1);
					reloadWaypoint(waypoint);
					reloadPlan();
					});
			}
		}
	}
	else
	{
		$('#deliverHeader').hide();
	}

	var keepHere = getTrainManifestAtWaypoint(null, waypoint);
	if (keepHere.length > 0)
	{
		$('#keepHeader').show();
		for (var i in keepHere)
		{
			var resource_type = keepHere[i];
			var $row = $('#keepTemplate').clone();

			$row.addClass('insertedRow');
			$('img.resource_icon', $row).attr('src', 'resource_icons/' + resource_type + '.png');
			$('.resource_name', $row).text(resource_type);

			var wantedPlaces = new Array();
			var deliverReward = 0;
			for (var j in curPlayer.demands)
			{
				var dem = curPlayer.demands[j];
				if (dem[1] == resource_type)
				{
					wantedPlaces.push(mapData.cities[dem[0]].name);
					if (dem[0] == waypoint.location)
					{
						deliverReward = dem[2];
					}
				}
			}
			$('.wantedInfo', $row).text(wantedPlaces.length > 0 ?
				"Wanted in " + wantedPlaces.join(', ') :
				"");

			if (deliverReward > 0)
			{
				$('button', $row).text("Deliver for $" + deliverReward);
			}
			else
			{
				$('button', $row).text("Abandon here");
			}

			$('#keepTemplate').before($row);
			$row.show();

			with({ resource_type: resource_type })
			{
				$('button', $row).click(function() {
					if (!waypoint.deliver)
						waypoint.deliver = new Array();
					waypoint.deliver.push(resource_type);
					reloadWaypoint(waypoint);
					reloadPlan();
					});
			}
		}
	}
	else
	{
		$('#keepHeader').hide();
	}

	var pickupHere = waypoint.pickup;
	if (pickupHere && pickupHere.length > 0)
	{
		$('#pickupHeader').show();
		for (var i in pickupHere)
		{
			var resource_type = pickupHere[i];
			var $row = $('#pickupTemplate').clone();

			$row.addClass('insertedRow');
			$('img.resource_icon', $row).attr('src', 'resource_icons/' + resource_type + '.png');
			$('.resource_name', $row).text(resource_type);

			var wantedPlaces = new Array();
			for (var j in curPlayer.demands)
			{
				var dem = curPlayer.demands[j];
				if (dem[1] == resource_type)
				{
					wantedPlaces.push(mapData.cities[dem[0]].name);
				}
			}
			$('.wantedInfo', $row).text(wantedPlaces.length > 0 ?
				"Wanted in " + wantedPlaces.join(', ') :
				"");

			$('#pickupTemplate').before($row);
			$row.show();

			with({ index: i })
			{
				$('button', $row).click(function() {
					waypoint.pickup.splice(index,1);
					reloadWaypoint(waypoint);
					reloadPlan();
					});
			}
		}
	}
	else
	{
		$('#pickupHeader').hide();
	}
	
	var availableHere = mapData.cities[waypoint.location].offers;
	if (availableHere && availableHere.length > 0)
	{
		$('#availableHeader').show();
		for (var i in availableHere)
		{
			var resource_type = availableHere[i];
			var $row = $('#availableTemplate').clone();
	
			$row.addClass('insertedRow');
			$('img.resource_icon', $row).attr('src', 'resource_icons/' + resource_type + '.png');
			$('.resource_name', $row).text(resource_type);

			var wantedPlaces = new Array();
			for (var j in curPlayer.demands)
			{
				var dem = curPlayer.demands[j];
				if (dem[1] == resource_type)
				{
					wantedPlaces.push(mapData.cities[dem[0]].name);
				}
			}
			$('.wantedInfo', $row).text(wantedPlaces.length > 0 ?
				"Wanted in " + wantedPlaces.join(', ') :
				"");

			$('#availableTemplate').before($row);
			$row.show();

			with({ resource_type: resource_type })
			{
				$('button', $row).click(function() {
					if (!waypoint.pickup)
					{
						waypoint.pickup = new Array();
					}
					waypoint.pickup.push(resource_type);
					reloadWaypoint(waypoint);
					reloadPlan();
					});
			}
		}
	}
	else
	{
		$('#availableHeader').hide();
	}
}

function selectDemand($row)
{
	var oldDemand = $('#demandsPane').attr('selected-demand');
	if (oldDemand)
	{
		$('.aDemand[demand-index='+oldDemand+']').removeClass('selected');
	}

	$row.addClass('selected');
	$('#demandsPane').attr('selected-demand', $row.attr('demand-index'));

	// show only cities that have this resource
	var filteredCities = {};
	mapFeatures.highlightCities = {};

	var demand = curPlayer.demands[$row.attr('demand-index')-1];
	if (demand)
	{
		filteredCities[demand[0]] = true;
		mapFeatures.highlightCities[demand[0]] = true;
		for (var i in mapData.cities)
		{
			var city = mapData.cities[i];
			var foundResource = false;
			for (var j in city.offers)
			{
				if (city.offers[j] == demand[1])
					foundResource = true;
			}
			if (foundResource)
				filteredCities[i] = true;
		}
	}

	mapFeatures.filterCities = filteredCities;
	repaint();
}

function reloadPlan()
{
	$('#trainCargo').empty();
	$('#planPane .insertedRow').remove();

	var train = isPlanning && isPlanning.train;
	if (!train)
		return;

	//
	// train cargo
	//
	if (train.cargo && train.cargo.length)
	{
		for (var i in train.cargo)
		{
			var c = train.cargo[i];
			var $el = $('<img>');
			$el.attr('src', 'resource_icons/'+c+'.png');
			$('#trainCargo').append($el);
		}
	}

	//
	// train waypoints
	//
	var selWaypoint = $('#planPane').attr('selected-waypoint');
	var count = 0;
	var atFirstWaypoint = train.plan[0] && train.loc == train.plan[0].location;
	var usedSprites = {};
	for (var waypointNumber in train.plan)
	{
		var p = train.plan[waypointNumber];
		var waypointLabel =
			waypointNumber == 0 && atFirstWaypoint ? "At" :
			atFirstWaypoint ? parseInt(waypointNumber) :
			parseInt(waypointNumber)+1
			;

		count++;

		var $row = $('#aWaypointTemplate').clone();
		$row.attr('waypoint-number', waypointNumber);
		$row.addClass('insertedRow');
		$row.addClass(count % 2 == 0 ? 'evenRow' : 'oddRow');
		if (selWaypoint != null && selWaypoint == waypointNumber)
		{
			$row.addClass('selected');
		}
		$('.waypointNumber', $row).text(waypointLabel);
		$('.waypointCity', $row).text(
			mapData.cities[p.location].name
			);
		$('.waypointEta', $row).text(
			waypointNumber == 0 && p.location == train.loc ? "" :
			waypointNumber == 0 && train.route.length > 0 ? train.route.length :
			p.distanceHint);

		var onClick;
		with ({ waypointNumber: waypointNumber })
		{
			onClick = function() { selectWaypoint(waypointNumber); };
		}
		$row.click(onClick);
		$row.show();
		$('#planPane table').append($row);

		if (p.location != train.loc)
		{
			usedSprites[waypointLabel] = true;
			addWaypointSprite(waypointLabel, p.location);
		}

		if ((p.pickup && p.pickup.length > 0)
			|| (p.deliver && p.deliver.length > 0))
		{
			var $row = $('#aManifestTemplate').clone();
			$row.attr('waypoint-number', waypointNumber);
			$row.addClass('insertedRow');
			$row.addClass(count % 2 == 0 ? 'evenRow' : 'oddRow');
			$('.pickupSummary', $row).text(p.pickup && p.pickup.length > 0
				? "Pick up " + p.pickup.join(', ') : "");
			$('.deliverSummary', $row).text(p.deliver && p.deliver.length > 0 ?
				"Deliver " + p.deliver.join(', ') : "");
			$row.click(onClick);
			$row.show();
			$('#planPane table').append($row);
		}
	}
	for (var waypointLabel in waypointSprites)
	{
		if (!usedSprites[waypointLabel])
		{
			removeWaypointSprite(waypointLabel);
		}
	}
}

function showDemands()
{
	$('#demandsPane .insertedRow').remove();
	var count = 0;
	for (var i in curPlayer.demands)
	{
		count++;

		var demand = curPlayer.demands[i];
		var $row = $('#demandsPaneTableTemplate').clone();
		$row.attr('demand-index', parseInt(i) + 1);
		$row.addClass('insertedRow');
		$row.addClass(count % 2 == 0 ? 'evenRow' : 'oddRow');
		$('img', $row).attr('src', 'resource_icons/' + demand[1] + '.png');
		$('img', $row).attr('alt', demand[1]);
		$('.cityName', $row).text(mapData.cities[demand[0]].name);
		$('.amount', $row).text(demand[2]);
		$('#demandsPane table').append($row);
		$row.hover(
			function() { $(this).addClass('hover'); },
			function() { $(this).removeClass('hover'); }
			);
		$row.click(
			function() { selectDemand($(this)); }
			);
		$row.show();
	}
	$('#demandsPane').fadeIn();

	var $widget = $('#demandsPane');
	fixWidgetDimensions($widget);
}

function dismissDemandsPane()
{
	$('#demandsPane').fadeOut();
	delete mapFeatures.filterCities;
	delete mapFeatures.highlightCities;
	repaint();
}

function startTrainBtn()
{
	var train = isPlanning && isPlanning.train;
	if (!train)
		return;
	if (train.timer)
		return;

	startTrain(train);

	$('#startTrain_btn').hide();
	$('#stopTrain_btn').show();
}

function startTrain(train)
{
	if (train.running)
		return;

	delete train.brandNew;
	train.running = true;

	train_next(train);
}

function stopTrainBtn()
{
	if (isPlanning && isPlanning.train)
	{
		return stopTrain(isPlanning.train);
	}
}

function stopTrain(train)
{
	var nextLoc = train.route[0];
	if (train.route.length > 1)
	{
		train.route.splice(1, train.route.length - 1);
	}
	train.running = false;

	$('#startTrain_btn').show();
	$('#stopTrain_btn').hide();
}

function simpleDistance(cellIdx1, cellIdx2)
{
	var row1 = getCellRow(cellIdx1);
	var col1 = getCellColumn(cellIdx1);
	var row2 = getCellRow(cellIdx2);
	var col2 = getCellColumn(cellIdx2);

	var distRows = Math.abs(row2-row1);
	var distCols = Math.abs(col2-col1);
	var diag = Math.floor(distRows / 2);
	return distRows + (distCols > diag ? distCols - diag : 0);
}

function autoCreateDemands()
{
	var allResourceTypes = {};
	for (var cityId in mapData.cities)
	{
		var c = mapData.cities[cityId];
		for (var i in c.offers)
		{
			if (!allResourceTypes[c.offers[i]])
				allResourceTypes[c.offers[i]] = {};
			allResourceTypes[c.offers[i]][cityId]=true;
		}
	}

	mapData.futureDemands = new Array();
	mapData.pastDemands = new Array();
	for (var cityId in mapData.cities)
	{
		var c = mapData.cities[cityId];
		var here = {};
		for (var i in c.offers)
		{
			here[c.offers[i]] = true;
		}

		for (var rt in allResourceTypes)
		{
			if (here[rt])
				continue;

			// find nearest source of this resource
			var best = mapData.terrain.length + CELLS_PER_ROW;
			for (var s in allResourceTypes[rt])
			{
				var d = simpleDistance(cityId, s);
				if (d < best) { best = d; }
			}

			var value = Math.round(best * .5);
			if (value >= 1)
			{
				mapData.futureDemands.push(
					[cityId, rt, value]
					);
			}
		}
	}
	shuffleArray(mapData.futureDemands);

	curPlayer.demands = mapData.futureDemands.splice(0,5);
}

function shuffleArray(arr)
{
	var l = arr.length;
	for (var i = 0; i < l; i++)
	{
		var j = i + Math.floor(Math.random() * (l-i));
		var t = arr[i];
		arr[i] = arr[j];
		arr[j] = t;
	}
	return;
}

function findBestPath(fromIdx, toIdx, route)
{
	if (fromIdx == toIdx)
		return fromIdx;

	var queue = new Array();
	queue.push(fromIdx);

	var visited = {};
	var backlinks = {};

outerLoop:
	while (queue.length)
	{
		var l = queue.shift();
		visited[l] = true;

		for (var dir = 0; dir < 6; dir++)
		{
			var ti = getTrackIndex(l, dir);
			if (mapData.rails[ti])
			{
				var adjCellIdx = getAdjacentCell(l, dir);
				if (!visited[adjCellIdx])
				{
					backlinks[adjCellIdx] = l;
					if (adjCellIdx == toIdx)
						break outerLoop;
					queue.push(adjCellIdx);
				}
			}
		}
	}

	if (backlinks[toIdx])
	{
		var proute = new Array();
		var l = toIdx;
		while (l != fromIdx)
		{
			proute.push(l);
			l = backlinks[l];
		}
		while (proute.length > 0)
		{
			route.push(proute.pop());
		}
		return toIdx;
	}
	else
	{
		return fromIdx;
	}
}

function spaces(l)
{
	var x = "";
	while (l > 10)
	{
		x += "          ";
		l -= 10;
	}
	x += "          ".substr(0,l);
	return x;
}

function cropTerrain(offsetx, offsety, cx, cy)
{
	var newTerrain = new Array();
	for (var row = 0; row < cy; row++)
	{
		var s = "";
		if (offsety + row >= 0 && offsety + row < mapData.terrain.length)
		{
			var col = offsetx;
			if (col < 0)
			{
				s += spaces(-offsetx);
				col = 0;
			}
			s += mapData.terrain[offsety+row].substr(col);
			if (s.length < cx)
				s += spaces(cx - s.length);
			else
				s = s.substr(0, cx);
		}
		else
		{
			s = spaces(cx);
		}
		newTerrain.push(s);
	}

	var convertCellIdx = function(cellIdx)
	{
		var row = Math.floor(cellIdx / CELLS_PER_ROW);
		var col = cellIdx % CELLS_PER_ROW;

		row -= offsety;
		col -= offsetx;

		if (row >= 0 && row < cy
			&& col >= 0 && col < cx)
		{
			return row * cx + col;
		}
		else
		{
			return -1;
		}
	};

	var newCities = {};
	for (var cityIdx in mapData.cities)
	{
		var newCityIdx = convertCellIdx(cityIdx);
		if (newCityIdx >= 0)
		{
			newCities[newCityIdx] = mapData.cities[cityIdx];
		}
	}

	var newRivers = {};
	for (var edgeIdx in mapData.rivers)
	{
		var cellIdx = Math.floor(edgeIdx / 3);
		var newCellIdx = convertCellIdx(cellIdx);
		if (newCellIdx >= 0)
		{
			newRivers[newCellIdx * 3 + edgeIdx % 3] = mapData.rivers[edgeIdx];
		}
	}

	mapData.cities = newCities;
	mapData.terrain = newTerrain;
	mapData.rivers = newRivers;
	CELLS_PER_ROW = cx;
	MAP_ORIGIN_X -= CELL_WIDTH * offsetx;
	MAP_ORIGIN_Y -= CELL_HEIGHT * offsety;
	repaint();
}

function showEditMapPane()
{
	isEditing = {};
	$('#editMapPane').fadeIn();

	makeMoreRoomOnMap(10);
}

function makeMoreRoomOnMap(amt)
{
	var minX = CELLS_PER_ROW;
	var maxX = 0;
	var minY = mapData.terrain.length;
	var maxY = 0;

	var height = mapData.terrain.length;
	for (var row = 0; row < height; row++)
	{
		for (var col = 0; col < CELLS_PER_ROW; col++)
		{
			var c = mapData.terrain[row].charAt(col);
			if (c && c != " ")
			{
				if (col < minX) minX = col;
				if (col > maxX) maxX = col;
				if (row < minY) minY = row;
				if (row > maxY) maxY = row;
			}
		}
	}

	cropTerrain(minX - amt, minY - amt, (maxX+1-minX) + 2*amt, (maxY+1-minY) + 2*amt);
}

function dismissEditMapPane()
{
	isEditing = null;
	$('#editMapPane').fadeOut();
	$('#editCityPane').fadeOut();

	makeMoreRoomOnMap(0);

	var aRivers = new Array();
	for (var i in mapData.rivers)
	{
		aRivers.push(i);
	}

	sendRequest("editMap",
		{
		terrain: mapData.terrain.join("\n"),
		rivers: aRivers.join(" "),
		cities: mapData.cities
		});
}

function editmap_addCityResource()
{
	var cityId = $('#editCityPane').attr('selected-city');
	var cityInfo = mapData.cities[cityId];
	if (!cityInfo)
		return;

	var rt = document.getElementById('offersChoices');
	if (rt.value)
	{
		if (!cityInfo.offers)
			cityInfo.offers = new Array();
		cityInfo.offers.push(rt.value);
		editmap_reloadEditCityPane();
	}
}

function editmap_removeCityResource()
{
	var cityId = $('#editCityPane').attr('selected-city');
	var cityInfo = mapData.cities[cityId];
	if (!cityInfo)
		return;

	var rt = document.getElementById('offersList');
	if (rt.value)
	{
		if (!cityInfo.offers)
			cityInfo.offers = new Array();
		var newList = new Array();
		for (var i in cityInfo.offers)
		{
			if (cityInfo.offers[i] != rt.value)
				newList.push(cityInfo.offers[i]);
		}
		cityInfo.offers = newList;
		editmap_reloadEditCityPane();
	}
}

function editmap_reloadEditCityPane()
{
	$('#offersList').empty();
	$('#nullOffersChoice ~ option').remove();

	var cityId = $('#editCityPane').attr('selected-city');
	var cityInfo = mapData.cities[cityId];
	if (!cityInfo)
		return;

	var found = {};
	if (cityInfo.offers)
	{
		for (var i in cityInfo.offers)
		{
			var r = cityInfo.offers[i];
			var $r = $('<option></option>');
			$r.attr('value', r);
			$r.text(r);
			$('#offersList').append($r);
			found[r] = true;
		}
	}

	var types = (serverState && serverState.allServerResourceTypes) ||
		[ "coal", "imports", "furniture", "passengers",
		"plastics", "steel", "wood" ];
	for (var i in types)
	{
		if (!found[types[i]])
		{
			var $o = $('<option></option>');
			$o.attr('value', types[i]);
			$o.text(types[i]);
			$('#offersChoices').append($o);
		}
	}
}

function showEditCityPane(cityId)
{
	var cityInfo = mapData.cities[cityId];
	if (!cityInfo)
	{
		cityInfo = {
		name: "Unnamed City",
		};
		mapData.cities[cityId] = cityInfo;
		repaint();
	}

	var $w = $('#editCityPane');
	$w.attr('selected-city', cityId);

	$('#editCityNameEntry').attr('value', cityInfo.name);

	editmap_reloadEditCityPane();

	$w.fadeIn();

	var $pw = $('#editMapPane');
	$w.css({
		top: ($pw.position().top + $pw.outerHeight() + 10) + "px"
		});
	fixWidgetDimensions($w);
}

function dismissEditCityPane()
{
	var cityId = $('#editCityPane').attr('selected-city');
	var cityInfo = mapData.cities[cityId];

	if (cityInfo)
	{
		cityInfo.name = document.getElementById('editCityNameEntry').value;
		repaint();
	}

	$('#editCityPane').fadeOut();
}

function editmap_deleteCity()
{
	if (!confirm("Really delete this city?"))
		return;

	var cityId = $('#editCityPane').attr('selected-city');
	delete mapData.cities[cityId];

	$('#editCityPane').fadeOut();
}
