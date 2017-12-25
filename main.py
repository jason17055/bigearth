import json
import webapp2

from google.appengine.ext import ndb


class GameActions(ndb.Model):
  actions = ndb.StringProperty(repeated=True)
  events = ndb.StringProperty(repeated=True)
  map_json = ndb.TextProperty()
  rails_json = ndb.TextProperty()


class GameStateHandler(webapp2.RequestHandler):
  def get(self):
    game_id = self.request.get('game')
    gamestate_key = ndb.Key(GameActions, game_id)
    def _Fetch():
      ent = gamestate_key.get()
      if not ent:
        ent = GameActions(id=gamestate_key.id())
        with open('maps/nippon.txt', 'r') as f:
          ent.map_json = json.dumps(json.load(f))
        ent.put()
      return ent

    ent = ndb.transaction(_Fetch)
    response = {
        "rails": {},
        "map": json.loads(ent.map_json),
        "players": {},
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


app = webapp2.WSGIApplication([
    ('/api/gamestate', GameStateHandler),
    ('/api/actions', ActionsHandler),
    ('/api/events', EventsHandler),
], debug=True)