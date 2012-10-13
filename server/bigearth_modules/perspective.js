var Fleet = require('./fleet.js');

function Perspective(fleetId, fleet)
{
	this.fleetId = fleetId;
	this.fleet = fleet;
}

Perspective.prototype.getLocation = function()
{
	return this.fleet.location;
};

Perspective.prototype.getMap = function()
{
	return Fleet.getMap(this.fleetId, this.fleet);
};

module.exports = Perspective;
