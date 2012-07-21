var nextEventStreamId = 1;
var allEventStreams = {};

function createEventStream()
{
	var me = new EventStream(nextEventStreamId++);
	allEventStreams[me.id] = me;
	return me;
}

function EventStream(streamId)
{
	this.id = streamId;
	this.nextEventId = 1;
	this.waitingListeners = [];
	this.sentEvents = {};
}

EventStream.prototype.getNextEventUrl = function()
{
	return '/events/'+this.id+'/'+this.nextEventId;
};

EventStream.prototype.postEvent = function(evt)
{
	var eventId = this.nextEventId++;
	var nextUrl = this.getNextEventUrl();
	this.sentEvents[eventId] = evt;

	for (var i in this.waitingListeners)
	{
		var l = this.waitingListeners[i];
		this.sendEvent(eventId, l);
	}
	this.waitingListeners = [];
};

EventStream.prototype.sendEvent = function(eventId, httpResponse)
{
	var evt = this.sentEvents[eventId];
	evt.id = eventId;
	evt.nextEventUrl = '/events/'+this.id+'/'+(+eventId+1);

	httpResponse.writeHead(200, {
		'Content-Type': 'text/json'
		});
	httpResponse.end(
		JSON.stringify(evt)
		);

	delete evt.id;
	delete evt.nextEventUrl;
};

EventStream.prototype.handleEventRequest = function(eventId, request, response)
{
	console.log('got event request ' + this.id + '/' + eventId);
	if (eventId && (+eventId) < this.nextEventId)
	{
		// request for an already sent event
		return this.sendEvent(eventId, response);
	}
	else
	{
		// must wait
		this.waitingListeners.push(response);
	}
};

exports.createEventStream = createEventStream;
exports.allEventStreams = allEventStreams;
