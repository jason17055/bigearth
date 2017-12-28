/** @typedef {
 *    name: {string}
 *    offers: {Array<string>}
 *  }
 */
var CityInfo;

function MapData() {
  /** @type {Array<string>} one str per row, each str one character per cell. */
  this.terrain = ['.'];

  /** @type {Object<int,string>} maps edge numbers to owners (player ids). */
  this.rails = {};

  /** @type {Object<int,bool>} indicates edges that have rivers. */
  this.rivers = {};

  /** @type {Object<int,CityInfo>} maps cell numbers to cities. */
  this.cities = {};
}

MapData.initialize = function(mapData) {
  if (!mapData.terrain) {
    alert('Oops; map does not have terrain.');
    return null;
  }
  let me = new MapData();
  me.terrain = mapData.terrain;
  me.rails = mapData.rails || {};
  me.rivers = mapData.rivers || {};
  return me;
};

function Painter(canvas, ctx, mapData, mapFeatures) {
  this.canvas = canvas;
  this.ctx = ctx;
  this.mapData = mapData;
  this.mapFeatures = mapFeatures;
}

Painter.prototype.cityVisible = function(cityId) {
  return !this.mapFeatures.filterCities ||
      this.mapFeatures.filterCities[cityId];
}

Painter.prototype.cityColor = function(cityId) {
  if (this.mapFeatures.highlightCities
      && this.mapFeatures.highlightCities[cityId]) {
    return "#ffff44";
  }
  else {
    return "#ff4444";
  }
};

Painter.prototype.trackVisible = function(trackId) {
  return !this.mapFeatures.filterTrack ||
      this.mapFeatures.filterTrack[trackId];
};

// pt: the desired *center* point of the dot, in screen coordinates
//
Painter.prototype.drawCityDot = function(pt, cityId) {
  const ctx = this.ctx;

  ctx.fillStyle = this.cityColor(cityId);
  ctx.beginPath();
  ctx.arc(pt.x, pt.y, CELL_HEIGHT * .36, 0, Math.PI * 2, true);
  ctx.closePath();
  ctx.fill();
};

Painter.prototype.paint = function() {

  const ctx = this.ctx;
  const mapData = this.mapData;

  ctx.save();
  ctx.translate(DISPLAY_SETTINGS.offsetX, DISPLAY_SETTINGS.offsetY);
  ctx.scale(DISPLAY_SETTINGS.zoomLevel / CELL_WIDTH, DISPLAY_SETTINGS.zoomLevel / CELL_WIDTH);

	for (var y = 0; y < mapData.terrain.length; y++)
	{
		for (var x = 0; x < mapData.terrain[y].length; x++)
		{
			var c = mapData.terrain[y].charAt(x);
			var w = x > 0 ? mapData.terrain[y].charAt(x-1) : c;
			var n = y > 0 ? mapData.terrain[y-1].charAt(x) : c;
			var nw,ne;
			if (y % 2 == 0)
			{
				nw = n;
				ne = y > 0 && x+1 < mapData.terrain[y-1].length ?
					mapData.terrain[y-1].charAt(x+1) : n;
			}
			else
			{
				nw = y > 0 && x > 0 ? mapData.terrain[y-1].charAt(x-1) : n;
				ne = n;
			}

			var cellIdx = GEOMETRY.getCell(y,x);
			var pt = getCellPoint(cellIdx);

			this.drawCell(pt, c, w, nw, ne);
			this.drawRivers(pt, cellIdx);

			if (mapData.cities[cellIdx] && this.cityVisible(cellIdx))
			{
				this.drawCityDot(pt, cellIdx);
			}

			this.drawRails(pt, cellIdx);
		}
	}

  ctx.fillStyle = "#333333";
  ctx.font = DISPLAY_SETTINGS.zoomLevel >= 24 ?
      "30px sans-serif" :
      "40px sans-serif";

	for (var cityLoc in mapData.cities)
	{
		if (this.cityVisible(cityLoc))
		{
			var cityName = mapData.cities[cityLoc].name;
			var p = getCellPoint(cityLoc);
			ctx.fillText(cityName,
			p.x + Math.round(CELL_HEIGHT*.36)-2,
			p.y + CELL_ASCENT / 2 - 5);

			var xx = p.x + Math.round(CELL_HEIGHT*.36)-2;
			for (var o in mapData.cities[cityLoc].offers)
			{
				var resourceType = mapData.cities[cityLoc].offers[o];
				if (resourceImages[resourceType])
				{
					ctx.drawImage(resourceImages[resourceType],
					xx,
					p.y + CELL_ASCENT / 2,
					16,16);
					xx += 16;
				}
				else
				{
					loadResourceImage(resourceType);
				}
			}
		}
	}

  ctx.restore();
};

Painter.prototype.drawCell = function(pt, c, w, nw, ne) {
  const ctx = this.ctx;

	var getColor = function(cc)
	{
		if (this.mapFeatures.hideTerrain)
		{
			return cc == "w" ? "#1155ff" : "#ffffff";
		}

		return cc == "." ? "#99dd55" :
			cc == "M" ? "#884400" :
			cc == "w" ? "#1155ff" :
			"#ffffff";
	};

	ctx.fillStyle = getColor(c);
	if (c != nw || c != ne)
	{
		ctx.beginPath();
		ctx.moveTo(pt.x - CELL_WIDTH / 2, pt.y - CELL_ASCENT / 2);
		ctx.lineTo(pt.x, pt.y - CELL_DESCENT - CELL_ASCENT / 2);
		ctx.lineTo(pt.x + CELL_WIDTH / 2, pt.y - CELL_ASCENT / 2);
		ctx.lineTo(pt.x + CELL_WIDTH / 2, pt.y + CELL_HEIGHT + 1 - CELL_ASCENT / 2);
		ctx.lineTo(pt.x - CELL_WIDTH / 2, pt.y + CELL_HEIGHT + 1 - CELL_ASCENT / 2);
		ctx.closePath();
		ctx.fill();
	}
	else
	{
		ctx.fillRect(
			pt.x - CELL_WIDTH / 2, pt.y - CELL_ASCENT / 2,
			CELL_WIDTH+1, CELL_HEIGHT+1
			);
	}

	if (c == "M" && terrainImages.mountain && !this.mapFeatures.hideTerrain
		&& DISPLAY_SETTINGS.zoomLevel >= 24)
	{
		var imageSize = CELL_WIDTH * .8;
		ctx.drawImage(terrainImages.mountain,
			pt.x - imageSize/2,
			pt.y - imageSize/2,
			imageSize, imageSize);
	}
};

Painter.prototype.drawRivers = function(pt, cellIdx) {
  const ctx = this.ctx;
  const mapData = this.mapData;

	ctx.save();
	ctx.strokeStyle = '#1155ff';
	ctx.lineWidth = 3;

	var drawRiverHelper = function()
	{
		ctx.beginPath();
		ctx.moveTo(0, -CELL_ASCENT / 2);
		ctx.lineTo(0, CELL_ASCENT / 2);
		ctx.stroke();
	};

	var t;
	if (t = mapData.rivers[cellIdx * 3]) //west
	{
		ctx.save();
		ctx.translate(pt.x - CELL_WIDTH/2, pt.y);
		drawRiverHelper(t);
		ctx.restore();
	}
	if (t = mapData.rivers[cellIdx * 3 + 1]) //northwest
	{
		ctx.save();
		ctx.translate(pt.x - CELL_WIDTH/2 + CELL_WIDTH / 4, pt.y - CELL_ASCENT / 2 - CELL_DESCENT / 2);
		ctx.rotate(Math.PI / 3);
		drawRiverHelper(t);
		ctx.restore();
	}
	if (t = mapData.rivers[cellIdx * 3 + 2]) //northeast
	{
		ctx.save();
		ctx.translate(pt.x - CELL_WIDTH/2 + 3 * CELL_WIDTH / 4, pt.y - CELL_ASCENT / 2 - CELL_DESCENT / 2);
		ctx.rotate(Math.PI * 2 / 3);
		drawRiverHelper(t);
		ctx.restore();
	}

	ctx.restore();
};

Painter.prototype.drawRailsHelper = function(ctx, owner) {
	var RAIL_WIDTH = CELL_WIDTH / 16;
	var TIE_LENGTH = CELL_WIDTH / 10;

	if (owner == 2)
	{
		ctx.strokeStyle = "#099";
		ctx.lineWidth = 2;
	}
	else if (owner == 3)
	{
		ctx.strokeStyle = '#f4c';
		ctx.lineWidth = 1;
	}
	else
	{
		ctx.strokeStyle = "#000000";
		ctx.lineWidth = 1;
	}

	if (DISPLAY_SETTINGS.zoomLevel < 20)
	{
		ctx.lineWidth = 1.2*RAIL_WIDTH;
		ctx.beginPath();
		ctx.moveTo(-CELL_WIDTH / 2, 0);
		ctx.lineTo(CELL_WIDTH / 2, 0);
		ctx.stroke();
		return;
	}

	ctx.beginPath();
	ctx.moveTo(-CELL_WIDTH / 2, -RAIL_WIDTH);
	ctx.lineTo(CELL_WIDTH / 2, -RAIL_WIDTH);
	ctx.stroke();
	ctx.beginPath();
	ctx.moveTo(-CELL_WIDTH / 2, RAIL_WIDTH);
	ctx.lineTo(CELL_WIDTH / 2, RAIL_WIDTH);
	ctx.stroke();
	for (var i = -2; i <= 2; i++)
	{
		ctx.beginPath();
		ctx.moveTo(CELL_WIDTH * i / 5, -TIE_LENGTH);
		ctx.lineTo(CELL_WIDTH * i / 5, TIE_LENGTH);
		ctx.stroke();
	}
};

Painter.prototype.drawRails = function(pt, cellIdx) {
  const ctx = this.ctx;

	var t;
	if (t = hasTrackAtDir(cellIdx, 0)) //West
	{
		if (this.trackVisible(GEOMETRY.getTrackIndex(cellIdx, 0)))
		{
		ctx.save();
		ctx.translate(pt.x - CELL_WIDTH / 2, pt.y);
		this.drawRailsHelper(ctx, t);
		ctx.restore();
		}
	}
	if (t = hasTrackAtDir(cellIdx, 1)) //Northwest
	{
		if (this.trackVisible(GEOMETRY.getTrackIndex(cellIdx, 1)))
		{
		ctx.save();
		ctx.translate(pt.x - CELL_WIDTH / 2 + CELL_WIDTH / 4, pt.y - CELL_ASCENT / 2 - CELL_DESCENT / 2);
		ctx.rotate(Math.PI / 3);
		this.drawRailsHelper(ctx, t);
		ctx.restore();
		}
	}
	if (t = hasTrackAtDir(cellIdx, 2)) //Northeast
	{
		if (this.trackVisible(GEOMETRY.getTrackIndex(cellIdx, 2)))
		{
		ctx.save();
		ctx.translate(pt.x - CELL_WIDTH / 2 + 3 * CELL_WIDTH / 4, pt.y - CELL_ASCENT / 2 - CELL_DESCENT / 2);
		ctx.rotate(Math.PI * 2 / 3);
		this.drawRailsHelper(ctx, t);
		ctx.restore();
		}
	}
};

