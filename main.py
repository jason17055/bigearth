import json
import webapp2

from google.appengine.ext import ndb


class GameActions(ndb.Model):
  foo = ndb.StringProperty()
  actions = ndb.StringProperty(repeated=True)


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
      ent.actions.append(json.dumps(req))
      ent.put()

    ndb.transaction(_Update)
    response = {}
    self.response.headers['Content-Type'] = 'application/json'
    self.response.write(json.dumps(response))


app = webapp2.WSGIApplication([
    ('/api/gamestate', GameStateHandler),
    ('/api/actions', ActionsHandler),
], debug=True)
