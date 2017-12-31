var mapData;
var mapFeatures = {};
var isBuilding = null;
var isPlanning = null;
var isEditing = null;
var curPlayer = {
	playerId: null,
	demands: [],
	};
var curDialog = null;
var noRedraw = 0;

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

var DISPLAY_SETTINGS = {
  zoomLevel: 64,
  offsetX: 0,
  offsetY: 0,
  showEditingDots: false,
};
const CELL_WIDTH = 64;
const CELL_HEIGHT = 2*Math.round(CELL_WIDTH*(56/64)/2);
const CELL_ASCENT = Math.round(CELL_HEIGHT * 36/56);
const CELL_DESCENT = CELL_HEIGHT - CELL_ASCENT;

var pendingImages = 0;
var terrainImages = {};
function preloadImages() {
	pendingImages++;
	var img = new Image();
	img.onload = function() {
			terrainImages.mountain = img;
			if (--pendingImages == 0)
				repaint();
		};
	img.src = "resources/terrain_mountain.png";
}

var resourceImages = {};
var resourceImagesFetch = {};
function loadResourceImage(resourceType)
{
	if (resourceImagesFetch[resourceType])
		return;

	resourceImagesFetch[resourceType] = true;
	pendingImages++;

	var img = new Image();
	img.onload = function() {
		resourceImages[resourceType] = img;
		if (--pendingImages == 0)
			repaint();
		};
	img.src = "resource_icons/" + resourceType + ".png";
}

function getPlayerId()
{
	return curPlayer.playerId;
}

function setPlayerId(pid)
{
	document.title = 'Trains : Seat ' + pid;

	curPlayer.playerId = pid;
	curPlayer.demands = gameState.players[pid] ? gameState.players[pid].demands : [];
	curPlayer.money = gameState.players[pid] ? gameState.players[pid].money : 0;

	// reload or something?
	if (curDialog == 'gameRosterPane')
	{
		showPlayers();
	}
}

function repaint()
{
	var canvas = document.getElementById('theCanvas');
	var ctx = canvas.getContext('2d');
	ctx.fillStyle = "#ffffff";
	ctx.fillRect(0,0,canvas.width,canvas.height);

	if (mapData) {
		new Painter(canvas, ctx, mapData, mapFeatures).paint();
	}
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

var serverState;
var gameState = new GameState();
function getGameTime()
{
	if (serverState)
		return (new Date().getTime() - serverState.basisTime) / 1000.0;
	else
		return 0;
}

var eventsListenerEnabled = false;

function onGameEvent(evt)
{
	if (evt.rails)
	{
		for (var i in evt.rails)
		{
			mapData.rails[i] = evt.rails[i];
		}
	}
	if (evt.event == 'join') {
		let p = evt.playerData;
		gameState.newPlayer(evt.playerId, p);
		if (curPlayer.playerId == evt.playerId) {
			curPlayer.demands = p.demands;
		}
	}
	if (evt.event == 'train') {
		if (!gameState.hasTrain(evt.trainId)) {
			if (!evt.spawnLocation) {
				return;
			}
			createTrain(evt.trainId, evt.spawnLocation);
		}
		var t = gameState.trains[evt.trainId];
		t.owner = evt.owner || '1';
		gameState.updateTrainPlan(evt.trainId, evt.plan);
		t.lastUpdated = evt.time;
		t.running = evt.running;
		if (evt.running) {
			train_next(t);
		}
	}
	if (evt.playerMoney)
	{
		for (var pid in evt.playerMoney)
		{
			var p = gameState.players[pid];
			if (p)
			{
				var newBalance = evt.playerMoney[pid];
				p.money = newBalance;
			}
		}
	}

	onGameState(false);

	if (evt.event == 'join' && curDialog == 'gameRosterPane')
	{
		showPlayers();
	}
}

function getEventsNow() {
	stopEventsListener();
	startEventsListener();
}

function stopEventsListener() {
	if (serverState.timerId) {
		clearTimeout(serverState.timerId);
		serverState.timerId = 0;
	}
	serverState.currentFetch = null;
}

function startEventsListener() {

	var rdm = new Date().getTime();
	var gameId = serverState.gameId;
	var errorCounter = 0;
	serverState.timerId = 0;

	var myFetch = {};
	serverState.currentFetch = myFetch;

	var fetchNextEvent;
	var onSuccess = function(data,status)
	{
		if (serverState.currentFetch !== myFetch) {
			return;
		}

		errorCounter = 0;
		let anyFound = false;
		while (serverState.eventsSeen < data.length) {
			anyFound = true;
			onGameEvent(data[serverState.eventsSeen++]);
		}
		serverState.timerId = setTimeout(fetchNextEvent,
			anyFound ? 500 : 2500);
	};
	var onError = function(xhr, status, errorThrown)
	{
		if (serverState != myFetch) {
			return;
		}

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
		url: "/api/events?game=" + escape(gameId) + "&r="+rdm,
		success: onSuccess,
		error: onError,
		dataType: "json"
		});
	};

	fetchNextEvent();
	eventsListenerEnabled = true;
}

function onGameState(firstLoad)
{
	if (serverState.map)
	{
		mapData = MapData.initialize(serverState.map);
	}
	if (mapData && serverState.rails)
		mapData.rails = serverState.rails;

	if (curPlayer.playerId && gameState.players[curPlayer.playerId])
	{
		curPlayer.demands = gameState.players[curPlayer.playerId].demands;
		document.title = 'Trains : Seat ' + curPlayer.playerId;
	}
	else
	{
		curPlayer.demands = new Array();
	}

	if (!eventsListenerEnabled)
	{
		startEventsListener();
	}

	if (firstLoad)
		zoomShowAll();
	else
		repaint();
}

function fromCanvasCoords(pt) {
  return {
      x: (pt.x - DISPLAY_SETTINGS.offsetX) / (DISPLAY_SETTINGS.zoomLevel / CELL_WIDTH),
      y: (pt.y - DISPLAY_SETTINGS.offsetY) / (DISPLAY_SETTINGS.zoomLevel / CELL_WIDTH),
  };
}

function toCanvasCoords(pt) {
  return {
      x: pt.x * (DISPLAY_SETTINGS.zoomLevel / CELL_WIDTH) + DISPLAY_SETTINGS.offsetX,
      y: pt.y * (DISPLAY_SETTINGS.zoomLevel / CELL_WIDTH) + DISPLAY_SETTINGS.offsetY,
  };
}

function updateWaypointSpritePosition(sprite)
{
	var pt = toCanvasCoords(mapData.G.getCellPoint(sprite.loc));

	var $t = sprite.el;
	var p = $('#theCanvas').position();
	$t.css({
		left: (p.left + pt.x - $t.outerWidth()/2) + "px",
		top: (p.top + pt.y - $t.outerHeight()) + "px"
		});
}

function updateTrainSpritePosition(train)
{
	var pt = toCanvasCoords(mapData.G.getCellPoint(train.loc));

	var elapsed = getGameTime() - train.lastUpdated;
	var dist = elapsed * train.speed;
	if (dist > 0 && train.route && train.route[0])
	{
		var pt1 = toCanvasCoords(mapData.G.getCellPoint(train.route[0]));
		pt.x += (pt1.x - pt.x) * dist;
		pt.y += (pt1.y - pt.y) * dist;
		let angle = Math.atan2(pt1.y - pt.y, pt1.x - pt.x);
		angle = Math.floor(angle / (Math.PI / 6) + 0.5);
		train.orientationName =
			angle == -5 ? 'WNW' :
			angle == -4 ? 'NNW' :
			angle == -3 ? 'N' :
			angle == -2 ? 'NNE' :
			angle == -1 ? 'ENE' :
			angle == 0 ? 'E' :
			angle == 1 ? 'ESE' :
			angle == 2 ? 'SSE' :
			angle == 3 ? 'S' :
			angle == 4 ? 'SSW' :
			angle == 5 ? 'WSW' :
			'W';
	} else {
		if (!train.orientationName) {
			train.orientationName = 'N';
		}
	}

	var trainSize = Math.round(DISPLAY_SETTINGS.zoomLevel * 1.5);
	var $t = train.el;
	var origin_pt = $('#theCanvas').position();
	$t.css({
		left: (origin_pt.left + pt.x - (trainSize/2 - 1)) + "px",
		top: (origin_pt.top + pt.y - (trainSize/2 - 1)) + "px",
		width: trainSize + 'px',
		height: trainSize + 'px',
		'background-size': trainSize + 'px',
		'background-image': 'url(resources/locomotive-' + train.orientationName + '.png)',
		});
}

function train_cargoChanged(train)
{
	if (isPlanning && isPlanning.train == train)
	{
		reloadPlan();
	}
}

function train_deliver(train, resource_type)
{
	console.log('delivering ' + resource_type);
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
		var p = gameState.players[train.owner];
		for (var i in p.demands)
		{
			var d = p.demands[i];
			if (d[0] == train.loc && d[1] == resource_type) {
				train.revenue += (+d[2]);
				adjustPlayerCash(train.owner, d[2]);
				p.demands.splice(i,1);
				gameState.pastDemands.push(d);
				gameState.nextDemand(train.owner);
				break;
			}
		}
	}

	return;
}

function train_pickup(train, resource_type)
{
	console.log('picking up ' + resource_type);

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
			train.curWaypoint = train.plan[0].id;
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
	if (!('speed' in train)) {
		train.speed = 1000.0/(12*150);
	}

	var elapsed = getGameTime() - train.lastUpdated;
	var dist = elapsed * train.speed;

	while (dist >= 1.0 && train.route.length >= 1) {
		train.lastUpdated += 1.0 / train.speed;
		train.loc = train.route.shift();
		onTrainLocationChanged(train);
		dist -= 1.0;
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

function removeAllWaypointSprites() {
	for (let waypointId in waypointSprites) {
		removeWaypointSprite(waypointId);
	}
}

function removeWaypointSprite(waypointId)
{
	if (waypointSprites[waypointId])
	{
		waypointSprites[waypointId].el.remove();
		delete waypointSprites[waypointId];
	}
}

function addTrainSprite(trainId, trainLoc)
{
	var $t = $('<div class="trainSprite"></div>');
	$('#contentArea').append($t);

	var train = {
		el: $t,
		trainId: trainId,
		loc: trainLoc,
		plan: new Array(),
		route: new Array(),
		revenue: 0,
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
	var screenPt = {
		x: evt.clientX - p.left,
		y: evt.clientY - p.top
	};
	var pt = fromCanvasCoords(screenPt);
	var cellIdx = mapData.G.getCellFromPoint(pt);
	if (cellIdx === null)
		return;

	var cellP = toCanvasCoords(mapData.G.getCellPoint(cellIdx));

	if (isCity(cellIdx) && isPlanning)
	{
		addCityToPlan(cellIdx);
		return;
	}

	var canBuild = false;
	if (isCity(cellIdx) || mapData.hasTrackAt(cellIdx, getPlayerId()))
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
		originX: screenPt.x,
		originY: screenPt.y
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
	var t = getRadioButtonValue(document.editMapForm.tool);
	if (t == "city")
	{
		showEditCityPane(cellIdx);
		return;
	}
	else if (t == "rivers")
	{
		var edgeIdx = mapData.G.getEdgeFromPoint(oPt);
		if (edgeIdx === null) {
			return;
		}
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
		t == 'alpine' ? 'A' :
		t == 'urban' ? 'c' :
		" ";
	mapData.setTerrainAt(cellIdx, c);
	repaint();
}

function onMouseDown_build(cellIdx)
{
	var pt = toCanvasCoords(mapData.G.getCellPoint(cellIdx));
	var canvas = document.getElementById('theCanvas');
	var ctx = canvas.getContext('2d');
	ctx.beginPath();
	ctx.fillStyle = '#009999';
	ctx.arc(pt.x, pt.y, 6, 0, Math.PI * 2, true);
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
	if (!p) {
		// No canvas.
		return;
	}
	var pt = {
		x: evt.clientX - p.left,
		y: evt.clientY - p.top
		};

	if (isDragging)
	{
		var cellIdx = mapData.G.getCellFromPoint(fromCanvasCoords(pt));
		if (cellIdx === null) {
			return;
		}
		var cellPt = toCanvasCoords(mapData.G.getCellPoint(cellIdx));

		if (cellIdx != isDragging.start
			&& Math.abs(cellPt.x - pt.x) < 16
			&& Math.abs(cellPt.y - pt.y) < 16
			&& mapData.G.isCellAdjacent(isDragging.start, cellIdx))
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

		DISPLAY_SETTINGS.offsetX += dx;
		DISPLAY_SETTINGS.offsetY += dy;
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
	if (fromIdx == mapData.G.getAdjacentCell(toIdx, Geometry.WEST))
	{
		cellIdx = toIdx;
		dir = 0;
	}
	else if (fromIdx == mapData.G.getAdjacentCell(toIdx, Geometry.NORTHWEST))
	{
		cellIdx = toIdx;
		dir = 1;
	}
	else if (fromIdx == mapData.G.getAdjacentCell(toIdx, Geometry.NORTHEAST))
	{
		cellIdx = toIdx;
		dir = 2;
	}
	else if (fromIdx == mapData.G.getAdjacentCell(toIdx, Geometry.EAST))
	{
		cellIdx = fromIdx;
		dir = 0;
	}
	else if (fromIdx == mapData.G.getAdjacentCell(toIdx, Geometry.SOUTHEAST))
	{
		cellIdx = fromIdx;
		dir = 1;
	}
	else if (fromIdx == mapData.G.getAdjacentCell(toIdx, Geometry.SOUTHWEST))
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

	var p0 = toCanvasCoords(mapData.G.getCellPoint(fromIdx));
	var p1 = toCanvasCoords(mapData.G.getCellPoint(toIdx));

	ctx.save();
	ctx.beginPath();
	ctx.strokeStyle = isBuilding.erasing ? '#ffffff' : '#009999';
	ctx.lineWidth = 4;
	ctx.moveTo(p0.x, p0.y);
	ctx.lineTo(p1.x, p1.y);
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

function setZoomLevel(w, basisPt)
{
	if (!basisPt)
	{
		var canvas = document.getElementById('theCanvas');
		basisPt = {x: canvas.width/2, y: canvas.height/2};
	}

	var newZoomLevel = 2 * Math.round(w/2);
	if (newZoomLevel > 64)
		newZoomLevel = 64;
	if (newZoomLevel < 12)
		newZoomLevel = 12;

	DISPLAY_SETTINGS.offsetX -= basisPt.x;
	DISPLAY_SETTINGS.offsetX *= newZoomLevel / DISPLAY_SETTINGS.zoomLevel;
	DISPLAY_SETTINGS.offsetX += basisPt.x;
	DISPLAY_SETTINGS.offsetY -= basisPt.y;
	DISPLAY_SETTINGS.offsetY *= newZoomLevel / DISPLAY_SETTINGS.zoomLevel;
	DISPLAY_SETTINGS.offsetY += basisPt.y;
	DISPLAY_SETTINGS.zoomLevel = newZoomLevel;

	if (!noRedraw)
	{
		repaint();
		updateAllSpritePositions();
	}
}

function zoomIn(basisPt)
{
	setZoomLevel(DISPLAY_SETTINGS.zoomLevel * 4/3, basisPt);
}

function zoomOut(basisPt)
{
	setZoomLevel(DISPLAY_SETTINGS.zoomLevel * 3/4, basisPt);
}

function getMapWidth() {
	return mapData.G.width;
}

function getMapHeight() {
	return mapData.G.height;
}

function zoomShowAll()
{
	var mapWidth = getMapWidth();
	var mapHeight = getMapHeight();

	var canvas = document.getElementById('theCanvas');
	var cw1 = canvas.width / mapWidth;
	var cw2 = (canvas.height / mapHeight) * 64/56;

	DISPLAY_SETTINGS.zoomLevel = 12;
	DISPLAY_SETTINGS.offsetX = canvas.width / 2;
	DISPLAY_SETTINGS.offsetY = canvas.height / 2;

	setZoomLevel(cw1 < cw2 ? cw1 : cw2);
}

function centerMapOn(cellIdx) {

  var canvas = document.getElementById('theCanvas');

  var pt = mapData.G.getCellPoint(cellIdx);
  DISPLAY_SETTINGS.offsetX = -pt.x * DISPLAY_SETTINGS.zoomLevel / CELL_WIDTH + canvas.width / 2;
  DISPLAY_SETTINGS.offsetY = -pt.y * DISPLAY_SETTINGS.zoomLevel / CELL_WIDTH + canvas.height / 2;

  if (!noRedraw) {
    repaint();
    updateAllSpritePositions();
  }
}

function beginBuilding()
{
	isBuilding = {
	rails: {}
	};
	updateBuildingCost();
	dismissCurrentDialog();
	$('#buildTrackInfo').fadeIn();

	updateAllTrainSpritesSensitivity();
}

function updateAllTrainSpritesSensitivity() {
  for (let trainId in gameState.trains) {
    updateTrainSpriteSensitivity(gameState.trains[trainId]);
  }
}

function adjustPlayerCash(pid, delta) {

	gameState.players[pid].money += delta;
	if (curPlayer.playerId == pid) {
		curPlayer.money = gameState.players[pid].money;
		$('#cashIndicator').text(curPlayer.money);
	}
}

function sendRequest(verb, data, success)
{
	data['action'] = verb;
	var onSuccess = function(data)
	{
		if (success) { success(data); }
		getEventsNow();
	};
	var onError = function(xhr, status, errorThrown)
	{
		alert("request error " + errorThrown);
	};

	$.ajax({
	type: "POST",
	url: "/api/actions?game=" + escape(serverState.gameId),
	data: JSON.stringify(data),
	success: onSuccess,
	error: onError,
	dataType: "json",
	contentType: "application/json"
	});
}

function commitBuilding()
{
	if (!isBuilding)
		return;

	var cost = parseFloat($('#buildTrackCost').text());
	var money = curPlayer.money;
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

	updateAllTrainSpritesSensitivity();
	repaint();
}

function abandonBuilding()
{
	if (!isBuilding)
		return;

	isBuilding = null;
	$('#buildTrackInfo').fadeOut();

	updateAllTrainSpritesSensitivity();
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
	showPlan(train.trainId, train);
}

function addLocomotive()
{
	var trainId = curPlayer.playerId + '.1';
	if (gameState.hasTrain(trainId)) {
		showPlan(trainId, gameState.trains[trainId]);
	} else {
		// New train
		showPlan(trainId, null);
	}
}

function createTrain(trainId, location) {
	gameState.trains[trainId] = addTrainSprite(trainId, location);
	return gameState.trains[trainId];
}

function showPlan(trainId, train)
{
	if (isPlanning && isPlanning.train == train)
		return;

	isPlanning = {
		trainId: trainId,
		train: train,
	};
	reloadPlan();
	$('#planPane').fadeIn();

	if (train)
	{
		// update map for planning
		mapFeatures.hideTerrain = true;
		filterMapToReachable(isPlanning.train);
		repaint();
		$('#brandNewInstructions').hide();
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
	}

	isPlanning = null;
	mapFeatures.hideTerrain = false;
	delete mapFeatures.filterCities;
	delete mapFeatures.filterTrack;
	repaint();

	$('#planPane').fadeOut();
	$('#waypointPane').fadeOut();
	removeAllWaypointSprites();
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
			var ti = mapData.G.getTrackIndex(l, dir);
			if (mapData.rails[ti])
			{
				reachableTrack[ti] = true;
				var adjCellIdx = mapData.G.getAdjacentCell(l, dir);
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
		isPlanning.train = createTrain(isPlanning.trainId, cellIdx);
		isPlanning.train.brandNew = true;

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
		location: cellIdx
		};
	if (priorWaypoint)
	{
		var r = new Array();
		findBestPath(priorWaypoint.location, waypoint.location, r);
		waypoint.distanceHint = r.length;
		waypoint.id = (priorWaypoint.id || 0) + 1;
	} else {
		waypoint.id = 0;
		isPlanning.train.curWaypoint = 0;
	}

        isPlanning.train.plan.push(waypoint);
	reloadPlan();
	selectWaypoint(isPlanning.train.plan.length-1);
}

function updateAllSpritePositions()
{
	for (let trainId in gameState.trains) {
		let train = gameState.trains[trainId];
		updateTrainSpritePosition(train);
	}
	for (var i in waypointSprites)
	{
		updateWaypointSpritePosition(waypointSprites[i]);
	}
}

function dropWaypoint() {
	var curWaypoint = +$('#planPane').attr('selected-waypoint');
	// TODO
	alert('not implemented');
}

function selectWaypoint(newSelection)
{
	var oldSelection = $('#planPane').attr('selected-waypoint');
	if (oldSelection !== null)
	{
		$('#planPane tr[waypoint-number='+oldSelection+']').removeClass('selected');
		if (oldSelection == newSelection) {
			$('#planPane').removeAttr('selected-waypoint');
			$('#waypointPane').fadeOut();
			return;
		}
	}

	$('#planPane tr[waypoint-number='+newSelection+']').addClass('selected');
	$('#planPane').attr('selected-waypoint', newSelection);

	var waypoint = isPlanning.train.plan[newSelection];

	var cityName = mapData.cities[waypoint.location].name;
	$('#waypointPane .widgetHeader').text(cityName);

	reloadWaypoint(waypoint);
	$('#waypointPane .drop-waypoint-btn').toggle(newSelection != 0);

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

	noRedraw++;
	centerMapOn(demand[0]);
	setZoomLevel(12);
	noRedraw--;

	if (!noRedraw)
	{
		repaint();
		updateAllSpritePositions();
	}
}

function reloadPlan()
{
	$('#trainCargo').empty();
	$('#planPane .train-income').hide();
	$('#planPane .insertedRow').remove();

	var train = isPlanning && isPlanning.train;
	if (!train)
		return;

	$('#planPane .train-id').text(train.trainId);
	$('#planPane .train-income').show();
	$('#planPane .income-ind').text(train.revenue);

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
	dismissPlan();
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

	popupDialog('demandsPane');
}

function startTrainBtn()
{
	var train = isPlanning && isPlanning.train;
	if (!train)
		return;
	if (train.timer)
		return;

	var req = {
            trainId: train.trainId,
	    owner: curPlayer.playerId,
            plan: train.plan,
            running: true,
        };
	if (train.brandNew) {
		req.spawnLocation = train.loc;
	}
	sendRequest('startTrain', req);
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
			var ti = mapData.G.getTrackIndex(l, dir);
			if (mapData.rails[ti])
			{
				var adjCellIdx = mapData.G.getAdjacentCell(l, dir);
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

function popupDialog(dialogName)
{
	var andThen = function()
	{
		var $widget = $(document.getElementById(dialogName));
		$widget.fadeIn();
		fixWidgetDimensions($widget);
		curDialog = dialogName;
	};

	if (curDialog && curDialog != dialogName)
	{
		dismissCurrentDialog(andThen);
	}
	else
	{
		andThen();
	}
}

function dismissCurrentDialog(andThen)
{
	if (curDialog == 'demandsPane')
	{
		delete mapFeatures.filterCities;
		delete mapFeatures.highlightCities;
		repaint();
	}

	if (curDialog)
	{
		var $widget = $(document.getElementById(curDialog));
		curDialog = null;
		$widget.fadeOut(400, andThen);
	}
}

function showPlayers()
{
	$('#gameRosterPane .insertedRow').remove();
	if (serverState)
	{
		for (var pid in gameState.players)
		{
			var p = gameState.players[pid];

			var $row = $('#gameRosterPaneTableTemplate').clone();
			$row.addClass('insertedRow');
			$('.playerId', $row).text(pid);
			$('.playerName', $row).text(p.identity || 'Anonymous');
			with({ pid: pid })
			{
				$('button.playAsBtn', $row).click(function() {
					dismissCurrentDialog();
					setPlayerId(pid);
					});
			}
			if (pid == getPlayerId())
			{
				$('.playAsBtn', $row).hide();
			}
			$('#gameRosterPane table').append($row);
			$row.show();
		}
	}

	popupDialog('gameRosterPane');
}

function canvasInitialization() {
  onResize();
  document.getElementById('theCanvas').addEventListener('mousedown', onMouseDown, false);
  $(document).mouseup(onMouseUp);
  $(document).mousemove(onMouseMove);
  document.getElementById('theCanvas').addEventListener('DOMMouseScroll',
      onMouseWheel, false);

  document.getElementById('theCanvas').addEventListener('touchstart', onTouchStart_r, false);
  document.addEventListener('touchmove', onTouchMove_r, false);
  document.addEventListener('touchend', onTouchEnd_r, false);
  preloadImages();
}

angular.module('trains', ['ngRoute'])

.config(function($routeProvider, $locationProvider) {
  $routeProvider
  .when('/game/:game', {
    controller: 'GameController',
    controllerAs: 'c',
    templateUrl: 'resources/game.ng',
    resolve: {
      'gameData': function($http, $route) {
        const gameId = $route.current.params['game'];
        const seat = $route.current.params['seat'];
        return $http.get('/api/gamestate', {
            params: {game: gameId},
            responseType: 'json',
        }).then(
          httpResponse => httpResponse.data
        );
      },
    },
  })
  .when('/map/edit/:map', {
    controller: 'MapEditorController',
    controllerAs: 'c',
    templateUrl: 'resources/game.ng',
    resolve: {
      'mapDataRaw': function($http, $route) {
        return $http.get('/api/map', {
          params: {'map': $route.current.params['map']},
          responseType: 'json',
        }).then(httpResponse => httpResponse.data,
                httpError => null);
      },
    },
  })
  .when('/lobby', {
    controller: 'LobbyController',
    controllerAs: 'c',
    templateUrl: 'resources/lobby.ng',
    resolve: {
      'gamesList': function($http) {
        return $http.get('/api/games').then(
            httpResponse => httpResponse.data);
      },
    },
  })
  .otherwise({
    redirectTo: '/lobby'
  });
  $locationProvider.html5Mode(false);
})
.controller('LobbyController', function($http, $location, gamesList) {
  this.gamesList = gamesList;
  this.newGame = {
    name: '',
    map: '',
  };

  this.join = function(game) {

    var playerName = window.prompt('Enter player name');
    if (!playerName) {
      return;
    }
    var request = {
      game: game.name,
      name: playerName,
    };
    $http.post('/api/login', JSON.stringify(request))
      .then(httpResponse => {
        $location.path('/game/' + escape(game.name));
        $location.search('seat', httpResponse.data.playerId);
      });
  };
  this.watch = function(game) {
    $location.path('/game/' + escape(game.name));
  };
  this.newGameFormSubmitted = function() {
    if (!this.newGame.name) {
      alert('Please enter a name for the new game.');
      return;
    }
    if (!this.newGame.map) {
      alert('Please enter a map name.');
      return;
    }
    let request = this.newGame;
    $http.post('/api/games', JSON.stringify(request))
      .then(httpResponse => {
        $location.path('/game/' + escape(this.newGame.name));
      }, httpError => {
        if (httpError.status == 400) {
          // Bad request; just show the user the error message.
          alert(httpError.data);
        } else {
          alert('HTTP error ' + httpError.status);
        }
      });
  };
})
.controller('MapEditorController', function($http, $location, $routeParams, mapDataRaw) {

  canvasInitialization();

  DISPLAY_SETTINGS.showEditingDots = true;
  mapFeatures = {};
  if (mapDataRaw) {
    mapData = MapData.initialize(mapDataRaw);
  } else {
    mapData = new MapData();
  }
  mapData.makeMoreRoomOnMap(10);
  zoomShowAll();

  this.isEditing = true;
  isEditing = {};
  $('#editMapPane').fadeIn();

  var lastMapName = $routeParams['map'];
  if (lastMapName == '_new') {
    lastMapName = '';
  }
  this.loadMap = function() {
    var mapName = window.prompt('Enter map name to load', lastMapName);
    if (mapName) {
      $location.path('/map/edit/' + escape(mapName));
    }
  };
  this.saveMap = function() {
    var mapName = window.prompt('Save map as', lastMapName);
    if (mapName) {
      var mapRenamed = mapName != lastMapName;
      lastMapName = mapName;
      $http.put('/api/map', JSON.stringify(mapData), {
        params: {'map': mapName},
      }).then(
        function(httpResponse) {
          alert('success');
          if (mapRenamed) {
            $location.path('/map/edit/' + escape(mapName));
          }
        });
    }
  };
  this.dismissEditor = function() {
    isEditing = null;
    $('#editMapPane').fadeOut();
    $('#editCityPane').fadeOut();

    if (confirm('Create a new game with this map?')) {
      // TODO
      DISPLAY_SETTINGS.showEditingDots = false;
      mapData.makeMoreRoomOnMap(0);
      repaint();
      startEventsListener();
    } else {
      $location.path('/');
    }
  };
})
.controller('GameController', function($http, $location, $routeParams, gameData) {
  this.gameId = $routeParams['game'];

  canvasInitialization();

  // Handle game state.
  {
    let fetchEndTime = new Date().getTime();
    serverState = gameData;
    serverState.basisTime = fetchEndTime - 1000 * serverState.serverTime;
    serverState.eventsSeen = 0;
    serverState.gameId = $routeParams['game'];
    gameState.futureDemands = serverState.allDemands;
    onGameState(true);
  }

  this.playerId = null;
  this.playerData = null;
  if ($routeParams['seat']) {
    this.playerId = $routeParams['seat'];
    this.playerData = gameData.players[this.playerId];
    setPlayerId(this.playerId);
  }

  this.leaveGame = function() {
    stopEventsListener();
    $location.path('/lobby');
    $location.search('seat', null);
  };
});
