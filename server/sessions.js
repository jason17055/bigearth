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
	if (sid && mySessions[sid])
		return mySessions[sid];
	else
		return {};
}

function newSession(s)
{
	var sid = CRYPTO.randomBytes(20).toString('hex');
	mySessions[sid] = s;
	return sid;
}

exports.getSessionIdFromCookie = getSessionIdFromCookie;
exports.getSessionFromCookie = function(req) {
		return getSession(getSessionIdFromCookie(req));
		};
exports.getSession = getSession;
exports.newSession = newSession;
exports.cookieName = 'sid';
