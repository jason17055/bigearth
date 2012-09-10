var Scheduler = require('./scheduler.js');
var Location = require('../../html/location.js');
var Terrain = require('./terrain.js');

function onFarmCompleted(cityId, city)
{
	stealWorkers(cityId, city, 15, 'farm');
}

// [0]: number of builders to assign when task starts
// [1]: production cost
// [2]: function to call after land has been developed (can be null)
//
var LAND_TYPE_COSTS = {
	farm:   [ 25, 50, onFarmCompleted ],
	hamlet: [ 50, 100, null ]
	};

var FACTORY_RECIPES = {
	'stone-weapon': { 'rate': 0.01, 'input': { 'stone': 0.25 } },
	'stone-block': { 'rate': 0.01,  'input': { 'stone': 1.00 } }
	};

var FOOD_TYPES = [ 'food', 'meat', 'wheat' ];

// [0]: number of builders to assign when task starts
// [1]: production cost
// [2]: function to call after building has been completed (can be null)
//
var BUILDING_TYPE_COSTS = {
	'stone-workshop':   [ 25, 50, null ]
	};

// each value is an object with the following properties:
//   builders: number of workers to build the unit
//   productionCost: production points required to build the unit
//   populationCost: the number of people that are taken from the city and put in the new fleet
//
var UNIT_COSTS = {
	'explorer': { builders: 50, productionCost: 25, populationCost: 25 },
	'settler': { builders: 50, productionCost: 200, populationCost: 100 },
	'trieme': { builders: 400, productionCost: 400, populationCost: 50 }
	};

var TECHNOLOGIES;
function makeTechnologyTree()
{
	var baseTechs = [ 'alphabet', 'husbandry', 'smelting',
		'burial', 'masonry', 'pottery', 'warrior-code' ];
	TECHNOLOGIES = {};
	for (var i = 0; i < baseTechs.length; i++)
	{
		for (var r = 0; r < 5; r++)
		{
			for (var c = 0; c < 5; c++)
			{
				var j = r*5+c+1;
				var techId = baseTechs[i] + '-' + j;
				var tech = {
					prereqs: []
					};
				if (r % 2 == 1)
				{
					var k = (r-1)*5+c+1;
					var parentTech = baseTechs[i] + '-' + k;
					tech.prereqs.push(parentTech);

					if (c + 1 < 5)
					{
						var k = (r-1)*5+c+2;
						var parentTech = baseTechs[i] + '-' + k;
						tech.prereqs.push(parentTech);
					}
				}
				else if (r > 0)
				{
					var k = (r-1)*5+c+1;
					var parentTech = baseTechs[i] + '-' + k;
					tech.prereqs.push(parentTech);

					if (c - 1 >= 0)
					{
						var k = (r-1)*5+c;
						var parentTech = baseTechs[i] + '-' + k;
						tech.prereqs.push(parentTech);
					}
				}

				if (j%2 == 1)
				{
					if (baseTechs[i] == 'husbandry')
						tech.resourceCost = { 'meat': 5 };
					else if (baseTechs[i] == 'smelting')
						tech.resourceCost = { 'copper-ore': 1 };
					else if (baseTechs[i] == 'masonry')
						tech.resourceCost = { 'stone': 5 };
					else if (baseTechs[i] == 'pottery')
						tech.resourceCost = { 'clay': 2 };
					else if (baseTechs[i] == 'warrior-code')
						tech.resourceCost = { 'stone-weapon': 2, 'wood': 1 };
				}

				TECHNOLOGIES[techId] = tech;
			}
		}
	}
	console.log(TECHNOLOGIES);
}
makeTechnologyTree();

function newCity(location, owner)
{
	var tid = nextFleetId();
	var city = new City(tid, location, owner);
	G.cities[tid] = city;
	return city;
}

// inspect properties of a single city.
// add any that are missing,
// fix any whose semantics have changed.
//
function checkCity(cityId, city)
{
	if ('fuel' in city)
	{
		city.wood = city.fuel;
		delete city.fuel;
	}
	if (!('food' in city))
		city.food = 0;
	if (!city.workers)
	{
		city.workers = {};
		city.workers.hunt = 50;
		city.workers.childcare = 50;
	}
	if (!city.workerRates)
		city.workerRates = {};
	if (city.workers.procreate)
	{
		city.workers['raise-children'] = city.workers.procreate;
		city.workerRates['raise-children'] = city.workerRates.procreate;
		delete city.workers.procreate;
		delete city.workerRates.procreate;
	}
	if (city.workers['raise-children'])
	{
		city.workers.childcare = city.workers['raise-children'];
		city.workerRates.childcare = city.workerRates['raise-children'];
		delete city.workers['raise-children'];
		delete city.workerRates['raise-children'];
	}
	city.population = 0;
	for (var j in city.workers)
	{
		if (city.workers[j])
			city.population += (+city.workers[j]);
		else
			delete city.workers[j];
	}
	if (!city.production)
	{
		city.production = {};
	}
	for (var j in city.production)
	{
		if (!city.production[j])
			delete city.production[j];
	}
	if (!city.childrenByAge)
		city.childrenByAge = [];
	city.children = 0;
	for (var i = 0; i < city.childrenByAge.length; i++)
	{
		if (!city.childrenByAge[i])
			city.childrenByAge[i] = 0;
		city.children += (+(city.childrenByAge[i] || 0));
	}

	if (!city.lastUpdate)
		city.lastUpdate = G.world.age;
	if (!city.birth)
		city.birth = city.lastUpdate;

	if (!city.buildings)
		city.buildings = {};
	delete city.buildingOrders;

	for (var bt in city.buildings)
	{
		if ((typeof city.buildings[bt]) == 'number')
		{
			var sz = city.buildings[bt];
			city.buildings[bt] = {
				buildingType: bt,
				size: sz
				};
		}
	}

	if (!city.stock)
		city.stock = {};

	var OLD_STOCK_TYPES = [ 'food', 'fuel', 'wheat', 'meat', 'wood', 'clay', 'stone', 'stone-block', 'stone-weapon' ];
	for (var i = 0; i < OLD_STOCK_TYPES.length; i++)
	{
		var st = OLD_STOCK_TYPES[i];
		if (city[st])
		{
			city.stock[st] = city[st];
			delete city[st];
		}
	}

	if (!city.science)
		city.science = {};

	updateFleetSight(cityId, city);
}

function City(cityId, location, owner)
{
	this._id = cityId;
	this.owner = owner;
	this.location = location;
	this.workers = {};
	this.workerRates = {};
	this.production = {};
	this.population = 0;
	this.children = 0;
	this.childrenByAge = [];
	this.stock = {};
	this.science = {};
	this.lastUpdate = Scheduler.time;
}

function addAvailableJobs(cityId, jobs)
{
	var city = G.cities[cityId];
	var cell = G.terrain.cells[Location.toCellId(city.location)];

	if (!jobs.hunt)
		jobs.hunt = 0;
	if (!jobs.childcare)
		jobs.childcare = 0;
	if (!jobs.research)
		jobs.research = 0;

	if (!jobs['gather-wood'] && cell.terrain == 'forest')
		jobs['gather-wood'] = 0;

	if (!jobs['gather-clay'] && (
			cell.terrain == 'grassland' ||
			cell.terrain == 'hills' ||
			cell.terrain == 'plains' ||
			cell.terrain == 'mountains' ||
			cell.terrain == 'swamp'))
		jobs['gather-clay'] = 0;

	if (!jobs['gather-stone'] && (
			cell.terrain == 'desert' ||
			cell.terrain == 'forest' ||
			cell.terrain == 'grassland' ||
			cell.terrain == 'hills' ||
			cell.terrain == 'mountains' ||
			cell.terrain == 'plains' ||
			cell.terrain == 'swamp' ||
			cell.terrain == 'tundra'))
		jobs['gather-stone'] = 0;

	if (!jobs.farm && cell.subcells.farm)
		jobs.farm = 0;

	if (!jobs.shepherd && city.stock && city.stock.sheep)
		jobs.shepherd = 0;

	if (!jobs.build && city.activity)
		jobs.build = 0;

	for (var bid in city.buildings)
	{
		var b = city.buildings[bid];
		if (b.orders && b.orders.match(/^make-/))
		{
			if (!jobs[b.orders])
				jobs[b.orders] = 0;
		}
	}
}

// given an associative array, return a new associative array with
// all the numbers rounded up, in such a way that the sum of the
// numbers in the result equals the floored sum of the input.
//
function roundWorkers(aa)
{
	var err = 0;
	var sum = 0;
	var jj = [];
	for (var k in aa)
	{
		jj.push([k,aa[k]]);
		sum += aa[k];
	}
	var target = Math.floor(sum);

	if (jj.length == 0)
		return {};

	// sort smallest to largest
	jj.sort(function(a,b) {
		return a[1]-b[1];
		});

	var result = {};
	for (var i = 0; i + 1 < jj.length; i++)
	{
		var x = jj[i];
		var v = Math.ceil(x[1]-err);
		err += v - x[1];
		sum -= x[1];
		target -= v;
		result[x[0]] = v;
	}
	result[jj[jj.length-1][0]] = target;

	return result;
}

function reassignWorkers(cityId, city, quantity, fromJob, toJob)
{
	lockCityStruct(city);

	quantity = removeWorkers(cityId, city, quantity, fromJob);
	addWorkers(cityId, city, quantity, toJob);

	unlockCityStruct(cityId, city);
	return quantity;
}

// adds new people to the city, given a particular job
//
function addWorkers(cityId, city, quantity, toJob)
{
	if (quantity < 0)
		throw new Error("invalid argument for addWorkers");
	if (quantity > 0)
	{
		if (city.lastUpdate != Scheduler.time)
			throw new Error("not ready for addWorkers");

		city.workers[toJob] = (city.workers[toJob] || 0) + quantity;
		city.population += quantity;
		cityNewWorkerRate(city, toJob);
	}
}

var cityWorkerRatesSpecial = {

	farm: function(city, baseProduction) {
		var cell = G.terrain.cells[Location.toCellId(city.location)];
		var numFarms = cell.subcells.farm || 0;
		var maxYield = numFarms * 30;
		if (maxYield == 0)
			return 0;

		var z = maxYield - maxYield * Math.exp(-1 * baseProduction / maxYield);
		return z;
		},

	hunt: function(city, baseProduction) {
		var cell = G.terrain.cells[Location.toCellId(city.location)];
		return Terrain.calculateHuntingRate(cell, baseProduction);
		}
	};

function cityNewWorkerRate(city, job)
{
	var baseProduction = (city.workers[job] || 0) * nextRandomWorkerRate();
	var f = cityWorkerRatesSpecial[job];
	city.workerRates[job] = f ?
			f(city, baseProduction) :
			baseProduction;
}

function removeWorkers(cityId, city, quantity, fromJob)
{
	if (quantity < 0)
		throw new Error("invalid argument for removeWorkers");

	if (quantity > 0)
	{
		if (city.lastUpdate != Scheduler.time)
			throw new Error("not ready for addWorkers");

		if (city.workers[fromJob] > quantity)
		{
			city.population -= quantity;
			city.workers[fromJob] -= quantity;
			cityNewWorkerRate(city, fromJob);
		}
		else if (city.workers[fromJob])
		{
			quantity = city.workers[fromJob];
			city.population -= quantity;
			delete city.workers[fromJob];
			delete city.workerRates[fromJob];
		}
		else
		{
			quantity = 0;
		}
	}
	return quantity;
}

function cityMessage(cityId, message)
{
	var city = G.cities[cityId];
	if (!city)
	{
		throw new Error("city "+cityId+" not found");
	}

	if (!city.messages)
	{
		city.messages = [];
	}

	city.messages.unshift({
		message: message,
		time: Scheduler.time
		});
	while (city.messages.length > 12)
		city.messages.pop();

	notifyPlayer(city.owner, {
		event: 'message',
		source: cityId,
		sourceType: 'city',
		time: Scheduler.time,
		message: message
		});
	cityChanged(cityId);
}

function cityActivityError(cityId, city, errorMessage)
{
	cityMessage(cityId, "Unable to complete orders: " + errorMessage);
}

function setCityActivity(cityId, city, activity, builders, cost)
{
	if (city.activity != activity)
	{
		city.activity = activity;

		var targetWorkerCount = builders;
		if (targetWorkerCount > city.population - 50)
		{
			targetWorkerCount = city.population - 50;
		}

		if (targetWorkerCount < 0)
		{
			targetWorkerCount = 0;
		}

		var curWorkerCount = city.workers.build || 0;
		if (curWorkerCount < targetWorkerCount)
		{
			stealWorkers(cityId, city,
				targetWorkerCount-curWorkerCount, 'build');
		}
		rebalanceWorkers(cityId, city);
	}

	var rate = city.workerRates.build || 0;
	var partComplete = city.production.build || 0;

	city.activityTime = Scheduler.time;
	city.activityComplete = (partComplete / cost);
	city.activitySpeed = (rate / cost);
	cityChanged(cityId);

	if (rate > 0)
	{
		var remaining = cost - partComplete;
		var timeRemaining = remaining / rate;
		if (timeRemaining < 0.001)
		{
			console.log("warning: city "+cityId+" ("+city.name+") activity "+activity+" has "+(city.production.build||0)+" of "+cost+" already");
			timeRemaining = 0.001;
		}
		return cityActivityWakeup(cityId, city, timeRemaining);
	}
}

function freeWorkers(cityId, city, job)
{
	var num = city.workers[job] || 0;

	delete city.workers[job];
	delete city.workerRates[job];
	city.population -= num;

	addWorkers(cityId, city, num, 'idle');
}

function tryEquipNewUnit(cityId, city, unitType)
{
	var costInfo = UNIT_COSTS[unitType];
	if (!costInfo)
	{
		return cityActivityError(cityId, city, "unknown unit type: '"+unitType+"'");
	}

	var builders = costInfo.builders;
	var cost = costInfo.productionCost;
	var activityName = 'equip-'+unitType;

	if (city.activity == activityName && city.production.build >= cost)
	{
		freeWorkers(cityId, city, 'build');

		if (city.population < 100 + costInfo.populationCost)
		{
			return cityActivityError(cityId, city, "not large enough to equip "+unitType);
		}
		delete city.production.build;

		stealWorkers(cityId, city, costInfo.populationCost, 'equip-unit');
		var numSettlers = removeWorkers(cityId, city, Infinity, 'equip-unit');
		createUnit(city.owner, unitType, city.location, {
			population: numSettlers
			});

		cityActivityComplete(cityId, city);
		return;
	}
	else
	{
		setCityActivity(cityId, city, activityName, builders, cost);
		return;
	}
	
}

function cityActivityWakeup(cityId, city, yearsRemaining)
{
	if (city._wakeupTimer)
	{
		Scheduler.cancel(city._wakeupTimer);
		delete city._wakeupTimer;
	}

	var targetTime = Scheduler.time + yearsRemaining;
	if (targetTime < G.world.lastYear+1)
	{
		city._wakeupTimer = Scheduler.scheduleYears(function() {
				lockCityStruct(city);
				unlockCityStruct(cityId, city);
			}, yearsRemaining);
	}
	// otherwise, just wait until cityEndOfYear is called...
}

function cityChanged(cityId)
{
	var city = G.cities[cityId];
	if (!city)
		throw new Error("oops! city "+cityId+" not found");

	terrainChanged(Location.toCellId(city.location));
}

function cityActivityComplete(cityId, city)
{
	delete city.activity;

	city.tasks.shift();
	if (city.tasks.length == 0)
	{
		delete city.tasks;
		cityMessage(cityId, 'Orders complete.');
		cityChanged(cityId);
		rebalanceWorkers(cityId, city);
	}
	else
	{
		return cityActivity(cityId, city);
	}
}

function cityActivity(cityId, city)
{
	if (!city.tasks || city.tasks.length == 0)
	{
		// this city does not have any orders
		return;
	}

	var currentTask = city.tasks[0];
	if (!currentTask)
		throw new Error("invalid first task", city.tasks);

	if (currentTask.task == 'equip')
	{
		return tryEquipNewUnit(cityId, city, currentTask.type);
	}
	else if (currentTask.task == 'improvement')
	{
		return tryDevelopLand(cityId, city, currentTask.type);
	}
	else if (currentTask.task == 'building')
	{
		return tryMakeBuilding(cityId, city, currentTask.type);
	}

	return cityActivityError(cityId, city, "unrecognized task " + currentTask.task);
}

function cmd_rename_city(requestData, queryString, remoteUser)
{
	if (!queryString.match(/^city=(.*)$/))
	{
		console.log("rename-city: invalid query string");
		return;
	}

	var cityId = RegExp.$1;
	var city = G.cities[cityId];
	if (!city)
	{
		console.log("rename-city: city " + cityId + " not found");
		return;
	}

	if (city.owner != remoteUser)
	{
		console.log("rename-city: city " + cityId + " not owned by player " + remoteUser);
		return;
	}

	city.name = requestData.name;
	cityChanged(cityId);
}

function cmd_test_city(requestData, queryString, remoteUser)
{
	if (!queryString.match(/^city=(.*)$/))
	{
		console.log("test-city: invalid query string");
		return;
	}

	var cityId = RegExp.$1;
	var city = G.cities[cityId];
	if (!city)
	{
		console.log("test-city: city " + cityId + " not found");
		return;
	}

	throw new Error("not implemented");
}

function cityEndOfYear(cityId, city)
{
	lockCityStruct(city);

	var ADULT_AGE = G.world.childYears;
	var LIFE_EXPECTANCY = G.world.lifeExpectancy;

	// check child care
	var childCareDemand = city.children;
	var childCareSupply = 2*(city.production.childcare || 0);
	if (childCareSupply < childCareDemand)
	{
		// not enough child care, kill off some children
		var caredForPortion = childCareSupply / childCareDemand;
		var survivalRate = 0.60 + 0.40 * Math.pow(caredForPortion,2);

		if (survivalRate > 1) //sanity check
			survivalRate = 1;

		var newSum = 0.0;
		for (var i = 0; i < ADULT_AGE; i++)
		{
			city.childrenByAge[i] = (city.childrenByAge[i] || 0) * survivalRate;
			newSum += city.childrenByAge[i];
		}
		city.children = newSum;
	}

	// bring forward growing children
	var newAdults = city.childrenByAge[ADULT_AGE-1] || 0;
	for (var i = ADULT_AGE-1; i > 0; i--)
	{
		city.childrenByAge[i] = (city.childrenByAge[i-1] || 0);
	}
	city.childrenByAge[0] = 0;
	city.children -= newAdults;
	if (city.children < 0) { city.children = 0; }

	// calculate new births
	{
		// determine how much room there is for growth
		var cityCapacity = getCityPopulationCapacity(city);
		if (cityCapacity < 1) cityCapacity = 1;
		var housingUsage = (city.population + city.children) / cityCapacity;

		var coreBirthRate = 0.125 + 0.04 * (Math.random() * 2 - 1);
		var birthRate = coreBirthRate / (1 + Math.exp(-(0.85-housingUsage)*12));
		var births = city.population * birthRate;

		city.childrenByAge[0] = births;
		city.children = (city.children || 0) + births;
		city.births += births;
	}

	if (city.production['gather-wood'])
	{
		var pts = city.production['gather-wood'];
		delete city.production['gather-wood'];

		var woodYield = pts * G.world.woodPerWoodGatherer;
		city.stock.wood = (city.stock.wood || 0) + woodYield;

		console.log("  wood gatherers brought in " + woodYield + " wood");
	}

	if (city.production['gather-clay'])
	{
		var pts = city.production['gather-clay'];
		delete city.production['gather-clay'];

		var clayYield = pts * G.world.clayPerClayGatherer;
		city.stock.clay = (city.stock.clay || 0) + clayYield;

		console.log('  clay gatherers brought in ' + clayYield + " clay");
	}

	if (city.production['gather-stone'])
	{
		var pts = city.production['gather-stone'];
		delete city.production['gather-stone'];

		var stoneYield = pts * G.world.stonePerStoneGatherer;
		city.stock.stone = (city.stock.stone || 0) + stoneYield;

		console.log('  stone gatherers brought in ' + stoneYield + " stone");
	}

	// food production
	if (city.production.hunt)
	{
		var pts = city.production.hunt;
		delete city.production.hunt;

		// check if there are any wild animals that can be captured
		var cell = G.terrain.cells[Location.toCellId(city.location)];
		if (cell.hasWildSheep && Math.random() < 1/cell.hasWildSheep && pts >= 8)
		{
			// found a sheep!
			city.stock.sheep = (city.stock.sheep || 0) + 1;
			city.newSheep = (city.newSheep || 0) + 1;
			cell.hasWildSheep -= 1;
			if (cell.hasWildSheep < 1)
				delete cell.hasWildSheep;

			cityMessage(cityId, 'Your hunters found and captured a sheep!');
			pts -= 2;
		}

		var numHarvested = pts;

		// record number of animals hunted
		cell.wildlifeHunted += numHarvested;

		var foodYield = numHarvested * G.world.foodPerAnimal;
		city.stock.meat = (city.stock.meat || 0) + foodYield;

		console.log("  hunters brought in " + foodYield + " meat");
	}

	if (city.production.farm)
	{
		var pts = city.production.farm;
		delete city.production.farm;

		var foodYield = pts * G.world.foodPerFarmer;
		city.stock.wheat = (city.stock.wheat || 0) + foodYield;

		console.log("  farmers brought in " + foodYield + " wheat");
	}

	// feed the population
	var foodDemand = city.hunger || 0;
	var foodSupply = getTotalFood(city);
	var foodConsumed;
	if (foodSupply >= foodDemand)
	{
		// ok, enough to satisfy everyone
		foodConsumed = foodDemand;
	}
	else   // city.food < foodDemand
	{
		// not enough food, some people gonna die
		foodConsumed = foodSupply;
	}
	subtractFood(city, foodConsumed);
	var sustenance = foodDemand > 0 ? Math.sqrt(foodConsumed / foodDemand) : 1;
	city.hunger = 0;

	// scientists
	processResearchingOutput(city);

	// livestock
	if (city.stock && city.stock.sheep)
	{
		var numSheep = city.stock.sheep;
		var numShepherds = (city.production.shepherd || 0);

		var vulnerableSheep = numSheep -
			(city.newSheep || 0) -
			numShepherds * 8;
		if (vulnerableSheep > 0)
		{
			if (Math.random() < vulnerableSheep / 4)
			{
				var lostSheep = (Math.random() < 0.25 ? vulnerableSheep : Math.round(vulnerableSheep / 3));
				if (lostSheep > 0)
				{
					// some sheep run away
					cityMessage(cityId, lostSheep + " sheep have run away.");
					city.stock.sheep -= lostSheep;
				}
			}
		}

		if (numSheep >= 2)
		{
			var newSheep = Math.floor(numSheep / 5) + Math.round((numSheep + 7*Math.random()) / 11);
			city.stock.sheep += newSheep;

			// hard code a limit of 1000 sheep, for now,
			// since sheep have no useful purpose
			if (city.stock.sheep > 1000)
				city.stock.sheep = 1000;

			// Note: I read somewhere that you want one acre of grazing land for
			// every 3-6 sheep. There are 640 acres in a square mile.
			// Assuming 1 sq. mi. == 1 zone, then that's 1920-3840 sheep per zone.
			// Could round that off to max. 3000 sheep per zone.
		}

		delete city.newSheep;
	}

	// calculate deaths
	var sustainRate = 1 - 1/LIFE_EXPECTANCY;
	sustainRate *= sustenance;
	var deathRate = 1 - sustainRate;
	var deaths = city.population * deathRate;
	city.deaths += deaths;

	// distribute net change in adults evenly
	var netPopChange = newAdults - deaths;
	var changeApplied = 0;
	for (var job in city.workers)
	{
		var portion = city.workers[job] / city.population;
		var thisChange = portion * netPopChange;
		city.workers[job] += thisChange;
		changeApplied += thisChange;
		cityNewWorkerRate(city, job);
	}
	if (netPopChange > changeApplied)
	{
		city.workers.childcare = (city.workers.childcare || 0) + (netPopChange - changeApplied);
		changeApplied = netPopChange;
	}
	city.population += changeApplied;

	// let governor adjust workers assignment
	governor_endOfYear(cityId, city);

	// notify interested parties
	// done changing the city properties
	unlockCityStruct(cityId, city);

	// record stats
	var fs = require('fs');
	var fd = fs.appendFileSync(G.worldName+'/city'+cityId+'.log',
		[ G.world.lastYear,
		city.population,
		city.children,
		city.food,
		city.births,
		city.deaths,
		newAdults ].join(',') + "\n");

	cityMessage(cityId,
		"end of year: "+Math.floor(city.births)+" births, " +
				Math.floor(city.deaths)+ " deaths");
	
	city.births = 0;
	city.deaths = 0;
}

// prereqs- terrain wildlife counters should be updated
function cityEndOfYear_cleanup(cityId, city)
{
	// now that wildlife counters have been updated, recalculate hunter production rate
	if (city.workers.hunt)
	{
		cityNewWorkerRate(city, 'hunt');
	}
}

function governor_endOfYear(cityId, city)
{
	// TODO- check for actionable problems and announce them as appropriate

	var jobLevels = governor_determineJobLevels(cityId, city);
	governor_dispatchJobAssignments(cityId, city, jobLevels);
}

function governor_dispatchJobAssignments(cityId, city, jobLevels)
{
	// group job requests by priority
	var priorities = [];
	var jobsByPriority = {};
	var totalByPriority = {};

	for (var i = 0; i < jobLevels.length; i++)
	{
		var jl = jobLevels[i];
		var p = jl.priority;

		if (!jobsByPriority[p])
		{
			priorities.push(p);
			jobsByPriority[p] = [];
			totalByPriority[p] = 0;
		}
		jobsByPriority[p].push(jl);
		totalByPriority[p] += jl.quantity;
	}

	priorities.sort(function(a,b) { return -(a - b); });

	var workersLeft = city.population;
	var assignments = {};

	for (var i = 0; i < priorities.length; i++)
	{
		var p = priorities[i];
		var totalDemand = totalByPriority[p];

		if (workersLeft <= 0) continue;

		var rations = totalDemand > workersLeft ? totalDemand / workersLeft : 1;

		for (var j = 0; j < jobsByPriority[p].length; j++)
		{
			var jl = jobsByPriority[p][j];
			assignments[jl.job] = (assignments[jl.job] || 0) + jl.quantity * rations;
			workersLeft -= jl.quantity * rations;
		}
	}

	// look for any jobs with extra workers; when we assign workers to jobs that need
	// extra people, we will take them from the ones we find here
	var jobsWithExtra = [];
	if (city.workers.idle)
	{
		jobsWithExtra.push('idle');
	}
	for (var job in city.workers)
	{
		if (job == 'idle')
			continue;

		var wanted = assignments[job] || 0;
		if (city.workers[job] > wanted)
		{
			jobsWithExtra.push(job);
		}
	}

	// look for any jobs where additional workers need to be assigned
	for (var job in assignments)
	{
		var actual = city.workers[job] || 0;
		if (actual < assignments[job])
		{
			var amountShort = assignments[job] - actual;
			while (amountShort > 0 && jobsWithExtra.length > 0)
			{
				var fromJob = jobsWithExtra[0];
				var extraThere = city.workers[fromJob] - (assignments[fromJob] || 0);
				if (extraThere >= amountShort)
				{
					reassignWorkers(cityId, city, amountShort, fromJob, job);
					amountShort = 0;
				}
				else
				{
					reassignWorkers(cityId, city, extraThere, fromJob, job);
					amountShort -= extraThere;
					jobsWithExtra.shift();
				}
			}
		}
	}
}

function governor_determineJobLevels(cityId, city)
{
	var jobLevels = [];

	var foodInStorage = getTotalFood(city);
	var oneYearFood = G.world.hungerPerAdult * city.population +
			G.world.hungerPerChild * city.children;

	var DESIRED_STOCK = 1.5;
	var MAX_ASSIGN = 4/3;

	var desiredFoodNextYear = oneYearFood * (
			MAX_ASSIGN + (1-MAX_ASSIGN) * (foodInStorage / oneYearFood) / DESIRED_STOCK);
	if (desiredFoodNextYear < 0)
		desiredFoodNextYear = 0;

	// first consider farms
	var mandatoryFarmers = Math.ceil(desiredFoodNextYear / G.world.foodPerFarmer);
	var numFarms = getFarmCount(city);
	if (mandatoryFarmers > numFarms * 15)
	{
		mandatoryFarmers = numFarms * 15;
	}
	var optionalFarmers = numFarms * 10;
	desiredFoodNextYear -= mandatoryFarmers * G.world.foodPerFarmer;

	jobLevels.push({ priority: 100, job: 'farm', quantity: mandatoryFarmers });
	jobLevels.push({ priority: 1, job: 'farm', quantity: optionalFarmers });

	// remaining food must come from hunting
	var mandatoryHunters = Math.ceil(desiredFoodNextYear / G.world.foodPerAnimal);
	var optionalHunters = mandatoryHunters < 20 ? 20 - mandatoryHunters : 0;

	jobLevels.push({ priority: 100, job: 'hunt', quantity: mandatoryHunters });
	jobLevels.push({ priority: 5, job: 'hunt', quantity: optionalHunters });

	// consider child care
	var mandatoryChildCaretakers = Math.ceil(city.children / 2);
	jobLevels.push({ priority: 90, job: 'childcare', quantity: mandatoryChildCaretakers });

	// consider livestock care
	var numSheep = city.stock ? (city.stock.sheep || 0) : 0;
	var mandatoryShepherds = Math.ceil(numSheep / 8);
	jobLevels.push({ priority: 85, job: 'shepherd', quantity: mandatoryShepherds });

	return jobLevels;
}

function processResearchingOutput(city)
{
	var scienceOutput = city.production.research || 0;
	delete city.production.research;

	scienceOutput /= 10;

	if (!city.partialScience)
		city.partialScience = {};

	for (var k in city.partialScience)
	{
		if (city.partialScience[k] + scienceOutput >= 1.0)
		{
			// completed research
			scienceOutput -= city.partialScience[k];
			delete city.partialScience[k];
			cityLearned(city, k);
		}
		else
		{
			city.partialScience[k] += scienceOutput;
			scienceOutput = 0;
		}
	}

	if (scienceOutput == 0)
		return;

	// look for new topics to start research on
	var candidates = [];
	for (var techId in TECHNOLOGIES)
	{
		if (city.science[techId] || city.partialScience[techId])
			continue;

		var tech = TECHNOLOGIES[techId];
		var haveAllPrereqs = true;
		if (tech.prereqs)
		{
			for (var i = 0; i < tech.prereqs.length; i++)
			{
				if (!city.science[tech.prereqs[i]])
					haveAllPrereqs = false;
			}
		}
		if (!haveAllPrereqs)
			continue;

		if (tech.resourceCost)
		{
			var canAfford = true;
			for (var type in tech.resourceCost)
			{
				var cost = tech.resourceCost[type]; 
				var onHand = city.stock[type] || 0;
				if (cost > onHand)
					canAfford = false;
			}
			if (!canAfford)
				continue;
		}

		candidates.push(techId);
	}

	// shuffle candidates list
	shuffleArray(candidates);

	for (var i = 0; i < scienceOutput; i++)
	{
		if (i >= candidates.length)
			break;

		// subtract out the resources this technology costs to learn
		//
		var techId = candidates[i];
		var tech = TECHNOLOGIES[techId];
		if (tech.resourceCost)
		{
			for (var type in tech.resourceCost)
			{
				city.stock[type] = (city.stock[type] || 0) - tech.resourceCost[type];
			}
		}

		// record the fact that we started learning this tech
		//
		city.partialScience[techId] = 0;
	}
}

function shuffleArray(a)
{
	for (var i = 0, l = a.length; i < l; i++)
	{
		var j = Math.floor(Math.random() * (l-i)) + i;
		var t = a[i];
		a[i] = a[j];
		a[j] = t;
	}
}

function cityLearned(city, technologyId)
{
	city.science[technologyId] = 1;
	console.log("City "+city.name+" learned "+technologyId);
}

function processManufacturingOutput(city)
{
	var ppByOutput = {};
	for (var job in city.workers)
	{
		if (job.match(/^make-(.*)$/))
		{
			var outputResource = RegExp.$1;
			ppByOutput[outputResource] = city.production[job];
			delete city.production[job];
		}
	}

	for (var outputResource in ppByOutput)
	{
		var recipe = FACTORY_RECIPES[outputResource];
		if (!recipe)
			continue;

		var idealOutput = ppByOutput[outputResource] * (recipe.rate || 0.01);
		var actualOutput = idealOutput;
		for (var inputType in recipe.input)
		{
			var demand = actualOutput * recipe.input[inputType];
			var supply = city.stock[inputType] || 0;
			if (supply < 0) { supply = 0; }

			if (demand > supply)
			{
				actualOutput *= supply / demand;
			}
		}

		for (var inputType in recipe.input)
		{
			var demand = actualOutput * recipe.input[inputType];
			var newSupply = (city.stock[inputType] || 0) - demand;
			if (newSupply > 0)
				city.stock[inputType] = newSupply;
			else
				delete city.stock[inputType];
		}

		city.stock[outputResource] = (city.stock[outputResource] || 0) + actualOutput;
	}
}

// prepare a City struct for manipulation; each call to lockCityStruct()
// should be paired with a call to unlockCityStruct().
// nesting the calls is ok.
// the global value Scheduler.time should be properly set when calling
// lockCityStruct().
//
function lockCityStruct(city)
{
	if (!city.lastUpdate)
		city.lastUpdate = 0;

	var FOOD_PER_CHILD = G.world.hungerPerChild;
	var FOOD_PER_ADULT = G.world.hungerPerAdult;

	var bringForward = function(aTime)
	{
		if (city.lastUpdate >= aTime)
			return;

		var yearsElapsed = aTime - city.lastUpdate;

		// calculate worker production
		for (var job in city.workers)
		{
			var rate = city.workerRates[job] || 0;
			var productionPoints = yearsElapsed * rate;
			city.production[job] = (city.production[job] || 0) +
				productionPoints;
		}

		// manufacturing jobs
		processManufacturingOutput(city);

		// calculate hunger
		var foodRequired = (FOOD_PER_ADULT * city.population + FOOD_PER_CHILD * city.children) * yearsElapsed;
		city.hunger = (city.hunger || 0) + foodRequired;

		// finished
		city.lastUpdate = aTime;
	};

	if (!Scheduler.time)
		throw new Error("invalid year ("+Scheduler.time+", "+G.world.lastYear+")");

	if (city._lockLevel)
	{
		if (city._lockedWhen != Scheduler.time)
			throw new Error("lockCityStruct() called on city already locked with a different timestamp");

		city._lockLevel++;
	}
	else
	{
		city._lockLevel = 1;
		city._lockedWhen = Scheduler.time;
		bringForward(Scheduler.time);
	}
}

function unlockCityStruct(cityId, city)
{
	if (city._lockLevel > 1)
	{
		city._lockLevel--;
	}
	else
	{
		if (!city._inActivity)
		{
			city._inActivity = true;
			cityActivity(cityId, city);
			cityChanged(cityId);
			delete city._inActivity;
		}
		delete city._lockLevel;
		delete city._lockedWhen;
	}
}

function getCityPopulationCapacity(city)
{
	var c = G.terrain.cells[Location.toCellId(city.location)];
	return (c.subcells.hamlet || 0) * 200;
}

function getTotalFood(city)
{
	var sum = 0;
	for (var i = 0; i < FOOD_TYPES.length; i++)
	{
		var ft = FOOD_TYPES[i];
		sum += (city.stock[ft] || 0);
	}
	return sum;
}

function subtractFood(city, amount)
{
	for (var i = 0; i < FOOD_TYPES.length; i++)
	{
		var ft = FOOD_TYPES[i];
		if (city.stock[ft])
		{
			if (city.stock[ft] > amount)
			{
				city.stock[ft] -= amount;
				return true;
			}
			amount -= city.stock[ft];
			delete city.stock[ft];
		}
	}
	return false;
}

function nextRandomWorkerRate()
{
	var t = Math.random();
	if (t == 0) return 0;
	return Math.exp( -Math.log((1/t)-1) / 15 );
}

//TODO- ensure that the following two distributions are equivalent:
//   RndProductionPoints(x)
//  and
//   RndProductionPoints(x/2) + RndProductionPoints(x/2)
//
function RndProductionPoints(x)
{
	var t = Math.random();
	if (t == 0) return 0;
	return x * Math.exp( -Math.log((1/t)-1) / 15 );
}

//caller should call fire-city-update-notification
//
function stealWorkers(cityId, city, quantity, toJob)
{
	var sumAvailable = 0;
	for (var job in city.workers)
	{
		if (job != toJob)
			sumAvailable += city.workers[job];
	}

	if (sumAvailable < quantity)
		throw new Error("oops, not enough available workers (city "+city.name+", want "+quantity+" for job " + toJob+")");

	for (var job in city.workers)
	{
		if (job != toJob)
		{
			var amt = quantity * city.workers[job]/sumAvailable;
			city.workers[job] -= amt;
			city.population -= amt;
			if (city.workers[job] > 0)
			{
				cityNewWorkerRate(city, job);
			}
			else
			{
				delete city.workers[job];
				delete city.workerRates[job];
			}
		}
	}

	addWorkers(cityId, city, quantity, toJob);
}

function cmd_reassign_workers(requestData, queryString, remoteUser)
{
	if (!queryString.match(/^city=(.*)$/))
	{
		console.log("reassign-workers: invalid query string");
		return;
	}

	var cityId = RegExp.$1;
	var city = G.cities[cityId];
	if (!city)
	{
		console.log("reassign-workers: city " + cityId + " not found");
		return;
	}

	if (city.owner != remoteUser)
	{
		console.log("reassign-workers: city " + cityId + " not owned by player " + remoteUser);
		return;
	}

	var fromJob = requestData.fromJob;
	var toJob = requestData.toJob;
	var quantity = +requestData.amount;

	lockCityStruct(city);

	if (quantity + 1 >= +(city.workers[fromJob] || 0))
		quantity = quantity+1;

	reassignWorkers(cityId, city, quantity, fromJob, toJob);

	unlockCityStruct(cityId, city);
}

function cmd_equip_unit(requestData, queryString, remoteUser)
{
	if (!queryString.match(/^city=(.*)$/))
	{
		console.log("equip-unit: invalid query string");
		return;
	}

	var cityId = RegExp.$1;
	var city = G.cities[cityId];
	if (!city)
	{
		console.log("equip-unit: city " + cityId + " not found");
		return;
	}

	if (city.owner != remoteUser)
	{
		console.log("equip-unit: city " + cityId + " not owned by player " + remoteUser);
		return;
	}

	lockCityStruct(city);

	if (!city.tasks)
		city.tasks = [];
	city.tasks.push({
		task: 'equip',
		type: requestData.type
		});

	unlockCityStruct(cityId, city);
}

function cmd_build_improvement(requestData, queryString, remoteUser)
{
	if (!queryString.match(/^city=(.*)$/))
	{
		console.log("build-improvement: invalid query string");
		return;
	}

	var cityId = RegExp.$1;
	var city = G.cities[cityId];
	if (!city)
	{
		console.log("build-improvement: city " + cityId + " not found");
		return;
	}

	if (city.owner != remoteUser)
	{
		console.log("build-improvement: city " + cityId + " not owned by player " + remoteUser);
		return;
	}

	lockCityStruct(city);

	if (!city.tasks)
		city.tasks = [];
	city.tasks.push({
		task: 'improvement',
		type: requestData.improvement
		});

	unlockCityStruct(cityId, city);
}

function cmd_build_building(requestData, queryString, remoteUser)
{
	if (!queryString.match(/^city=(.*)$/))
	{
		console.log("build-building: invalid query string");
		return;
	}

	var cityId = RegExp.$1;
	var city = G.cities[cityId];
	if (!city)
	{
		console.log("build-building: city " + cityId + " not found");
		return;
	}

	if (city.owner != remoteUser)
	{
		console.log("build-building: city " + cityId + " not owned by player " + remoteUser);
		return;
	}

	lockCityStruct(city);

	if (!city.tasks)
		city.tasks = [];
	city.tasks.push({
		task: 'building',
		type: requestData.building
		});

	unlockCityStruct(cityId, city);
}

function cmd_building_orders(requestData, queryString, remoteUser)
{
	var QS = require('querystring');
	var args = QS.parse(queryString);

	if (!args.city || !args.building)
	{
		console.log("building-orders: invalid query string");
		return;
	}

	var city = G.cities[args.city];
	if (!city)
	{
		console.log("building-orders: city " + args.city + " not found");
		return;
	}

	if (city.owner != remoteUser)
	{
		console.log("building-orders: city " + args.city + " not owned by player " + remoteUser);
		return;
	}

	var building = city.buildings[args.building];
	if (!building)
	{
		console.log("building-orders: building " + args.building + " not found in city " + args.city);
		return;
	}
		
	lockCityStruct(city);

	building.orders = requestData.orders;

	unlockCityStruct(args.city, city);
}

function addBuilding(cityId, city, buildingType)
{
	city.buildings[buildingType] = (city.buildings[buildingType] || 0) + 1;
}

function tryMakeBuilding(cityId, city, buildingType)
{
	var costInfo = BUILDING_TYPE_COSTS[buildingType];
	if (!costInfo)
	{
		return cityActivityError(cityId, city, "invalid building type: "+buildingType);
	}

	var builders = costInfo[0];
	var cost = costInfo[1];
	var activityName = 'build-'+buildingType;

	if (city.activity == activityName && city.production.build >= cost)
	{
		delete city.production.build;
		freeWorkers(cityId, city, 'build');

		addBuilding(cityId, city, buildingType);
		cityActivityComplete(cityId, city);

		if (costInfo[2])
		{
			costInfo[2](cityId, city);
		}

		return;
	}
	else
	{
		setCityActivity(cityId, city, activityName, builders, cost);
	}
}

function tryDevelopLand(cityId, city, landType)
{
	var ltc = LAND_TYPE_COSTS[landType];
	if (!ltc)
	{
		return cityActivityError(cityId, city, "invalid land type: "+landType);
	}

	var builders = ltc[0];
	var cost = ltc[1];
	var activityName = 'build-'+landType;

	if (city.activity == activityName && city.production.build >= cost)
	{
		delete city.production.build;
		freeWorkers(cityId, city, 'build');

		developLand(city.location, landType, 1);
		cityActivityComplete(cityId, city);

		if (ltc[2])
		{
			ltc[2](cityId, city);
		}

		return;
	}
	else
	{
		setCityActivity(cityId, city, activityName, builders, cost);
	}
}

function city_addWorkersAny(cityId, city, amount)
{
	lockCityStruct(city);

	addWorkers(cityId, city, amount, 'idle');
	rebalanceWorkers(cityId, city);

	unlockCityStruct(cityId, city);
}

function getFarmCount(city)
{
	var cell = G.terrain.cells[Location.toCellId(city.location)];
	var numFarms = cell.subcells.farm || 0;
	return numFarms;
}

function rebalanceWorkers(cityId, city)
{
	var amount = city.workers.idle || 0;
	delete city.workers.idle;
	delete city.workerRates.idle;
	city.population -= amount;

	if (amount > 0)
	{
		var numFarms = getFarmCount(city);
		var needForFarmers = numFarms * 15;
		var q = needForFarmers - (city.workers.farm || 0);
		if (q > 0)
		{
			if (q > amount) { q = amount; }
			addWorkers(cityId, city, q, 'farm');
			amount -= q;
		}
	}

	if (amount > 0)
	{
		var needForChildCaretakers = (city.children || 0) * .4;
		var q = needForChildCaretakers - (city.workers.childcare || 0);
		if (q > 0)
		{
			if (q > amount) { q = amount; }
			addWorkers(cityId, city, q, 'childcare');
			amount -= q;
		}
	}

	if (amount > 0)
	{
		var needForBuilders = city.activity ? 20 : 0;
		var q = needForBuilders - (city.workers.build || 0);
		if (q > 0)
		{
			if (q > amount) { q = amount; }
			addWorkers(cityId, city, q, 'build');
			amount -= q;
		}
	}

	if (amount > 0)
	{
		addWorkers(cityId, city, amount/2, 'hunt');
		addWorkers(cityId, city, amount/2, 'childcare');
	}
}

global.roundWorkers = roundWorkers;
global.addAvailableJobs = addAvailableJobs;
global.newCity = newCity;
global.checkCity = checkCity;
global.cityEndOfYear = cityEndOfYear;
global.cityEndOfYear_cleanup = cityEndOfYear_cleanup;
global.city_addWorkersAny = city_addWorkersAny;

exports.cmd_rename_city = cmd_rename_city;
exports.cmd_test_city = cmd_test_city;
exports.cmd_reassign_workers = cmd_reassign_workers;
exports.cmd_equip_unit = cmd_equip_unit;
exports.cmd_build_improvement = cmd_build_improvement;
exports.cmd_build_building = cmd_build_building;
exports.cmd_building_orders = cmd_building_orders;
