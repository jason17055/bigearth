var VIEWPORT = {
	latitude: 0.0,
	longitude: 0.0,
	scale: 300,
	offsetX: 280,
	offsetY: 280
	};

function rotateX(pt, a)
{
	var r = Math.sqrt(pt.y*pt.y + pt.z*pt.z);
	var t = Math.atan2(pt.z, pt.y);
	t += a;
	return {
	x: pt.x,
	y: r*Math.cos(t),
	z: r*Math.sin(t)
	};
}

function rotateY(pt, a)
{
	var r = Math.sqrt(pt.x*pt.x + pt.z*pt.z);
	var t = Math.atan2(pt.z, pt.x);
	t += a;
	return {
	x: r*Math.cos(t),
	y: pt.y,
	z: r*Math.sin(t)
	};
}

function rotateZ(pt, a)
{
	var r = Math.sqrt(pt.x*pt.x + pt.y*pt.y);
	var t = Math.atan2(pt.y, pt.x);
	t += a;
	return {
	x: r*Math.cos(t),
	y: r*Math.sin(t),
	z: pt.z
	};
}

var geometry;
var map = {
	vertices: {}
	};
var coords = {};
var pawn = null;

function toScreenPoint(pt)
{
	var p = rotateX(rotateZ(pt, VIEWPORT.longitude), VIEWPORT.latitude);
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
	$('#contentArea').css({
		width: canvas.width+"px",
		height: canvas.height+"px"
		});
	repaint();
}
window.onresize = onResize;
$(onResize);

function resetMap()
{
	map = makeMap(geometry);
	coords = makeCoords(geometry);
	numBumps = 0;
	pawn = null;
}

geometry = new SphereGeometry(3);
resetMap();

var rotateTimer;
var keepGoing = true;
var rotationIdx = 0;
function doRotation()
{
	rotationIdx++;

	VIEWPORT.longitude += 0.08;
	VIEWPORT.latitude = -Math.sin(0.05*rotationIdx) + Math.PI/2;
	repaint();

	if (keepGoing)
	rotateTimer = setTimeout(doRotation,250);
	else
	rotateTimer = null;
}
$(doRotation);

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

function addWaterClicked()
{
	for (var i in map.cells)
	{
		var cellIdx = parseInt(i)+1;
		var c = map.cells[i];
		if (!c.water)
			c.water = 0;
		c.water += 1;
	}
	repaint();
}

function flowWaterClicked()
{
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
			var d = map.cells[adj[j]];
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

		var adj = geometry.getVerticesAdjacentToCell(cellIdx);
		var best = null;
		var bestL = Infinity;
		for (var j = 0; j < adj.length; j++)
		{
			if (map.vertices[adj[j]].height < bestL)
			{
				best = adj[j];
				bestL = map.vertices[adj[j]].height;
			}
		}

		var amt = map.cells[cellIdx-1].water;
		if (amt > 1) amt = 1;
		map.cells[cellIdx-1].water -= amt;

		pawn = {
		locationType: 'vertex',
		location: best,
		water: amt
		};
		repaint();
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

function flowPawnClicked()
{
	if (!pawn) return;
	if (pawn.locationType != 'vertex') return;

	if (!map.vertexHeightsDone)
		calculateVertexHeights();

	var h = map.vertices[pawn.location].height;

	var adj = geometry.getVerticesAdjacentToVertex(pawn.location);
	var sumFitness = 0;
	var rouletteWheel = new Array();
	for (var i = 0; i < adj.length; i++)
	{
		var other_height = map.vertices[adj[i]].height;
		if (other_height > h+1) continue;

		var f = (h+1-other_height);
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

function addWaterAtPawn()
{
	if (pawn && pawn.locationType == 'cell')
	{
		map.cells[pawn.location-1].water++;
		repaint();
	}
}
