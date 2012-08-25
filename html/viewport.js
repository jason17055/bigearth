var DESIRED_CELL_SIZE = 64;
var VIEWPORT_TILE_SIZE = 400;
var VIEWPORT_TILE_OVERFLOW = 80;

var TERRAIN_IMAGES = {};
var imagesToLoad = 0;
var viewportsWaitingOnImages = [];
function loadTerrainImage(terrainType)
{
	imagesToLoad++;
	var imageObj = new Image();
	imageObj.onload = function() {
		TERRAIN_IMAGES[terrainType] = imageObj;
		imagesToLoad--;
		if (imagesToLoad == 0)
			allImagesLoaded();
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
loadTerrainImage("jungle");

var CITIES_IMAGE = null;
function loadCitiesImage()
{
	imagesToLoad++;
	var imageObj = new Image();
	imageObj.onload = function() {
		CITIES_IMAGE = imageObj;
		imagesToLoad--;
		if (imagesToLoad == 0)
			allImagesLoaded();
		};
	imageObj.src = "city_images/ancientcities.png";
}
loadCitiesImage();

function allImagesLoaded()
{
	//VIEWPORT.repaint();
}

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

function BigEarthViewPort(containerElement)
{
	this.el = containerElement;

	this.latitude = Math.PI/2;
	this.longitude = 0;
	this.scale = 450;
	this.offsetX = 0;
	this.offsetY = 0;
	this.translateX = 0;
	this.translateY = 0;

	this.canvases = [];
	this.deferredRepaints = { todo: {} };
}

BigEarthViewPort.prototype.initialize = function()
{
	this.updateTransformMatrix();
	this.translateX = -Math.round(-this.screenWidth/2);
	this.translateY = -Math.round(-this.screenHeight/2);

	var translateRule = 'translate('+this.translateX+'px,'+this.translateY+'px)';

	$('.bigearth-scrollPanel', this.el).css({
		'-webkit-transition':'none',
		'-moz-transition':'none',
		'-webkit-transform':translateRule,
		'-moz-transform':translateRule
		});
	CANVASES = [];
	$('.aCanvas', this.el).remove();
	this.exposeCanvases();
	this.recreateFleetIcons();

};

BigEarthViewPort.prototype.recreateFleetIcons = function()
{
	var $selected = $('.fleetIcon.selectedFleet', this.el);
	var selectedFleet = null;
	if ($selected.length)
	{
		selectedFleet = $selected.attr('fleet-id');
	}

	$('.fleetIcon', this.el).remove();

	if (fleets)
	{
		for (var fid in fleets)
		{
			this.updateFleetIcon(fid);
		}
	}

	if (selectedFleet)
	{
		this.selectFleet(selectedFleet);
	}
};

BigEarthViewPort.prototype.toScreenPoint = function(pt)
{
	var M = this.rotMatrix;
	return {
	x: M[0][0]*pt.x + M[0][1]*pt.y + M[0][2]*pt.z,
	y: M[1][0]*pt.x + M[1][1]*pt.y + M[1][2]*pt.z,
	z: M[2][0]*pt.x + M[2][1]*pt.y + M[2][2]*pt.z
	};
};

BigEarthViewPort.prototype.repaintOne = function(canvasRow, canvasCol)
{
	if (!this.canvases[canvasRow]) return;

	var canvas = this.canvases[canvasRow][canvasCol];
	if (!canvas) return;
	var ctx = canvas.getContext('2d');

	ctx.fillStyle = '#444';
	ctx.fillRect(0,0,canvas.width,canvas.height);
	if (!map)
		return;

	ctx.save();
	ctx.translate(
		-(this.offsetX + VIEWPORT_TILE_SIZE*canvasCol),
		-(this.offsetY + VIEWPORT_TILE_SIZE*canvasRow)
		);

	var citiesToDraw = [];
	var myPatterns = {};
	for (var cid in map.cells)
	{
		var c = map.cells[cid];
		if (!c)
			continue;

		var co = coords.cells[cid];
		var centerP = this.toScreenPoint(co.pt);
		if (centerP.z < 0)
			continue;

		ctx.save();
		ctx.lineWidth = 0;
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
		var p = this.toScreenPoint(co.pts[0]);
		ctx.moveTo(p.x, p.y);
		for (var j = 0, l = co.pts.length; j < l; j++)
		{
			var p = this.toScreenPoint(co.pts[(j+1)%l]);
			ctx.lineTo(p.x, p.y);
		}
		ctx.fill();

		if (c.city)
		{
			citiesToDraw.push({
				city: c.city,
				location: cid,
				screenPt: centerP
				});
		}

	// SHOW HEIGHTS
	//	ctx.fillStyle = '#800';
	//	ctx.fillText(c.height, centerP.x, centerP.y-8);

	// SHOW CELL IDS
	//	ctx.fillStyle = '#fff';
	//	ctx.fillText(cellIdx, centerP.x, centerP.y-8);

		ctx.restore();
	}

	for (var eId in map.edges)
	{
		var ed = map.edges[eId];
		var p = this.toScreenPoint(coords.edges[eId].pt);
		if (p.z < .4)
			continue;

		if (!(ed && ed.feature))
			continue;

		if (ed.feature == 'river')
		{
			var vv = geometry.getVerticesAdjacentToEdge(eId);
			var p1 = this.toScreenPoint(coords.vertices[vv[0]].pt);
			var p2 = this.toScreenPoint(coords.vertices[vv[1]].pt);
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
		var p = this.toScreenPoint(coords.vertices[vId].pt);
		if (p.z < 0)
			continue;

		if (!v || !v.water)
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

	for (var i = 0; i < citiesToDraw.length; i++)
	{
		var cityInfo = citiesToDraw[i];
		var p = cityInfo.screenPt;

		if (CITIES_IMAGE)
		{
			ctx.drawImage(CITIES_IMAGE,1,1,96,72,
				Math.round(p.x-48-6),
				Math.round(p.y-36-12),
				96,72);
		}
	}

	for (var i = 0; i < citiesToDraw.length; i++)
	{
		var cityInfo = citiesToDraw[i];
		var p = cityInfo.screenPt;

		var cityName = cityInfo.city.name || ('city'+cityInfo.city.id);
		ctx.font = 'bold 16px sans-serif';
		var m = ctx.measureText(cityName);

		ctx.fillStyle = '#000';
		ctx.globalAlpha = 0.4;
		ctx.fillRect(p.x-Math.round(m.width/2)-2,p.y+12,Math.round(m.width)+4,19);

		ctx.globalAlpha = 1;
		ctx.fillStyle = '#fff';
		ctx.fillText(cityName, p.x-m.width/2, p.y+12+15);
	}

	ctx.restore();
};

BigEarthViewPort.prototype.repaint = function()
{
	for (var cr = 0; cr < CANVASES.length; cr++)
	{
		for (var cc = 0; cc < CANVASES[cr].length; cc++)
		{
			//this.repaintOne(cr,cc);
		}
	}
};

BigEarthViewPort.prototype.updateTransformMatrix = function()
{
	var aZ = this.longitude;
	var rZ = [[ Math.cos(aZ), -Math.sin(aZ), 0 ],
		[ Math.sin(aZ), Math.cos(aZ), 0 ],
		[ 0, 0, 1 ]
		];

	var aX = this.latitude;
	var rX = [[ 1, 0, 0 ],
		[ 0, Math.cos(aX), -Math.sin(aX) ],
		[ 0, Math.sin(aX), Math.cos(aX) ]
		];

	if (geometry)
	{
		// desired size of hex in pixels
		var desiredCellSize = this.cellSize || DESIRED_CELL_SIZE;
		this.scale = Math.round(geometry.size * 5 * desiredCellSize / Math.PI / 2);
	}

	var I = [[ this.scale,0,0 ], [ 0,this.scale,0 ], [ 0,0,1 ]];
	this.rotMatrix = matrixMultiply(I, matrixMultiply(rX, rZ));
};

BigEarthViewPort.prototype.sizeChanged = function(newWidth, newHeight)
{
	this.screenWidth = newWidth;
	this.screenHeight = newHeight;

	this.exposeCanvases();
};

BigEarthViewPort.prototype.newCanvas = function(row, col)
{
	var $scrollPanel = $('.bigearth-scrollPanel', this.el);

	var $c = $('<canvas class="aCanvas"></canvas>');
	$c.attr('width', VIEWPORT_TILE_SIZE);
	$c.attr('height', VIEWPORT_TILE_SIZE);
	$c.css({
		left: (VIEWPORT_TILE_SIZE*col+this.offsetX) + "px",
		top: (VIEWPORT_TILE_SIZE*row+this.offsetY) + "px"
		});
	$scrollPanel.append($c);

	var self = this;
	var canvas = $c.get(0);
	canvas.addEventListener('mousedown', function(evt) {
		self.onMouseDown(evt);
		}, false);
	return canvas;
};

BigEarthViewPort.prototype.exposeCanvases = function()
{
	var topY = this.offsetY + this.translateY;
	while (topY > 0)
	{
		// add another row of canvases above the current
		this.canvases.unshift([]);
		this.offsetY -= VIEWPORT_TILE_SIZE;
		topY -= VIEWPORT_TILE_SIZE;
	}

	var bottomY = topY + VIEWPORT_TILE_SIZE * this.canvases.length;
	while (bottomY <= VIEWPORT.screenHeight)
	{
		this.canvases.push([]);
		bottomY += VIEWPORT_TILE_SIZE;
	}

	var leftX = this.offsetX + this.translateX;
	while (leftX > 0)
	{
		this.offsetX -= VIEWPORT_TILE_SIZE;
		leftX -= VIEWPORT_TILE_SIZE;

		for (var row = 0; row < this.canvases.length; row++)
		{
			var C = this.canvases[row];
			var canvas = this.newCanvas(row, 0);
			C.unshift(canvas);
			this.repaintOne(row, 0);
		}
	}

	for (var row = 0; row < this.canvases.length; row++)
	{
		var C = this.canvases[row];

		var rightX = leftX + VIEWPORT_TILE_SIZE*this.canvases[row].length;
		while (rightX <= this.screenWidth)
		{
			rightX += VIEWPORT_TILE_SIZE;

			var canvas = this.newCanvas(row, C.length);
			C.push(canvas);
			this.repaintOne(row,C.length-1);
		}
	}
};

BigEarthViewPort.prototype.removeFleetIcon = function(fleetId)
{
	var $f = $('.fleetIcon[fleet-id="'+fleetId+'"]', this.el);
	$f.remove();
};

BigEarthViewPort.prototype.updateFleetIcon = function(fleetId)
{
	var $f = $('.fleetIcon[fleet-id="'+fleetId+'"]', this.el);

	var fleetInfo = fleets[fleetId];
	if (!fleetInfo)
	{
		$f.remove();
		return;
	}

	var p = this.toScreenPoint(coords.cells[fleetInfo.location].pt);
	if (p.z < 0.5)
	{
		$f.remove();
		return;
	}

	var self = this;
	if ($f.length == 0)
	{
		$f = $('<div class="fleetIcon"><img class="selectionCircle" src="fleet_selection_circle_front.gif"><img class="unitIcon"><span class="ownerIcon"></span><span class="activityIcon"></span></div>');
		$f.attr('fleet-id', fleetId);
		$('.bigearth-scrollPanel', this.el).append($f);

		var imgEl = $('img.unitIcon',$f).get(0);
		$(imgEl).click(function() {
			if (self.fleetClicked)
			{
				(self.fleetClicked)(fleetId);
			}
			});
		imgEl.addEventListener('dragstart',
			function(evt) {
				return self.onFleetDragStart(fleetId, evt);
			}, false);
	}

	$('img.unitIcon',$f).attr('src', 'unit_images/'+fleetInfo.type+'.png');

	var delay = fleetInfo.stepDelay ? (fleetInfo.stepDelay / 2) / 1000 : 0.5;
	$f.css({
		'-webkit-transition': 'all '+delay+'s ease-out',
		'-moz-transition': 'all '+delay+'s ease-out',
		left: (p.x - 32)+"px",
		top: (p.y - 24)+"px"
		});

	if (fleetInfo.activity)
	{
		$('.activityIcon',$f).text('B');
		$('.activityIcon',$f).show();
	}
	else
	{
		$('.activityIcon',$f).hide();
	}

	if (fleetInfo.owner != gameState.identity)
	{
		$('.ownerIcon',$f).text('E');
		$('.ownerIcon',$f).show();
	}
	else
	{
		$('.ownerIcon',$f).hide();
	}
};

BigEarthViewPort.prototype.doDeferredRepaint = function()
{
	for (var k in this.deferredRepaints.todo)
	{
		var kk = k.split(/,/);
		var ix = +kk[0];
		var iy = +kk[1];
		//this.repaintOne(iy, ix);
	}
	this.deferredRepaints.todo = {};
	this.deferredRepaints.timer = null;
};

BigEarthViewPort.prototype.triggerRepaintCanvasTile = function(x, y)
{
	this.deferredRepaints.todo[x+','+y] = true;
	if (!this.deferredRepaints.timer)
	{
		var self = this;
		this.deferredRepaints.timer = setTimeout(function() {
			self.doDeferredRepaint();
			}, 1000);
	}
};

BigEarthViewPort.prototype.triggerRepaintCell = function(cellIdx)
{
	var p = this.toScreenPoint(coords.cells[cellIdx].pt);
	if (p.z <= 0) return;

	if (p.x < this.offsetX)
		return;
	if (p.y < this.offsetY)
		return;

	var ix = Math.floor((p.x - this.offsetX) / VIEWPORT_TILE_SIZE);
	var iy = Math.floor((p.y - this.offsetY) / VIEWPORT_TILE_SIZE);
	var ox = (p.x - this.offsetX) - VIEWPORT_TILE_SIZE * ix;
	var oy = (p.y - this.offsetY) - VIEWPORT_TILE_SIZE * iy;

	this.triggerRepaintCanvasTile(ix, iy);
	if (ox < VIEWPORT_TILE_OVERFLOW)
		this.triggerRepaintCanvasTile(ix-1, iy);
	else if (ox >= VIEWPORT_TILE_SIZE - VIEWPORT_TILE_OVERFLOW)
		this.triggerRepaintCanvasTile(ix+1, iy);
	if (oy < VIEWPORT_TILE_OVERFLOW)
		this.triggerRepaintCanvasTile(ix, iy-1);
	else if (oy >= VIEWPORT_TILE_SIZE - VIEWPORT_TILE_OVERFLOW)
		this.triggerRepaintCanvasTile(ix, iy+1);
};

BigEarthViewPort.prototype.showDragTargetIndicator = function(location)
{
	var p = this.toScreenPoint(coords.cells[location].pt);
	$('#dragTargetIndicator', this.el).css({
		left: p.x-10,
		top: p.y-10
		});
	$('#dragTargetIndicator', this.el).show();
}

BigEarthViewPort.prototype.hideDragTargetIndicator = function()
{
	$('#dragTargetIndicator', this.el).hide();
};

BigEarthViewPort.prototype.onFleetDragStart = function(fleetId, evt)
{
	evt.dataTransfer.effectAllowed = 'move';
	evt.dataTransfer.setData('applicaton/bigearth+fleet', fleetId);
	evt.dataTransfer.setDragImage(
		document.getElementById('cursorGotoImg'),
		9, 32);

	var dragHandler = function(evvt) {
		evvt.preventDefault();
		var screenPt = {
		x: evvt.clientX - VIEWPORT.translateX,
		y: evvt.clientY - VIEWPORT.translateY
		};
		var xx = getNearestFeatureFromScreen(screenPt, false, true, true);
		showDragTargetIndicator(xx.id);
		return false;
	};
	var dropHandler = function(evvt) {
		evvt.preventDefault();
		var screenPt = {
		x: evvt.clientX - VIEWPORT.translateX,
		y: evvt.clientY - VIEWPORT.translateY
		};
		var xx = getNearestFeatureFromScreen(screenPt, false, true, true);
		orderGoTo(fleetId, xx.id);
		return false;
	};

	var spEl = $('.bigearth-scrollPanel', this.el).get(0);
	spEl.addEventListener('dragover', dragHandler, false);
	spEl.addEventListener('drop', dropHandler, false);

	var iconEl = this;
	var dragEndHandler;
	dragEndHandler = function() {
		spEl.removeEventListener('dragover',dragHandler);
		spEl.removeEventListener('drop', dropHandler);
		iconEl.removeEventListener('dragend', dragEndHandler);
		hideDragTargetIndicator();
		};
	iconEl.addEventListener('dragend', dragEndHandler, false);

	return false;
};

BigEarthViewPort.prototype.onMouseDown = function(evt)
{
	if (evt.which != 1) return;
	evt.preventDefault();

	var canvas = this;
	var screenPt = {
		x: evt.clientX - this.translateX,
		y: evt.clientY - this.translateY
		};

	var xx = this.getNearestFeatureFromScreen(screenPt, false, true, true);
	if (xx.type == 'cell')
	{
		if (map.cells[xx.id] && map.cells[xx.id].city)
		{
			if (this.cityClicked)
			{
				(this.cityClicked)(xx.id, map.cells[xx.id].city);
			}
		}
		else
		{
			return this.panToCoords(coords.cells[xx.id].pt);
		}
	}
};

BigEarthViewPort.prototype.getNearestFeatureFromScreen = function(screenPt, noCells, noEdges, noVertices)
{
	var best = null;
	var bestDist = Infinity;

	if (!noVertices)
	{
	for (var vId in map.vertices)
	{
		var p = this.toScreenPoint(coords.vertices[vId].pt);
		if (p.z < 0)
			continue;

		var d = Math.sqrt(Math.pow(p.x-screenPt.x,2) + Math.pow(p.y-screenPt.y,2));
		if (d < bestDist)
		{
			best = {
			type: "vertex",
			id: vId
			};
			bestDist = d;
		}
	}
	}

	if (!noCells)
	{
	for (var cid in coords.cells)
	{
		var co = coords.cells[cid];

		var p = this.toScreenPoint(co.pt);
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
		var p = this.toScreenPoint(eco.pt);
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
};

BigEarthViewPort.prototype.panToCoords = function(pt)
{
	var ptLgt = Math.atan2(pt.y, pt.x);
	var ptLat = Math.asin(pt.z);

	var needsAttitudeChange = null;

	var latInterval = Math.PI/12;
	var curLat = Math.PI/2 - this.latitude;
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
			longitude: this.longitude
			};
			curLat = desiredLat;
		}
	}

	var lgtInterval = Math.PI/6;
	var curLgt = Math.PI/2 - this.longitude;
	if (Math.abs(curLgt - ptLgt) > lgtInterval &&
		Math.abs(curLgt + 2*Math.PI - ptLgt) > lgtInterval &&
		Math.abs(curLgt - (ptLgt + 2*Math.PI)) > lgtInterval)
	{
		var desiredLgt = Math.round(ptLgt/lgtInterval) * lgtInterval;
		
		// force re-orientation
		if (!needsAttitudeChange)
			needsAttitudeChange = { latitude: this.latitude };
		needsAttitudeChange.longitude = Math.PI/2 - desiredLgt;
	}

	var p = this.toScreenPoint(pt);
	var dx = -Math.round(p.x - this.screenWidth / 2);
	var dy = -Math.round(p.y - this.screenHeight / 2);

	this.translateX = dx;
	this.translateY = dy;
	if (!needsAttitudeChange)
		this.exposeCanvases();

	var $sp = $('.bigearth-scrollPanel', this.el);
	$sp.css({
		'-webkit-transition': 'all 1.0s ease-out',
		'-moz-transition': 'all 1.0s ease-out',
		'-webkit-transform': 'translate('+dx+'px,'+dy+'px)',
		'-moz-transform': 'translate('+dx+'px,'+dy+'px)'
		});

	if (needsAttitudeChange)
	{
		var a_pt = fromPolar(Math.PI/2-this.longitude,
				Math.PI/2-this.latitude);
		var a_p = this.toScreenPoint(a_pt);
		var b_p = p;

		var oldAtt_lat = this.latitude;
		var oldAtt_lgt = this.longitude;
		this.latitude = needsAttitudeChange.latitude;
		this.longitude = needsAttitudeChange.longitude;
		this.updateTransformMatrix();

		var a_q = this.toScreenPoint(a_pt);
		var b_q = this.toScreenPoint(pt);

		this.latitude = oldAtt_lat;
		this.longitude = oldAtt_lgt;
		this.updateTransformMatrix();

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
				'-webkit-transform': 'translate('+dx+'px,'+dy+'px) rotate('+rotateAmt+')',
				'-moz-transform': 'translate('+dx+'px,'+dy+'px) rotate('+rotateAmt+')',
				'-webkit-transform-origin': Math.round(b_p.x)+'px '+Math.round(b_p.y)+'px',
				'-moz-transform-origin': Math.round(b_p.x)+'px '+Math.round(b_p.y)+'px'
				});
		}

		var self = this;
		setTimeout(function() {

			// force re-orientation
			self.latitude = needsAttitudeChange.latitude;
			self.longitude = needsAttitudeChange.longitude;
			self.resetCanvases(pt);

		}, 1000);
	}
}

BigEarthViewPort.prototype.resetCanvases = function(pt)
{
	this.canvases = [];
	$('.aCanvas', this.el).remove();

	this.updateTransformMatrix();
	this.recreateFleetIcons();

	var p = this.toScreenPoint(pt);
	var dx = -Math.round(p.x - this.screenWidth / 2);
	var dy = -Math.round(p.y - this.screenHeight / 2);
	this.translateX = dx;
	this.translateY = dy;
	this.exposeCanvases();

	var $sp = $('.bigearth-scrollPanel', this.el);
	$sp.css({
		'-webkit-transition': 'none',
		'-moz-transition': 'none',
		'-webkit-transform': 'translate('+dx+'px,'+dy+'px)',
		'-moz-transform': 'translate('+dx+'px,'+dy+'px)',
		'-webkit-transform-origin': '50% 50%',
		'-moz-transform-origin': '50% 50%'
		});
};
