function Location()
{
}

Location.fromCellId = function(cellId)
{
	return cellId;
};

Location.fromEdgeId = function(edgeId)
{
	return edgeId;
};

Location.toCellId = function(l)
{
	return l;
};

Location.toPoint = function(l)
{
	return coords.cells[Location.toCellId(l)].pt;
};

Location.isCell = function(l)
{
	if ((""+l).match(/^\d+$/))
		return true;
	else
		return false;
};

Location.isEdge = function(l)
{
	if ((""+l).match(/^(\d+)-(\d+)$/))
		return true;
	else
		return false;
};

Location.toEdgeId = function(l)
{
	return l;
};

if (typeof module !== 'undefined')
{
	module.exports = Location;
}
