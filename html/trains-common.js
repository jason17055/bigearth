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

function loadGeometry(width, height)
{
	CELLS_PER_ROW = width;
}

function getCellRow(cellIdx)
{
	return Math.floor(cellIdx / CELLS_PER_ROW);
}

function getCellColumn(cellIdx)
{
	return cellIdx % CELLS_PER_ROW;
}

function getCell(row, column)
{
	return row * CELLS_PER_ROW + column;
}

function isCellAdjacent(cell1, cell2)
{
	return cell2 == getAdjacentW(cell1) ||
		cell2 == getAdjacentNW(cell1) ||
		cell2 == getAdjacentNE(cell1) ||
		cell2 == getAdjacentE(cell1) ||
		cell2 == getAdjacentSE(cell1) ||
		cell2 == getAdjacentSW(cell1);
}

function getAdjacentCell(cellIdx, dir)
{
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
}

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

// dir: 0 == west, 1 == northwest, 2 == northeast,
//      3 == east, 4 == southeast, 5 == southwest
//
function hasTrackAtDir(cellIdx, dir)
{
	var trackIdx = getTrackIndex(cellIdx, dir);
	if (mapData.rails[trackIdx] && mapData.rails[trackIdx] == getPlayerId())
		return 1;
	else if (isBuilding && isBuilding.rails[trackIdx])
		return 2;
	else if (mapData.rails[trackIdx])
		return 3;
	else
		return null;
}

function getTrackIndex(cellIdx, dir)
{
	if (dir == 3)
	{
		return getTrackIndex(getAdjacentE(cellIdx), 0);
	}
	else if (dir == 4)
	{
		return getTrackIndex(getAdjacentSE(cellIdx), 1);
	}
	else if (dir == 5)
	{
		return getTrackIndex(getAdjacentSW(cellIdx), 2);
	}
	else
	{
		return cellIdx * 3 + (dir + 1);
	}
}

function hasTrackAt(cellIdx)
{
	return hasTrackAtDir(cellIdx, 0) ||
		hasTrackAtDir(cellIdx, 1) ||
		hasTrackAtDir(cellIdx, 2) ||
		hasTrackAtDir(cellIdx, 3) ||
		hasTrackAtDir(cellIdx, 4) ||
		hasTrackAtDir(cellIdx, 5);
}

function simpleDistance(cellIdx1, cellIdx2)
{
	var row1 = getCellRow(cellIdx1);
	var col1 = getCellColumn(cellIdx1);
	var row2 = getCellRow(cellIdx2);
	var col2 = getCellColumn(cellIdx2);

	var distRows = Math.abs(row2-row1);
	var distCols = Math.abs(col2-col1);
	var diag = Math.floor(distRows / 2);
	return distRows + (distCols > diag ? distCols - diag : 0);
}

if (typeof global !== 'undefined')
{
	global.shuffleArray = shuffleArray;
	global.simpleDistance = simpleDistance;
	global.loadGeometry = loadGeometry;
}
