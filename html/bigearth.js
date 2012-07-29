var VIEWPORT = {
	latitude: Math.PI/2,
	longitude: 0,
	scale: 450,
	offsetX: 0,
	offsetY: 0,
	translateX: 0,
	translateY: 0
	};
var VIEWPORT_TILE_SIZE = 400;
var VIEWPORT_TILE_OVERFLOW = 40;

var TERRAIN_IMAGES = {};
var imagesToLoad = 0;
function loadTerrainImage(terrainType)
{
	imagesToLoad++;
	var imageObj = new Image();
	imageObj.onload = function() {
		TERRAIN_IMAGES[terrainType] = imageObj;
		imagesToLoad--;
		if (imagesToLoad == 0)
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
			repaint();
		};
	imageObj.src = "city_images/ancientcities.png";
}
loadCitiesImage();


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
var fleets;
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
		-(VIEWPORT.offsetX + VIEWPORT_TILE_SIZE*canvasCol),
		-(VIEWPORT.offsetY + VIEWPORT_TILE_SIZE*canvasRow)
		);

	var citiesToDraw = [];
	var myPatterns = {};
	for (var cid in map.cells)
	{
		var c = map.cells[cid];
		if (!c)
			continue;

		var co = coords.cells[cid];
		var centerP = toScreenPoint(co.pt);
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
		var p = toScreenPoint(co.pts[0]);
		ctx.moveTo(p.x, p.y);
		for (var j = 0, l = co.pts.length; j < l; j++)
		{
			var p = toScreenPoint(co.pts[(j+1)%l]);
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
		var p = toScreenPoint(coords.edges[eId].pt);
		if (p.z < .4)
			continue;

		if (!(ed && ed.feature))
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

var gameState;

function onMapReplaced()
{
	pawn = null;

	updateTransformMatrix();
	onResize();
	VIEWPORT.translateX = -Math.round(-VIEWPORT.screenWidth/2);
	VIEWPORT.translateY = -Math.round(-VIEWPORT.screenHeight/2);

	var translateRule = 'translate('+VIEWPORT.translateX+'px,'+VIEWPORT.translateY+'px)';

	$('#scrollPanel').css({
		'-webkit-transition':'none',
		'-moz-transition':'none',
		'-webkit-transform':translateRule,
		'-moz-transform':translateRule
		});
	CANVASES = [];
	$('.aCanvas').remove();
	exposeCanvases();
	recreateFleetIcons();

	fetchNextEvent();
}

function onGameState()
{
	geometry = new SphereGeometry(gameState.mapSize);
	coords = makeCoords(geometry);
	if (gameState.map)
		fetchMap();
	if (gameState.fleets)
		fetchFleets();

	if (gameState.role == 'observer')
	{
		window.location.href = '/login.html';
	}
}

function recreateFleetIcons()
{
	$('.fleetIcon').remove();

	if (fleets)
	{
		for (var fid in fleets)
		{
			var f = fleets[fid];
			updateFleetIcon(fid, f);
		}
	}
}

function onCityClicked(location, city)
{
	resetCityPane();
	loadCityInfo(city, location);

	$('#fleetPane').hide();
	$('#cityPane').show();
}

function onJobBoxDragStart(evt)
{
	var $countBox = $(this);

	var jobBoxEl = this;
	while (jobBoxEl && !jobBoxEl.hasAttribute('job'))
		jobBoxEl = jobBoxEl.parentNode;

	if (!jobBoxEl)
		return;
	var job = jobBoxEl.getAttribute('job');
	var count = +($countBox.text());

	evt.dataTransfer.effectAllowed = 'move';
	evt.dataTransfer.setData('application/bigearth+workers', job + " " + count);
	this.style.opacity = 0.4;
}

function onJobBoxDragEnd(evt)
{
	this.style.opacity = 1.0;

	var jobBoxEl = this;
	while (jobBoxEl && !jobBoxEl.hasAttribute('job'))
		jobBoxEl = jobBoxEl.parentNode;

	if (!jobBoxEl)
		return;

	if (evt.dataTransfer.dropEffect == 'move')
	{
		var t = evt.dataTransfer.getData('application/bigearth+workers');
		var tt = t.split(/ /);
		var amountMoved = +tt[1];

		var myCount = +($(this).text());
		myCount -= amountMoved;

		var numCounts = $('.jobCount', $(jobBoxEl)).length;

		if (myCount > 0 || numCounts == 1)
		{
			$(this).text(myCount);
		}
		else
		{
			$(this).remove();
		}
	}
}

function onJobBoxDragEnter(evt)
{
	// the idea here is to highlight the box when it is ready to
	// accept a drop; unfortunately, the 'dragenter' and 'dragleave'
	// events are fired only for the mouse cursor being over the
	// part of this element not taken up by the text

	evt.stopPropagation();
	evt.preventDefault();
	this.classList.add('over');
}

function onJobBoxDragLeave(evt)
{
	// the idea here is to highlight the box when it is ready to
	// accept a drop; unfortunately, the 'dragenter' and 'dragleave'
	// events are fired only for the mouse cursor being over the
	// part of this element not taken up by the text

	evt.stopPropagation();
	evt.preventDefault();
	this.classList.remove('over');
}

function onJobBoxDragOver(evt)
{
	if (evt.dataTransfer.types.contains('application/bigearth+workers'))
	{
		evt.stopPropagation();
		evt.preventDefault();
		evt.dropEffect = 'move';
	}
	return false;
}

function onJobBoxDrop(evt)
{
	var $countBox = $(this);

	var jobBoxEl = this;
	while (jobBoxEl && !jobBoxEl.hasAttribute('job'))
		jobBoxEl = jobBoxEl.parentNode;

	if (!jobBoxEl)
		return;
	var job = jobBoxEl.getAttribute('job');
	var count = +($countBox.text());

	var t = evt.dataTransfer.getData('application/bigearth+workers');
	var tt = t.split(/ /);
	$countBox.text(count + (+tt[1]));

	if (tt[0] != job)
		transferWorkers(+tt[1], tt[0], job);
}

function addJobBoxEventListeners(jobBoxEl)
{
	$(jobBoxEl).click(onJobBoxClicked);
	jobBoxEl.addEventListener('dragstart', onJobBoxDragStart, false);
	jobBoxEl.addEventListener('dragend', onJobBoxDragEnd, false);
	//jobBoxEl.addEventListener('dragenter', onJobBoxDragEnter, false);
	jobBoxEl.addEventListener('dragover', onJobBoxDragOver, false);
	//jobBoxEl.addEventListener('dragleave', onJobBoxDragLeave, false);
	jobBoxEl.addEventListener('drop', onJobBoxDrop, false);
}

function onJobBoxClicked()
{
	var $countBox = $(this);

	var jobBoxEl = this;
	while (jobBoxEl && !jobBoxEl.hasAttribute('job'))
		jobBoxEl = jobBoxEl.parentNode;

	if (!jobBoxEl)
		return;

	var $jobBox = $(jobBoxEl);
	var job = jobBoxEl.getAttribute('job');

	var curCount = +($countBox.text());
	var newCountA = Math.ceil(curCount/3);
	var newCountB = curCount - newCountA;
	if (newCountA == 0 || newCountB == 0)
		return;

	var $aCountBox = $countBox.clone(false);
	addJobBoxEventListeners($aCountBox.get(0));
	$countBox.text(newCountA);
	$aCountBox.text(newCountB);
	$countBox.after($aCountBox);
}

function resetCityPane()
{
	var el = document.getElementById('cityNewJobChoice');
	if (el) { el.value = ""; }

	$('#cityPane .cityJobBox').remove();
}

function cityMakeJobBox(job)
{
	var $jobBox = $('#cityPane .cityJobBox[job="'+job+'"]');
	if ($jobBox.length == 0)
	{
		$jobBox = $('#cityPane .cityJobBoxTemplate').clone();
		$jobBox.attr('class','cityJobBox');
		$jobBox.attr('job',job);
		addJobBoxEventListeners($('.jobCount',$jobBox).get(0));
		$('.jobLabel',$jobBox).text(job);
		$('#cityPane .cityJobsContainer').append($jobBox);
	}
	return $jobBox;
}

function getGameTime()
{
	var elapsed = new Date().getTime() - gameState.timeStamp;
	return gameState.gameYear + elapsed / gameState.gameSpeed;
}

var progressBarAnimation = null;
function animateCityActivityProgressBar(city)
{
	if (progressBarAnimation)
	{
		if (progressBarAnimation.city == city)
			return;
		else
			clearTimeout(progressBarAnimation.timer);

		progressBarAnimation = null;
	}

	var $cac = $('#cityPane .cityActivityComplete');

	var myAnim = { city: city };

	if (city && city.activity)
	{
		if (city.activityTime || city.activitySpeed || city.activityComplete)
		{
			var gameTime = getGameTime();
			var el = gameTime - city.activityTime;
			var complete = +city.activityComplete + el * city.activitySpeed;
			$cac.css({
			width: Math.round(complete*100)+'%'
			});

	progressBarAnimation = myAnim;
	myAnim.timer = 
	setTimeout(function() {
			if (progressBarAnimation == myAnim)
			{
				progressBarAnimation = null;
				animateCityActivityProgressBar(city);
			}
		}, 400);

			return;
		}
	}
	$cac.css({ width: 0 });
}

function loadCityInfo(city, location)
{
	var mapCell = map.cells[location];
	if (!mapCell)
		return;
	if (!mapCell.subcells)
		mapCell.subcells = {};

	$('#cityPane').attr('city-id', city.id);
	$('#cityPane .cityName').text(city.name);
	$('#cityPane .citySize').text(mapCell.subcells.hamlet || 0);
	$('#cityPane .cityPopulation').text(city.population + city.children);
	$('#cityPane .cityChildren').text(city.children);
	$('#cityPane .cityWorkersCount').text(city.population);
	$('#cityResourcesContainer div').remove();
	var RESOURCE_TYPES = [ 'food', 'wood', 'clay', 'stone', 'fuel' ];
	var RESOURCE_DISPLAY_NAMES = {
		food: "Food",
		wood: "Wood",
		clay: "Clay",
		stone: "Stone",
		fuel: "Fuel"
		};
	for (var i = 0; i < RESOURCE_TYPES.length; i++)
	{
		var t = RESOURCE_TYPES[i];
		if (city[t])
		{
			var $x = $('<div><span class="cityResourceType"></span>: <span class="cityResourceAmount"></span></div>');
			$('.cityResourceType', $x).text(RESOURCE_DISPLAY_NAMES[t]);
			$('.cityResourceAmount', $x).text(city[t]);
			$('#cityResourcesContainer').append($x);
		}
	}
	$('#cityPane .cityFuel').text(city.fuel);
	$('#cityPane .cityFarms').text(mapCell.subcells.farm);
	if (mapCell.subcells.farm)
		$('#cityPane .cityFarmsContainer').show();
	else
		$('#cityPane .cityFarmsContainer').hide();
	$('#cityPane img.icon').attr('src', 'city_images/city1.png');
	$('#cityPane .cityActivity').text(city.activity || '');
	animateCityActivityProgressBar(city);

	if (city.workers)
	{
		for (var job in city.workers)
		{
			var count = city.workers[job];

			var $jobBox = cityMakeJobBox(job);

			var $jobCounts = $('.jobCount', $jobBox);
			var targetCount = count;
			var toRemove = [];
			for (var i = 0; i + 1 < $jobCounts.length; i++)
			{
				var $aCount = $($jobCounts.get(i));
				var t = +($aCount.text());
				if (targetCount > t)
				{
					targetCount -= t;
				}
				else
				{
					toRemove.push($aCount);
				}
			}
			$($jobCounts.get($jobCounts.length-1)).text(targetCount);
			for (var i = 0; i < toRemove.length; i++)
			{
				toRemove[i].remove();
			}
		}

		var $jobBoxen = $('#cityPane .cityJobBox');
		for (var i = 0; i < $jobBoxen.length; i++)
		{
			var $jobBox = $($jobBoxen.get(i));
			var job = $jobBox.attr('job');
			if (!city.workers[job])
				$('.jobCount',$jobBox).text('0');
		}
	}
}

function onFleetClicked(fleetId)
{
	$('#cityPane').hide();

	var fleet = fleets[fleetId];
	if (!fleet)
		return;

	$('#fleetPane').attr('fleet-id', fleetId);
	$('#fleetPane img.icon').attr('src','unit_images/'+fleet.type+'.png');
	$('#fleetPane .featureType').text(fleet.type);
	$('#fleetPane').show();
}

function removeFleetIcon(fleetId)
{
	var $f = $('.fleetIcon[fleet-id="'+fleetId+'"]');
	$f.remove();
}

function updateFleetIcon(fleetId, fleetInfo)
{
	var $f = $('.fleetIcon[fleet-id="'+fleetId+'"]');

	var p = toScreenPoint(coords.cells[fleetInfo.location].pt);
	if (p.z < 0.5)
	{
		$f.remove();
		return;
	}

	if ($f.length == 0)
	{
		$f = $('<div class="fleetIcon"><img class="unitIcon"><span class="ownerIcon"></span><span class="activityIcon"></span></div>');
		$f.attr('fleet-id', fleetId);
		$('#scrollPanel').append($f);
	}

	$('img.unitIcon',$f).attr('src', 'unit_images/'+fleetInfo.type+'.png');
	$('img.unitIcon',$f).click(function() {
		onFleetClicked(fleetId)
		});
	{
		var imgEl = $('img',$f).get(0);
		imgEl.addEventListener('dragstart',
			function(evt) {
				return onFleetDragStart(fleetId, evt);
			}, false);
	}

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
}

function onFleetMovement(eventData)
{
	var fleetId = eventData.fleet;
	if (fleets[fleetId])
	{
		fleets[fleetId].location = eventData.toLocation;
		fleets[fleetId].stepDelay = eventData.delay;
		updateFleetIcon(fleetId, fleets[fleetId]);
	}
}

function onFleetActivity(eventData)
{
	var fleetId = eventData.fleet;
	if (fleets[fleetId])
	{
		fleets[fleetId].activity = eventData.activity;
		updateFleetIcon(fleetId, fleets[fleetId]);
	}
}

function onFleetTerminated(eventData)
{
	var fleetId = eventData.fleet;
	if (fleets[fleetId])
	{
		if (eventData.disposition == 'moved-out-of-sight' && eventData.newLocation)
		{
			var $f = $('.fleetIcon[fleet-id="'+fleetId+'"]');
			var p = toScreenPoint(coords.cells[eventData.newLocation].pt);
			$f.css({
				'-webkit-transition': 'all 0.5s ease-out',
				'-moz-transition': 'all 0.5s ease-out',
				left: (p.x-32)+"px",
				top: (p.y-24)+"px",
				opacity: '0'
				});
			setTimeout(function() {
				$f.remove();
				}, 500);
		}
		else
		{
			removeFleetIcon(fleetId);
		}

		delete fleets[fleetId];
	}

	if (fleetId == $('#fleetPane').attr('fleet-id'))
	{
		$('#fleetPane').hide();
	}
}

var RepaintInfo = { todo: {} };
var repaintTriggered = null;
function doDeferredRepaint()
{
	for (var k in RepaintInfo.todo)
	{
		var kk = k.split(/,/);
		var ix = +kk[0];
		var iy = +kk[1];
		repaintOne(iy, ix);
	}
	RepaintInfo.todo = {};
	RepaintInfo.timer = null;
}

function triggerRepaintCanvasTile(x, y)
{
	RepaintInfo.todo[x+','+y] = true;
	if (!RepaintInfo.timer)
	{
		RepaintInfo.timer = setTimeout(doDeferredRepaint, 1000);
	}
}

function triggerRepaintCell(cellIdx)
{
	var p = toScreenPoint(coords.cells[cellIdx].pt);
	if (p.z <= 0) return;

	if (p.x < VIEWPORT.offsetX)
		return;
	if (p.y < VIEWPORT.offsetY)
		return;

	var ix = Math.floor((p.x - VIEWPORT.offsetX) / VIEWPORT_TILE_SIZE);
	var iy = Math.floor((p.y - VIEWPORT.offsetY) / VIEWPORT_TILE_SIZE);
	var ox = (p.x - VIEWPORT.offsetX) - VIEWPORT_TILE_SIZE * ix;
	var oy = (p.y - VIEWPORT.offsetY) - VIEWPORT_TILE_SIZE * iy;

	triggerRepaintCanvasTile(ix, iy);
	if (ox < VIEWPORT_TILE_OVERFLOW)
		triggerRepaintCanvasTile(ix-1, iy);
	else if (ox >= VIEWPORT_TILE_SIZE - VIEWPORT_TILE_OVERFLOW)
		triggerRepaintCanvasTile(ix+1, iy);
	if (oy < VIEWPORT_TILE_OVERFLOW)
		triggerRepaintCanvasTile(ix, iy-1);
	else if (oy >= VIEWPORT_TILE_SIZE - VIEWPORT_TILE_OVERFLOW)
		triggerRepaintCanvasTile(ix, iy+1);
}

function onMapCellChanged(location)
{
	var city = map.cells[location].city;
	if (city && city.id == $('#cityPane').attr('city-id'))
	{
		loadCityInfo(city, location);
	}
	triggerRepaintCell(location);
}

function onEvent(eventData)
{
	if (eventData.event == 'fleet-spawned')
	{
		return onFleetSpawned(eventData);
	}
	else if (eventData.event == 'fleet-movement')
	{
		return onFleetMovement(eventData);
	}
	else if (eventData.event == 'fleet-activity')
	{
		return onFleetActivity(eventData);
	}
	else if (eventData.event == 'fleet-terminated')
	{
		return onFleetTerminated(eventData);
	}
	else if (eventData.event == 'map-update')
	{
		if (eventData.locationType == 'cell')
		{
			map.cells[eventData.location] = eventData.data;
			onMapCellChanged(eventData.location);
		}
		else if (eventData.locationType == 'edge')
		{
			map.edges[eventData.location] = eventData.data;
		}
	}
	else
	{
		document.title = "event " + eventData.event;
	}
}

var nextEventFetcher = null;
function fetchNextEvent()
{
	if (!gameState || !gameState.nextEventUrl)
		return;

	var thisEventFetcher = { startTime: new Date().getTime() };
	nextEventFetcher = thisEventFetcher;

	var onSuccess = function(data,status)
	{
		if (thisEventFetcher == nextEventFetcher)
		{
			gameState.nextEventUrl = data.nextEventUrl;
			if (data.event)
				onEvent(data);
			return fetchNextEvent();
		}
	};
	var onError = function(xhr, status, errorThrown)
	{
		var elapsed = new Date().getTime() - thisEventFetcher.startTime;
		if (elapsed > 30000 && thisEventFetcher == nextEventFetcher)
			return fetchNextEvent();

		var oldTitle = document.title;
		document.title = "Lost connection to server";
		$('#lostConnectionMessage').show();

		setTimeout(fetchNextEvent, 30000);
	};
	
	$.ajax({
	url: gameState.nextEventUrl,
	success: onSuccess,
	error: onError,
	dataType: "json"
	});
}

function onFleetSpawned(eventData)
{
	fleets[eventData.fleet] = eventData.data;
	updateFleetIcon(eventData.fleet, eventData.data);
}

function fetchFleets()
{
	var onSuccess = function(data,status)
	{
		fleets = data;
		recreateFleetIcons();

		for (var fid in fleets)
		{
			var pt = coords.cells[fleets[fid].location].pt;
			panToCoords(pt);
			break;
		}
	};
	var onError = function(xhr, status, errorThrown)
	{
		//TODO- throw an error
	};

	$.ajax({
	url: gameState.fleets,
	success: onSuccess,
	error: onError,
	dataType: "json"
	});
}

function fetchMap()
{
	var onSuccess = function(data,status)
	{
		map = {
		cells: {},
		edges: {}
		};
		for (var k in data)
		{
			if (k.match(/^(\d+)$/))
			{
				map.cells[k]=data[k];
			}
			else if (k.match(/^(\d+)-(\d+)$/))
			{
				map.edges[k]=data[k];
			}
		}
		onMapReplaced();
	};
	var onError = function(xhr, status, errorThrown)
	{
		//TODO- throw an error
	};

	$.ajax({
	url: gameState.map,
	success: onSuccess,
	error: onError,
	dataType: "json"
	});
}

function fetchGameState()
{
	var onSuccess = function(data,status)
	{
		gameState = data;
		gameState.timeStamp = new Date().getTime();
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

function nextUnitClicked()
{
	var fleetId = $('#fleetPane').attr('fleet-id') || 0;
	var found = false;
	var nextFleet;
	for (var fid in fleets)
	{
		if (!nextFleet) nextFleet = fid;
		if (fid == fleetId)
			found = true;
		else if (found) {
			nextFleet = fid;
			break;
		}
	}

	if (nextFleet)
	{
		var loc = fleets[nextFleet].location;
		var pt = coords.cells[loc].pt;
		panToCoords(pt);
		onFleetClicked(nextFleet);
	}
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
		'-webkit-transition': 'all 1.0s ease-out',
		'-moz-transition': 'all 1.0s ease-out',
		'-webkit-transform': 'translate('+dx+'px,'+dy+'px)',
		'-moz-transform': 'translate('+dx+'px,'+dy+'px)'
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
				'-webkit-transform': 'translate('+dx+'px,'+dy+'px) rotate('+rotateAmt+')',
				'-moz-transform': 'translate('+dx+'px,'+dy+'px) rotate('+rotateAmt+')',
				'-webkit-transform-origin': Math.round(b_p.x)+'px '+Math.round(b_p.y)+'px',
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
				'-webkit-transition': 'none',
				'-moz-transition': 'none',
				'-webkit-transform': 'translate('+dx+'px,'+dy+'px)',
				'-moz-transform': 'translate('+dx+'px,'+dy+'px)',
				'-webkit-transform-origin': '50% 50%',
				'-moz-transform-origin': '50% 50%'
				});
		}, 1000);
	}
}

function exposeCanvases()
{
	function newCanvas(row,col)
	{
		var $c = $('<canvas class="aCanvas"></canvas>');
		$c.attr('width', VIEWPORT_TILE_SIZE);
		$c.attr('height', VIEWPORT_TILE_SIZE);
		$c.css({
			left: (VIEWPORT_TILE_SIZE*col+VIEWPORT.offsetX) + "px",
			top: (VIEWPORT_TILE_SIZE*row+VIEWPORT.offsetY) + "px"
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
		VIEWPORT.offsetY -= VIEWPORT_TILE_SIZE;
		topY -= VIEWPORT_TILE_SIZE;
	}

	var bottomY = topY + VIEWPORT_TILE_SIZE * CANVASES.length;
	while (bottomY <= VIEWPORT.screenHeight)
	{
		CANVASES.push([]);
		bottomY += VIEWPORT_TILE_SIZE;
	}

	var leftX = VIEWPORT.offsetX + VIEWPORT.translateX;
	while (leftX > 0)
	{
		VIEWPORT.offsetX -= VIEWPORT_TILE_SIZE;
		leftX -= VIEWPORT_TILE_SIZE;

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

		var rightX = leftX + VIEWPORT_TILE_SIZE*CANVASES[row].length;
		while (rightX <= VIEWPORT.screenWidth)
		{
			rightX += VIEWPORT_TILE_SIZE;

			var canvas = newCanvas(row, C.length);
			C.push(canvas);
			repaintOne(row,C.length-1);
		}
	}
}

function doOneExpose(cellIdx)
{
	var onSuccess = function(data)
	{
		return;
	};
	$.ajax({
	type: "POST",
	url: "/request/expose",
	data: { cell: cellIdx },
	success: onSuccess,
	dataType: "json"
	});
}

function showDragTargetIndicator(location)
{
	var p = toScreenPoint(coords.cells[location].pt);
	$('#dragTargetIndicator').css({
		left: p.x-10,
		top: p.y-10
		});
	$('#dragTargetIndicator').show();
}

function hideDragTargetIndicator()
{
	$('#dragTargetIndicator').hide();
}

function onFleetDragStart(fleetId, evt)
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

	var spEl = document.getElementById('scrollPanel');
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
	if (xx.type == 'cell')
	{
		if (map.cells[xx.id] && map.cells[xx.id].city)
		{
			return onCityClicked(xx.id, map.cells[xx.id].city);
		}
		else
		{
			return panToCoords(coords.cells[xx.id].pt);
		}
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
		map.cells[pawn.location].water++;
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

function orderStop()
{
	var fleetId = $('#fleetPane').attr('fleet-id');
	$.ajax({
	type: "POST",
	url: "/request/orders?fleet="+fleetId,
	data: JSON.stringify([]),
	contentType: "json"
	});
}

function orderFollowCoast()
{
	var fleetId = $('#fleetPane').attr('fleet-id');
	$.ajax({
	type: "POST",
	url: "/request/orders?fleet="+fleetId,
	data: JSON.stringify([ { command: "follow-coast" } ]),
	contentType: "json"
	});
}

function orderBuildCity()
{
	var fleetId = $('#fleetPane').attr('fleet-id');
	$.ajax({
	type: "POST",
	url: "/request/orders?fleet="+fleetId,
	data: JSON.stringify([ { command: "build-city" } ]),
	contentType: "json"
	});
}

function orderDisband()
{
	var fleetId = $('#fleetPane').attr('fleet-id');
	$.ajax({
	type: "POST",
	url: "/request/orders?fleet="+fleetId,
	data: JSON.stringify([ { command: "disband" } ]),
	contentType: "json"
	});
}

function orderGoTo(fleetId, location)
{
	$.ajax({
	type: "POST",
	url: "/request/orders?fleet="+fleetId,
	data: JSON.stringify([ {
		command: "goto",
		location: location,
		locationType: "cell"
		} ]),
	contentType: "json"
	});
}

function cityRename()
{
	var cityId = $('#cityPane').attr('city-id');
	var newName = prompt('Name for city?', $('#cityPane .cityName').text());
	if (newName)
	{
		$.ajax({
		type: "POST",
		url: "/request/rename-city?city="+cityId,
		data: { name: newName }
		});
	}
}

function transferWorkers(numWorkers, fromJob, toJob)
{
	var cityId = $('#cityPane').attr('city-id');
	$.ajax({
		type: "POST",
		url: "/request/reassign-workers?city="+cityId,
		data: {
		fromJob: fromJob,
		toJob: toJob,
		amount: numWorkers
		}
		});
}

function cityTest()
{
	var cityId = $('#cityPane').attr('city-id');
	$.ajax({
		type: "POST",
		url: "/request/test-city?city="+cityId,
		data: {}
		});
}

function onCityNewJob()
{
	var el = document.getElementById('cityNewJobChoice');
	if (el.value)
	{
		cityMakeJobBox(el.value);
		el.value = '';
	}
}

function cityBuildSettler()
{
	var cityId = $('#cityPane').attr('city-id');
	$.ajax({
		type: "POST",
		url: "/request/build-unit?city="+cityId,
		data: { type: 'settler' }
		});
}

function cityBuildTrieme()
{
	var cityId = $('#cityPane').attr('city-id');
	$.ajax({
		type: "POST",
		url: "/request/build-unit?city="+cityId,
		data: { type: 'trieme' }
		});
}

function cityBuildFarm()
{
	var cityId = $('#cityPane').attr('city-id');
	$.ajax({
		type: "POST",
		url: "/request/build-improvement?city="+cityId,
		data: { improvement: 'farm' }
		});
}

function cityExpandVillage()
{
	var cityId = $('#cityPane').attr('city-id');
	$.ajax({
		type: "POST",
		url: "/request/build-improvement?city="+cityId,
		data: { improvement: 'hamlet' }
		});
}

$(function() {
$('.closeBtn').click(function() {
		$('#fleetPane').hide();
		$('#cityPane').hide();
	});
});
