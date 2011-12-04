var mapData = {
	terrain: [
		"www          ",
		"w..          ",
		"..MM.MM..... ",
		" ..MM........",
		"....MM...... ",
		" ..M..M....M ",
		"...M.MM...M. ",
		" ...MM.......",
		"...M.MM.....w",
		" .M..M......w",
		"           ww"
		],
	rails: {},
	cities: {
		15: {
			name: "Erie", offers: ["plastics"]
			},
		49: {
			name: "Scranton", offers: ["coal"]
			},
		75: {
			name: "Allentown", offers: ["coal", "steel"]
			},
		93: {
			name: "Pittsburgh", offers: ["steel"]
			},
		115: {
			name: "Philadelphia", offers: ["imports"]
			},
		99: {
			name: "Harrisburg", offers: ["passengers"]
			}
		},
	demands: [
		[ 15, "passengers", 10 ],
		[ 49, "steel", 2 ],
		[ 93, "imports", 15 ]
		]
	};
var trainRoute = [
	17, 10, 11, 12, 13, 21, 30, 38, 46, 45, 44, 43, 34, 33, 26
	];
var theTrain = null;
var mapFeatures = {};
var isBuilding = null;
var isPlanning = null;

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

var CELLS_PER_ROW = 13;
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
	MAP_ORIGIN_X = CELL_WIDTH/2;
	MAP_ORIGIN_Y = CELL_ASCENT/2;
}
updateMapMetrics();

function drawCell(ctx, pt, c, w, nw, ne)
{
	var getColor = function(cc)
	{
		if (mapFeatures.hideTerrain)
		{
			return cc == "w" ? "#0000ff" : "#ffffff";
		}

		return cc == "." ? "#88ff66" :
			cc == "M" ? "#884400" :
			cc == "w" ? "#0000ff" :
			"#ffffff";
	};

	ctx.fillStyle = getColor(c);
	if (c != nw || c != ne)
	{
		ctx.beginPath();
		ctx.moveTo(pt.x, pt.y);
		ctx.lineTo(pt.x + CELL_WIDTH / 2, pt.y - CELL_DESCENT);
		ctx.lineTo(pt.x + CELL_WIDTH, pt.y);
		ctx.closePath();
		ctx.fill();
	}

	ctx.fillRect(
		pt.x, pt.y,
		CELL_WIDTH+1, CELL_HEIGHT+1
		);
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
	if (mapData.rails[trackIdx])
		return 1;
	else if (isBuilding && isBuilding.rails[trackIdx])
		return 2;
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

function drawRailsHelper(ctx, owner)
{
	var RAIL_WIDTH = CELL_WIDTH / 16;
	var TIE_LENGTH = CELL_WIDTH / 10;

	if (owner == 2)
	{
		ctx.strokeStyle = "#009999";
		ctx.lineWidth = 2;
	}
	else
	{
		ctx.strokeStyle = "#000000";
		ctx.lineWidth = 1;
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

function trackVisible(trackId)
{
	return !mapFeatures.filterTrack ||
		mapFeatures.filterTrack[trackId];
}

function repaint()
{
	var canvas = document.getElementById('theCanvas');
	var ctx = canvas.getContext('2d');

	ctx.clearRect(0,0,canvas.width,canvas.height);
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
			if (mapData.cities[cellIdx] && cityVisible(cellIdx))
			{
				ctx.fillStyle = "#ff4444";
				ctx.beginPath();
				ctx.arc(pt.x + CELL_WIDTH / 2, pt.y + CELL_ASCENT / 2, CELL_HEIGHT * .36, 0, Math.PI * 2, true);
				ctx.closePath();
				ctx.fill();
			}

			drawRails(ctx, pt, cellIdx);
		}
	}

	ctx.fillStyle = "#000000";
	ctx.font = "24px sans-serif";
	for (var cityLoc in mapData.cities)
	{
		if (cityVisible(cityLoc))
		{
			var cityName = mapData.cities[cityLoc].name;
			var p = getCellPoint(cityLoc);
			ctx.fillText(cityName, p.x, p.y + CELL_ASCENT);
		}
	}
}

function onResize()
{
	var canvas = document.getElementById('theCanvas');
	canvas.width = window.innerWidth - 0;
	canvas.height = window.innerHeight - 100;
	repaint();
}
window.onresize = onResize;
$(onResize);

function getCellPoint(cellIdx)
{
	var x = getCellColumn(cellIdx);
	var y = getCellRow(cellIdx);

	return {
		x: (y % 2 == 0 ? CELL_WIDTH / 2 : 0) + CELL_WIDTH * x - MAP_ORIGIN_X,
		y: CELL_HEIGHT * y - MAP_ORIGIN_Y
		};
}

function getCellFromPoint(x, y)
{
	var iy = Math.floor((y+MAP_ORIGIN_Y) / CELL_HEIGHT);
	var ix = Math.floor((x + MAP_ORIGIN_X - (iy % 2 == 0 ? CELL_WIDTH/2 : 0)) / CELL_WIDTH);
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

function updateTrainPosition(train)
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

function onTrainLocationChanged(train)
{
	if (train.plan && train.plan[0])
	{
		var p = train.plan[0];
		if (p.location == train.loc)
		{
			onWaypointReached(train);
		}
	}
}

function onWaypointReached(train)
{
	train.plan.shift();
	if (isPlanning)
	{
		reloadPlan();
	}
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

	updateTrainPosition(train);
	if (train.route.length >= 1)
	{
		train.timer =
		setTimeout(function() { animateTrain(train) }, 150);
	}
	else
	{
		delete train.timer;

		if (train.running)
			stopTrain(train);
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
	updateTrainPosition(train);
	//animateTrain(train);

	$t.click(function() { onTrainClicked(train); });
	return train;
}

var isDragging = null;
var isPanning = null;
function onMouseDown(evt)
{
	if (evt.which != 1) return;

	evt.preventDefault();
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

	// begin pan of whole map
	isPanning = {
		originX: pt.x,
		originY: pt.y
		};
	return;
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

	evt.preventDefault();

	if (isBuilding)
	{
		repaint();
		isDragging = null;
	}
	isPanning = null;
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
		MAP_ORIGIN_X -= (pt.x - isPanning.originX);
		MAP_ORIGIN_Y -= (pt.y - isPanning.originY);
		repaint();

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
}

$(function() {
	$('#contentArea').mousedown(onMouseDown);
	$('#contentArea').mouseup(onMouseUp);
	$('#contentArea').mousemove(onMouseMove);
});

function zoomIn()
{
	//var canvas = document.getElementById('theCanvas');
	CELL_WIDTH = 2*Math.round(CELL_WIDTH*(4/3)/2);
	if (CELL_WIDTH > 64)
		CELL_WIDTH = 64;

	updateMapMetrics();
	repaint();
	updateAllTrainPositions();
}

function zoomOut()
{
	CELL_WIDTH = 2*Math.round(CELL_WIDTH*(3/4)/2);
	if (CELL_WIDTH < 12)
		CELL_WIDTH = 12;

	updateMapMetrics();
	repaint();
	updateAllTrainPositions();
}

function beginBuilding()
{
	isBuilding = {
	rails: {}
	};
	$('#buildTrackInfo').fadeIn();
}

function commitBuilding()
{
	if (!isBuilding)
		return;

	for (var i in isBuilding.rails)
	{
		mapData.rails[i] = isBuilding.rails[i];
	}

	isBuilding = null;
	$('#buildTrackInfo').fadeOut();

	repaint();
}

function abandonBuilding()
{
	if (!isBuilding)
		return;

	isBuilding = null;
	$('#buildTrackInfo').fadeOut();

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
	if (isPlanning && isPlanning.train == train)
		return;

	isPlanning = {
		train: train
		};
	reloadPlan();
	$('#trainPlan').fadeIn();

	fixWidgetDimensions($('#trainPlan'));
}

function addLocomotive()
{
	isPlanning = {
		train: theTrain
		};
	reloadPlan();
	$('#trainPlan').fadeIn();

	var $widget = $('#trainPlan');
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

	$('#trainPlan').fadeOut();
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
	}

	var waypoint = {
		class: "waypoint",
		location: cellIdx
		};
        isPlanning.train.plan.push(waypoint);
	reloadPlan();
	selectWaypointByIndex(isPlanning.train.plan.length-1);
}

function updateAllTrainPositions()
{
	if (theTrain)
	{
		updateTrainPosition(theTrain);
	}
}

function selectWaypointByIndex(i)
{
	var $row = $('.aWaypoint[plan-index='+i+']');
	selectWaypoint($row);
}

function selectWaypoint($row)
{
	var oldSelection = $('#trainPlan').attr('selected-waypoint');
	if (oldSelection)
	{
		$('.aWaypoint[waypoint-number='+oldSelection+']').removeClass('selected');
	}

	$row.addClass('selected');
	$('#trainPlan').attr('selected-waypoint', $row.attr('waypoint-number'));

        var planIdx = $row.attr('plan-index');
	var waypoint = isPlanning.train.plan[planIdx];

	var cityName = mapData.cities[waypoint.location].name;
	$('#waypointPane .widgetHeader').text(cityName);

	reloadWaypoint(waypoint);

	$('#waypointPane').fadeIn();
	var $pw = $('#trainPlan');
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
	for (var planIdx in train.plan)
	{
		var waypoint = train.plan[planIdx];
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
			for (var j in mapData.demands)
			{
				var dem = mapData.demands[j];
				if (dem[1] == resource_type)
				{
					wantedPlaces.push(mapData.cities[dem[0]].name);
				}
			}
			$('.wantedInfo', $row).text(wantedPlaces.length > 0 ?
				"Wanted in " + wantedPlaces.join(', ') :
				"");

			$('#keepTemplate').before($row);
			$row.show();

			with({ resource_type: resource_type })
			{
				$('button', $row).click(function() {
					alert('deliver ' + resource_type);
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
			for (var j in mapData.demands)
			{
				var dem = mapData.demands[j];
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
			for (var j in mapData.demands)
			{
				var dem = mapData.demands[j];
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

	var demand = mapData.demands[$row.attr('demand-index')-1];
	if (demand)
	{
		filteredCities[demand[0]] = true;
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
	$('#trainPlan .insertedRow').remove();

	if (!(isPlanning && isPlanning.train))
		return;

	var count = 0;
	for (var i in isPlanning.train.plan)
	{
		var p = isPlanning.train.plan[i];
		if (p.class == "waypoint")
		{
			count++;
			var waypointNumber = count;

			var $row = $('#aWaypointTemplate').clone();
			$row.attr('plan-index', i);
			$row.attr('waypoint-number', waypointNumber);
			$row.addClass('insertedRow');
			$row.addClass(count % 2 == 0 ? 'evenRow' : 'oddRow');
			$('.waypointNumber', $row).text(waypointNumber);
			$('.waypointCity', $row).text(
				mapData.cities[p.location].name
				);
			$row.click(
				function() { selectWaypoint($(this)); }
				);
			$row.show();
			$('#trainPlan table').append($row);

			if (p.location == isPlanning.train.loc)
			{
				removeWaypointSprite(waypointNumber);
			}
			else
			{
				addWaypointSprite(waypointNumber, p.location);
			}
		}
	}
	for (var waypointNumber in waypointSprites)
	{
		if (parseInt(waypointNumber) > count)
		{
			removeWaypointSprite(waypointNumber);
		}
	}
}

function showDemands()
{
	$('#demandsPane .insertedRow').remove();
	var count = 0;
	for (var i in mapData.demands)
	{
		count++;

		var demand = mapData.demands[i];
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
	repaint();
}

function startTrain()
{
	var train = isPlanning && isPlanning.train;
	if (!train)
		return;
	if (train.timer)
		return;

	var l = train.loc;
	train.route = new Array();
	for (var i in train.plan)
	{
		var p = train.plan[i];
		if (p.class == "waypoint")
		{
			l = findBestPath(l, p.location, train.route);
		}
	}

	train.running = true;
	delete train.brandNew;

	onTrainLocationChanged(train);
	animateTrain(train);

	$('#startTrain_btn').hide();
	$('#stopTrain_btn').show();
}

function stopTrain(train)
{
	if (!train && isPlanning)
	{
		train = isPlanning.train;
	}
	if (!train && theTrain)
	{
		train = theTrain;
	}
	if (!train)
		return;

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
