function shuffleArray(arr)
{
	var l = arr.length;
	for (var i = 0; i < l; i++)
	{
		var j = i + Math.floor(Math.random() * (l-i));
		var t = arr[i];
		arr[i] = arr[j];
		arr[j] = t;
	}
	return;
}

function Geometry(width, height) {
  this.width = width;
  this.height = height;
}

Geometry.prototype.getCellRow = function(cellIdx) {
  return Math.floor(cellIdx / this.width);
};

Geometry.prototype.getCellColumn = function(cellIdx) {
  return cellIdx % this.width;
};

Geometry.prototype.getCell = function(row, column) {
  return row * this.width + column;
};

Geometry.prototype.isCellAdjacent = function(cell1, cell2) {
	return cell2 == this.getAdjacentW(cell1) ||
		cell2 == this.getAdjacentNW(cell1) ||
		cell2 == this.getAdjacentNE(cell1) ||
		cell2 == this.getAdjacentE(cell1) ||
		cell2 == this.getAdjacentSE(cell1) ||
		cell2 == this.getAdjacentSW(cell1);
};

Geometry.WEST = 0;
Geometry.NORTHWEST = 1;
Geometry.NORTHEAST = 2;
Geometry.EAST = 3;
Geometry.SOUTHEAST = 4;
Geometry.SOUTHWEST = 5;

Geometry.prototype.getAdjacentCell = function(cellIdx, dir) {
  switch (dir) {
  case 0: return this.getAdjacentW(cellIdx);
  case 1: return this.getAdjacentNW(cellIdx);
  case 2: return this.getAdjacentNE(cellIdx);
  case 3: return this.getAdjacentE(cellIdx);
  case 4: return this.getAdjacentSE(cellIdx);
  case 5: return this.getAdjacentSW(cellIdx);
  }
  return null;
};

Geometry.prototype.getAdjacentW = function(cellIdx) {
  return cellIdx - 1;
};

Geometry.prototype.getAdjacentNW = function(cellIdx) {
  var origRow = this.getCellRow(cellIdx);
  if (origRow % 2 == 0) {
    return cellIdx - this.width;
  }
  else {
    return cellIdx - this.width - 1;
  }
};

Geometry.prototype.getAdjacentNE = function(cellIdx) {
  var origRow = this.getCellRow(cellIdx);
  if (origRow % 2 == 0) {
    return cellIdx - this.width + 1;
  }
  else {
    return cellIdx - this.width;
  }
};

Geometry.prototype.getAdjacentE = function(cellIdx) {
  return cellIdx + 1;
};

Geometry.prototype.getAdjacentSE = function(cellIdx) {
  var origRow = this.getCellRow(cellIdx);
  if (origRow % 2 == 0) {
    return cellIdx + this.width + 1;
  }
  else {
    return cellIdx + this.width;
  }
};

Geometry.prototype.getAdjacentSW = function(cellIdx) {
  var origRow = this.getCellRow(cellIdx);
  if (origRow % 2 == 0) {
    return cellIdx + this.width;
  }
  else {
    return cellIdx + this.width - 1;
  }
};

Geometry.prototype.getTrackIndex = function(cellIdx, dir) {
  if (dir == 3) {
    return this.getTrackIndex(this.getAdjacentCell(cellIdx, Geometry.EAST), 0);
  }
  else if (dir == 4) {
    return this.getTrackIndex(this.getAdjacentCell(cellIdx, Geometry.SOUTHEAST), 1);
  }
  else if (dir == 5) {
    return this.getTrackIndex(this.getAdjacentCell(cellIdx, Geometry.SOUTHWEST), 2);
  }
  else {
    return cellIdx * 3 + dir + 1;
  }
};

/**
 * Returns information about the specified edge.
 * Track indexes are just Edge indexes plus one.
 * @return struct with 'cell1', 'cell2', and 'dir' properties.
 */
Geometry.prototype.decodeEdge = function(edgeIdx) {
  let dir = edgeIdx % 3;
  let cell1 = Math.floor(edgeIdx / 3);
  let cell2 = this.getAdjacentCell(cell1, dir);
  return {
    cell1: cell1,
    cell2: cell2,
    dir: dir,
    //For debugging:
    //cell1_row: this.getCellRow(cell1),
    //cell2_row: this.getCellRow(cell2),
    //cell1_col: this.getCellColumn(cell1),
    //cell2_col: this.getCellColumn(cell2),
  };
};

/**
 * @return cell's center position in (unzoomed) display coordinates.
 */
Geometry.prototype.getCellPoint = function(cellIdx) {
  const mapCenterX = this.width / 2;
  const mapCenterY = this.height / 2;

  if (cellIdx >= 0) {
    const x = this.getCellColumn(cellIdx);
    const y = this.getCellRow(cellIdx);

	return {
		x: (y % 2 == 0 ? CELL_WIDTH / 2 : 0) + CELL_WIDTH * (x - mapCenterX),
		y: CELL_HEIGHT * (y - mapCenterY)
		};
  } else {
    console.log('getCellPoint called for cell ' + cellIdx);
  }
};

Geometry.prototype.getEdgeFromPoint = function(pt) {

  const mapCenterX = this.width / 2;
  const mapCenterY = this.height / 2;

  var iy = Math.floor((pt.y + CELL_ASCENT / 2) / CELL_HEIGHT + mapCenterY);
  var ia = Math.floor((pt.x + CELL_WIDTH / 2) / (CELL_WIDTH/2) + mapCenterX * 2) - (1 - iy % 2);

  if (iy < 0 || iy >= this.height) {
    return null;
  }

  var ry = pt.y + CELL_ASCENT / 2 - (iy - mapCenterY) * CELL_HEIGHT;
  if (ry < CELL_ASCENT) {
    var col = Math.floor((ia+1) / 2);
    return this.getCell(iy, col) * 3;
  }
  else if (ia % 2 == 0) {
    var col = Math.floor(ia / 2);
    return this.getAdjacentCell(this.getCell(iy, col), Geometry.SOUTHWEST) * 3 + 2;
  }
  else {
    var col = Math.floor(ia / 2);
    return this.getAdjacentCell(this.getCell(iy, col), Geometry.SOUTHEAST) * 3 + 1;
  }
};

Geometry.prototype.getCellFromPoint = function(pt) {
  const mapCenterX = this.width / 2;
  const mapCenterY = this.height / 2;

  var iy = Math.floor((pt.y + CELL_ASCENT / 2) / CELL_HEIGHT + mapCenterY);
  var ix = Math.floor((pt.x + CELL_WIDTH / 2 - (iy % 2 == 0 ? CELL_WIDTH/2 : 0)) / CELL_WIDTH + mapCenterX);
  if (iy >= 0 && iy < this.height) {
    if (ix >= 0 && ix < this.width) {
      return this.getCell(iy, ix);
    }
  }
  return null;
};

/** @deprecated not used */
Geometry.prototype.simpleDistance = function(cellIdx1, cellIdx2) {

  var row1 = this.getCellRow(cellIdx1);
  var col1 = this.getCellColumn(cellIdx1);
  var row2 = this.getCellRow(cellIdx2);
  var col2 = this.getCellColumn(cellIdx2);

  var distRows = Math.abs(row2-row1);
  var distCols = Math.abs(col2-col1);
  var diag = Math.floor(distRows / 2);
  return distRows + (distCols > diag ? distCols - diag : 0);
};

function HexVertGeometry(width, height) {
  Geometry.call(this, width, height);
}
HexVertGeometry.prototype = Object.create(Geometry.prototype);
HexVertGeometry.prototype.constructor = HexVertGeometry;

HexVertGeometry.prototype.getCellPoint = function(cellIdx) {
  var pt = Geometry.prototype.getCellPoint.call(this, cellIdx);
  if (!pt) { return pt; }

  return {
      x: pt.y,
      y: -pt.x,
  };
};

HexVertGeometry.prototype.getCellFromPoint = function(pt) {
  pt = {x: -pt.y, y: pt.x};
  return Geometry.prototype.getCellFromPoint.call(this, pt);
};

HexVertGeometry.prototype.getEdgeFromPoint = function(pt) {
  pt = {x: -pt.y, y: pt.x};
  return Geometry.prototype.getEdgeFromPoint.call(this, pt);
};

function isCity(cellIdx)
{
	return mapData.cities[cellIdx];
}
