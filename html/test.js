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
var cells = new Array();

function repaint()
{
	var canvas = document.getElementById('theCanvas');
	var ctx = canvas.getContext('2d');

	ctx.fillStyle = '#ff0';
	ctx.fillRect(0,0,canvas.width,canvas.height);

	var SCALE = 250;
	var OFFSET = 280;
	for (var i in cells)
	{
		var sumLen = 0.0;
		var c = cells[i];
		if (applyRotation(c.pt).z < 0)
			continue;

		ctx.lineWidth = 1;
		ctx.strokeStyle = '#000';
	if (2 == 1) {
		for (var j = 0, l = c.adjacent.length; j < l; j++)
		{
			var d = c.adjacent[j];
			ctx.beginPath();
			var p = applyRotation(c.pt);
			var q = applyRotation(d.pt);
			ctx.moveTo(p.x*SCALE+OFFSET, p.y*SCALE+OFFSET);
			ctx.lineTo(q.x*SCALE+OFFSET, q.y*SCALE+OFFSET);
			ctx.stroke();
		}
	}
 
	//if (parseInt(i)+1 < 47){
	if (1==1) {

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
			sumLen += d;

			var p = applyRotation(c.pts[(j+1)%l]);
			ctx.lineTo(p.x*SCALE+OFFSET, p.y*SCALE+OFFSET);
		}
		ctx.stroke();
	}

		var p = applyRotation(c.pt);
		ctx.strokeText(parseInt(i)+1, p.x*SCALE+OFFSET, p.y*SCALE+OFFSET-8);

		ctx.save();
		ctx.fillStyle = '#800';
		var ri = geometry.getRowIdx(parseInt(i)+1);
		ctx.fillText(ri.row+','+ri.idx, p.x*SCALE+OFFSET, p.y*SCALE+OFFSET);
		ctx.restore();
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


function makeCells(geometry)
{
	var cells = new Array();

	var numCells = geometry.getCellCount();
	for (var cellIdx = 1; cellIdx <= numCells; cellIdx++)
	{
		var c = {
			pt: geometry.getSpherePoint(cellIdx)
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
			pts.push(normalize(avg));
		}
		c.pts = pts;
	}
	return cells;
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

geometry = new SphereGeometry(0);
cells = makeCells(geometry);

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
	var x = document.getElementById('numEntry').value;
	var adj = geometry.getNeighbors(x);
	alert('adjacent to ' + x + ' is ' + adj.join(', '));
}

function nextSizeClicked()
{
	geometry = new SphereGeometry((geometry.size+1)%6);
	cells = makeCells(geometry);
}
