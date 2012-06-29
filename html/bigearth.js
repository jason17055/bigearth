var VIEWPORT = {
	latitude: Math.PI/2,
	longitude: 0,
	scale: 450,
	offsetX: 0,
	offsetY: 0,
	translateX: 0,
	translateY: 0
	};

var TERRAIN_IMAGES = {};
var terrainImagesToLoad = 0;
function loadTerrainImage(terrainType)
{
	terrainImagesToLoad++;
	var imageObj = new Image();
	imageObj.onload = function() {
		TERRAIN_IMAGES[terrainType] = imageObj;
		terrainImagesToLoad--;
		if (terrainImagesToLoad == 0)
			repaint();
		};
	imageObj.src = "terrain_textures/"+terrainType+".png";
}
loadTerrainImage("ocean");
loadTerrainImage("desert");
loadTerrainImage("glacier");
loadTerrainImage("tundra");
loadTerrainImage("plains");
loadTerrainImage("grassland");
loadTerrainImage("swamp");
loadTerrainImage("forest");
loadTerrainImage("mountains");
loadTerrainImage("hills");

var CANVASES = [];

function matrixMultiply(A, B)
{
	return [
	[ A[0][0]*B[0][0] + A[0][1]*B[1][0] + A[0][2]*B[2][0],
	  A[0][0]*B[0][1] + A[0][1]*B[1][1] + A[0][2]*B[2][1],
	  A[0][0]*B[0][2] + A[0][1]*B[1][2] + A[0][2]*B[2][2] ],

	[ A[1][0]*B[0][0] + A[1][1]*B[1][0] + A[1][2]*B[2][0],
	  A[1][0]*B[0][1] + A[1][1]*B[1][1] + A[1][2]*B[2][1],
	  A[1][0]*B[0][2] + A[1][1]*B[1][2] + A[1][2]*B[2][2] ],

	[ A[2][0]*B[0][0] + A[2][1]*B[1][0] + A[2][2]*B[2][0],
	  A[2][0]*B[0][1] + A[2][1]*B[1][1] + A[2][2]*B[2][1],
	  A[2][0]*B[0][2] + A[2][1]*B[1][2] + A[2][2]*B[2][2] ]

	];
}

var geometry;
var map;
var coords;
var pawn = null;

function updateTransformMatrix()
{
	var aZ = VIEWPORT.longitude;
	var rZ = [[ Math.cos(aZ), -Math.sin(aZ), 0 ],
		[ Math.sin(aZ), Math.cos(aZ), 0 ],
		[ 0, 0, 1 ]
		];

	var aX = VIEWPORT.latitude;
	var rX = [[ 1, 0, 0 ],
		[ 0, Math.cos(aX), -Math.sin(aX) ],
		[ 0, Math.sin(aX), Math.cos(aX) ]
		];

	if (geometry)
	{
		// desired size of hex in pixels
		var DESIRED_SIZE = 40;
		VIEWPORT.scale = Math.round(geometry.size * 5 * DESIRED_SIZE / Math.PI / 2);
	}

	var I = [[ VIEWPORT.scale,0,0 ], [ 0,VIEWPORT.scale,0 ], [ 0,0,1 ]];
	VIEWPORT.rotMatrix = matrixMultiply(I, matrixMultiply(rX, rZ));
}
updateTransformMatrix();

function toScreenPoint(pt)
{
	var M = VIEWPORT.rotMatrix;
	return {
	x: M[0][0]*pt.x + M[0][1]*pt.y + M[0][2]*pt.z,
	y: M[1][0]*pt.x + M[1][1]*pt.y + M[1][2]*pt.z,
	z: M[2][0]*pt.x + M[2][1]*pt.y + M[2][2]*pt.z
	};
}

function repaintOne(canvasRow, canvasCol)
{
	if (!CANVASES[canvasRow]) return;

	var canvas = CANVASES[canvasRow][canvasCol];
	if (!canvas) return;
	var ctx = canvas.getContext('2d');

	ctx.fillStyle = '#444';
	ctx.fillRect(0,0,canvas.width,canvas.height);
	if (!map)
		return;

	ctx.save();
	ctx.translate(
		-(VIEWPORT.offsetX + 400*canvasCol),
		-(VIEWPORT.offsetY + 400*canvasRow)
		);

	var myPatterns = {};
	for (var i in map.cells)
	{
		var c = map.cells[i];
		var cellIdx = parseInt(i)+1;

		var co = coords.cells[cellIdx];
		var centerP = toScreenPoint(co.pt);
		if (centerP.z < 0)
			continue;

		ctx.save();
		ctx.lineWidth = 1;
		ctx.strokeStyle = '#000';
		ctx.fillStyle =
			c.height < -3 ? '#730' :
			c.height < 0 ? '#940' :
			c.height < 3 ? '#e70' :
			c.height < 7 ? '#fa3' :
			'#fe8';
			
		if (c.water && c.water > 0)
		{
			ctx.fillStyle = c.water <= 3 ? '#68f' :
				c.water <= 6 ? '#05a' :
				'#028';
		}
		if (("waterLevel" in map) && c.height < map.waterLevel)
		{
			var hh = map.waterLevel - c.height;
			ctx.fillStyle = hh <= 2 ? '#68f' : '#05a';
		}

		if (c.terrain && TERRAIN_IMAGES[c.terrain])
		{
			if (!myPatterns[c.terrain])
			{
				var imageObj = TERRAIN_IMAGES[c.terrain];
				var pattern = ctx.createPattern(imageObj,"repeat");
				myPatterns[c.terrain] = pattern;
			}
			ctx.fillStyle = myPatterns[c.terrain];
		}
		else if (c.terrain)
		{
			ctx.fillStyle = c.terrain == 'ocean' ? '#05a' :
				c.terrain == 'glacier' ? '#fff' :
				c.terrain == 'desert' ? '#fd4' :
				c.terrain == 'tundra' ? '#bbe' : '#f0f';
		}

		//	c.height < -3 ? '#05a' :
		//	c.height < 0 ? '#8af' :
		//	c.height < 3 ? '#8f8' :
		//	c.height < 7 ? '#f80' :
		//	'#eee';
 
		ctx.beginPath();
		var p = toScreenPoint(co.pts[0]);
		ctx.moveTo(p.x, p.y);
		for (var j = 0, l = co.pts.length; j < l; j++)
		{
			var p = toScreenPoint(co.pts[(j+1)%l]);
			ctx.lineTo(p.x, p.y);
		}
		ctx.fill();

	// SHOW HEIGHTS
	//	ctx.fillStyle = '#800';
	//	ctx.fillText(c.height, centerP.x, centerP.y-8);

	// SHOW CELL IDS
	//	ctx.fillStyle = '#fff';
	//	ctx.fillText(cellIdx, centerP.x, centerP.y-8);

		if (Math.floor(c.water) != 0)
		{
		ctx.fillStyle = '#ff0';
		ctx.fillText(Math.floor(c.water), centerP.x, centerP.y);
		}
		ctx.restore();
	}

	for (var eId in map.edges)
	{
		var ed = map.edges[eId];
		var p = toScreenPoint(coords.edges[eId].pt);
		if (p.z < .4)
			continue;

		if (!ed.feature)
			continue;

		if (ed.feature == 'river')
		{
			var vv = geometry.getVerticesAdjacentToEdge(eId);
			var p1 = toScreenPoint(coords.vertices[vv[0]].pt);
			var p2 = toScreenPoint(coords.vertices[vv[1]].pt);
			ctx.save();
			ctx.lineWidth = 4;
			ctx.strokeStyle = '#00f';
			ctx.beginPath();
			ctx.moveTo(p1.x,p1.y);
			ctx.lineTo(p2.x,p2.y);
			ctx.stroke();
			ctx.restore();
		}
	}

	for (var vId in map.vertices)
	{
		var v = map.vertices[vId];
		var p = toScreenPoint(coords.vertices[vId].pt);
		if (p.z < 0)
			continue;

		if (!v.water)
			continue;
		if (Math.floor(v.water) == 0)
			continue;

		ctx.save();
		ctx.lineWidth = 1;
		ctx.strokeStyle = '#000';
		ctx.fillStyle = '#00c';
 
		ctx.fillRect(p.x-4,p.y-4,8,8);

		ctx.fillStyle = '#ffc';
		ctx.fillText(Math.floor(v.water), p.x-4, p.y+4);

		ctx.restore();
	}

	ctx.restore();
}

function repaint()
{
	for (var cr = 0; cr < CANVASES.length; cr++)
	{
		for (var cc = 0; cc < CANVASES[cr].length; cc++)
		{
			repaintOne(cr,cc);
		}
	}
}

function onResize()
{
	VIEWPORT.screenWidth = window.innerWidth - 0;
	VIEWPORT.screenHeight = window.innerHeight - $('#buttonBar').outerHeight();
	$('#contentArea').css({
		width: VIEWPORT.screenWidth+"px",
		height: VIEWPORT.screenHeight+"px"
		});
	$('#crossHairs').css({
		left: (Math.round(VIEWPORT.screenWidth/2)-16)+"px",
		top: (Math.round(VIEWPORT.screenHeight/2)-16)+"px"
		});

	exposeCanvases();
}

function resetMap()
{
	map = makeMap(geometry);
	coords = makeCoords(geometry);
	numBumps = 0;
	pawn = null;
}

var gameState;

function onGameState()
{
	map = gameState.map;
	geometry = new SphereGeometry(map.size);
	coords = makeCoords(geometry);
	numBumps = 0;
	pawn = null;
	updateTransformMatrix();
	onResize();
	repaint();
	recreateFleetIcons();
}

function recreateFleetIcons()
{
	$('.fleetIcon').remove();

	if (gameState.fleets)
	{
		for (var fid in gameState.fleets)
		{
			var f = gameState.fleets[fid];
			addFleetImage(f);
		}
	}
}

function addFleetImage(fleet)
{
	var $i = $('<img>');
	$i.attr('src', 'unit_images/'+fleet.type+'.png');
	$i.addClass('fleetIcon');

	var p = toScreenPoint(coords.cells[fleet.location].pt);
	if (p.z < 0.5)
		return;

	$i.css({
		left: (p.x - 32)+"px",
		top: (p.y - 24)+"px"
		});
	$i.attr('virtual-location', fleet.location);
	$('#scrollPanel').append($i);
}

function fetchGameState()
{
	var onSuccess = function(data,status)
	{
		gameState = data;
		onGameState();
	};
	var onError = function(xhr, status, errorThrown)
	{
		//TODO- throw an error
	};

	$.ajax({
	url: "/gamestate",
	success: onSuccess,
	error: onError,
	dataType: "json"
	});
}

$(fetchGameState);

window.onresize = onResize;
$(onResize);

var rotateTimer;
var keepGoing = false;
var rotationIdx = 0;
function doRotation()
{
	rotationIdx++;

	VIEWPORT.longitude += 0.08;
	VIEWPORT.latitude = -Math.sin(0.05*rotationIdx) + Math.PI/2;
	updateTransformMatrix();
	repaint();
	recreateFleetIcons();

	if (keepGoing)
	rotateTimer = setTimeout(doRotation,250);
	else
	rotateTimer = null;
}

function pauseClicked()
{
	keepGoing = !keepGoing;
	if (!rotateTimer) { doRotation(); }
}

function testBtnClicked()
{
	var x = parseInt(document.getElementById('numEntry').value);
	var adj = geometry.getNeighbors(x);
	alert('adjacent to ' + x + ' is ' + adj.join(', '));
}

function bumpMapBtnClicked()
{
	bumpMap(map, coords);
	repaint();

	numBumps++;
	$('#bumpMapBtn').text('Bump Map ('+numBumps+')');
}

var numBumps = 0;
function nextSizeClicked()
{
	geometry = new SphereGeometry((geometry.size+1)%14);
	resetMap();

	document.title = 'Big Earth ' + geometry.size;
	repaint();
}

function panToCoords(pt)
{
	var ptLgt = Math.atan2(pt.y, pt.x);
	var ptLat = Math.asin(pt.z);

	var needsAttitudeChange = null;

	var latInterval = Math.PI/12;
	var curLat = Math.PI/2 - VIEWPORT.latitude;
	if (Math.abs(curLat - ptLat) > latInterval)
	{
		var desiredLat = Math.round(ptLat/latInterval) * latInterval;
		if (desiredLat > 5*latInterval)
			desiredLat = 5*latInterval;
		else if (desiredLat < -5*latInterval)
			desiredLat = -5*latInterval;

		if (Math.abs(desiredLat - curLat) > 0.001)
		{
			needsAttitudeChange = {
			latitude: Math.PI/2 - desiredLat,
			longitude: VIEWPORT.longitude
			};
			curLat = desiredLat;
		}
	}

	var lgtInterval = Math.PI/6;
	var curLgt = Math.PI/2 - VIEWPORT.longitude;
	if (Math.abs(curLgt - ptLgt) > lgtInterval &&
		Math.abs(curLgt + 2*Math.PI - ptLgt) > lgtInterval &&
		Math.abs(curLgt - (ptLgt + 2*Math.PI)) > lgtInterval)
	{
		var desiredLgt = Math.round(ptLgt/lgtInterval) * lgtInterval;
		
		// force re-orientation
		if (!needsAttitudeChange)
			needsAttitudeChange = { latitude: VIEWPORT.latitude };
		needsAttitudeChange.longitude = Math.PI/2 - desiredLgt;
	}

	var p = toScreenPoint(pt);
	var dx = -Math.round(p.x - VIEWPORT.screenWidth / 2);
	var dy = -Math.round(p.y - VIEWPORT.screenHeight / 2);

	VIEWPORT.translateX = dx;
	VIEWPORT.translateY = dy;
	if (!needsAttitudeChange)
		exposeCanvases();

	var $sp = $('#scrollPanel');
	$sp.css({
		'-moz-transition': 'all 1.0s ease-out',
		'-moz-transform': 'translate('+dx+','+dy+')'
		});

	if (needsAttitudeChange)
	{
		var a_pt = fromPolar(Math.PI/2-VIEWPORT.longitude,
				Math.PI/2-VIEWPORT.latitude);
		var a_p = toScreenPoint(a_pt);
		var b_p = p;

		var oldAtt_lat = VIEWPORT.latitude;
		var oldAtt_lgt = VIEWPORT.longitude;
		VIEWPORT.latitude = needsAttitudeChange.latitude;
		VIEWPORT.longitude = needsAttitudeChange.longitude;
		updateTransformMatrix();

		var a_q = toScreenPoint(a_pt);
		var b_q = toScreenPoint(pt);

		VIEWPORT.latitude = oldAtt_lat;
		VIEWPORT.longitude = oldAtt_lgt;
		updateTransformMatrix();

		var a_v = { x: a_q.x-a_p.x, y: a_q.y-a_p.y };
		var b_v = { x: b_q.x-b_p.x, y: b_q.y-b_p.y };
		var a_len = Math.sqrt(Math.pow(a_v.x,2)+Math.pow(a_v.y,2));
		var b_len = Math.sqrt(Math.pow(b_v.x,2)+Math.pow(b_v.y,2));
		var intAngle = Math.acos((a_v.x * b_v.x + a_v.y * b_v.y) / (a_len * b_len));
		var crossProd = a_v.x*b_v.y - a_v.y*b_v.x;
		if (intAngle >= 10*Math.PI/180)
		{
			// apply some sort of rotation effect
			var rotateAmt = (crossProd > 0 ? "-" : "") +
				Math.round(intAngle*180/Math.PI) + "deg";
			$sp.css({
				'-moz-transform': 'translate('+dx+','+dy+') rotate('+rotateAmt+')',
				'-moz-transform-origin': Math.round(b_p.x)+'px '+Math.round(b_p.y)+'px'
				});
		}

		setTimeout(function() {

			// force re-orientation
			CANVASES = [];
			$('.aCanvas').remove();

			VIEWPORT.latitude = needsAttitudeChange.latitude;
			VIEWPORT.longitude = needsAttitudeChange.longitude;
			updateTransformMatrix();
			recreateFleetIcons();

			var p = toScreenPoint(pt);
			var dx = -Math.round(p.x - VIEWPORT.screenWidth / 2);
			var dy = -Math.round(p.y - VIEWPORT.screenHeight / 2);
			VIEWPORT.translateX = dx;
			VIEWPORT.translateY = dy;
			exposeCanvases();

			$sp.css({
				'-moz-transition': 'none',
				'-moz-transform': 'translate('+dx+','+dy+')',
				'-moz-transform-origin': '50% 50%'
				});
		}, 1000);
	}
}

function exposeCanvases()
{
	function newCanvas(row,col)
	{
		var $c = $('<canvas class="aCanvas" width="400" height="400"></canvas>');
		$c.css({
			left: (400*col+VIEWPORT.offsetX) + "px",
			top: (400*row+VIEWPORT.offsetY) + "px"
			});
		$('#scrollPanel').append($c);

		var canvas = $c.get(0);
		canvas.addEventListener('mousedown', onMouseDown, false);
		return canvas;
	}

	var topY = VIEWPORT.offsetY + VIEWPORT.translateY;
	while (topY > 0)
	{
		// add another row of canvases above the current
		CANVASES.unshift([]);
		VIEWPORT.offsetY -= 400;
		topY -= 400;
	}

	var bottomY = topY + 400 * CANVASES.length;
	while (bottomY <= VIEWPORT.screenHeight)
	{
		CANVASES.push([]);
		bottomY += 400;
	}

	var leftX = VIEWPORT.offsetX + VIEWPORT.translateX;
	while (leftX > 0)
	{
		VIEWPORT.offsetX -= 400;
		leftX -= 400;

		for (var row = 0; row < CANVASES.length; row++)
		{
			var C = CANVASES[row];
			var canvas = newCanvas(row, 0);
			C.unshift(canvas);
			repaintOne(row, 0);
		}
	}

	for (var row = 0; row < CANVASES.length; row++)
	{
		var C = CANVASES[row];

		var rightX = leftX + 400*CANVASES[row].length;
		while (rightX <= VIEWPORT.screenWidth)
		{
			rightX += 400;

			var canvas = newCanvas(row, C.length);
			C.push(canvas);
			repaintOne(row,C.length-1);
		}
	}
}

function onMouseDown(evt)
{
	if (evt.which != 1) return;
	evt.preventDefault();

	var canvas = this;
	var screenPt = {
		x: evt.clientX - VIEWPORT.translateX,
		y: evt.clientY - VIEWPORT.translateY
		};

	var xx = getNearestFeatureFromScreen(screenPt, false, true, true);
	if (xx.type == 'vertex')
	{
		var vId = xx.id;
		$('#infoPane .featureType').text('Vertex');
		$('#vId').text(vId);
		$('#infoPane .adjacentCells').text(geometry.getCellsAdjacentToVertex(vId).join('; '));
		$('#infoPane .adjacentVertices').text(geometry.getVerticesAdjacentToVertex(vId).join('; '));
		$('#infoPane').show();

		if (!pawn) { pawn = {}; }
		pawn.locationType = "vertex";
		pawn.location = xx.id;
		repaint();
	}
	else if (xx.type == 'cell')
	{
		return panToCoords(coords.cells[xx.id].pt);

		$('#infoPane .featureType').text('Cell');
		$('#vId').text(xx.id);
		$('#infoPane .adjacentCells').text(geometry.getNeighbors(xx.id).join('; '));
		$('#infoPane .adjacentVertices').text(geometry.getVerticesAdjacentToCell(xx.id).join('; '));
		$('#infoPane').show();

		if (!pawn) { pawn = {}; }
		pawn.locationType = "cell";
		pawn.location = xx.id;
		repaint();
	}
	else if (xx.type == 'edge')
	{
		$('#infoPane .featureType').text('Edge');
		$('#vId').text(xx.id);
		$('#infoPane .adjacentCells').text("");
		$('#infoPane .adjacentVertices').text("");
		$('#infoPane').show();

		if (!pawn) { pawn = {}; }
		pawn.locationType = xx.type;
		pawn.location = xx.id;
		repaint();
	}
}

function getNearestFeatureFromScreen(screenPt, noCells, noEdges, noVertices)
{
	var best = null;
	var bestDist = Infinity;

	if (!noVertices)
	{
		var bestvertex = getVertexFromScreen(screenPt);
		var p = toScreenPoint(coords.vertices[bestvertex].pt);

		bestDist = Math.sqrt(Math.pow(screenPt.x-p.x,2)+
				Math.pow(screenPt.y-p.y,2));
		best = {
		type: "vertex",
		id: bestvertex
		};
	}

	if (!noCells)
	{
	for (var cid in coords.cells)
	{
		var co = coords.cells[cid];

		var p = toScreenPoint(co.pt);
		if (p.z < 0)
			continue;
		var d = Math.sqrt(Math.pow(p.x-screenPt.x,2)+Math.pow(p.y-screenPt.y,2));
		if (d<bestDist)
		{
			best = {
			type: "cell",
			id: parseInt(cid)
			};
			bestDist = d;
		}
	}
	}

	if (!noEdges)
	{
	for (var eid in coords.edges)
	{
		var eco = coords.edges[eid];
		var p = toScreenPoint(eco.pt);
		if (p.z < 0)
			continue;
		var d = Math.sqrt(Math.pow(p.x-screenPt.x,2)+Math.pow(p.y-screenPt.y,2));

		if (d<bestDist)
		{
			best = {
			type: "edge",
			id: eid
			};
			bestDist = d;
		}
	}
	}

	return best;
}

function getVertexFromScreen(screenPt)
{
	var best = null;
	var bestDist = Infinity;

	for (var vId in map.vertices)
	{
		var p = toScreenPoint(coords.vertices[vId].pt);
		if (p.z < 0)
			continue;

		var d = Math.sqrt(Math.pow(p.x-screenPt.x,2)+Math.pow(p.y-screenPt.y,2));
		if (d < bestDist)
		{
			best = vId;
			bestDist = d;
		}
	}
	return best;
}

function addWaterAtPawn()
{
	if (pawn && pawn.locationType == 'cell')
	{
		map.cells[pawn.location-1].water++;
		repaint();
	}
}

function setEdgeFeature(feat)
{
	if (pawn && pawn.locationType == 'edge')
	{
		var edge = map.edges[pawn.location];
		if (feat)
			edge.feature = feat;
		else
			delete edge.feature;
		repaint();
	}
}

function makeRiversClicked()
{
	var R = new RiverFactory();
	while (R.step());
	repaint();
}

function greatFloodClicked()
{
	var $btn = $(this);
	$btn.attr('disabled','disabled');

	var maxHeight = -Infinity;
	var minHeight = Infinity;
	for (var i in map.cells)
	{
		var h = map.cells[i].height;
		if (h > maxHeight)
			maxHeight = h;
		if (h < minHeight)
			minHeight = h;
	}

	map.waterLevel = maxHeight+1;
	repaint();

	var nextStep;
	nextStep = function()
	{
		map.waterLevel--;
		repaint();
		if (map.waterLevel > minHeight)
		{
			setTimeout(nextStep, 2000);
		}
		else
		{
			$btn.removeAttr('disabled');
		}
	};
	setTimeout(nextStep, 2000);
}

function testSeaLevelClicked()
{
	findSeaLevel(map, 0.6);
}
