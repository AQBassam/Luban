"""The data server with every upstream source faked: validation, caching, stale-if-error, host allowlist."""
import json
import sys
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "server"))
import app  # noqa: E402

REAL_FETCH = app.fetch_json

CALLS = []
FAIL = {"on": False}


def fake_fetch(url, timeout=12):
    CALLS.append(url)
    if FAIL["on"]:
        raise OSError("upstream down")
    if "coingecko" in url:
        return {"bitcoin": {"usd": 100000, "usd_24h_change": -1.5}}
    if "finance.yahoo" in url:
        return {"chart": {"result": [{"meta": {"regularMarketPrice": 110.0, "chartPreviousClose": 100.0}}]}}
    if "eventsnext" in url:
        return {"events": [{"idEvent": "1", "strHomeTeam": "Al Seeb", "strAwayTeam": "Dhofar", "secret": "x"}]}
    if "eventslast" in url:
        return {"results": []}
    if "searchteams" in url:
        return {"teams": [{"idTeam": "9", "strTeam": "Al Seeb", "strSport": "Soccer", "strLeague": "Oman Professional League"},
                          {"idTeam": "8", "strTeam": "Seeb Handball", "strSport": "Handball"}]}
    if "tvmaze" in url:
        return [{"season": 1, "number": 2, "airdate": "2026-10-21",
                 "_embedded": {"show": {"id": 5, "name": "A", "type": "Scripted", "genres": ["Drama"], "language": "English", "rating": {"average": 8.1}}}}]
    raise AssertionError(url)


class ServerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        app.fetch_json = fake_fetch
        cls.srv = ThreadingHTTPServer(("127.0.0.1", 0), app.Handler)
        cls.base = f"http://127.0.0.1:{cls.srv.server_address[1]}"
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown()

    def get(self, path):
        try:
            with urllib.request.urlopen(self.base + path, timeout=5) as r:
                return r.status, json.loads(r.read()), r.headers
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read()), e.headers

    def test_markets_and_cache(self):
        CALLS.clear()
        code, body, headers = self.get("/v1/markets?ids=gold,btc,evil")
        self.assertEqual(code, 200)
        self.assertEqual(set(body), {"gold", "btc"})
        self.assertAlmostEqual(body["gold"]["change"], 10.0)
        self.assertEqual(headers["Access-Control-Allow-Origin"], "*")
        n = len(CALLS)
        self.get("/v1/markets?ids=gold,btc")
        self.assertEqual(len(CALLS), n, "second request must come from cache")

    def test_stale_if_error(self):
        self.get("/v1/matches?teams=77")
        app.CACHE.data["t:77"] = (0.0 + 1e-9 + __import__("time").time() - 700, app.CACHE.data["t:77"][1])  # expired
        FAIL["on"] = True
        try:
            code, body, _ = self.get("/v1/matches?teams=77")
        finally:
            FAIL["on"] = False
        self.assertEqual(code, 200)
        self.assertEqual(body["77"]["next"][0]["strHomeTeam"], "Al Seeb")
        self.assertNotIn("secret", body["77"]["next"][0], "only known fields are passed on")

    def test_validation(self):
        CALLS.clear()
        self.assertEqual(self.get("/v1/matches?teams=1;rm,abc")[1], {})
        self.assertEqual(self.get("/v1/teams?q=<script>")[0], 400)
        self.assertEqual(self.get("/nope")[0], 404)
        self.assertEqual(CALLS, [], "invalid input never reaches an upstream")

    def test_team_search_soccer_only(self):
        code, body, _ = self.get("/v1/teams?q=seeb")
        self.assertEqual([t["id"] for t in body], ["9"])

    def test_shows(self):
        code, body, _ = self.get("/v1/shows?genres=Drama&min=8")
        self.assertEqual(body[0]["name"], "A")
        self.assertEqual(self.get("/v1/shows?genres=Comedy")[1], [])

    def test_host_allowlist(self):
        for url in ("http://192.168.1.1/admin", "https://evil.example.com/x", "file:///etc/passwd"):
            with self.assertRaises(ValueError):
                REAL_FETCH(url)


if __name__ == "__main__":
    unittest.main(verbosity=1)
