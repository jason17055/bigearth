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

function getCellRow(cellIdx)
{
	return Math.floor(cellIdx / CELLS_PER_ROW);
}

Geometry.prototype.getCellRow = function(cellIdx) {
	//FIXME
	return getCellRow(cellIdx);
};

Geometry.prototype.getCellColumn = function(cellIdx) {
  return cellIdx % this.width;
};

Geometry.prototype.getCell = function(row, column) {
  return row * this.width + column;
};

Geometry.prototype.isCellAdjacent = function(cell1, cell2) {
	return cell2 == getAdjacentW(cell1) ||
		cell2 == getAdjacentNW(cell1) ||
		cell2 == getAdjacentNE(cell1) ||
		cell2 == getAdjacentE(cell1) ||
		cell2 == getAdjacentSE(cell1) ||
		cell2 == getAdjacentSW(cell1);
};

Geometry.WEST = 0;
Geometry.NORTHWEST = 1;
Geometry.NORTHEAST = 2;
Geometry.EAST = 3;
Geometry.SOUTHEAST = 4;
Geometry.SOUTHWEST = 5;

Geometry.prototype.getAdjacentCell = function(cellIdx, dir) {
	switch (dir)
	{
	case 0: return getAdjacentW(cellIdx);
	case 1: return getAdjacentNW(cellIdx);
	case 2: return getAdjacentNE(cellIdx);
	case 3: return getAdjacentE(cellIdx);
	case 4: return getAdjacentSE(cellIdx);
	case 5: return getAdjacentSW(cellIdx);
	}
	return null;
};

function getAdjacentW(cellIdx)
{
	return cellIdx - 1;
}

function getAdjacentNW(cellIdx)
{
	var origRow = getCellRow(cellIdx);
	if (origRow % 2 == 0)
	{
		return cellIdx - CELLS_PER_ROW;
	}
	else
	{
		return cellIdx - CELLS_PER_ROW - 1;
	}
}

function getAdjacentNE(cellIdx)
{
	var origRow = getCellRow(cellIdx);
	if (origRow % 2 == 0)
	{
		return cellIdx - CELLS_PER_ROW + 1;
	}
	else
	{
		return cellIdx - CELLS_PER_ROW;
	}
}

function getAdjacentE(cellIdx)
{
	return cellIdx + 1;
}

function getAdjacentSE(cellIdx)
{
	var origRow = getCellRow(cellIdx);
	if (origRow % 2 == 0)
	{
		return cellIdx + CELLS_PER_ROW + 1;
	}
	else
	{
		return cellIdx + CELLS_PER_ROW;
	}
}

function getAdjacentSW(cellIdx)
{
	var origRow = getCellRow(cellIdx);
	if (origRow % 2 == 0)
	{
		return cellIdx + CELLS_PER_ROW;
	}
	else
	{
		return cellIdx + CELLS_PER_ROW - 1;
	}
}

function isCity(cellIdx)
{
	return mapData.cities[cellIdx];
}

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
    return cellIdx * 3 + (dir + 1);
  }
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
