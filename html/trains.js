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
		ctx.save();
		ctx.translate(pt.x, pt.y + CELL_ASCENT/2);
		drawRailsHelper(ctx, t);
		ctx.restore();
	}
	if (t = hasTrackAtDir(cellIdx, 1)) //Northwest
	{
		ctx.save();
		ctx.translate(pt.x + CELL_WIDTH / 4, pt.y - CELL_DESCENT / 2);
		ctx.rotate(Math.PI / 3);
		drawRailsHelper(ctx, t);
		ctx.restore();
	}
	if (t = hasTrackAtDir(cellIdx, 2)) //Northeast
	{
		ctx.save();
		ctx.translate(pt.x + 3 * CELL_WIDTH / 4, pt.y - CELL_DESCENT / 2);
		ctx.rotate(Math.PI * 2 / 3);
		drawRailsHelper(ctx, t);
		ctx.restore();
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
	if (dir == 3)
	{
		return hasTrackAtDir(getAdjacentE(cellIdx), 0);
	}
	else if (dir == 4)
	{
		return hasTrackAtDir(getAdjacentSE(cellIdx), 1);
	}
	else if (dir == 5)
	{
		return hasTrackAtDir(getAdjacentSW(cellIdx), 2);
	}
	else
	{
		var trackIdx = cellIdx * 3 + (dir + 1);
		if (mapData.rails[trackIdx])
			return 1;
		else if (isBuilding && isBuilding.rails[trackIdx])
			return 2;
		else
			return null;
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

function updateTrainPosition(train)
{
	var pt = getCellPoint(train.loc);

	var $t = train.el;
	var origin_pt = $('#theCanvas').position();
	$t.css({
		left: (origin_pt.left + pt.x + CELL_WIDTH/2 - 15) + "px",
		top: (origin_pt.top + pt.y + CELL_ASCENT/2 - 15) + "px"
		});
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

	if (train.tick > 12 * trainRoute.length)
	{
		train.el.remove();
		return;
	}

	var from_idx = Math.floor(train.tick / 12) % trainRoute.length;
	var to_idx = (from_idx + 1) % trainRoute.length;

	var from_pt = getCellPoint(trainRoute[from_idx]);
	var to_pt = getCellPoint(trainRoute[to_idx]);
	var progress = (train.tick % 12) / 12;

	var $t = train.el;
	var origin_pt = $('#theCanvas').position();
	$t.css({
		left: (origin_pt.left + CELL_WIDTH/2 - 15 + from_pt.x + progress * (to_pt.x - from_pt.x) + "px"),
		top: (origin_pt.top + from_pt.y + progress * (to_pt.y - from_pt.y) + "px")
		});

	setTimeout(function() { animateTrain(train) }, 150);
}

function addTrainSprite(trainLoc)
{
	var $t = $('<div class="trainSprite">T</div>');
	$t.css({
		position: "absolute",
		backgroundColor: "#ffff00"
	});
	$('#contentArea').append($t);

	var train = {
		el: $t,
		loc: trainLoc
		};
	updateTrainPosition(train);
	//animateTrain(train);

	return train;
}

var isDragging = null;
function onMouseDown(evt)
{
	if (evt.which != 1) return;

	evt.preventDefault();
	var p = $('#theCanvas').position();
	var cellIdx = getCellFromPoint(
		evt.clientX - p.left,
		evt.clientY - p.top);

	if (cellIdx == 0)
		return;
	var cellP = getCellPoint(cellIdx);

	if (isCity(cellIdx) && isPlanning)
	{
		addCityToPlan(cellIdx);
		return;
	}

	if (!isBuilding) return;


	var canBuild = false;
	if (isCity(cellIdx) || hasTrackAt(cellIdx))
		canBuild = true;

	if (!canBuild)
		return;

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
}

function onMouseMove(evt)
{
	if (!isDragging) return;

	var p = $('#theCanvas').position();
	var cellIdx = getCellFromPoint(
		evt.clientX - p.left,
		evt.clientY - p.top);

	if (cellIdx != isDragging.start)
	{
		track_addSegment(isDragging.start, cellIdx);
		isDragging.start = cellIdx;
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
	$('#theCanvas').mousedown(onMouseDown);
	$('#theCanvas').mouseup(onMouseUp);
	$('#theCanvas').mousemove(onMouseMove);
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

function addLocomotive()
{
	isPlanning = {};
	$('#trainPlan').fadeIn();
}

function dismissPlan()
{
	if (!isPlanning)
		return;

	if (isPlanning.train)
	{
		isPlanning.train.el.remove();
		delete isPlanning.train;
		theTrain = null;
	}

	isPlanning = null;
	mapFeatures.hideTerrain = false;
	mapFeatures.hideUnreachable = false;
	repaint();

	$('#trainPlan').fadeOut();
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
		mapFeatures.hideUnreachable = true;
		repaint();
	}
	else
	{
		isPlanning.train.loc = cellIdx;
		updateTrainPosition(isPlanning.train);
	}
}

function updateAllTrainPositions()
{
	if (theTrain)
	{
		updateTrainPosition(theTrain);
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

function showDemands()
{
	$('#demandsPane .insertedRow').remove();
	var count = 0;
	for (var i in mapData.demands)
	{
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

	var h = $('#demandsPane').innerHeight();
	h -= $('#demandsPane .widgetHeader').outerHeight();
	h -= $('#demandsPane .widgetFooter').outerHeight();
	$('#demandsPane .widgetContent').css('height', h + "px");
}

function dismissDemandsPane()
{
	$('#demandsPane').fadeOut();
}
