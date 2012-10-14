var Fleet = require('./fleet.js');
var Location = require('../../html/location.js');

function Perspective(fleetId, fleet)
{
	this.fleetId = fleetId;
	this.fleet = fleet;
}

Perspective.prototype.estimateMovementCost = function(fromLoc, toLoc, map)
{
	if (!map)
	{
		map = this.getMap();
	}

	var mInfo = Fleet.getMovementCost_byMap(this.fleet,
		Location.toCellId(fromLoc),
		Location.toCellId(toLoc),
		map
		);
	return mInfo.delay;
};

//return the population of the city that this fleet is currently in
Perspective.prototype.getCityPopulation = function()
{
	var terrain = getTerrainLocation(this.fleet.location);
	if (terrain && terrain.city)
	{
		var city = G.cities[terrain.city];
		return (city.population || 0) + (city.children || 0);
	}
	return 0;
};

Perspective.prototype.getLocation = function()
{
	return this.fleet.location;
};

Perspective.prototype.getMap = function()
{
	return Fleet.getMap(this.fleetId, this.fleet);
};

module.exports = Perspective;
