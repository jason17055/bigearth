var VIEWPORT = null;

var BE = {};
var map = null;
var fleets = null;
var cities = null;

function onResize()
{
	var newWidth = window.innerWidth - 0;
	var newHeight = window.innerHeight - $('#buttonBar').outerHeight();

	$('#contentArea').css({
		width: newWidth+"px",
		height: newHeight+"px"
		});
	if (VIEWPORT)
		VIEWPORT.sizeChanged(newWidth, newHeight);

	$('#regionPage').css({
		width: newWidth+"px",
		height: newHeight+"px"
		});
}

var gameState;

function onMapReplaced()
{
	maybeStartPlaying();
}

function getFirstFleet()
{
	for (var fid in fleets)
	{
		return fid;
	}
	return null;
}

function getFirstCity()
{
	for (var tid in cities)
	{
		return tid;
	}
	return null;
}

function maybeStartPlaying()
{
	if (map && fleets && cities && !VIEWPORT)
	{
		var contentDiv = document.getElementById('contentArea');
		VIEWPORT = new BigEarthViewPort(contentDiv, map);
		VIEWPORT.initialize();
		VIEWPORT.cityClicked = onCityClicked;
		VIEWPORT.fleetClicked = onFleetClicked;
		VIEWPORT.orderGoTo = orderGoTo;

		onResize();

		var fid = getFirstFleet();
		var tid = getFirstCity();
		if (fid)
		{
			var pt = Location.toPoint(fleets[fid].location);
			VIEWPORT.panToCoords(pt);
		}
		else if (tid)
		{
			var pt = Location.toPoint(cities[tid].location);
			VIEWPORT.panToCoords(pt);
		}
		else
		{
			VIEWPORT.panToCoords({x:1,y:0,z:0});
		}

		fetchNextEvent();
	}
}

function onGameState()
{
	BE.geometry = new SphereGeometry(gameState.mapSize);
	BE.coords = makeCoords(BE.geometry);
	if (gameState.map)
		fetchMap();
	if (gameState.fleets)
		fetchFleets();
	if (gameState.cities)
		fetchCities();
}

function unselect()
{
	$('.fleetIcon').removeClass('selectedFleet');
	$('#fleetPane').hide();
	$('#cityPane').hide();
	$('#citiesReport').hide();
}

function selectCity(cityId)
{
	var city = cities[cityId];
	if (city)
	{
		onCityClicked(city.location, cityId);
	}
}

function onCityClicked(location, cityId)
{
	unselect();

	resetCityPane();
	loadCityInfo(cityId, location);

	$('#cityPane').show();
}

function onBuildingChangeOrdersClicked(evt)
{
	var tmpEl = this;
	var buildingId = null;
	var cityId = null;
	var cityLocation = null;
	for (var tmpEl = this; tmpEl; tmpEl = tmpEl.parentNode)
	{
		if (!buildingId && tmpEl.hasAttribute('building-id'))
		{
			buildingId = tmpEl.getAttribute('building-id');
		}
		if (!cityId && tmpEl.hasAttribute('city-id'))
		{
			cityId = tmpEl.getAttribute('city-id');
		}
		if (!cityLocation && tmpEl.hasAttribute('city-location'))
		{
			cityLocation = tmpEl.getAttribute('city-location');
		}
	}

	if (!(buildingId && cityId && cityLocation))
		return;

	var city = cities[cityId];
	if (!city)
		return;

	var bldg = city.buildings[buildingId];
	if (!bldg)
		return;

	var currentOrders = bldg.orders;
	if (currentOrders == 'make-stone-block')
		currentOrders = 'make-stone-weapon';
	else
		currentOrders = 'make-stone-block';

	setBuildingOrders(cityId, buildingId, currentOrders);
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

function loadTerrainResources($box, location, onResourceClicked)
{
	var mapCell = map.cells[Location.toCellId(location)];
	if (mapCell && mapCell.city)
	{
		mapCell = cities[mapCell.city];
	}
	if (!mapCell)
		return;

	return loadFleetResources($box, mapCell, onResourceClicked);
}

function loadFleetResources($box, fleet, onResourceClicked)
{
	$('.aResource', $box).remove();
	var RESOURCE_DISPLAY_NAMES = {
		clay: "Clay",
		meat: "Meat",
		pig: "Pig",
		sheep: "Sheep",
		stone: "Stone",
		wheat: "Wheat",
		wood: "Wood"
		};

	var resourceIcon = function($m, resourceType)
	{
		$m.attr('resource-type', resourceType);
		$m.attr('src', 'commodity_images/'+resourceType+'.png');
		$m.attr('title', RESOURCE_DISPLAY_NAMES[resourceType] || resourceType);
		if (onResourceClicked)
		{
			$m.click(onResourceClicked);
		}
	};

	if (fleet.stock)
	{
		for (var resourceType in fleet.stock)
		{
			var amount = fleet.stock[resourceType];

			if (amount >= 5)
			{
			var $r = $('<div class="aResource"><img class="resourceIcon" src="">&times;<span class="resourceAmount"></span></div>');
			resourceIcon($('img',$r), resourceType);
			$('.resourceAmount', $r).text(amount);
			$box.append($r);
			}
			else
			{
				var $r = $('<span class="aResource"></span>');
				for (var i = 0; i < amount; i++)
				{
					var $m = $('<img class="resourceIcon" src="">');
					resourceIcon($m, resourceType);
					$r.append($m);
				}
				$box.append($r);
			}
		}
	}
}

function loadAtThisLocation(cityOrFleetId, location, $box)
{
	var anyFound = false;

	$('.fleetTile', $box).remove();
	for (var fid in fleets)
	{
		if (fid == cityOrFleetId)
			continue;

		if (fleets[fid].location == location)
		{
			var $x = $('<div class="otherFleet fleetTile"><img class="icon" align="left"><span class="owner"></span></div>');
			$('img.icon', $x).attr('src', 'unit_images/'+fleets[fid].type+'.png');
			$('.owner', $x).text(fleets[fid].owner);
			with ({otherFleetId: fid}) {
			$x.click(function() {
				onFleetClicked(otherFleetId);
				});
			}
			$box.append($x);
			anyFound = true;
		}
	}

	if (anyFound)
		$box.show();
	else
		$box.hide();
}

function onFleetClicked(fleetId)
{
	selectFleet(fleetId);
}

function selectFleet(fleetId)
{
	unselect();
	$('.fleetIcon[fleet-id="'+fleetId+'"]').addClass('selectedFleet');

	loadFleetInfo(fleetId);
	$('#fleetPane').show();
}

function loadFleetOrCityMessages(messagesArray, $box)
{
	$('.fleetMessage', $box).remove();
	if (messagesArray)
	{
		for (var i = 0, l = messagesArray.length; i < l && i < 3; i++)
		{
			var m = messagesArray[i];
			var $m = $('<div class="fleetMessage"><span class="messageText"></span></div>');
			$('.messageText', $m).text(m.message);
			$box.append($m);
		}
	}
}

function loadFleetInfo(fleetId)
{
	var fleet = fleets[fleetId];
	if (!fleet)
		return;

	var $fleetPane = $('#fleetPane');

	$fleetPane.attr('fleet-id', fleetId);
	$('img.icon', $fleetPane).attr('src','unit_images/'+fleet.type+'.png');
	$('.unitType', $fleetPane).text(fleet.type);

	if (fleet.population)
	{
		$('.population', $fleetPane).text(fleet.population);
		$('.populationContainer', $fleetPane).show();
	}
	else
	{
		$('.populationContainer', $fleetPane).hide();
	}

	if (fleet.encumbranceCategory)
	{
		$('.encumbranceLabel', $fleetPane).text(fleet.encumbranceCategory).show();
	}
	else
	{
		$('.encumbranceLabel', $fleetPane).hide();
	}

	loadFleetResources($('.fleetResourceExchange .ResourceExchange-left .resourcesList', $fleetPane), fleet, doDropResource);
	loadTerrainResources($('.fleetResourceExchange .ResourceExchange-right .resourcesList', $fleetPane), fleet.location, doTakeResource);

	loadFleetOrCityMessages(fleet.messages, $('.fleetMessagesContainer', $fleetPane));

	if (fleet.inBattle)
	{
		$('.terrainInfo', $fleetPane).text('In a battle.');
	}
	else if (fleet.settlementFitness)
	{
		$('.terrainInfo', $fleetPane).text('settlement value '+fleet.settlementFitness);
	}
	else if (fleet.sheepBeHere)
	{
		$('.terrainInfo', $fleetPane).text('Sheep spotted at this location.');
	}
	else if (fleet.pigBeHere)
	{
		$('.terrainInfo', $fleetPane).text('Pig spotted at this location.');
	}
	else
	{
		$('.terrainInfo', $fleetPane).text('');
	}

	if (fleet.canSettle)
		$('#buildCityBtn, #autoSettleBtn').show();
	
	else
		$('#buildCityBtn, #autoSettleBtn').hide();

	loadAtThisLocation(fleetId, fleet.location, $('#fleetPane .atThisLocation'));
}

function doDropResource()
{
	var tmpEl = this;
	var resourceType = null;
	for (var tmpEl = this; tmpEl; tmpEl = tmpEl.parentNode)
	{
		if (!resourceType && tmpEl.hasAttribute('resource-type'))
		{
			resourceType = tmpEl.getAttribute('resource-type');
		}
	}

	orderDropResource(resourceType);
}

function doTakeResource(evt)
{
	var tmpEl = this;
	var resourceType = null;
	for (var tmpEl = this; tmpEl; tmpEl = tmpEl.parentNode)
	{
		if (!resourceType && tmpEl.hasAttribute('resource-type'))
		{
			resourceType = tmpEl.getAttribute('resource-type');
		}
	}

	orderTakeResource(resourceType);
}

function onBattleCreated(eventData)
{
	for (var side in eventData.groups)
	{
		for (var fid in eventData.groups[side])
		{
			if (fleets[fid])
			{
				fleets[fid].inBattle = eventData.battle;
				fleets[fid].inBattleGroup = side;
				fleets[fid].location = eventData.location;
				VIEWPORT.updateFleetIcon(fid, fleets[fid]);
			}
		}
	}
}

function onBattleTerminated(eventData)
{
	if (eventData.victors)
	{
		for (var fid in eventData.victors)
		{
			if (fleets[fid])
			{
				delete fleets[fid].inBattle;
				delete fleets[fid].inBattleGroup;
				VIEWPORT.updateFleetIcon(fid, fleets[fid]);
			}
		}
	}

	VIEWPORT.removeBattleIcon(eventData.battle);
}

var attackCount = 0;
function onBattleAttack(eventData)
{
	VIEWPORT.showFleetAttack(eventData.attacker, eventData.defender);
}

function onFleetMovement(eventData)
{
	var fleetId = eventData.fleet;
	if (fleets[fleetId])
	{
		fleets[fleetId].location = eventData.toLocation;
		fleets[fleetId].stepDelay = eventData.delay;
		fleets[fleetId].inBattle = eventData.inBattle;
		fleets[fleetId].inBattleGroup = eventData.inBattleGroup;
		VIEWPORT.updateFleetIcon(fleetId, fleets[fleetId]);

		var terrain = map.cells[eventData.toLocation];
		if (terrain && terrain.city == $('#cityPane').attr('city-id'))
		{
			loadCityInfo(terrain.city, eventData.toLocation);
		}
	}
}

function moveFleetOutOfSight(fleetId, newLoc)
{
	var $f = $('.fleetIcon[fleet-id="'+fleetId+'"]');
	var p = toScreenPoint(BE.coords.cells[eventData.newLocation].pt);
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

function onFleetTerminated(eventData)
{
	var fleetId = eventData.fleet;
	if (fleets[fleetId])
	{
		VIEWPORT.removeFleetIcon(fleetId, eventData);
		delete fleets[fleetId];
	}

	if (fleetId == $('#fleetPane').attr('fleet-id'))
	{
		$('#fleetPane').hide();
	}
}

function onMapCellChanged(location)
{
	var cityId = map.cells[Location.toCellId(location)].city;
	if (cityId == $('#cityPane').attr('city-id'))
	{
		loadCityInfo(cityId, location);
	}

	VIEWPORT.triggerRepaintCell(location);
}

function onCityUpdated(eventData)
{
	var cityId = eventData.city;
	cities[cityId] = eventData.data;

	if (cityId == $('#cityPane').attr('city-id'))
	{
		loadCityInfo(cityId, eventData.location);
	}
}

function onGameMessage(eventData)
{
	//FIXME
}

function onEvent(eventData)
{
	if (eventData.event == 'battle-created' || eventData.event == 'battle-updated')
	{
		return onBattleCreated(eventData);
	}
	else if (eventData.event == 'battle-terminated')
	{
		return onBattleTerminated(eventData);
	}
	else if (eventData.event == 'battle-attack')
	{
		return onBattleAttack(eventData);
	}
	else if (eventData.event == 'city-updated')
	{
		return onCityUpdated(eventData);
	}
	else if (eventData.event == 'fleet-spawned')
	{
		return onFleetSpawned(eventData);
	}
	else if (eventData.event == 'fleet-movement')
	{
		return onFleetMovement(eventData);
	}
	else if (eventData.event == 'fleet-updated')
	{
		return onFleetUpdated(eventData);
	}
	else if (eventData.event == 'fleet-terminated')
	{
		return onFleetTerminated(eventData);
	}
	else if (eventData.event == 'map-update')
	{
		if (Location.isCell(eventData.location))
		{
			map.cells[Location.toCellId(eventData.location)] = eventData.data;
			onMapCellChanged(eventData.location);
		}
		else if (Location.isEdge(eventData.location))
		{
			map.edges[Location.toEdgeId(eventData.location)] = eventData.data;
		}
	}
	else if (eventData.event == 'message')
	{
		return onGameMessage(eventData);
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

function checkFleetMessage(fleetId)
{
	var fleet = fleets[fleetId];

	var $msgBox = $('.fleetMessageBox[fleet-id="'+fleetId+'"]');
	if (!fleet || !fleet.message)
	{
		if ($msgBox.length)
		{
			$msgBox.remove();
		}
		return;
	}
	
	if ($msgBox.length == 0)
	{
		$msgBox = $('<div class="fleetMessageBox"><img class="unitIcon" src=""><span class="fleetName"></span><span class="message"></span></div>');
		$msgBox.attr('fleet-id', fleetId);
		$('#fleetMessagesContainer').append($msgBox);

		$msgBox.click(function() {
			scrollToFleet(fleetId);
			selectFleet(fleetId);
			});
	}

	$('img.unitIcon', $msgBox).attr('src', 'unit_images/'+fleet.type+'.png');
	$('.fleetName', $msgBox).text(fleet.type);
	$('.message', $msgBox).text(fleet.message);
}

function onFleetSpawned(eventData)
{
	var fleetId = eventData.fleet;
	fleets[fleetId] = eventData.data;

	if (VIEWPORT)
		VIEWPORT.updateFleetIcon(fleetId);

	checkFleetMessage(fleetId);
}

function onFleetUpdated(eventData)
{
	var fleetId = eventData.fleet;
	fleets[fleetId] = eventData.data;
	VIEWPORT.updateFleetIcon(fleetId, eventData.data);

	checkFleetMessage(fleetId);

	if (fleetId == $('#fleetPane').attr('fleet-id'))
	{
		loadFleetInfo(fleetId);
	}
}

function fetchCities()
{
	var onSuccess = function(data,status)
	{
		cities = data;
		maybeStartPlaying();
	};
	var onError = function(xhr, status, errorThrown)
	{
		//TODO- throw an error
	};

	$.ajax({
	url: gameState.cities,
	success: onSuccess,
	error: onError,
	dataType: "json"
	});
}

function fetchFleets()
{
	var onSuccess = function(data,status)
	{
		fleets = data;
		maybeStartPlaying();

		for (var fid in fleets)
		{
			checkFleetMessage(fid);
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
			if (Location.isCell(k))
			{
				map.cells[k]=data[k];
			}
			else if (Location.isEdge(k))
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

function onUnauthorized()
{
	window.location.href = '/';
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
		if (xhr.status == 401)
		{
			return onUnauthorized();
		}
		else
		{
		//TODO- throw an error
		alert('gamestate error '+status+' ' + errorThrown + " " + xhr.status);
		}
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
	var adj = BE.geometry.getNeighbors(x);
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
		scrollToFleet(nextFleet);
		selectFleet(nextFleet);
	}
}

function scrollToCity(cityId)
{
	var city = cities[cityId];
	if (city && city.location)
	{
		var pt = Location.toPoint(city.location);
		VIEWPORT.panToCoords(pt);
	}
}

function scrollToFleet(fleetId)
{
	var fleet = fleets[fleetId];
	if (fleet && fleet.location)
	{
		var pt = Location.toPoint(fleet.location);
		VIEWPORT.panToCoords(pt);
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

function makeRiversClicked()
{
	var R = new RiverFactory();
	while (R.step());
	repaint();
}

function orderDropResource(resourceType)
{
	var fleetId = $('#fleetPane').attr('fleet-id');
	$.ajax({
	type: "POST",
	url: "/request/orders?fleet="+fleetId,
	data: JSON.stringify([ {
		command: "drop",
		resourceType: resourceType,
		amount: 1
		} ]),
	contentType: "text/json"
	});
}

function orderTakeResource(resourceType)
{
	var fleetId = $('#fleetPane').attr('fleet-id');
	$.ajax({
	type: "POST",
	url: "/request/orders?fleet="+fleetId,
	data: JSON.stringify([ {
		command: "take",
		resourceType: resourceType,
		amount: 1
		} ]),
	contentType: "text/json"
	});
}

function orderStop()
{
	var fleetId = $('#fleetPane').attr('fleet-id');
	$.ajax({
	type: "POST",
	url: "/request/orders?fleet="+fleetId,
	data: JSON.stringify([]),
	contentType: "text/json"
	});
}

function orderFollowCoast()
{
	var fleetId = $('#fleetPane').attr('fleet-id');
	$.ajax({
	type: "POST",
	url: "/request/orders?fleet="+fleetId,
	data: JSON.stringify([ { command: "follow-coast" } ]),
	contentType: "text/json"
	});
}

function orderFleetCommand(buttonEl)
{
	var fleetId = $('#fleetPane').attr('fleet-id');
	var cmd = $(buttonEl).attr('command-name');

	$.ajax({
	type: "POST",
	url: "/request/orders?fleet="+fleetId,
	data: JSON.stringify([ { command: cmd } ]),
	contentType: "text/json"
	});
}

function orderBuildCity()
{
	var fleetId = $('#fleetPane').attr('fleet-id');
	$.ajax({
	type: "POST",
	url: "/request/orders?fleet="+fleetId,
	data: JSON.stringify([ { command: "build-city" } ]),
	contentType: "text/json"
	});
}

function orderDisband()
{
	var fleetId = $('#fleetPane').attr('fleet-id');
	$.ajax({
	type: "POST",
	url: "/request/orders?fleet="+fleetId,
	data: JSON.stringify([ { command: "disband" } ]),
	contentType: "text/json"
	});
}

function orderGoTo(fleetId, location)
{
	$.ajax({
	type: "POST",
	url: "/request/orders?fleet="+fleetId,
	data: JSON.stringify([ {
		command: "goto",
		location: location
		} ]),
	contentType: "text/json"
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

function cityEmancipate()
{
	var cityId = $('#cityPane').attr('city-id');
	$.ajax({
		type: "POST",
		url: "/request/emancipate-city?city="+cityId,
		data: {}
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

function cityEquipNewUnit(unitType)
{
	var cityId = $('#cityPane').attr('city-id');
	$.ajax({
		type: "POST",
		url: "/request/equip-unit?city="+cityId,
		data: { type: unitType }
		});
}

function cityDevelopLand(landType)
{
	var cityId = $('#cityPane').attr('city-id');
	$.ajax({
		type: "POST",
		url: "/request/build-improvement?city="+cityId,
		data: { improvement: landType }
		});
}

function setBuildingOrders(cityId, buildingId, newOrders)
{
	$.ajax({
		type: "POST",
		url: "/request/building-orders?city="+cityId+"&building="+buildingId,
		data: { orders: newOrders }
		});
}

$(function() {
$('.closeBtn').click(unselect);
$('.popupTabsButton').click(function() {
			var pageId = $(this).attr('popup-page');
			var el = document.getElementById(pageId);
			if (!el) {
				alert("no element with id '"+pageId+"'");
				return;
			}
			$('.cityPaneTab').hide();
			$(el).show();
		});
});

function cityBuildStoneWorkshop()
{
	var cityId = $('#cityPane').attr('city-id');
	$.ajax({
		type: "POST",
		url: "/request/build-building?city="+cityId,
		data: { building: 'stone-workshop' }
		});
}

function citiesReportClicked()
{
	unselect();

	var $r = $('#citiesReport');
	$('tr.cityRow', $r).remove();

	// want cities to be sorted
	var citiesSorted = [];
	for (var tid in cities)
		citiesSorted.push(tid);
	citiesSorted.sort(function(a,b) {
		return cities[a].name > cities[b].name ? 1 :
			cities[a].name < cities[b].name ? -1 : 0;
		});

	for (var i = 0, l = citiesSorted.length; i < l; i++)
	{
		var tid = citiesSorted[i];
		var city = cities[tid];

		var $d = $('<tr class="cityRow"><td><img src="city_images/city1.png"><a href="#" class="cityName"></a></td><td><span class="cityPopulation"></span></td><td class="cityZones"></td></tr>');
		$('.cityName', $d).text(city.name);
		with({tid:tid}) {
		$('a.cityName', $d).click(function() {
			scrollToCity(tid);
			selectCity(tid);
			return false;
			});
		}
		$('.cityPopulation', $d).text(city.population+city.children);

		var zonesStr = [];
		var mapCell = map.cells[Location.toCellId(city.location)];
		if (mapCell.zones['mud-cottages'])
		{
			zonesStr.push(mapCell.zones['mud-cottages'] + " housing");
		}
		if (mapCell.zones.farm)
		{
			zonesStr.push(mapCell.zones.farm + " farmland");
		}

		$('.cityZones', $d).text(zonesStr.join(', '));
		$('table', $r).append($d);
	}

	$r.show();
}

function onFleetNameClicked()
{
	var fleetId = $('#fleetPane').attr('fleet-id');
	var u = '/region?follow='+escape(fleetId);

	$('#fleetPane').hide();
	$('#cityPane').hide();
	$('.world-view').hide();
	$('.region-view').show();
}
