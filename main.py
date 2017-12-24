import json
import webapp2


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


app = webapp2.WSGIApplication([
    ('/api/gamestate', GameStateHandler),
], debug=True)
