var GAME = {};
(function() {

var serverState = {
	rails: {},
	map: {},
	players: {}
	};

var loadMap = function(mapName)
{
	var mapDir = "../html/maps";
	var data = FS.readFileSync(mapDir+"/"+mapName);
	serverState.mapName = mapName;
	serverState.map = JSON.parse(data);
	autoCreateDemands();
};

GAME.getGameState = function()
{
	return {
	rails: serverState.rails,
	map: serverState.map,
	players: {}
	};
};

// end of scope
})();

if (!exports)
{
	exports = GAME;
}
exports.getGameState = GAME.getGameState;
