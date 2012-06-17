var curLatitude = 0.0;
var curLongitude = 0.0;

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

function applyRotation(pt)
{
	return rotateX(rotateZ(pt, curLongitude), curLatitude);
}

var geometry;
var map = {
	vertices: {}
	};
var cells = new Array();
var pawn = null;

var SCALE = 250;
var OFFSET = 280;

function toScreenPoint(pt)
{
	var p = applyRotation(pt);
	return {
	x: p.x * SCALE + OFFSET,
	y: p.y * SCALE + OFFSET,
	z: p.z };
}

function repaint()
{
	var canvas = document.getElementById('theCanvas');
	var ctx = canvas.getContext('2d');

	ctx.fillStyle = '#444';
	ctx.fillRect(0,0,canvas.width,canvas.height);

	for (var i in cells)
	{
		var c = cells[i];
		var cellIdx = parseInt(i)+1;
		if (applyRotation(c.pt).z < 0)
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
			ctx.fillStyle = c.water < 3 ? '#8af' : '#05a';
		}

		//	c.height < -3 ? '#05a' :
		//	c.height < 0 ? '#8af' :
		//	c.height < 3 ? '#8f8' :
		//	c.height < 7 ? '#f80' :
		//	'#eee';
 
		ctx.beginPath();
		var p = applyRotation(c.pts[0]);
		ctx.moveTo(p.x*SCALE+OFFSET, p.y*SCALE+OFFSET);
		for (var j = 0, l = c.pts.length; j < l; j++)
		{
			var d = Math.sqrt(
				Math.pow(c.pts[j].x - c.pts[(j+1)%l].x, 2.0)
				+ Math.pow(c.pts[j].y - c.pts[(j+1)%l].y, 2.0)
				+ Math.pow(c.pts[j].z - c.pts[(j+1)%l].z, 2.0)
				);

			var p = applyRotation(c.pts[(j+1)%l]);
			ctx.lineTo(p.x*SCALE+OFFSET, p.y*SCALE+OFFSET);
		}
		ctx.fill();
		ctx.stroke();

		var p = applyRotation(c.pt);

	// SHOW HEIGHTS
	//	ctx.fillStyle = '#800';
	//	ctx.fillText(c.height, p.x*SCALE+OFFSET, p.y*SCALE+OFFSET-8);

	// SHOW CELL IDS
	//	ctx.fillStyle = '#fff';
	//	ctx.fillText(cellIdx, p.x*SCALE+OFFSET, p.y*SCALE+OFFSET-8);

		if (Math.floor(c.water) != 0)
		{
		ctx.fillStyle = '#008';
		ctx.fillText(Math.floor(c.water), p.x*SCALE+OFFSET, p.y*SCALE+OFFSET);
		}
		//var ri = geometry.getRowIdx(parseInt(i)+1);
		//ctx.fillText(ri.row+','+ri.idx, p.x*SCALE+OFFSET, p.y*SCALE+OFFSET);
		ctx.restore();
	}

	for (var vId in map.vertices)
	{
		var v = map.vertices[vId];
		var p = applyRotation(v.pt);
		if (p.z < 0)
			continue;


		p = {
		x: p.x * SCALE + OFFSET,
		y: p.y * SCALE + OFFSET
		};

		if (Math.random() < 0)
		{
			ctx.save();
			ctx.lineWidth = 2;
			ctx.strokeStyle = '#c0c';

			var nn = geometry.getVerticesAdjacentToVertex(vId);
			for (var i = 0, l = nn.length; i < l; i++)
			{
				var u = map.vertices[nn[i]];
				var q = applyRotation(u.pt);
				if (q.z < 0) continue;

				q = {
				x: q.x * SCALE + OFFSET,
				y: q.y * SCALE + OFFSET
				};

			ctx.beginPath();
			ctx.moveTo(p.x, p.y);
			ctx.lineTo(q.x, q.y);
			ctx.stroke();
			}

			ctx.restore();
		}

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

	if (pawn)
	{
		var v = map.vertices[pawn.location];
		var p = toScreenPoint(v.pt);
		if (p.z >= 0)
		{
		ctx.save();
		ctx.fillStyle = '#c0c';
		ctx.fillRect(p.x-4,p.y-4,8,8);

		var adj = geometry.getVerticesAdjacentToVertex(pawn.location);
		for (var i = 0; i < adj.length; i++)
		{
			var q = toScreenPoint(map.vertices[adj[i]].pt);
			if (q.z < 0) continue;

			ctx.fillStyle = '#fff';
			ctx.strokeStyle = '#c0c';
			ctx.lineWidth = 2;
			ctx.fillRect(q.x-3,q.y-3,6,6);
		}

		ctx.restore();
		}
	}
}

function onResize()
{
	var canvas = document.getElementById('theCanvas');
	canvas.width = window.innerWidth - 0;
	canvas.height = window.innerHeight - $('#buttonBar').outerHeight();
	$('#contentArea').css({
		width: canvas.width+"px",
		height: canvas.height+"px"
		});
	repaint();
}
window.onresize = onResize;
$(onResize);


function makeMap(geometry)
{
	var cells = new Array();
	var vertices = {};

	var numCells = geometry.getCellCount();
	for (var cellIdx = 1; cellIdx <= numCells; cellIdx++)
	{
		var c = {
			pt: geometry.getSpherePoint(cellIdx),
			height: 0,
			water: 0
			};
		cells.push(c);
	}

	for (var i in cells)
	{
		var cellIdx = parseInt(i)+1;
		var c = cells[i];

		var adj = geometry.getNeighbors(cellIdx);
		c.adjacent = new Array();
		for (var j = 0; j < adj.length; j++)
		{
			c.adjacent.push(cells[adj[j]-1]);
		}

		var pts = new Array();
		for (var j = 0, l = c.adjacent.length; j < l; j++)
		{
			var d = c.adjacent[j];
			var e = c.adjacent[(j+1)%l];

			var avg = {
			x: (c.pt.x + d.pt.x + e.pt.x) / 3,
			y: (c.pt.y + d.pt.y + e.pt.y) / 3,
			z: (c.pt.z + d.pt.z + e.pt.z) / 3
			};
			avg = normalize(avg);
			pts.push(avg);

			var vId = geometry._makeVertex(cellIdx,adj[j],adj[(j+1)%l]);
			if (!(vId in vertices))
			{
				vertices[vId] = {
				pt: avg
				};
			}
		}
		c.pts = pts;
	}
	return {
	cells: cells,
	vertices: vertices
	};
}

function normalize(pt)
{
	var l = Math.sqrt(
		Math.pow(pt.x, 2.0)
		+Math.pow(pt.y, 2.0)
		+Math.pow(pt.z, 2.0)
		);
	return {
	x: pt.x / l,
	y: pt.y / l,
	z: pt.z / l
	};
}

geometry = new SphereGeometry(3);
map = makeMap(geometry);
cells = map.cells;

var rotateTimer;
var keepGoing = true;
var rotationIdx = 0;
function doRotation()
{
	rotationIdx++;

	curLongitude += 0.08;
	curLatitude = -Math.sin(0.05*rotationIdx) + Math.PI/2;
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
	var M = Math.sqrt(1/3);
	var v = {
	x: Math.random() * 2 * M - M,
	y: Math.random() * 2 * M - M,
	z: Math.random() * 2 * M - M
	};
	var d = Math.random() >= 0.5 ? 1 : -1;

	for (var i in cells)
	{
		var c = cells[i];
		var u = {
		x: c.pt.x - v.x,
		y: c.pt.y - v.y,
		z: c.pt.z - v.z
		};
		var dp = u.x * v.x + u.y * v.y + u.z * v.z;
		if (dp > 0)
		{
			c.height += d;
		}
	}
	repaint();

	numBumps++;
	$('#bumpMapBtn').text('Bump Map ('+numBumps+')');
}

var numBumps = 0;
function nextSizeClicked()
{
	geometry = new SphereGeometry((geometry.size+1)%14);
	map = makeMap(geometry);
	cells = map.cells;
	numBumps = 0;
	pawn = null;

	document.title = 'Big Earth ' + geometry.size;
	repaint();
}

function addWaterClicked()
{
	for (var vId in map.vertices)
	{
		var v = map.vertices[vId];
		if (!v.water)
			v.water = 0;
		v.water += 1;
	}
	repaint();
}

function flowWaterClicked()
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

	pawn = {
	locationType: "vertex",
	location: vId
	};
	repaint();
	}
	else if (xx.type == 'cell')
	{
		$('#infoPane .featureType').text('Cell');
		$('#vId').text(xx.id);
		$('#infoPane .adjacentCells').text(geometry.getNeighbors(xx.id).join('; '));
		$('#infoPane .adjacentVertices').text(geometry.getVerticesAdjacentToCell(xx.id).join('; '));
		$('#infoPane').show();
	}
}

function getNearestFeatureFromScreen(screenPt)
{
	var bestvertex = getVertexFromScreen(screenPt);
	var p = toScreenPoint(map.vertices[bestvertex].pt);

	var bestDist = Math.sqrt(Math.pow(screenPt.x-p.x,2)+Math.pow(screenPt.y-p.y,2));
	var best = {
	type: "vertex",
	id: bestvertex
	};

	for (var i in map.cells)
	{
		var cellIdx = parseInt(i)+1;
		var c = map.cells[i];

		var p = toScreenPoint(c.pt);
		var d = Math.sqrt(Math.pow(p.x-screenPt.x,2)+Math.pow(p.y-screenPt.y,2));
		if (d<bestDist)
		{
			best = {
			type: "cell",
			id: cellIdx
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
		var p = toScreenPoint(map.vertices[vId].pt);
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
