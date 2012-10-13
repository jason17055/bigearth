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

Perspective.prototype.getLocation = function()
{
	return this.fleet.location;
};

Perspective.prototype.getMap = function()
{
	return Fleet.getMap(this.fleetId, this.fleet);
};

module.exports = Perspective;
