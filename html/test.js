//      *     *
//     * *   * *
//    * * * * * *
//   * * * * * * *
//  * * * * * * *
//
function fromPolar(lgt, lat)
{
	var zz = Math.cos(lat);
	return {
	x: Math.cos(lgt) * zz,
	y: Math.sin(lgt) * zz,
	z: Math.sin(lat)
	};
}

var A1 = 0.913;
var A2 = 0.1;
var pentPts = new Array();
for (var i = 0; i < 5; i++)
{
	pentPts.push(fromPolar(i * (Math.PI * 2/5), A1));
}

var cells = new Array();
var c;
c = {
	pts: pentPts
	};
cells.push(c);

for (var i = 0; i < 5; i++)
{
	c = {
		pts: new Array()
		};
	var a = i * (Math.PI * 2/5);
	var b = (i+1) * (Math.PI * 2/5);
	c.pts.push(fromPolar(a, A1));
	c.pts.push(fromPolar(b, A1));
	c.pts.push(fromPolar(b, A2));
	c.pts.push(fromPolar((a+b)/2, -A2));
	c.pts.push(fromPolar(a, A2));
	cells.push(c);
}

function repaint()
{
	var canvas = document.getElementById('theCanvas');
	var ctx = canvas.getContext('2d');

	ctx.fillStyle = '#ff0';
	ctx.fillRect(0,0,canvas.width,canvas.height);

	var SCALE = 100;
	var OFFSET = 200;
	for (var i in cells)
	{
		var sumLen = 0.0;
		var c = cells[i];
		if (c.pt.z < 0)
			continue;

		ctx.beginPath();
		ctx.lineWidth = 1;
		ctx.strokeStyle = '#000';
		ctx.moveTo(c.pts[0].x*SCALE+OFFSET, c.pts[0].y*SCALE+OFFSET);
		for (var j = 0, l = c.pts.length; j < l; j++)
		{
			var d = Math.sqrt(
				Math.pow(c.pts[j].x - c.pts[(j+1)%l].x, 2.0)
				+ Math.pow(c.pts[j].y - c.pts[(j+1)%l].y, 2.0)
				+ Math.pow(c.pts[j].z - c.pts[(j+1)%l].z, 2.0)
				);
			sumLen += d;
			ctx.lineTo(c.pts[(j+1)%l].x*SCALE+OFFSET, c.pts[(j+1)%l].y*SCALE+OFFSET);
		}
		ctx.stroke();

		ctx.strokeText("len="+sumLen, 0, i * 20+20);
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


/**
 * sz=0
 *        /-----0-----\
 *       /   /  |  \   \
 *      2   4   6   8   10
 *       \ / \ / \ / \ / \
 *        3   5   7   9  11
 *         \   \  |  /  /
 *          \-----1----/
 *
 * sz=1
 *  idx 0-11 special
 *  idx 12-31 intermediates
 *
 */
function makeCells(sz)
{
	var cells = new Array();
	var c;

	c = {
	pt: { x: 0, y: 0, z: 1 }
	};
	cells.push(c);

	c = {
	pt: { x: 0, y: 0, z: -1 }
	};
	cells.push(c);

	var THIRTYDEGREES = Math.PI*1/6;
	for (var i = 0; i < 5; i++)
	{
		var a = i * Math.PI * 2 / 5;
		c = {
		pt: fromPolar(a, THIRTYDEGREES)
		};
		cells.push(c);

		c = {
		pt: fromPolar(a + Math.PI/5, -THIRTYDEGREES)
		};
		cells.push(c);
	}

	for (var i in cells)
	{
		var c = cells[i];

		var adj = getNeighbors(sz, i);
		c.adjacent = new Array();
		for (var j = 0; j < adj.length; j++)
		{
			c.adjacent.push(cells[adj[j]]);
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

function getNeighbors_size0(cellIdx)
{
	if (cellIdx == 0)
		return [ 10, 8, 6, 4, 2 ];
	if (cellIdx == 1)
		return [ 3, 5, 7, 9, 11 ];

	var i = Math.floor((cellIdx-2)/2);
	var j = (i+1)%5;
	var k = (i+4)%5;

	if (cellIdx % 2 == 0)
	{
		return [ 0, j*2+2, i*2+3, k*2+3, k*2+2 ];
	}
	else
	{
		return [ 1, k*2+3, i*2+2, j*2+2, j*2+3 ];
	}
}

function getNeighbors(mapSize, cellIdx)
{
	if (mapSize == 0)
		return getNeighbors_size0(cellIdx);

	
	alert('not implemented');
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
cells = makeCells(0);
