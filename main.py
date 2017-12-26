import collections
import json
import random
import webapp2

from google.appengine.ext import ndb


ALL_COMMODITIES = [
    "beer",
    "cars",
    "cattle",
    "chemicals",
    "china",
    "coal",
    "copper",
    "cotton",
    "electronics",
    "fish",
    "fruit",
    "furniture",
    "gold",
    "imports",
    "iron",
    "machinery",
    "oats",
    "paper",
    "passengers",
    "plastics",
    "rice",
    "sake",
    "silk",
    "steel",
    "tea",
    "tobacco",
    "wine",
    "wood",
];

class GameActions(ndb.Model):
  actions = ndb.StringProperty(repeated=True)
  events = ndb.StringProperty(repeated=True)
  map_json = ndb.TextProperty()
  rails_json = ndb.TextProperty()
  players_json = ndb.TextProperty()
  all_demands = ndb.StringProperty(repeated=True)


class Map(ndb.Model):
  terrain_json = ndb.TextProperty()
  cities_json = ndb.TextProperty()
  rivers_json = ndb.TextProperty()
  geometry = ndb.StringProperty()


class MapNotFound(Exception):
  pass


def GetMap(map_name):
  map_key = ndb.Key(Map, map_name)
  ent = map_key.get()
  if not ent:
    raise MapNotFound('%s: not found' % map_name)

  map_data = {
      'terrain': json.loads(ent.terrain_json),
      'cities': json.loads(ent.cities_json),
      'rivers': json.loads(ent.rivers_json),
      'geometry': ent.geometry,
  }
  return map_data


def InitializeGame(ent, map_data):
  ent.map_json = json.dumps(map_data)
  ent.players_json = json.dumps({})

  # Create demands.
  all_resource_types = collections.defaultdict(set)
  for city_id, city in map_data['cities'].items():
    for resource_type in city.get('offers', []):
      all_resource_types[resource_type].add(city_id)

  all_demands = []
  for city_id, city in map_data['cities'].items():
    here = set(city.get('offers', []))
    for rt in all_resource_types.keys():
      if rt not in here:
        all_demands.append('%s:%s:%d' % (city_id, rt, 10))
  random.shuffle(all_demands)
  ent.all_demands = all_demands


class GameStateHandler(webapp2.RequestHandler):
  def get(self):
    game_id = self.request.get('game')
    gamestate_key = ndb.Key(GameActions, game_id)
    map_data = GetMap('nippon')
    def _Fetch():
      ent = gamestate_key.get()
      if not ent:
        ent = GameActions(id=gamestate_key.id())
        InitializeGame(ent, map_data)
        ent.put()
      return ent

    ent = ndb.transaction(_Fetch)
    response = {
        "rails": {},
        "map": json.loads(ent.map_json),
        "players": json.loads(ent.players_json or '{}'),
        "allServerResourceTypes": ALL_COMMODITIES,
    }
    self.response.headers['Content-Type'] = 'application/json'
    self.response.write(json.dumps(response))


class EventsHandler(webapp2.RequestHandler):
  def get(self):
    game_id = self.request.get('game')
    gamestate_key = ndb.Key(GameActions, game_id)
    ent = gamestate_key.get()
    if ent:
      response = [json.loads(evt) for evt in ent.events]
    else:
      self.error(404)
      self.response.write('Not found')
      return
    self.response.headers['Content-Type'] = 'application/json'
    self.response.write(json.dumps(response))


def ProcessGameAction(game_ent, action_req):
  game_ent.actions.append(json.dumps(action_req))
  if action_req['action'] == 'build':
    rails_data = {}
    if game_ent.rails_json:
      rails_data = json.loads(game_ent.rails_json)
    changes = {}
    for i in action_req['rails'].split(' '):
      rails_data[int(i)] = action_req['player']
      changes[int(i)] = action_req['player']
    game_ent.rails_json = json.dumps(rails_data)
    event = {
      'event': 'track-built',
      'rails': changes,
      'playerMoney': 0,
    }
    game_ent.events.append(json.dumps(event))


class ActionsHandler(webapp2.RequestHandler):
  def post(self):
    game_id = self.request.get('game')
    req = json.loads(self.request.body)
    if 'action' not in req:
      self.error(400)
      self.response.write('missing required field')
      return

    class EntityNotFound(Exception):
      pass

    actions_key = ndb.Key(GameActions, game_id)
    def _Update():
      ent = actions_key.get()
      if not ent:
        raise EntityNotFound()
      ProcessGameAction(ent, req)
      ent.put()

    try:
      ndb.transaction(_Update)
    except EntityNotFound:
      self.error(404)
      self.response.write('Not found')
      return

    response = {}
    self.response.headers['Content-Type'] = 'application/json'
    self.response.write(json.dumps(response))


class EditMapHandler(webapp2.RequestHandler):
  def get(self):
    map_name = self.request.get('map')
    try:
      map_data = GetMap(map_name)
      self.response.headers['Content-Type'] = 'application/json'
      self.response.write(json.dumps(map_data))
    except MapNotFound:
      self.error(404)
      self.response.write('Not found')

  def put(self):
    map_name = self.request.get('map')
    req = json.loads(self.request.body)
    if not map_name:
      self.error(400)
      self.response.write('Invalid request')
      return

    map_key = ndb.Key(Map, map_name)
    def _Update():
      ent = map_key.get()
      if not ent:
        ent = Map(id=map_key.id())
      ent.terrain_json = json.dumps(req['terrain'])
      ent.cities_json = json.dumps(req['cities'])
      ent.rivers_json = json.dumps(req['rivers'])
      ent.geometry = req.get('geometry', 'hex_horz')
      ent.put()

    ndb.transaction(_Update)
    response = {}

    self.response.headers['Content-Type'] = 'application/json'
    self.response.write(json.dumps(response))


class LoginHandler(webapp2.RequestHandler):
  def post(self):
    req = json.loads(self.request.body)
    if 'game' not in req or 'name' not in req:
      self.error(400)
      self.response.write('missing required field')
      return

    game_id = req['game']
    gamestate_key = ndb.Key(GameActions, game_id)
    def _Update():
      gamestate = gamestate_key.get()
      players = json.loads(gamestate.players_json or '{}')
      max_pid = 0
      for pid, player in players.items():
        if int(pid) > max_pid:
          max_pid = int(pid)
        if player['identity'] == req['name']:
          return gamestate, pid, player
      new_pid = str(max_pid + 1)
      players[new_pid] = {'name': req['name'], 'money': 0}
      gamestate.players_json = json.dumps(players)
      gamestate.put()
      return gamestate, new_pid, players[new_pid]

    def _MakeDemand(demand_str):
      city_id, resource_type, value = demand_str.split(':')
      return [city_id, resource_type, value]

    ent, pid, p = ndb.transaction(_Update)
    response = {
        'playerId': 1,
        'player': {
            'identity': p['name'],
            'demands': [_MakeDemand(x) for x in ent.all_demands[:8]],
            'money': p['money'],
        },
    }
    self.response.headers['Content-Type'] = 'application/json'
    self.response.write(json.dumps(response))


app = webapp2.WSGIApplication([
    ('/api/gamestate', GameStateHandler),
    ('/api/actions', ActionsHandler),
    ('/api/events', EventsHandler),
    ('/api/login', LoginHandler),
    ('/api/map', EditMapHandler),
], debug=True)
