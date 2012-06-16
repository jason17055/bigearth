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
			var bx = 2 + 10 * (size + 1) * (size + 1);
			var rx = this.numRows - row;
			var b = bx - 5 * (rx) * (rx + 1) / 2;
			return b + idx;
		}
		else if (row == this.numRows)
		{
			return 2 + 10 * (size + 1) * (size + 1);		
		}
		else
		{
			return 0;
		}
	};
}

SphereGeometry.prototype.getNeighbors = function(cellIdx)
{
	var geometry = this;
	var sz = geometry.size;
	if (sz == 0)
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

	var ri = geometry.getRowIdx(cellIdx);
	if (ri.row == 1)
	{
		return [ 2, 3, 4, 5, 6];
	}
	else if (ri.row < sz + 2)
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
		else if (ri.idx % (ri.row-1) == 0)
		{
			var pri = ri.idx / (ri.row-1); //prev row index
			return [
			geometry.getCell(ri.row-1, ri.idx - pri),
			geometry.getCell(ri.row, (ri.idx+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row+1, (ri.idx+pri+sizeNextRow-1)%sizeNextRow),
			geometry.getCell(ri.row+1, (ri.idx+pri)%sizeNextRow),
			geometry.getCell(ri.row+1, (ri.idx+pri+1)%sizeNextRow),
			geometry.getCell(ri.row, (ri.idx+1)%sizeThisRow)
			];
		}
		else
		{
			var pri = Math.floor(ri.idx / (ri.row-1));
			return [
			geometry.getCell(ri.row-1, ri.idx-pri-1),
			geometry.getCell(ri.row, (ri.idx+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row+1, (ri.idx+pri)%sizeNextRow),
			geometry.getCell(ri.row+1, (ri.idx+pri+1)%sizeNextRow),
			geometry.getCell(ri.row, (ri.idx+1)%sizeThisRow),
			geometry.getCell(ri.row-1, (ri.idx-pri)%sizePrevRow)
			];
		}
	}
	else if (ri.row == sz + 2)
	{
		var sizeThisRow = (sz+1)*5;
		var sizePrevRow = sizeThisRow - 5;

		if (ri.idx % (sz+1) == 0)
		{
			// special cell
			var pri = Math.floor(ri.idx / (sz+1));
			return [
			geometry.getCell(ri.row-1, ri.idx-pri),
			geometry.getCell(ri.row, (ri.idx+1)%sizeThisRow),
			geometry.getCell(ri.row+1, ri.idx),
			geometry.getCell(ri.row+1, (ri.idx+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row, (ri.idx+sizeThisRow-1)%sizeThisRow)
			];
		}
		else
		{
			var pri = Math.floor(ri.idx / (sz+1));
			return [
			geometry.getCell(ri.row-1, ri.idx-pri-1),
			geometry.getCell(ri.row, (ri.idx+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row+1, (ri.idx+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row+1, (ri.idx)%sizeThisRow),
			geometry.getCell(ri.row, (ri.idx+1)%sizeThisRow),
			geometry.getCell(ri.row-1, (ri.idx-pri)%sizePrevRow)
			];
		}
	}
	else if (ri.row < sz * 2 + 3)
	{
		var sizeThisRow = (sz+1)*5;
		var rx = ri.row - (sz+2);

		if (rx % 2 == 1)
		{
			return [
			geometry.getCell(ri.row-1, ri.idx),
			geometry.getCell(ri.row, (ri.idx+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row+1, ri.idx),
			geometry.getCell(ri.row+1, (ri.idx+1)%sizeThisRow),
			geometry.getCell(ri.row, (ri.idx+1)%sizeThisRow),
			geometry.getCell(ri.row-1, (ri.idx+1)%sizeThisRow)
			];
		}
		else
		{
			return [
			geometry.getCell(ri.row-1, (ri.idx+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row, (ri.idx+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row+1, (ri.idx+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row+1, (ri.idx)%sizeThisRow),
			geometry.getCell(ri.row, (ri.idx+1)%sizeThisRow),
			geometry.getCell(ri.row-1, (ri.idx)%sizeThisRow)
			];
		}
	}
	else if (ri.row == sz * 2 + 3)
	{
		var sizeThisRow = (sz+1)*5;
		var sizeNextRow = sizeThisRow - 5;
		var rx = ri.row - (sz+2);
		var rm = (rx % 2 == 1 ? 1 : 0);

		if (ri.idx % (sz+1) == 0)
		{
			// special cell
			var pri = Math.floor(ri.idx / (sz+1));
			return [
			geometry.getCell(ri.row-1, (ri.idx+rm+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row, (ri.idx+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row+1, ri.idx-pri),
			geometry.getCell(ri.row, (ri.idx+1)%sizeThisRow),
			geometry.getCell(ri.row-1, (ri.idx+rm)%sizeThisRow)
			];
		}
		else
		{
			var pri = Math.floor(ri.idx / (sz+1));
			return [
			geometry.getCell(ri.row-1, (ri.idx+rm+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row, (ri.idx+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row+1, ri.idx-pri-1),
			geometry.getCell(ri.row+1, (ri.idx-pri)%sizeNextRow),
			geometry.getCell(ri.row, (ri.idx+1)%sizeThisRow),
			geometry.getCell(ri.row-1, (ri.idx+rm)%sizeThisRow)
			];
		}
	}
	else if (ri.row < sz * 3 + 4)
	{
		var rx = sz * 3 + 4 - ri.row;
		var sizePrevRow = (rx-1)*5;
		var sizeThisRow = (rx)*5;
		var sizeNextRow = (rx+1)*5;

		var bx = 2 + 10 * (sz + 1) * (sz + 1);
		if (rx == 1)
		{
			return [
			geometry.getCell(ri.row-1, (ri.idx*2)%sizeNextRow),
			geometry.getCell(ri.row-1, (ri.idx*2+sizeNextRow-1)%sizeNextRow),
			geometry.getCell(ri.row, (ri.idx+sizeThisRow-1)%sizeThisRow),
			bx,
			geometry.getCell(ri.row, (ri.idx+1)%sizeThisRow),
			geometry.getCell(ri.row-1, (ri.idx*2+1)%sizeNextRow)
			];
		}
		else if (ri.idx % (rx) == 0)
		{
			var pri = ri.idx / (rx); //prev row index
			return [
			geometry.getCell(ri.row-1, (ri.idx+pri)%sizeNextRow),
			geometry.getCell(ri.row-1, (ri.idx+pri+sizeNextRow-1)%sizeNextRow),
			geometry.getCell(ri.row, (ri.idx+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row+1, ri.idx - pri),
			geometry.getCell(ri.row, (ri.idx+1)%sizeThisRow),
			geometry.getCell(ri.row-1, (ri.idx+pri+1)%sizeNextRow)
			];
		}
		else
		{
			var pri = Math.floor(ri.idx / (rx));
			return [
			geometry.getCell(ri.row-1, (ri.idx+pri)%sizeNextRow),
			geometry.getCell(ri.row, (ri.idx+sizeThisRow-1)%sizeThisRow),
			geometry.getCell(ri.row+1, ri.idx-pri-1),
			geometry.getCell(ri.row+1, (ri.idx-pri)%sizePrevRow),
			geometry.getCell(ri.row, (ri.idx+1)%sizeThisRow),
			geometry.getCell(ri.row-1, (ri.idx+pri+1)%sizeNextRow)
			];
		}
	}
	else
	{
		var bx = 2 + 10 * (sz + 1) * (sz + 1);
		return [
			bx-5, bx-4, bx-3, bx-2, bx-1
			];
	}
};
