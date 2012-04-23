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

function Geometry(width, height)
{
	this.width = width;
	this.height = height;
}

Geometry.prototype.getCellRow = function(cellIdx)
{
	return Math.floor(cellIdx / this.width);
}

Geometry.prototype.getCellColumn = function(cellIdx)
{
	return cellIdx % this.width;
}

Geometry.prototype.getCell = function(row, column)
{
	return row * this.width + column;
}

Geometry.prototype.isCellAdjacent = function(cell1, cell2)
{
	return cell2 == this.getAdjacentW(cell1) ||
		cell2 == this.getAdjacentNW(cell1) ||
		cell2 == this.getAdjacentNE(cell1) ||
		cell2 == this.getAdjacentE(cell1) ||
		cell2 == this.getAdjacentSE(cell1) ||
		cell2 == this.getAdjacentSW(cell1);
}

Geometry.prototype.getAdjacentCell = function(cellIdx, dir)
{
	switch (dir)
	{
	case 0: return this.getAdjacentW(cellIdx);
	case 1: return this.getAdjacentNW(cellIdx);
	case 2: return this.getAdjacentNE(cellIdx);
	case 3: return this.getAdjacentE(cellIdx);
	case 4: return this.getAdjacentSE(cellIdx);
	case 5: return this.getAdjacentSW(cellIdx);
	}
	return null;
}

Geometry.prototype.getAdjacentW = function(cellIdx)
{
	return cellIdx - 1;
}

Geometry.prototype.getAdjacentNW = function(cellIdx)
{
	var origRow = this.getCellRow(cellIdx);
	if (origRow % 2 == 0)
	{
		return cellIdx - this.width;
	}
	else
	{
		return cellIdx - this.width - 1;
	}
}

Geometry.prototype.getAdjacentNE = function(cellIdx)
{
	var origRow = this.getCellRow(cellIdx);
	if (origRow % 2 == 0)
	{
		return cellIdx - this.width + 1;
	}
	else
	{
		return cellIdx - this.width;
	}
}

Geometry.prototype.getAdjacentE = function(cellIdx)
{
	return cellIdx + 1;
}

Geometry.prototype.getAdjacentSE = function(cellIdx)
{
	var origRow = this.getCellRow(cellIdx);
	if (origRow % 2 == 0)
	{
		return cellIdx + this.width + 1;
	}
	else
	{
		return cellIdx + this.width;
	}
}

Geometry.prototype.getAdjacentSW = function(cellIdx)
{
	var origRow = this.getCellRow(cellIdx);
	if (origRow % 2 == 0)
	{
		return cellIdx + this.width;
	}
	else
	{
		return cellIdx + this.width - 1;
	}
}

Geometry.prototype.simpleDistance = function(cellIdx1, cellIdx2)
{
	var row1 = this.getCellRow(cellIdx1);
	var col1 = this.getCellColumn(cellIdx1);
	var row2 = this.getCellRow(cellIdx2);
	var col2 = this.getCellColumn(cellIdx2);

	var distRows = Math.abs(row2-row1);
	var distCols = Math.abs(col2-col1);
	var diag = Math.floor(distRows / 2);
	return distRows + (distCols > diag ? distCols - diag : 0);
}

Geometry.prototype.getTrackIndex = function(cellIdx, dir)
{
	if (dir == 3)
	{
		return this.getTrackIndex(this.getAdjacentE(cellIdx), 0);
	}
	else if (dir == 4)
	{
		return this.getTrackIndex(this.getAdjacentSE(cellIdx), 1);
	}
	else if (dir == 5)
	{
		return this.getTrackIndex(this.getAdjacentSW(cellIdx), 2);
	}
	else
	{
		return cellIdx * 3 + (dir + 1);
	}
}

function Map(rawData)
{
	var w = rawData.terrain[0].length;
	var h = rawData.terrain.length;

	this.geometry = new Geometry(w,h);
	this.rivers = rawData.rivers;
	this.rails = rawData.rails;
	this.terrain = rawData.terrain;
	this.cities = rawData.cities;

	if (!this.rivers)
		this.rivers = {};
	if (!this.rails)
		this.rails = {};
	if (!this.cities)
		this.cities = {};
}

Map.prototype.isCity = function(cellIdx)
{
	return this.cities[cellIdx];
}

if (typeof global !== 'undefined')
{
	global.shuffleArray = shuffleArray;
	global.Geometry = Geometry;
	global.Map = Map;
}
