var VIEWPORT = {
	latitude: Math.PI/2,
	longitude: 0,
	scale: 450,
	offsetX: 280,
	offsetY: 280
	};

function matrixMultiply(A, B)
{
	return [
	[ A[0][0]*B[0][0] + A[0][1]*B[1][0] + A[0][2]*B[2][0],
	  A[0][0]*B[0][1] + A[0][1]*B[1][1] + A[0][2]*B[2][1],
	  A[0][0]*B[0][2] + A[0][1]*B[1][2] + A[0][2]*B[2][2] ],

	[ A[1][0]*B[0][0] + A[1][1]*B[1][0] + A[1][2]*B[2][0],
	  A[1][0]*B[0][1] + A[1][1]*B[1][1] + A[1][2]*B[2][1],
	  A[1][0]*B[0][2] + A[1][1]*B[1][2] + A[1][2]*B[2][2] ],

	[ A[2][0]*B[0][0] + A[2][1]*B[1][0] + A[2][2]*B[2][0],
	  A[2][0]*B[0][1] + A[2][1]*B[1][1] + A[2][2]*B[2][1],
	  A[2][0]*B[0][2] + A[2][1]*B[1][2] + A[2][2]*B[2][2] ]

	];
}

function updateTransformMatrix()
{
	var aZ = VIEWPORT.longitude;
	var rZ = [[ Math.cos(aZ), -Math.sin(aZ), 0 ],
		[ Math.sin(aZ), Math.cos(aZ), 0 ],
		[ 0, 0, 1 ]
		];

	var aX = VIEWPORT.latitude;
	var rX = [[ 1, 0, 0 ],
		[ 0, Math.cos(aX), -Math.sin(aX) ],
		[ 0, Math.sin(aX), Math.cos(aX) ]
		];

	VIEWPORT.rotMatrix = matrixMultiply(rX, rZ);
}
updateTransformMatrix();

var geometry;
var map;
var coords;
var pawn = null;

function toScreenPoint(pt)
{
	var M = VIEWPORT.rotMatrix;
	var p = {
	x: M[0][0]*pt.x + M[0][1]*pt.y + M[0][2]*pt.z,
	y: M[1][0]*pt.x + M[1][1]*pt.y + M[1][2]*pt.z,
	z: M[2][0]*pt.x + M[2][1]*pt.y + M[2][2]*pt.z
	};
	return {
	x: p.x * VIEWPORT.scale + VIEWPORT.offsetX,
	y: p.y * VIEWPORT.scale + VIEWPORT.offsetY,
	z: p.z };
}

function repaint()
{
	var canvas = document.getElementById('theCanvas');
	var ctx = canvas.getContext('2d');

	ctx.fillStyle = '#444';
	ctx.fillRect(0,0,canvas.width,canvas.height);
	if (!map)
		return;

	for (var i in map.cells)
	{
		var c = map.cells[i];
		var cellIdx = parseInt(i)+1;

		var co = coords.cells[cellIdx];
		var centerP = toScreenPoint(co.pt);
		if (centerP.z < 0)
			continue;

		ctx.save();
		ctx.lineWidth = 1;
		ctx.strokeStyle = '#000';
		ctx.fillStyle =
			c.height < -3 ? '#730' :
			c.height < 0 ? '#940' :
			c.height < 3 ? '#e70' :
			c.height < 7 ? '#fa3' :
			'#fe8';
			
		if (c.water && c.water > 0)
		{
			ctx.fillStyle = c.water <= 3 ? '#68f' :
				c.water <= 6 ? '#05a' :
				'#028';
		}

		//	c.height < -3 ? '#05a' :
		//	c.height < 0 ? '#8af' :
		//	c.height < 3 ? '#8f8' :
		//	c.height < 7 ? '#f80' :
		//	'#eee';
 
		ctx.beginPath();
		var p = toScreenPoint(co.pts[0]);
		ctx.moveTo(p.x, p.y);
		for (var j = 0, l = co.pts.length; j < l; j++)
		{
			var p = toScreenPoint(co.pts[(j+1)%l]);
			ctx.lineTo(p.x, p.y);
		}
		ctx.fill();
		ctx.stroke();

	// SHOW HEIGHTS
	//	ctx.fillStyle = '#800';
	//	ctx.fillText(c.height, centerP.x, centerP.y-8);

	// SHOW CELL IDS
	//	ctx.fillStyle = '#fff';
	//	ctx.fillText(cellIdx, centerP.x, centerP.y-8);

		if (Math.floor(c.water) != 0)
		{
		ctx.fillStyle = '#ff0';
		ctx.fillText(Math.floor(c.water), centerP.x, centerP.y);
		}
		ctx.restore();
	}

	for (var eId in map.edges)
	{
		var ed = map.edges[eId];
		var p = toScreenPoint(coords.edges[eId].pt);
		if (p.z < 0)
			continue;

		if (!ed.feature)
			continue;

		if (ed.feature == 'river')
		{
			var vv = geometry.getVerticesAdjacentToEdge(eId);
			var p1 = toScreenPoint(coords.vertices[vv[0]].pt);
			var p2 = toScreenPoint(coords.vertices[vv[1]].pt);
			ctx.save();
			ctx.lineWidth = 4;
			ctx.strokeStyle = '#00f';
			ctx.moveTo(p1.x,p1.y);
			ctx.lineTo(p2.x,p2.y);
			ctx.stroke();
			ctx.restore();
		}
	}

	for (var vId in map.vertices)
	{
		var v = map.vertices[vId];
		var p = toScreenPoint(coords.vertices[vId].pt);
		if (p.z < 0)
			continue;

		if (!v.water)
			continue;
		if (Math.floor(v.water) == 0)
			continue;

		ctx.save();
		ctx.lineWidth = 1;
		ctx.strokeStyle = '#000';
		ctx.fillStyle = '#00c';
 
		ctx.fillRect(p.x-4,p.y-4,8,8);

		ctx.fillStyle = '#ffc';
		ctx.fillText(Math.floor(v.water), p.x-4, p.y+4);

		ctx.restore();
	}

	if (pawn && pawn.locationType == 'vertex')
	{
		var v = map.vertices[pawn.location];
		var p = toScreenPoint(coords.vertices[pawn.location].pt);
		if (p.z >= 0)
		{
			drawPawn(ctx, p);
		}
	}
	else if (pawn && pawn.locationType == 'cell')
	{
		var p = toScreenPoint(coords.cells[pawn.location].pt);
		if (p.z >= 0)
		{
			drawPawn(ctx, p);
		}
	}
	else if (pawn && pawn.locationType == 'edge')
	{
		var p = toScreenPoint(coords.edges[pawn.location].pt);
		if (p.z >= 0)
		{
			drawPawn(ctx, p);
		}
	}
}

function drawPawn(ctx, p)
{
	ctx.save();
	ctx.lineWidth = 4;
	ctx.strokeStyle = '#c0c';
	ctx.beginPath();
	ctx.arc(p.x, p.y, 12, 0, Math.PI*2, true);
	ctx.closePath();
	ctx.stroke();
	ctx.restore();
}

function onResize()
{
	var canvas = document.getElementById('theCanvas');
	canvas.width = window.innerWidth - 0;
	canvas.height = window.innerHeight - $('#buttonBar').outerHeight();
	VIEWPORT.offsetY = Math.round(canvas.height/2);
	VIEWPORT.offsetX = Math.round(canvas.width*2/5);
	$('#contentArea').css({
		width: canvas.width+"px",
		height: canvas.height+"px"
		});
	repaint();
}

function resetMap()
{
	map = makeMap(geometry);
	coords = makeCoords(geometry);
	numBumps = 0;
	pawn = null;
}

function fetchGameState()
{
	var onSuccess = function(data,status)
	{
		map = data.map;
		geometry = new SphereGeometry(map.size);
		coords = makeCoords(geometry);
		numBumps = 0;
		pawn = null;
		repaint();
	};
	var onError = function(xhr, status, errorThrown)
	{
		//TODO- throw an error
	};

	$.ajax({
	url: "/gamestate",
	success: onSuccess,
	error: onError,
	dataType: "json"
	});
}

$(fetchGameState);

window.onresize = onResize;
$(onResize);

var rotateTimer;
var keepGoing = false;
var rotationIdx = 0;
function doRotation()
{
	rotationIdx++;

	VIEWPORT.longitude += 0.08;
	VIEWPORT.latitude = -Math.sin(0.05*rotationIdx) + Math.PI/2;
	updateTransformMatrix();
	repaint();

	if (keepGoing)
	rotateTimer = setTimeout(doRotation,250);
	else
	rotateTimer = null;
}

function pauseClicked()
{
	keepGoing = !keepGoing;
	if (!rotateTimer) { doRotation(); }
}

function testBtnClicked()
{
	var x = parseInt(document.getElementById('numEntry').value);
	var adj = geometry.getNeighbors(x);
	alert('adjacent to ' + x + ' is ' + adj.join(', '));
}

function bumpMapBtnClicked()
{
	bumpMap(map, coords);
	repaint();

	numBumps++;
	$('#bumpMapBtn').text('Bump Map ('+numBumps+')');
}

var numBumps = 0;
function nextSizeClicked()
{
	geometry = new SphereGeometry((geometry.size+1)%14);
	resetMap();

	document.title = 'Big Earth ' + geometry.size;
	repaint();
}

function beginFlowAt(cellIdx)
{
	if (!pawn) { pawn = {}; }

	var w = map.cells[cellIdx-1].water;
	if (w <= 0) return;
	if (w > 1) { w = 1; }

	pawn.locationType = 'cell';
	pawn.location = cellIdx;
	pawn.water = w;
	map.cells[cellIdx-1].water -= w;
	repaint();
}

function beginFlowClicked()
{
	if (pawn && pawn.locationType == 'cell')
		return beginFlowAt(pawn.location);

	if (!map.vertexHeightsDone)
		calculateVertexHeights();
	
	// pick a random cell as a starting point.
	// the cell should have water, and its height+water should
	// be greater than at least one of its adjacent vertices

	var candidates = new Array();
	for (var i in map.cells)
	{
		var c = map.cells[i];
		var cellIdx = parseInt(i)+1;

		if (c.water <= 0) continue;

		var waterLevel = c.height + c.water;
		var foundAny = false;
		var adj = geometry.getNeighbors(cellIdx);
		for (var j = 0, l = adj.length; j < l; j++)
		{
			var d = map.cells[adj[j]-1];
			if (waterLevel > d.height + d.water)
				foundAny = true;
		}

		if (!foundAny)
			continue;

		candidates.push(cellIdx);
	}

	if (candidates.length)
	{
		var i = Math.floor(Math.random() * candidates.length);
		var cellIdx = candidates[i];
		return beginFlowAt(cellIdx);
	}
}

function flowWaterClickedOld()
{
	var getVertexHeight = function(vId)
	{
		var cc = geometry.getCellsAdjacentToVertex(vId);
		return (
			map.cells[cc[0]-1].height +
			map.cells[cc[1]-1].height +
			map.cells[cc[2]-1].height) / 3;
	};

	var deltaWater = {};
	for (var vId in map.vertices)
	{
		var v = map.vertices[vId];
		var v_h = getVertexHeight(vId);
		var v_w = v.water ? v.water : 0;

		var adj = geometry.getVerticesAdjacentToVertex(vId);
		var sumFlow = 0;
		for (var j in adj)
		{
			var u = map.vertices[adj[j]];
			var u_h = getVertexHeight(adj[j]);
			var u_w = u.water ? u.water : 0;

			var flow = (v_h + v_w) - (u_h + u_w);
			if (flow > 0)
			{
				sumFlow += flow;
			}
		}
		var amt = v_w > sumFlow ? sumFlow : v_w;
		amt = amt * (.5+Math.random()*.5);
		for (var j in adj)
		{
			var u = map.vertices[adj[j]];
			var u_h = getVertexHeight(adj[j]);
			var u_w = u.water ? u.water : 0;

			var flow = (v_h + v_w) - (u_h + u_w);
			if (flow > 0)
			{
				flow *= amt / sumFlow;

				if (!(vId in deltaWater)) { deltaWater[vId] = 0; }
				if (!(adj[j] in deltaWater)) { deltaWater[adj[j]] = 0; }
				deltaWater[vId] -= flow;
				deltaWater[adj[j]] += flow;
			}
		}
	}

	for (var vId in deltaWater)
	{
		var v = map.vertices[vId];
		if (!v.water) { v.water = 0; }
		v.water += deltaWater[vId];
	}
	repaint();
}

function onMouseDown(evt)
{
	if (evt.which != 1) return;
	evt.preventDefault();

	var orig = $('#theCanvas').position();
	var screenPt = {
		x: evt.clientX - orig.left,
		y: evt.clientY - orig.top
		};
	var xx = getNearestFeatureFromScreen(screenPt);
	if (xx.type == 'vertex')
	{
		var vId = xx.id;
		$('#infoPane .featureType').text('Vertex');
		$('#vId').text(vId);
		$('#infoPane .adjacentCells').text(geometry.getCellsAdjacentToVertex(vId).join('; '));
		$('#infoPane .adjacentVertices').text(geometry.getVerticesAdjacentToVertex(vId).join('; '));
		$('#infoPane').show();

		if (!pawn) { pawn = {}; }
		pawn.locationType = "vertex";
		pawn.location = xx.id;
		repaint();
	}
	else if (xx.type == 'cell')
	{
		$('#infoPane .featureType').text('Cell');
		$('#vId').text(xx.id);
		$('#infoPane .adjacentCells').text(geometry.getNeighbors(xx.id).join('; '));
		$('#infoPane .adjacentVertices').text(geometry.getVerticesAdjacentToCell(xx.id).join('; '));
		$('#infoPane').show();

		if (!pawn) { pawn = {}; }
		pawn.locationType = "cell";
		pawn.location = xx.id;
		repaint();
	}
	else if (xx.type == 'edge')
	{
		$('#infoPane .featureType').text('Edge');
		$('#vId').text(xx.id);
		$('#infoPane .adjacentCells').text("");
		$('#infoPane .adjacentVertices').text("");
		$('#infoPane').show();

		if (!pawn) { pawn = {}; }
		pawn.locationType = xx.type;
		pawn.location = xx.id;
		repaint();
	}
}

function getNearestFeatureFromScreen(screenPt)
{
	var bestvertex = getVertexFromScreen(screenPt);
	var p = toScreenPoint(coords.vertices[bestvertex].pt);

	var bestDist = Math.sqrt(Math.pow(screenPt.x-p.x,2)+Math.pow(screenPt.y-p.y,2));
	var best = {
	type: "vertex",
	id: bestvertex
	};

	for (var cid in coords.cells)
	{
		var co = coords.cells[cid];

		var p = toScreenPoint(co.pt);
		if (p.z < 0)
			continue;
		var d = Math.sqrt(Math.pow(p.x-screenPt.x,2)+Math.pow(p.y-screenPt.y,2));
		if (d<bestDist)
		{
			best = {
			type: "cell",
			id: parseInt(cid)
			};
			bestDist = d;
		}
	}

	for (var eid in coords.edges)
	{
		var eco = coords.edges[eid];
		var p = toScreenPoint(eco.pt);
		if (p.z < 0)
			continue;
		var d = Math.sqrt(Math.pow(p.x-screenPt.x,2)+Math.pow(p.y-screenPt.y,2));

		if (d<bestDist)
		{
			best = {
			type: "edge",
			id: eid
			};
			bestDist = d;
		}
	}

	return best;
}

function getVertexFromScreen(screenPt)
{
	var best = null;
	var bestDist = Infinity;

	for (var vId in map.vertices)
	{
		var p = toScreenPoint(coords.vertices[vId].pt);
		if (p.z < 0)
			continue;

		var d = Math.sqrt(Math.pow(p.x-screenPt.x,2)+Math.pow(p.y-screenPt.y,2));
		if (d < bestDist)
		{
			best = vId;
			bestDist = d;
		}
	}
	return best;
}

$(function() {
	var canvas = document.getElementById('theCanvas');
	canvas.addEventListener('mousedown', onMouseDown, false);
});

function calculateVertexHeights()
{
	for (var vId in map.vertices)
	{
		var v = map.vertices[vId];
		var adj = geometry.getCellsAdjacentToVertex(vId);
		var sum = 0;
		for (var i = 0; i < adj.length; i++)
		{
			sum += map.cells[adj[i]-1].height;
		}
		v.height = sum/adj.length;
	}

	map.vertexHeightsDone = true;
}

function stepFlowClicked()
{
	if (!pawn) return;
	if (pawn.locationType == 'cell')
	{
		var c = map.cells[pawn.location-1];
		var h = c.height + c.water + (pawn.water||0);
		var adjCells = geometry.getNeighbors(pawn.location);
		var sumFitness = 0;
		var rouletteWheel = new Array();
		for (var j = 0, l = adjCells.length; j < l; j++)
		{
			var d = map.cells[adjCells[j]-1];
			var e = map.cells[adjCells[(j+1)%l]-1];
			var hh = (d.height + d.water + e.height + e.water) / 2;

			var f = h-hh;
			if (f <= 0) continue;

			rouletteWheel.push([f, geometry._makeVertex(pawn.location,adjCells[j],adjCells[(j+1)%l])]);
			sumFitness += f;
		}

		if (sumFitness <= 0) return;
		var choice = Math.random() * sumFitness;
		for (var j = 0, l = rouletteWheel.length; j < l; j++)
		{
			choice -= rouletteWheel[j][0];
			if (choice < 0)
			{
				pawn.locationType = 'vertex';
				pawn.location = rouletteWheel[j][1];
				break;
			}
		}
		repaint();
		return;
	}

	if (pawn.locationType != 'vertex') return;

	if (!map.vertexHeightsDone)
		calculateVertexHeights();

	var getVertexWaterLevel = function(vId) {
		var cc = geometry.getCellsAdjacentToVertex(vId);
		var c = map.cells[cc[0]-1];
		var d = map.cells[cc[1]-1];
		var e = map.cells[cc[2]-1];
		return (
		c.height + c.water +
		d.height + d.water +
		e.height + e.water) / 3;
	};

	var h = getVertexWaterLevel(pawn.location) + (pawn.water||0);
	var adj = geometry.getVerticesAdjacentToVertex(pawn.location);
	var sumFitness = 0;
	var rouletteWheel = new Array();
	for (var i = 0; i < adj.length; i++)
	{
		var other_height = getVertexWaterLevel(adj[i]);

		var f = h - other_height;
		if (f <= 0) continue;
		rouletteWheel.push([f, adj[i]]);
		sumFitness += f;
	}

	if (sumFitness == 0) return;

	var choice = Math.random() * sumFitness;
	var chosen = null;
	for (var i = 0, l = rouletteWheel.length; i < l; i++)
	{
		choice -= rouletteWheel[i][0];
		if (choice < 0)
		{
			chosen = rouletteWheel[i][1];
			break;
		}
	}

	if (chosen)
		pawn.location = chosen;
	repaint();
}

function endFlowClicked()
{
	if (!pawn) return;
	if (pawn.locationType == 'vertex')
	{
		// pick an adjacent cell with lowest water level
		var adj = geometry.getCellsAdjacentToVertex(pawn.location);
		var best = adj[0];
		var bestV = Infinity;
		for (var i = 0, l = adj.length; i < l; i++)
		{
			var c = map.cells[adj[i]-1];
			var h = c.height + c.water;
			if (h < bestV) {
				best = adj[i];
				bestV = h;
			}
		}
		pawn.location = best;
		pawn.locationType = 'cell';
	}

	if (pawn.locationType != 'cell')
		return;

	if (!pawn.water) {
		pawn = null;
		repaint();
		return;
	}
	var c = map.cells[pawn.location-1];
	c.water += pawn.water;
	pawn = null;
	repaint();
}

function addWaterAtPawn()
{
	if (pawn && pawn.locationType == 'cell')
	{
		map.cells[pawn.location-1].water++;
		repaint();
	}
}

function setEdgeFeature(feat)
{
	if (pawn && pawn.locationType == 'edge')
	{
		var edge = map.edges[pawn.location];
		if (feat)
			edge.feature = feat;
		else
			delete edge.feature;
		repaint();
	}
}

function RiverFactory()
{
	this.todo = new Array();
	this.visitedCount = 0;

	var r = {};
	for (var vId in map.vertices)
	{
		r[vId] = true;
	}
	this.remaining = r;
}

RiverFactory.prototype.getVertexHeight = function(vId)
{
	var cc = geometry.getCellsAdjacentToVertex(vId);
	return (
		map.cells[cc[0]-1].height +
		map.cells[cc[1]-1].height +
		map.cells[cc[2]-1].height) / 3;
};

RiverFactory.prototype.addRiverAt = function(vId)
{
	var h = this.getVertexHeight(vId);
	var vv = geometry.getVerticesAdjacentToVertex(vId);
	var candidates = new Array();
	for (var i = 0, l = vv.length; i < l; i++)
	{
		if (!this.remaining[vv[i]])
			continue;

		var hh = this.getVertexHeight(vv[i]);
		if (hh < h)
			continue;

		candidates.push(vv[i]);
	}

	if (candidates.length == 0)
		return false;

	var i = Math.floor(Math.random() * candidates.length);
	var otherV = candidates[i];

	delete this.remaining[otherV];
	this.visitedCount++;
	this.todo.push(otherV);
	var eId = geometry.makeEdgeFromEndpoints(vId, otherV);
	map.edges[eId].feature = 'river';
	return true;
};

RiverFactory.prototype.step = function()
{
	for (;;)
	{
		if (this.todo.length == 0)
		{
			// find a starting spot
			var candidates = new Array();
			var bestH = Infinity;
			for (var vId in this.remaining)
			{
				var h = this.getVertexHeight(vId);
				if (h < bestH)
				{
					bestH = h;
					candidates = [vId];
				}
				else if (h == bestH)
				{
					candidates.push(vId);
				}
			}

			if (candidates.length == 0)
				return false;

			var i = Math.floor(Math.random()*candidates.length);
			delete this.remaining[candidates[i]];
			this.todo.push(candidates[i]);
		}

		var i = Math.floor(Math.random()*this.todo.length);
		var vId = this.todo[i];
		if (this.addRiverAt(vId))
			return true;

		this.todo.splice(i,1);
	}
};

function riverFactory_stepSeveral()
{
	var finished = false;
	for (var i = 0; i < 3000; i++)
	{
		if (!pawn.rivers.step())
		{
			finished = true;
			break;
		}
	}
	$('#riversStatus').text("TODO: "+pawn.rivers.todo.length+", VISITED: "+pawn.rivers.visitedCount);
	repaint();

	if (!finished)
		setTimeout(riverFactory_stepSeveral,10000);
}

function riverOneClicked()
{
	if (pawn && pawn.locationType == 'vertex')
	{
		if (!pawn.rivers)
		{
			pawn.rivers = new RiverFactory();
		}
		riverFactory_stepSeveral();
	}
}
