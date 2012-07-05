var fs = require('fs');

function pruneStruct(fleet)
{
	var rv = {};
	for (var k in fleet)
	{
		if (k.substr(0,1)!='_')
			rv[k] = fleet[k];
	}
	return rv;
}

function saveWorld(G)
{
	var filename = G.worldName + '/world.txt';
	var newRealWorldTime = new Date().getTime();
	G.world.age += (newRealWorldTime - G.world.realWorldTime) / G.world.oneYear;
	G.world.realWorldTime = newRealWorldTime;
	fs.writeFileSync(filename+'.tmp', JSON.stringify(G.world));

	filename = G.worldName + '/terrain.txt';
	var _terrain = {
		cells: {},
		edges: G.terrain.edges,
		vertices: G.terrain.vertices,
		size: G.terrain.size,
		geometry: G.terrain.geometry.name
		};
	for (var cid in G.terrain.cells)
	{
		_terrain.cells[cid] = pruneStruct(G.terrain.cells[cid]);
	}
	fs.writeFileSync(filename+'.tmp', JSON.stringify(_terrain));

	filename = G.worldName + '/players.txt';
	fs.writeFileSync(filename+'.tmp', JSON.stringify(G.players));

	filename = G.worldName + '/maps.txt';
	fs.writeFileSync(filename+'.tmp', JSON.stringify(G.maps));

	filename = G.worldName + '/fleets.txt';
	var _fleets = {};
	for (var fid in G.fleets)
	{
		_fleets[fid] = pruneStruct(G.fleets[fid]);
	}
	fs.writeFileSync(filename+'.tmp', JSON.stringify(_fleets));

	filename = G.worldName + '/cities.txt';
	var _cities = {};
	for (var tid in G.cities)
	{
		_cities[tid] = pruneStruct(G.cities[tid]);
	}
	fs.writeFileSync(filename+'.tmp', JSON.stringify(_cities));

	var allFiles = ['world.txt','terrain.txt','players.txt','maps.txt','fleets.txt','cities.txt'];
	for (var i = 0; i < allFiles.length; i++)
	{
		var filename = G.worldName + '/' + allFiles[i];
		fs.renameSync(filename+'.tmp', filename);
	}
	console.log('Saved world '+G.worldName);
}

global.saveWorld = saveWorld;
