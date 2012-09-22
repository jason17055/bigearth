var Http = require('http');
var Querystring = require('querystring');
var Scheduler = require('./scheduler.js');

function postAdvertisement(ad_properties)
{
	var post_data = Querystring.stringify({
		url: ad_properties.url,
		description: ad_properties.description,
		secret: BE.serverSecret
		});
	var responseObject;
	var responseData = '';
	var onResponseEnd = function()
	{
		if (responseObject.statusCode >= 200 && responseObject.statusCode <= 299)
		{
			if (ad_properties.success)
				(ad_properties.success)(res);
		}
		else
		{
			console.log('status code is '+responseObject.statusCode);
			if (ad_properties.error)
				(ad_properties.error)(res);
		}
	};

	var post_req = Http.request({
		host: 'jason.long.name',
		path: '/bigearth/server-api/advertisement.php',
		method: 'POST',
		headers: {
			'Content-Type': 'application/x-www-form-urlencoded',
			'Content-Length': post_data.length
		}
		}, function(res) {

		responseObject = res;
		res.on('end', onResponseEnd);
		res.on('data', function(chunk) { responseData += chunk; });

		});

	post_req.write(post_data);
	post_req.end();
}

function postWorldStatus(args)
{
	if (!args) { args = {}; }

	var post_data = Querystring.stringify({
		url: BE.serverBaseUrl,
		secret: BE.serverSecret,
		size: BE.geometry.getCellCount(),
		year: Scheduler.time,
		year_real_world_duration: (Scheduler.ticksPerYear/1000),
		population: G.world.totalPopulation
		});
	var responseObject;
	var responseData = '';
	var onResponseEnd = function()
	{
		if (responseObject.statusCode >= 200 && responseObject.statusCode <= 299)
		{
			if (args.success)
				(args.success)(res);
		}
		else
		{
			console.log('status code is '+responseObject.statusCode);
			if (args.error)
				(args.error)(res);
		}
	};

	var post_req = Http.request({
		host: 'jason.long.name',
		path: '/bigearth/server-api/world.php',
		method: 'POST',
		headers: {
			'Content-Type': 'application/x-www-form-urlencoded',
			'Content-Length': post_data.length
		}
		}, function(res) {

		responseObject = res;
		res.on('end', onResponseEnd);
		res.on('data', function(chunk) { responseData += chunk; });

		});

	post_req.write(post_data);
	post_req.end();
}

exports.postAdvertisement = postAdvertisement;
exports.postWorldStatus = postWorldStatus;
