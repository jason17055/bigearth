var HTTP = require('http');
var URL = require('url');
var FS = require('fs');
var OS = require('os');
var QS = require('querystring');
var CRYPTO = require('crypto');
var SESSIONS = require('./sessions.js');
var EVENTS = require('./events.js');
var SECRET = CRYPTO.randomBytes(20).toString('hex');
var SECURE = false;
var Scheduler = require('./bigearth_modules/scheduler.js');
var Lobby = require('./bigearth_modules/lobby.js');

var GAME = require('./bigearth-game.js');
var PERSIST = require('./bigearth-persist.js');

// Big Earth globals, used by both client and server code
global.BE = {};
BE.fleetPace = 1000;

function handleStaticFileRequest(requestPath,request,response)
{
	if (requestPath == 'region')
	{
		requestPath = 'region.html';
	}

	var mimeType = requestPath.match('\.png$') ? 'image/png' :
		requestPath.match('\.html$') ? 'text/html' :
		requestPath.match('\.css$') ? 'text/css' :
		requestPath.match('\.js$') ? 'text/javascript' :
		'text/plain';

	var onFileOpenError = function(err)
	{
		console.log(requestPath+': 404 ('+err+')');
		response.writeHead(404, {'Content-Type': 'text/plain'});
		response.end('File not found\n');
	};

	var onFileOpenSuccess = function(data)
	{
		console.log(requestPath+': 200');
		response.writeHead(200, {'Content-Type': mimeType});
		response.end(data);
	};

	FS.readFile('../html'+requestPath, null, function(err,data)
		{
		if (err)
		{
			onFileOpenError(err);
		}
		else
		{
			onFileOpenSuccess(data);
		}

		});
}

function handleGameStateRequest(request,response)
{
	var s = request.Session;
	if (!request.remote_player)
	{
		response.writeHead(401);
		response.end();
		return;
	}

	var gameState = getGameState(request);

	var eventStream = EVENTS.createEventStream(request.remote_player);
	gameState.nextEventUrl = eventStream.getNextEventUrl();

	response.writeHead(200, {'Content-Type':'text/plain'});
	response.end(
		JSON.stringify(gameState)
		);
}

function handleLoginRequest(request,response)
{
	var requestPath = URL.parse(request.url, true);
	var args = requestPath.query;

	// calculate what the checksum *should* be for this identity
	var b = new Buffer(SECRET + '.' + args.id);
	var sha1 = CRYPTO.createHash('sha1');
	sha1.update(b);
	var expectedChecksum = sha1.digest('hex');

	if (args.id.match(/^([\w\d.@_-]+)$/)
		&& (args.cs == expectedChecksum || !SECURE))
	{
		var sid = SESSIONS.newSession({
			identity: args.id
			});
		var respondToLogin = function() {
		response.writeHead(303, {
			'Set-Cookie': SESSIONS.cookieName + "=" + sid + ';uid=' + args.id,
			'Content-Type': 'text/plain',
			'Location': '/bigearth.html'
			});
		response.end();
		};

		newPlayer(args.role, args.id);
		respondToLogin();
	}
	else
	{
		response.writeHead(500, {
			'Content-Type': 'text/plain'
			});
		response.end('Invalid login request');
		console.log("Invalid login request:");
		console.log(" got cs=" + args.cs + "; expected " + expectedChecksum);
	}
}

function sendEvent(evt, response)
{
	response.writeHead(200, {
		'Content-Type': 'text/json'
		});
	response.end(
		JSON.stringify(evt)
		);
}

function notifyPlayer(playerId, evt)
{
	var streams = EVENTS.eventStreamsByPlayer[playerId];
	if (streams)
	{
		for (var k in streams)
		{
			var s = streams[k];
			s.postEvent(evt);
		}
	}
}
global.notifyPlayer = notifyPlayer;

function handleActionRequest(verb, queryString, request, response)
{
	console.log('got request ' + verb + ' ' + queryString);

	if (!actionHandlers[verb])
	{
		response.writeHead(404);
		response.end('Bad request\n');
		return;
	}

	var body = '';
	request.on('data', function(chunk) {
		body += chunk;
		if (body.length >= 1e6) {
			// FLOOD ATTACK OR FAULTY CLIENT
			request.connection.destroy();
		}
		});
	request.on('end', function() {
		console.log("raw request is "+body);
		var requestData;

		try
		{
		if (request.headers['content-type'].match(/json/))
			requestData = JSON.parse(body);
		else
			requestData = QS.parse(body);
		}
		catch (err) {
			requestData = {};
		}

		var responseData = actionHandlers[verb](requestData, queryString, request.remote_player);
		if (responseData)
		{
			response.writeHead(200, {
				'Content-Type': 'text/json'
				});
			response.end(JSON.stringify(responseData));
		}
		else
		{
			response.writeHead(202);
			response.end();
		}
		});
}

function handleCitiesRequest(pathInfo, request, response)
{
	var sendResponse = function(resultData) {
		response.writeHead(200, {
			'Content-Type': 'text/json'
			});
		response.end(
			JSON.stringify(resultData)
			);
	};

	GAME.getCities(pathInfo, sendResponse);
}

function handleFleetsRequest(pathInfo, request, response)
{
	var sendResponse = function(resultData) {
		response.writeHead(200, {
			'Content-Type': 'text/json'
			});
		response.end(
			JSON.stringify(resultData)
			);
	};

	GAME.getFleets(pathInfo, sendResponse);
}

function handleMapRequest(pathInfo, request, response)
{
	console.log("got map request "+pathInfo);

	var processor = function(resultData) {
		response.writeHead(200, {
			'Content-Type': 'text/json'
			});
		response.end(
			JSON.stringify(resultData)
			);
	};

	//if (pathInfo.match(/^([\d]+)\/([\d.]+)-([\d.]+)\/([\d.]+)-([\d.]+)$/))
	//{
	//	var mapId = RegExp.$1;
	//	resultData = GAME.getMapFragment(mapId, RegExp.$2, RegExp.$3, RegExp.$4, RegExp.$5);
	//	
	//}

	GAME.getMapFragment(request.remote_player, processor);
}

function handleDefaultDocumentRequest(request, response)
{
	if (request.remote_user)
	{
		response.writeHead(302, {Location:'/bigearth.html'});
	}
	else if (SESSIONS.getLoginNameFromCookie(request))
	{
		var loginName = SESSIONS.getLoginNameFromCookie(request);
		response.writeHead(302, {Location:'/login?id='+loginName});
	}
	else
	{
		response.writeHead(302, {Location:'/login.html'});
	}
	response.end();
}

var dirty = false;
function makeDirty()
{
	if (!dirty)
	{
		dirty = true;
		setTimeout(function() {
			saveWorld(G);
			dirty = false;
			}, 20000);
	}
}

function handleRequest(request,response)
{
	var requestPath = URL.parse(request.url);
	if (requestPath.pathname.match(/\.\.|\.\/|\/\./))
	{
		// prevent any requests that remotely look like
		// they are trying to access a directory path with ..
		response.writeHead(404);
		response.end('Bad request\n');
		return;
	}

	var s = SESSIONS.getSessionFromCookie(request);
	request.Session = s;
	request.remote_user = s.identity;
	request.remote_player = s.identity;

	Scheduler.catchup(getYear());

	if (requestPath.pathname == '/')
	{
		return handleDefaultDocumentRequest(request, response);
	}
	else if (requestPath.pathname == "/gamestate")
	{
		return handleGameStateRequest(request,response);
	}
	else if (requestPath.pathname.match(/^\/map\/(.*)$/))
	{
		var pathInfo = RegExp.$1;
		return handleMapRequest(pathInfo, request, response);
	}
	else if (requestPath.pathname.match(/^\/cities\/(.*)$/))
	{
		var pathInfo = RegExp.$1;
		return handleCitiesRequest(pathInfo, request, response);
	}
	else if (requestPath.pathname.match(/^\/fleets\/(.*)$/))
	{
		var pathInfo = RegExp.$1;
		return handleFleetsRequest(pathInfo, request, response);
	}
	else if (requestPath.pathname == "/login")
	{
		makeDirty();
		return handleLoginRequest(request,response);
	}
	else if (requestPath.pathname.match(/^\/events\/(\d+)\/(.*)$/))
	{
		var streamId = RegExp.$1;
		var eventId = RegExp.$2;
		var stream = EVENTS.allEventStreams[streamId];
		if (stream)
		{
			return stream.handleEventRequest(eventId, request, response);
		}
		else
		{
			response.writeHead(404, {'Content-Type': 'text/plain'});
			response.end('Bad event stream id\n');
			return;
		}
	}
	else if (requestPath.pathname.match(/^\/request\/(.*)$/))
	{
		var verb = RegExp.$1;
		var queryString = requestPath.query;
		makeDirty();
		return handleActionRequest(verb, queryString, request, response);
	}

	// assume it is a request for a file
	return handleStaticFileRequest(requestPath.pathname,request,response);
}

function loadWorld(worldName)
{
	G.worldName = worldName;
	loadWorldParameters();
	loadTerrain();
	loadPlayers();
	loadMaps();
	loadBattles();
	loadFleets();
	loadCities();
}

function loadWorldParameters()
{
	var filename = G.worldName + '/world.txt';
	var rawData = FS.readFileSync(filename);
	G.world = JSON.parse(rawData);
}

function loadTerrain()
{
	var filename = G.worldName + '/terrain.txt';
	var rawData = FS.readFileSync(filename);

	G.terrain = JSON.parse(rawData);
	if (G.terrain.geometry == 'sphere')
	{
		var SG = require('../html/sphere-geometry.js');
		BE.geometry = new SG.SphereGeometry(G.terrain.size);
	}
	else
	{
		throw new Error('invalid geometry: '+G.terrain.geometry);
	}
}

function loadPlayers()
{
	var filename = G.worldName + '/players.txt';

	try
	{
		var rawData = FS.readFileSync(filename);
		G.players = JSON.parse(rawData);
	}
	catch (err)
	{
		G.players = {};
	}
}

function loadMaps()
{
	var filename = G.worldName + '/maps.txt';

	try
	{
		var rawData = FS.readFileSync(filename);
		G.maps = JSON.parse(rawData);
	}
	catch (err)
	{
		G.maps = {};
	}
}

function loadBattles()
{
	var filename = G.worldName + '/battles.txt';

	try
	{
		var rawData = FS.readFileSync(filename);
		G.battles = JSON.parse(rawData);
	}
	catch (err)
	{
		G.battles = {};
	}
}

function loadFleets()
{
	var filename = G.worldName + '/fleets.txt';

	try
	{
		var rawData = FS.readFileSync(filename);
		G.fleets = JSON.parse(rawData);
	}
	catch (err)
	{
		G.fleets = {};
	}
}

function loadCities()
{
	var filename = G.worldName + '/cities.txt';

	try
	{
		var rawData = FS.readFileSync(filename);
		G.cities = JSON.parse(rawData);
	}
	catch (err)
	{
		G.cities = {};
	}
}

function startListener()
{
	var tcpPort = 8124;

	HTTP.createServer(handleRequest).listen(tcpPort);
	BE.serverBaseUrl = 'http://' + require('os').hostname() + ':' + tcpPort;
	BE.serverSecret = Math.floor(Math.random()*1000000);

	console.log('Server running at ' + BE.serverBaseUrl);
}

var worldName = process.argv[2];
loadWorld(worldName);
startListener();
Lobby.postWorldStatus();
startGame();
