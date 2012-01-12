var FS = require('fs');
var GAME = require('./game.js');

function enumResourceTypes()
{
	var filenames = FS.readdirSync('../html/resource_icons');
	var types = new Array();
	for (var i in filenames)
	{
		if (filenames[i].match(/\.png$/))
		{
			var resourceType = filenames[i].substr(0, filenames[i].length - 4);
			types.push(resourceType);
		}
	}
	return types;
}

exports.handleGameStateRequest = function(request,response)
{
	var gameState = GAME.getGameState();
	gameState.allServerResourceTypes = enumResourceTypes();

	response.writeHead(200, {'Content-Type':'text/plain'});
	response.end(
		JSON.stringify(gameState)
		);
};

