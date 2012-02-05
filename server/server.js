var HTTP = require('http');
var URL = require('url');
var FS = require('fs');
var OS = require('os');
var QS = require('querystring');
var CRYPTO = require('crypto');
var GAME = require('./game.js');
var SESSIONS = require('./sessions.js');
var SECRET = CRYPTO.randomBytes(20).toString('hex');

// scan the 'resource_icons' directory to figure out the names of all
// resources that this server knows about...
//
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
	var s = SESSIONS.getSessionFromCookie(request);

	var gameState = getGameState();
	gameState.allServerResourceTypes = enumResourceTypes();
	gameState.identity = s.identity;
	gameState.nextEvent = EVENTS.nextEventId;

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
	
	if (args.cs == expectedChecksum)
	{
		var sid = SESSIONS.newSession({
			identity: args.id
			});
		response.writeHead(303, {
			'Set-Cookie': SESSIONS.cookieName + "=" + sid,
			'Content-Type': 'text/plain',
			'Location': '/index.html'
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
	evt.nextEvent = EVENTS.nextEventId;
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

function doEditMap(requestData, remoteUser)
{
	console.log("in doEditMap");

	var map = {
	cities: {}
	};
	for (var k in requestData)
	{
		var v = requestData[k];
		if (k == 'terrain')
		{
			var t = v.split("\n");
			map.terrain = t;
		}
		else if (k == 'rivers')
		{
			var t = v.split(" ");
			var r = {};
			for (var ii in t)
			{
				r[t[ii]] = 1;
			}
			map.rivers = r;
		}
		else if (k.match(/^cities/))
		{
			var re1 = /^cities\[(\d+)\]\[name\]$/;
			var a = re1.exec(k);
			if (a)
			{
				var cityId = a[1];
				if (!map.cities[cityId]) { map.cities[cityId] = {}; }
				map.cities[cityId].name = v;
			}
			var re2 = /^cities\[(\d+)\]\[offers\]\[\]$/;
			var b = re2.exec(k);
			if (b)
			{
				var cityId = b[1];
				if (!map.cities[cityId]) { map.cities[cityId] = {}; }
				map.cities[cityId].offers = (
					typeof v == 'object' ? v : [v]);
			}
		}
		else
		{
			console.log("in doEditMap, don't know " + k);
		}
	}

	FS.writeFile('map.txt', JSON.stringify(map),
	function(err) {
		if (err) {
			console.log(err);
		} else {
			console.log('The file was saved!');
		}
	});

	return {};
}

actionHandlers.editMap = doEditMap;

function handleActionRequest(verb, request, response)
{
	console.log('got request ' + verb);
	if (!actionHandlers[verb])
	{
		response.writeHead(404);
		response.end('Bad request\n');
		return;
	}

	var s = SESSIONS.getSessionFromCookie(request);

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

	if (requestPath.pathname == "/gamestate")
	{
		return handleGameStateRequest(request,response);
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
	var mapDir = "../html/maps";
	var mapFileName = mapDir + '/' + mapName + '.txt';
	console.log('Loading map data from ' + mapFileName);

	var data = FS.readFileSync(mapFileName);
	var map = JSON.parse(data);
	setMap(mapName, map);
	autoCreateDemands();
};
loadMap('nippon');

function myPost(url, postVars, onSuccess, onError)
{
	var urlParsed = URL.parse(url);
	var req = HTTP.request({
		host: urlParsed.hostname,
		port: urlParsed.port || 80,
		method: 'POST',
		path: urlParsed.path,
		headers: {
			'Content-Type': 'application/x-www-form-urlencoded'
			}
		}, function(resp) {
			var collected = '';
			resp.on('data', function(chunk) { collected += chunk; });
			resp.on('end', function() {
				if (resp.statusCode == 200)
				{
					if (onSuccess)
					onSuccess(resp, collected);
				}
				else
				{
					if (onError)
					onError(resp.statusCode, collected);
				}
			});
		});
	if (onError)
		req.on('error', onError);
	req.write(QS.stringify(postVars));
	req.end();
}

var gameId;
function deleteAdvertisement()
{
	var onComplete = function()
	{
		process.exit(0);
	};

	if (!gameId)
		return onComplete();

	console.log("updating gametable advertisement...");
	var postVars = {
		status: newStatus
		};
	myPost('http://jason.long.name/trains/server-api/gametable.php?id=' + gameId,
		postVars, onComplete, onComplete);
}

function postAdvertisement()
{
	var selfUrl = 'http://' + OS.hostname() + ':8124';
	var postVars = {
		map: 'nippon',
		url: selfUrl,
		secret: SECRET
		};

	var onSuccess = function(resp, data)
	{
		var lines = data.split("\n");
		if (lines[0] == "ok")
		{
			var parts = lines[1].split("=");
			gameId = parts[1];
			console.log("my game id is " + gameId);
		}
	};
	var onError = function(err)
	{
		console.log('problem with request: ' + err.message);
	};
	myPost('http://jason.long.name/trains/server-api/new_gametable.php',
		postVars, onSuccess, onError);
}

console.log("posting gametable advertisement...");
postAdvertisement();

HTTP.createServer(handleRequest).listen(8124);
console.log('Server running at http://localhost:8124/');

//process.on('SIGINT', deleteAdvertisement);
//process.on('SIGTERM', deleteAdvertisement);
