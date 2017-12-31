const INITIAL_DEMAND_COUNT = 12;

function GameState() {
  // List of demands not yet in play.
  // Each demand is [cityId, resourceType, payOut].
  this.futureDemands = [];

  // List of demands completed or abandoned.
  // Same structure as futureDemands.
  this.pastDemands = [];

  // Players, keyed by playerId.
  // Each Player is {
  //   money: int
  //   demands: list of Demand
  // }.
  this.players = {};

  // Trains, keyed by trainId.
  // Each Train is {
  //   trainId: int
  //   loc: cell index
  //   plan: list of Waypoint
  //   route: list
  //   revenue: int
  //   running: bool
  //   curWaypoint: int
  //   orientationName: 'W', 'N', 'NNE', etc.
  // }.
  // Each Waypoint is {
  //   id: int, starting from 0
  //   location: cell index
  //   deliver: optional list of str
  //   pickup: optional list of str
  //   distanceHint: int, number of steps
  // }.
  // When a waypoint is reached, it is removed from plan[].
  this.trains = {};
}

/** Called when a new player is added to the game. */
GameState.prototype.newPlayer = function(pid, p) {
  p.demands = [];
  p.money = 50;
  this.players[pid] = p;

  for (var i = 0; i < INITIAL_DEMAND_COUNT; i++) {
    this.nextDemand(pid);
  }
};

GameState.prototype.nextDemand = function(pid) {

  if (this.futureDemands.length == 0) {
    this.futureDemands = this.pastDemands;
    this.pastDemands = [];
  }

  if (this.futureDemands.length > 0) {
    var d = this.futureDemands.shift();
    this.players[pid].demands.push(d);
  }
}

GameState.prototype.hasTrain = function(trainId) {
  return trainId in this.trains;
};
