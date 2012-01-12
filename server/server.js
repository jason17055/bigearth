var HTTP = require('http');
var URL = require('url');
var FS = require('fs');

var TS = require('./trains-server.js');

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

function handleRequest(request,response)
{
	var requestPath = URL.parse(request.url);
	if (requestPath.href.match(/\.\.|\.\/|\/\./))
	{
		// prevent any requests that remotely look like
		// they are trying to access a directory path with ..
		response.writeHead(404);
		response.end('Bad request\n');
		return;
	}

	if (requestPath.href == "/gamestate")
	{
		return TS.handleGameStateRequest(request,response);
	}

	// assume it is a request for a file
	return handleStaticFileRequest(requestPath.href,request,response);
}

HTTP.createServer(handleRequest).listen(8124);
console.log('Server running at http://localhost:8124/');
