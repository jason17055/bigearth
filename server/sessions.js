var CRYPTO = require('crypto');

var mySessions = {};

function getCookie(req, cookieName)
{
	var cookieHeader = req.headers.cookie;
	if (cookieHeader)
	{
		var cookies = cookieHeader.split(';');
		for (var i in cookies)
		{
			var parts = cookies[i].split('=');
			var name = parts[0].trim();
			if (parts[0].trim() == cookieName)
			{
				return parts[1].trim();
			}
		}
	}
	return null;
}

function getSessionIdFromCookie(req)
{
	var sid = getCookie(req, exports.cookieName);
	return sid;
}

function getSession(sid)
{
	return sid ? mySessions[sid] : {};
}

function newSession(s)
{
	var sha1 = CRYPTO.createHash('sha1');
	var data = CRYPTO.randomBytes(100);
	sha1.update(data);

	var sid = sha1.digest('hex');
	mySessions[sid] = s;
	return sid;
}

exports.getSessionIdFromCookie = getSessionIdFromCookie;
exports.getSessionFromCookie = function(req) {
		return getSession(getSessionIdFromCookie(req));
		};
exports.getSession = getSession;
exports.newSession = newSession;
exports.cookieName = 'trainssession';
