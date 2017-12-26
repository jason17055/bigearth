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
