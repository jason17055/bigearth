function getCitySize(mapCell)
{
	return (mapCell.zones['mud-cottages'] || 0) +
		(mapCell.zones['wood-cottages'] || 0) +
		(mapCell.zones['stone-cottages'] || 0);
}

function loadCityInfo(city, location)
{
	var mapCell = map.cells[Location.toCellId(location)];
	if (!mapCell)
		return;
	if (!mapCell.zones)
		mapCell.zones = {};

	$('#cityPane').attr('city-id', city.id);
	$('#cityPane').attr('city-location', location);
	$('#cityPane .cityName').text(city.name || "(unnamed)");
	$('#cityPane .citySize').text(getCitySize(mapCell));
	$('#cityPane .cityPopulation').text(city.population + city.children);
	if (city.children || city.population)
	{
		$('#cityPane .cityChildren').text(city.children || 0);
		$('#cityPane .cityWorkersCount').text(city.population || 0);
		$('#cityPane .cityPopulationDetail').show();
	}
	else
	{
		$('#cityPane .cityPopulationDetail').hide();
	}

	loadFleetResources($('#cityPane .resourcesContainer'), city);

	$('#cityPane .cityFarms').text(mapCell.zones.farm);
	if (mapCell.zones.farm)
		$('#cityPane .cityFarmsContainer').show();
	else
		$('#cityPane .cityFarmsContainer').hide();
	$('#cityPane img.icon').attr('src', 'city_images/city1.png');
	$('#cityPane .cityActivity').text(city.activity || '');
	animateCityActivityProgressBar(city);

	$('#cityBuildingsContainer .cityBuildingItem').remove();
	if (city.buildings)
	{
		$('#cityBuildingsContainer').show();
		for (var bt in city.buildings)
		{
			var q = city.buildings[bt];

			var $x = $('#cityBuildingItemTemplate').clone();
			$x.removeAttr('id');
			$x.addClass('cityBuildingItem');
			$x.attr('building-id', bt);
			$x.removeClass('template');

			$('.cityBuildingName', $x).text(bt);

			var orders = q.orders;
			var $y = $(orders == 'make-stone-block' ? '<div class="cityBuildingOrdersBtn"><img src="commodity_images/stone.png"> &gt; <img src="commodity_images/stone-block.png"></div>' :
				orders == 'make-stone-weapon' ? '<div class="cityBuildingOrdersBtn"><img src="commodity_images/stone.png"> &gt; <img src="commodity_images/stone-weapon.png"></div>' :
				('<div class="cityBuildingOrdersBtn">'+ (orders || '(none)') + '</div>'));

			$y.click(onBuildingChangeOrdersClicked);

			$x.append($y);
			$('#cityBuildingsContainer').append($x);
		}
	}
	else
	{
		$('#cityBuildingsContainer').hide();
	}

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

	loadFleetOrCityMessages(city.messages, $('#cityMessages'));

	loadAtThisLocation(city, location, $('#cityPane .atThisLocation'));

	loadCityOverheadView(city.id, city, $('#cityPane'));
}

function loadCityOverheadView(cityId, city, $cityPane)
{
	var $t = $('#cityPaneOverheadViewTab .cityVisualClearedArea', $cityPane);
	$('.cityVisualFloater', $t).remove();

	var mapCell = map.cells[Location.toCellId(city.location)];
	if (mapCell && mapCell.terrain)
	{
		$('.cityVisualOutskirts', $cityPane).css({ 'background-image': 'url("terrain_textures/'+mapCell.terrain+'.png")' });

		if (isAdjacentToRiver(cityId, city))
		{
			$('.cityVisualShoreline', $cityPane).show();
		}
		else
		{
			$('.cityVisualShoreline', $cityPane).hide();
		}
	}

	$('img', $t).remove();

	var setCell = function(x,y,imageId)
	{
		var $td = $('.ovCell[ovcell-id="'+y+','+x+'"]', $t);
		$td.empty();

		var $img = $('<img src="">');
		$img.attr('src', 'building_images/'+imageId+'.png');
		$td.append($img);
	};

	var setImage = function(x, y, w, h, imageId)
	{
		var $d = $('<div class="cityVisualFloater"></div>');
		$d.css({
			left: (32*x)+"px",
			top: (32*y)+"px",
			width: (32*w)+"px",
			height: (32*h)+"px",
			'background-image': 'url("building_images/'+imageId+'.png")'
			});
		$t.append($d);
		return $d;
	};

	var numCottages = mapCell.zones['mud-cottages'] || 0;
	for (var i = 0; i < numCottages; i++)
	{
		setImage(4+(i%6),Math.floor(i/6),1,1,'mud-cottage');
	}

	var $d = setImage(4,2,3,3,'stockyard');
	$d.append('<div class="cityVisualStockyard"></div>');

	showCityResources($('.cityVisualStockyard',$d), city);

	var numFarms = mapCell.zones.farm || 0;
	for (var i = 0; i < numFarms; i++)
	{
		var x = i%2;
		var y = Math.floor(i/2);
		setImage(x*2,y*2,2,2,'wheat_field');
	}
}

function showCityResources($container, city)
{
	$('.aResource', $container).remove();

	if (!city.stock) return;
	for (var commodType in city.stock)
	{
		var amount = city.stock[commodType];
		if (amount > 4) { amount = 4; }

		var $r = $('<span class="aResource"></span>');
		for (var i = 0; i < amount; i++)
		{
			var $m = $('<img class="resourceIcon" width="16" height="16" src="">');
			$m.attr('src', 'commodity_images/'+commodType+'.png');
			$m.attr('title', commodType);
			$r.append($m);
		}
		$container.append($r);
	}
}

function isAdjacentToRiver(cityId, city)
{
	var location = city.location;
	var cellId = Location.toCellId(location);
	var ee = BE.geometry.getEdgesAdjacentToCell(cellId);
	for (var i = 0; i < ee.length; i++)
	{
		var eId = ee[i];
		var edge = map.edges[eId];
		if (edge && edge.feature == 'river')
			return true;
	}
	return false;
}
