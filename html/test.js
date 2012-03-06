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

	var SCALE = 100;
	var OFFSET = 200;
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
 
	if (parseInt(i)+1 < 27){

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
 * sz=0 ; 4 rows, 12 cells
 *        /-----1-----\
 *       /   /  |  \   \
 *      2   3   4   5   6
 *       \ / \ / \ / \ / \
 *        7   8   9  10  11
 *         \   \  |  /  /
 *          \----12----/
 *
 * sz=1 ; 7 rows, 42 cells
 *  row 1: 1 cell (pentagon)
 *  row 2: 5 cells (hexagons)
 *  row 3: 10 cells (alternating pentagon/hexagon)
 *  row 4: 10 cells (hexagons)
 *  row 5: 10 cells (alternating pentagon/hexagon)
 *  row 6: 5 cells (hexagons)
 *  row 7: 1 cell (pentagon)
 *
 * sz=2 ; 10 rows, 92 cells
 *  row 1: 1 cell
 *  row 2: 5 cells     1   \
 *  row 3: 10 cells    2    = 6
 *  row 4: 15 cells    3   /
 *  row 5: 15 cells    3
 *  row 6: 15 cells    3
 *  row 7: 15 cells    3   \
 *  row 8: 10 cells    2    = 6
 *  row 9: 5 cells     1   /
 *  row 10: 1 cell
 *
 * 1+2+3+...+n = n(n+1)/2
 * (sz+1)(sz+2) + sz(sz+1)
 * (sz+1)(sz+2+sz)
 * 2(sz+1)(sz+1)
 * 2(sz+1)^2
 *
 * in general, numCells = 2 + 10 * (sz+1)^2
 */
function makeCells(geometry)
{
	var cells = new Array();
	var c;
	var sz = geometry.size;

	var numRows = 3*sz + 4;

	for (var row = 1; row <= numRows; row++)
	{
		var lat = Math.PI / 2 - Math.PI * (row-1) / (numRows-1);
		var numCellsInRow = geometry.getNumCellsInRow(row);
		var offset = 0;
		if (row > (sz+1) && row <= 2*(sz+1))
		{
			if ((row - (sz+1)) % 2 == 0)
				offset = 0.5;
		}
		else if (row > (sz+1) && row < numRows)
		{
			if (sz % 2 == 0)
				offset = 0.5;
		}

		for (var i = 0; i < numCellsInRow; i++)
		{
			var lgt = Math.PI*2 * (i+offset) / numCellsInRow;
			c = {
			pt: fromPolar(lgt, lat)
			};
			cells.push(c);
		}
	}

	for (var i in cells)
	{
		var c = cells[i];

		var adj = getNeighbors(geometry, parseInt(i)+1);
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

function getNeighbors_size0(cellIdx)
{
	if (cellIdx == 1)
		return [ 6, 5, 4, 3, 2 ];
	if (cellIdx == 12)
		return [ 7, 8, 9, 10, 11 ];

	var i = (cellIdx - 2) % 5;
	var ir = (i+1)%5;
	var il = (i+4)%5;

	if (cellIdx < 7)
	{
		return [ 1, ir+2, i+7, il+7, il+2 ];
	}
	else
	{
		return [ 12, il+7, i+2, ir+2, ir+7 ];
	}
}

function getNeighbors(geometry, cellIdx)
{
	var sz = geometry.size;
	if (sz == 0)
		return getNeighbors_size0(cellIdx);

	var ri = geometry.getRowIdx(cellIdx);
	if (ri.row == 1)
	{
		return [ 2, 3, 4, 5, 6];
	}
	else if (ri.row < sz + 3)
	{
		var sizePrevRow = (ri.row-2)*5;
		var sizeThisRow = (ri.row-1)*5;
		var sizeNextRow = (ri.row)*5;

		if (ri.row == 2)
		{
			return [
			1,
			geometry.getCell(ri.row, (ri.idx+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row+1, (ri.idx*2+sizeNextRow-1)%sizeNextRow),
			geometry.getCell(ri.row+1, (ri.idx*2)%sizeNextRow),
			geometry.getCell(ri.row+1, (ri.idx*2+1)%sizeNextRow),
			geometry.getCell(ri.row, (ri.idx+1)%sizeThisRow)
			];
		}
		else if (ri.idx % 2 == 0)
		{
			return [
			geometry.getCell(ri.row-1, (ri.idx/2)),
			geometry.getCell(ri.row, (ri.idx+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row+1, (ri.idx*2+sizeThisRow-1)%sizeNextRow),
			geometry.getCell(ri.row+1, (ri.idx*2)%sizeNextRow),
			geometry.getCell(ri.row+1, (ri.idx*2+1)%sizeNextRow),
			geometry.getCell(ri.row, (ri.idx+1)%sizeThisRow)
			];
		}
		else
		{
			return [
			geometry.getCell(ri.row-1, (ri.idx-1)/2),
			geometry.getCell(ri.row, (ri.idx+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row+1, (ri.idx*2+sizeThisRow-1)%sizeNextRow),
			geometry.getCell(ri.row+1, (ri.idx*2)%sizeNextRow),
			geometry.getCell(ri.row, (ri.idx+1)%sizeThisRow),
			geometry.getCell(ri.row-1, ((ri.idx-1)/2+1)%sizePrevRow)
			];
		}
	}
	else
	{
		//lazy
		return [ 2, 3, 4, 5, 6];
	}
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

function SphereGeometry(size)
{
	this.size = size;

	this.numRows = size * 3 + 4;
	this.getNumCellsInRow = function(row)
	{
		if (row == 1 || row == this.numRows)
		{
			return 1;
		}
		else if (row < size+2)
		{
			return (row-1)*5;
		}
		else if (row <= size*2+3)
		{
			return (size+1)*5;
		}
		else if (row < this.numRows)
		{
			return (this.numRows-row)*5;
		}
		else
		{
			return 0;
		}
	};
	this.getRowIdx = function(cellIdx)
	{
		var i = cellIdx - 1;
		for (var row = 1; row <= this.numRows; row++)
		{
			var l = this.getNumCellsInRow(row);
			if (i < l)
				return { row: row, idx: i };
			else
				i -= l;
		}
		return null;
	};
	this.getCell = function(row, idx)
	{
		if (row == 1)
		{
			return 1;
		}
		else if (row < size+2)
		{
			var b = 2 + 5 * (row - 1) * (row - 2) / 2;
			return b + idx;
		}
		else if (row <= size*2+3)
		{
			var b1 = 2 + 5 * (size + 1) * (size) / 2;
			var b = b1 + (size+1)*5 * (row-(size+2));
			return b + idx;
		}
		else if (row < this.numRows)
		{
		//TODO
			return 0;
		}
		else if (row == this.numRows)
		{
			return 2 + 10 * (sz + 1) * (sz + 1);		
		}
		else
		{
			return 0;
		}
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
	var adj = getNeighbors(geometry, x);
	alert('adjacent to ' + x + ' is ' + adj.join(', '));
}

function nextSizeClicked()
{
	geometry = new SphereGeometry((geometry.size+1)%3);
	cells = makeCells(geometry);
}
