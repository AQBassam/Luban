"""The data server with every upstream source faked: validation, caching, stale-if-error, host allowlist, the dinner store."""
import base64
import contextlib
import hashlib
import io
import json
import os
import sys
import tempfile
import threading
import time
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
PLAN = json.loads((Path(__file__).parent / "dinner-fixture.json").read_text(encoding="utf-8"))["share"]


def fake_fetch(url, timeout=12, headers=None):
    CALLS.append(url)
    if FAIL["on"]:
        raise OSError("upstream down")
    if "coingecko" in url:
        return {"bitcoin": {"usd": 100000, "usd_24h_change": -1.5}}
    if "finance.yahoo" in url:
        return {"chart": {"result": [{"meta": {"regularMarketPrice": 110.0, "chartPreviousClose": 100.0}}]}}
    if "football-data.org" in url:
        assert headers and headers.get("X-Auth-Token") == "k", "the token travels in the header"
        if "/matches?" in url:
            team = lambda i, n: {"id": i, "name": n + " FC", "shortName": n}
            return {"matches": [
                {"id": 1, "utcDate": "2026-10-25T19:00:00Z", "status": "TIMED", "homeTeam": team(86, "Real Madrid"), "awayTeam": team(81, "Barça"),
                 "competition": {"name": "Primera Division"}, "score": {"fullTime": {"home": None, "away": None}}, "secret": "x"},
                {"id": 2, "utcDate": "2026-10-19T19:00:00Z", "status": "FINISHED", "homeTeam": team(77, "Athletic"), "awayTeam": team(86, "Real Madrid"),
                 "competition": {"name": "Primera Division"}, "score": {"fullTime": {"home": 1, "away": 2}}},
                {"id": 3, "utcDate": "2026-10-20T19:00:00Z", "status": "POSTPONED", "homeTeam": team(86, "Real Madrid"), "awayTeam": team(5, "X"),
                 "competition": {"name": "Primera Division"}, "score": {"fullTime": {}}}]}
        if url.endswith("/competitions/PD/teams"):
            return {"competition": {"name": "Primera Division"}, "teams": [{"id": 86, "name": "Real Madrid CF", "shortName": "Real Madrid", "tla": "RMA"},
                                                                         {"id": 81, "name": "FC Barcelona", "shortName": "Barça", "tla": "FCB"}]}
        raise OSError("no such competition yet")
    if "tvmaze" in url:
        return [{"season": 1, "number": 2, "airdate": "2026-10-21",
                 "_embedded": {"show": {"id": 5, "name": "A", "type": "Scripted", "genres": ["Drama"], "language": "English", "rating": {"average": 8.1}}}}]
    raise AssertionError(url)


class ServerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        app.fetch_json = fake_fetch
        app.FD_KEY, app.FD_GAP = "k", 0
        cls.tmp = tempfile.TemporaryDirectory()
        app.DINNER_DIR = cls.tmp.name
        cls.srv = ThreadingHTTPServer(("127.0.0.1", 0), app.Handler)
        cls.base = f"http://127.0.0.1:{cls.srv.server_address[1]}"
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown()
        cls.tmp.cleanup()

    def get(self, path, method="GET", body=None, headers=None):
        req = urllib.request.Request(self.base + path, data=body, method=method, headers=headers or {})
        try:
            with urllib.request.urlopen(req, timeout=5) as r:
                return r.status, json.loads(r.read()), r.headers
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read()), e.headers

    def put(self, key, plan, origin=None):
        h = {"Content-Type": "application/json", **({"X-Luban-Key": key} if key else {}), **({"Origin": origin} if origin else {})}
        return self.get("/v1/dinner", "PUT", json.dumps(plan).encode(), h)

    def test_dinner_write_read_delete(self):
        key = "A" * 43
        did = app.dinner_id(key)
        self.assertEqual(len(did), 22)
        self.assertEqual(did, base64.urlsafe_b64encode(hashlib.sha256(key.encode()).digest()).decode()[:22])
        app._dinner_last.clear()
        code, body, h = self.put(key, PLAN, "https://dinner.bassamailab.xyz")
        self.assertEqual((code, body["id"]), (200, did))
        self.assertEqual(h["Access-Control-Allow-Origin"], "https://dinner.bassamailab.xyz")
        code, body, h = self.get("/v1/dinner/" + did)
        self.assertEqual((code, body["plan"]), (200, PLAN))
        self.assertEqual(h["Cache-Control"], "no-store")
        self.assertIsInstance(body["at"], int)
        self.assertEqual(self.put(key, PLAN)[0], 429, "one write every few seconds per plan")
        self.assertEqual(self.get("/v1/dinner", "DELETE", headers={"X-Luban-Key": key})[0], 200)
        self.assertEqual(self.get("/v1/dinner/" + did)[0], 404, "unlinked: the plan is gone")

    def test_dinner_refusals(self):
        app._dinner_last.clear()
        key = "B" * 43
        self.assertEqual(self.put(None, PLAN)[0], 401, "no key")
        self.assertEqual(self.put("short", PLAN)[0], 401, "a key of the wrong shape")
        self.assertEqual(self.put(key, PLAN, "https://evil.example.com")[0], 403, "another site's page may not write")
        self.assertEqual(self.put(key, {**PLAN, "n": 3})[0], 400, "nights and days must agree")
        self.assertEqual(self.put(key, {"v": 2})[0], 400)
        self.assertEqual(self.put(key, {**PLAN, "big": "x" * 70000})[0], 413, "64 KB at most")
        self.assertEqual(self.get("/v1/dinner/../../etc/passwd")[0], 400, "only a 22-character id is read")
        self.assertEqual(self.get("/v1/dinner/" + "C" * 22)[0], 404)
        self.assertEqual(os.listdir(app.DINNER_DIR), [], "nothing refused was stored")

    def test_dinner_preflight_and_old_plans(self):
        req = urllib.request.Request(self.base + "/v1/dinner", method="OPTIONS")
        with urllib.request.urlopen(req, timeout=5) as r:
            self.assertEqual(r.headers["Access-Control-Allow-Origin"], "https://dinner.bassamailab.xyz")
            self.assertIn("X-Luban-Key", r.headers["Access-Control-Allow-Headers"])
        old = os.path.join(app.DINNER_DIR, "D" * 22 + ".json")
        with open(old, "w") as f:
            f.write("{}")
        os.utime(old, (0, 0))
        app._dinner_sweep[0] = 0
        app._dinner_last.clear()
        key = "E" * 43
        self.assertEqual(self.put(key, PLAN)[0], 200)
        self.assertFalse(os.path.exists(old), "a plan not written for 180 days is removed")
        self.get("/v1/dinner", "DELETE", headers={"X-Luban-Key": key})

    def test_dinner_ids_stay_out_of_the_log(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.get("/v1/dinner/" + "F" * 22)
            time.sleep(0.1)
        self.assertNotIn("F" * 22, out.getvalue())
        self.assertIn("/v1/dinner/…", out.getvalue())

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

    def test_matches_one_call_for_every_team(self):
        app.CACHE.data.pop("fd:window", None)
        CALLS.clear()
        code, body, _ = self.get("/v1/matches?teams=86,81,999")
        self.assertEqual(code, 200)
        self.assertEqual(len([c for c in CALLS if "/matches?" in c]), 1, "one upstream call however many teams")
        rm = body["86"]
        self.assertEqual([e["idEvent"] for e in rm["next"]], ["1"])
        self.assertEqual(rm["last"][0]["intHomeScore"], 1)
        self.assertEqual(rm["last"][0]["strStatus"], "FT")
        self.assertEqual(rm["next"][0]["strTimestamp"], "2026-10-25T19:00:00Z")
        self.assertNotIn("3", [e["idEvent"] for e in rm["last"] + rm["next"]], "postponed matches are left out")
        self.assertNotIn("secret", rm["next"][0], "only known fields are passed on")
        self.assertNotIn("999", body)

    def test_stale_if_error(self):
        self.get("/v1/matches?teams=86")
        t, v = app.CACHE.data["fd:window"]
        app.CACHE.data["fd:window"] = (t - 700, v)   # expired
        FAIL["on"] = True
        try:
            code, body, _ = self.get("/v1/matches?teams=86")
        finally:
            FAIL["on"] = False
        self.assertEqual(code, 200)
        self.assertEqual(body["86"]["next"][0]["strHomeTeam"], "Real Madrid")

    def test_one_refresh_at_a_time(self):
        n = {"calls": 0}
        def slow():
            n["calls"] += 1
            __import__("time").sleep(0.2)
            return 1
        ts = [threading.Thread(target=lambda: app.CACHE.get("flight", 60, slow)) for _ in range(8)]
        [t.start() for t in ts]; [t.join() for t in ts]
        self.assertEqual(n["calls"], 1)

    def test_ready_names_states_not_errors(self):
        self.get("/v1/markets?ids=gold")
        code, body, _ = self.get("/ready")
        self.assertEqual(code, 200)
        self.assertEqual(body["sources"]["markets"]["state"], "fresh")
        self.assertIn(body["sources"]["shows"]["state"], ("idle", "fresh"))
        app.STATUS["shows"] = {"fail": __import__("time").time()}
        code, body, _ = self.get("/ready")
        self.assertEqual((code, body["sources"]["shows"]["state"]), (503, "failing"), "failing with no data at all")
        self.assertNotIn("error", json.dumps(body), "no upstream error text is published")
        app.STATUS.pop("shows")

    def test_validation(self):
        CALLS.clear()
        self.assertEqual(self.get("/v1/matches?teams=1;rm,abc")[1], {})
        self.assertEqual(self.get("/v1/teams?q=<script>")[0], 400)
        self.assertEqual(self.get("/nope")[0], 404)
        self.assertEqual(CALLS, [], "invalid input never reaches an upstream")

    def test_team_search(self):
        code, body, _ = self.get("/v1/teams?q=real%20mad")
        self.assertEqual([t["id"] for t in body], ["86"])
        self.assertEqual(body[0]["en"], "Real Madrid")
        self.assertEqual([t["id"] for t in self.get("/v1/teams?q=fcb")[1]], ["81"], "three-letter code works too")

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
