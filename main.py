import json
import webapp2

from google.appengine.ext import ndb


class GameActions(ndb.Model):
  actions = ndb.StringProperty(repeated=True)
  events = ndb.StringProperty(repeated=True)
  rails_json = ndb.StringProperty()


class GameStateHandler(webapp2.RequestHandler):
  def get(self):
    response = {
        "rails": {},
        "map": {},
        "players": {},
    }
    with open('maps/nippon.txt', 'r') as f:
      response['map'] = json.load(f)
    self.response.headers['Content-Type'] = 'application/json'
    self.response.write(json.dumps(response))


class EventsHandler(webapp2.RequestHandler):
  def get(self):
    gamestate_key = ndb.Key(GameActions, 'test')
    ent = gamestate_key.get()
    if ent:
      response = [json.loads(evt) for evt in ent.events]
    else:
      response = []
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
    req = json.loads(self.request.body)
    if 'action' not in req:
      self.error(400)
      self.response.write('missing required field')
      return

    actions_key = ndb.Key(GameActions, 'test')
    def _Update():
      ent = actions_key.get()
      if not ent:
        ent = GameActions(id=actions_key.id(), actions=[])
      ProcessGameAction(ent, req)
      ent.put()

    ndb.transaction(_Update)
    response = {}
    self.response.headers['Content-Type'] = 'application/json'
    self.response.write(json.dumps(response))


app = webapp2.WSGIApplication([
    ('/api/gamestate', GameStateHandler),
    ('/api/actions', ActionsHandler),
    ('/api/events', EventsHandler),
], debug=True)
