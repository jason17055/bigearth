var HTTP = require('http');
var URL = require('url');
var FS = require('fs');
var OS = require('os');
var QS = require('querystring');
var CRYPTO = require('crypto');
var SESSIONS = require('./sessions.js');
var SECRET = CRYPTO.randomBytes(20).toString('hex');
var SECURE = false;

var GAME = require('./bigearth-game.js');


function handleStaticFileRequest(requestPath,request,response)
{
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

var EVENTS = {
	nextEventId: 1,
	waitingListeners: [],
	sentEvents: {}
	};

function handleGameStateRequest(request,response)
{
	var s = request.Session;

	var gameState = getGameState();
	gameState.identity = s.identity;
	gameState.nextEventUrl = '/event/' + EVENTS.nextEventId;

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
		response.writeHead(303, {
			'Set-Cookie': SESSIONS.cookieName + "=" + sid,
			'Content-Type': 'text/plain',
			'Location': '/bigearth.html'
			});
		response.end();
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

function postEvent(evt)
{
	evt.id = EVENTS.nextEventId++;
	evt.nextEventUrl = '/event/'+EVENTS.nextEventId;
	EVENTS.sentEvents[evt.id] = evt;

	for (var i in EVENTS.waitingListeners)
	{
		var l = EVENTS.waitingListeners[i];
		sendEvent(evt, l);
	}
	EVENTS.waitingListeners = [];
}
global.postEvent = postEvent;

function handleEventRequest(eventId, request, response)
{
	console.log('got event request ' + eventId);
	if (eventId && parseInt(eventId) < EVENTS.nextEventId)
	{
		// request for an already sent event
		var evt = EVENTS.sentEvents[eventId];
		return sendEvent(evt, response);
	}
	else
	{
		// must wait
		EVENTS.waitingListeners.push(response);
	}
}

function handleActionRequest(verb, request, response)
{
	console.log('got request ' + verb);
	if (!actionHandlers[verb])
	{
		response.writeHead(404);
		response.end('Bad request\n');
		return;
	}

	var s = request.Session;

	var body = '';
	request.on('data', function(chunk) {
		body += chunk;
		if (body.length >= 1e6) {
			// FLOOD ATTACK OR FAULTY CLIENT
			request.connection.destroy();
		}
		});
	request.on('end', function() {
		var requestData = QS.parse(body);
		var responseData = actionHandlers[verb](requestData, s.identity);
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

function handleMapRequest(pathInfo, request, response)
{
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

	if (pathInfo.match(/^([\d]+)$/))
	{
		var mapId = RegExp.$1;
		GAME.getMapFragment(mapId, processor);
	}
	else
	{
		processor({});
	}

}

function handleDefaultDocumentRequest(request, response)
{
	if (request.remote_user)
	{
		response.writeHead(302, {Location:'/bigearth.html'});
	}
	else
	{
		response.writeHead(302, {Location:'/login.html'});
	}
	response.end();
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
	else if (requestPath.pathname == "/login")
	{
		return handleLoginRequest(request,response);
	}
	else if (requestPath.pathname.match(/^\/event\//))
	{
		var eventId = requestPath.pathname.substr(7);
		return handleEventRequest(eventId, request, response);
	}
	else if (requestPath.pathname.match(/^\/request\//))
	{
		var verb = requestPath.pathname.substr(9);
		return handleActionRequest(verb, request, response);
	}

	// assume it is a request for a file
	return handleStaticFileRequest(requestPath.pathname,request,response);
}

function loadMap(mapName)
{
	var SG = require('../html/sphere-geometry.js');
	var cradle = require('cradle');
	var DB = new(cradle.Connection)().database(worldName);
	G.DB = DB;

	// read game world parameters
	DB.get('world', function(err,doc) {
		if (err)
		{
			console.log("ERROR", err);
		}
		else
		{
			G.globalMap = doc;
			G.geometry = new SG.SphereGeometry(doc.size);

			startGame();
			startListener();
		}
	});
}

function startGame()
{
	newPlayer(1, function() {
		addExplorer(1);
		});
}

function startListener()
{
	HTTP.createServer(handleRequest).listen(8124);
	console.log('Server running at http://localhost:8124/');
}

var worldName = process.argv[2];
loadMap(worldName);

