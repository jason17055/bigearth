exports = {
	time: 0,
	ticksPerYear: 60000
	};
module.exports = exports;

var eventQueue = [];
var wakeupTimer = null;

function schedule(callbackFunction, delay)
{
	return scheduleAtYear(callbackFunction, exports.time + delay / exports.ticksPerYear,
			callbackFunction);
}

function scheduleYears(callbackFunction, delayInYears)
{
	return scheduleAtYear(callbackFunction, exports.time + delayInYears, callbackFunction);
}

function scheduleAtYear(callbackFunction, aTime)
{
	var myEvent = [ aTime, callbackFunction ];

	for (var i = 0, l = eventQueue.length; i < l; i++)
	{
		if (eventQueue[i][0] > aTime)
		{
			eventQueue.splice(i,0,myEvent);
			if (i == 0)
			{
				rescheduleWakeup();
			}
			return myEvent;
		}
	}

	eventQueue.push(myEvent);
	if (eventQueue.length == 1)
		rescheduleWakeup();
	return myEvent;
}

function cancel(eventStruct)
{
	for (var i = 0, l = eventQueue.length; i < l; i++)
	{
		if (eventQueue[i] == eventStruct)
		{
			eventQueue.splice(i,1);
			if (i == 0)
			{
				rescheduleWakeup();
			}
			return;
		}
	}
	return;
}

function rescheduleWakeup()
{
	if (wakeupTimer)
	{
		clearTimeout(wakeupTimer);
		wakeupTimer = null;
	}

	if (eventQueue.length)
	{
		var nextEvent = eventQueue[0];
		var yearsRemaining = nextEvent[0] - exports.time;
		var delay = Math.ceil(yearsRemaining * exports.ticksPerYear);
		wakeupTimer = setTimeout(function() {

			wakeupTimer = null;
			catchup(nextEvent[0]);

		}, delay);
	}
}

function catchup(newTime)
{
	while (eventQueue.length && eventQueue[0][0] <= newTime)
	{
		var nextEvent = eventQueue.shift();
		exports.time = nextEvent[0];
		nextEvent[1]();
	}
	exports.time = newTime;
	rescheduleWakeup();
}

exports.schedule = schedule;
exports.scheduleYears = scheduleYears;
exports.scheduleAtYear = scheduleAtYear;
exports.catchup = catchup;
exports.cancel = cancel;
