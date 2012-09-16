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
			var $y = $(orders == 'make-stone-block' ? '<div class="cityBuildingOrdersBtn"><img src="resource_icons/stone.png"> &gt; <img src="resource_icons/stone-block.png"></div>' :
				orders == 'make-stone-weapon' ? '<div class="cityBuildingOrdersBtn"><img src="resource_icons/stone.png"> &gt; <img src="resource_icons/stone-weapon.png"></div>' :
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
	var $t = $('#cityPaneOverheadViewTab .ovTable', $cityPane);

	// check whether overhead table was initialized yet
	if (!$('.ovCell', $t).length)
	{
		$t.empty();
		for (var row = 0; row < 6; row++)
		{
			var $tr = $('<tr></tr>');
			for (var col = 0; col < 10; col++)
			{
				var $td = $('<td class="ovCell"></td>');
				$td.attr('ovcell-id', row+','+col);
				$tr.append($td);
			}
			$t.append($tr);
		}
	}

	var setCell = function(x,y,imageId)
	{
		var $td = $('.ovCell[ovcell-id="'+y+','+x+'"]', $t);
		$td.empty();

		var $img = $('<img src="">');
		$img.attr('src', 'building_images/'+imageId+'.png');
		$td.append($img);
	};

	setCell(2,0,'mud-cottage');
	setCell(3,0,'mud-cottage');
	setCell(4,0,'mud-cottage');
	setCell(5,0,'mud-cottage');
	setCell(6,0,'mud-cottage');

	setCell(0,1,'road');
	setCell(1,1,'road');
	setCell(2,1,'road');
	setCell(3,1,'road');
	setCell(4,1,'road');
	setCell(5,1,'road');
	setCell(6,1,'road');
	setCell(7,1,'road');
	setCell(8,1,'road');
	setCell(9,1,'road');
}
