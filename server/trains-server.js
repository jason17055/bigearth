var gameState = {
	rails: {},
	map: {},
	players: {}
	};

exports.handleGameStateRequest = function(request,response)
{
	response.writeHead(200, {'Content-Type':'text/plain'});
	response.end(
		JSON.stringify(gameState)
		);
};

