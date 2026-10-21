"""Luban data server: one cached answer for every TV instead of every TV asking every source.

    GET /v1/markets?ids=gold,brent,btc,spx    prices and day change (Yahoo Finance chart API, CoinGecko)
    GET /v1/matches?teams=86,81               recent results and next fixtures per team (football-data.org)
    GET /v1/shows?genres=Drama&langs=&min=7   new episodes this week (TVMaze)
    GET /v1/teams?q=real madrid               team search in the 12 free competitions (football-data.org)
    GET /health

Only these upstream hosts are ever contacted; requests are validated before any of them is. Standard library only.
Configuration (environment): PORT (8080), FOOTBALL_DATA_KEY (free token from football-data.org/client/register; without
it there are no matches), CORS_ORIGIN (*), MARKETS_TTL (900), MATCHES_TTL (600), SHOWS_TTL (21600).

Matches: football-data.org's free plan (10 calls a minute, scores delayed) covers the competitions people here follow:
Premier League, La Liga, Serie A, Bundesliga, Ligue 1, Eredivisie, Primeira Liga, Championship, Brasileirão, Champions
League, World Cup, European Championship. One call a few minutes fetches every match of the coming days for all of
them; each TV's teams are picked out of that, so the number of TVs never changes the number of calls.
"""
import json
import os
import re
import threading
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("PORT", "8080"))
FD_KEY = re.sub(r"[^A-Za-z0-9]", "", os.environ.get("FOOTBALL_DATA_KEY", ""))
FD = "https://api.football-data.org/v4"
FD_COMPETITIONS = ("PL", "PD", "SA", "BL1", "FL1", "DED", "PPL", "ELC", "BSA", "CL", "WC", "EC")
FD_GAP = 6.5   # seconds between calls: the free plan allows 10 a minute
CORS = os.environ.get("CORS_ORIGIN", "*")
TTL = {"markets": int(os.environ.get("MARKETS_TTL", 900)), "matches": int(os.environ.get("MATCHES_TTL", 600)),
       "shows": int(os.environ.get("SHOWS_TTL", 21600)), "teams": 86400}
UA = "Luban/1 (+https://github.com/AQBassam/Luban)"
ALLOWED_HOSTS = {"query1.finance.yahoo.com", "api.coingecko.com", "api.football-data.org", "api.tvmaze.com"}
MARKETS = {"gold": ("yahoo", "GC=F"), "brent": ("yahoo", "BZ=F"), "spx": ("yahoo", "^GSPC"), "btc": ("cg", "bitcoin")}
SKIP_TYPES = {"News", "Talk Show", "Sports", "Game Show"}
POOL = ThreadPoolExecutor(max_workers=8)


class Cache:
    """In-memory TTL cache; a failed refresh serves the last good value for up to a day (stale-if-error)."""

    def __init__(self):
        self.data, self.lock, self.flights = {}, threading.Lock(), {}

    def get(self, key, ttl, fn):
        with self.lock:
            hit = self.data.get(key)
            if hit and time.time() - hit[0] < ttl:
                return hit[1]
            flight = self.flights.setdefault(key, threading.Lock())
        # one refresh per key at a time: a hundred TVs asking at once cause one upstream call, not a hundred
        with flight:
            with self.lock:
                hit = self.data.get(key)
            now = time.time()
            if hit and now - hit[0] < ttl:
                return hit[1]
            try:
                val = fn()
            except Exception:
                if hit and now - hit[0] < 86400:
                    return hit[1]
                raise
            with self.lock:
                self.data[key] = (now, val)
                if len(self.data) > 5000:   # bound memory: drop the oldest half
                    for k in sorted(self.data, key=lambda k: self.data[k][0])[:2500]:
                        del self.data[k]
            return val


CACHE = Cache()


def fetch_json(url, timeout=12, headers=None):
    host = urllib.parse.urlsplit(url).hostname
    if host not in ALLOWED_HOSTS:
        raise ValueError(f"host not allowed: {host}")
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json", **(headers or {})})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        body = r.read(5_000_000)
    return json.loads(body)


def market(mid):
    kind, sym = MARKETS[mid]
    if kind == "cg":
        j = fetch_json(f"https://api.coingecko.com/api/v3/simple/price?ids={sym}&vs_currencies=usd&include_24hr_change=true")
        return {"price": j[sym]["usd"], "change": j[sym].get("usd_24h_change")}
    j = fetch_json(f"https://query1.finance.yahoo.com/v8/finance/chart/{urllib.parse.quote(sym)}?range=5d&interval=1d")
    meta = j["chart"]["result"][0]["meta"]
    price, prev = meta["regularMarketPrice"], meta.get("chartPreviousClose") or meta.get("previousClose")
    return {"price": price, "change": (price / prev - 1) * 100 if prev else None}


def markets(ids):
    out = {}
    for mid, res in zip(ids, POOL.map(lambda m: _safe(lambda: CACHE.get("m:" + m, TTL["markets"], lambda: market(m))), ids)):
        if res:
            out[mid] = res
    return out


_fd_lock, _fd_last = threading.Lock(), [0.0]


def fd(path):
    """One football-data.org call, spaced to stay inside the free plan's 10 a minute."""
    if not FD_KEY:
        raise RuntimeError("FOOTBALL_DATA_KEY is not set")
    with _fd_lock:
        wait = _fd_last[0] + FD_GAP - time.time()
        if wait > 0:
            time.sleep(wait)
        _fd_last[0] = time.time()
        return fetch_json(FD + path, 20, {"X-Auth-Token": FD_KEY})


def fd_event(m):
    """A football-data match in the shape the screen reads (the field names it has always used)."""
    st = m.get("status")
    ft = (m.get("score") or {}).get("fullTime") or {}
    played = st in ("FINISHED", "IN_PLAY", "PAUSED")
    return {"idEvent": str(m.get("id")), "idHomeTeam": str((m.get("homeTeam") or {}).get("id")),
            "idAwayTeam": str((m.get("awayTeam") or {}).get("id")),
            "strHomeTeam": (m.get("homeTeam") or {}).get("shortName") or (m.get("homeTeam") or {}).get("name"),
            "strAwayTeam": (m.get("awayTeam") or {}).get("shortName") or (m.get("awayTeam") or {}).get("name"),
            "intHomeScore": ft.get("home") if played else None, "intAwayScore": ft.get("away") if played else None,
            "strTimestamp": m.get("utcDate"), "strStatus": "FT" if st == "FINISHED" else "LIVE" if played else "NS",
            "strLeague": (m.get("competition") or {}).get("name")}


def fd_window():
    """Every match from three days ago to a week ahead in the free competitions (the API's 10-day maximum)."""
    today = date.today()
    j = fd(f"/matches?dateFrom={(today - timedelta(days=3)).isoformat()}&dateTo={(today + timedelta(days=6)).isoformat()}")
    return [fd_event(m) for m in j.get("matches") or [] if m.get("status") not in ("POSTPONED", "CANCELLED", "SUSPENDED", "AWARDED")]


def matches(ids):
    if not ids:
        return {}
    try:
        window = CACHE.get("fd:window", TTL["matches"], fd_window)
    except Exception:
        return {}
    out = {}
    for tid in ids:
        mine = [e for e in window if tid in (e["idHomeTeam"], e["idAwayTeam"])]
        if mine:
            out[tid] = {"last": sorted([e for e in mine if e["strStatus"] != "NS"], key=lambda e: e["strTimestamp"], reverse=True)[:5],
                        "next": sorted([e for e in mine if e["strStatus"] == "NS"], key=lambda e: e["strTimestamp"])[:5]}
    return out


def fd_teams():
    """Every team of the free competitions, once a day (12 calls, a minute and a half at the free pace)."""
    teams = {}
    for code in FD_COMPETITIONS:
        try:
            j = fd(f"/competitions/{code}/teams")
        except Exception:
            continue   # a tournament between editions has no teams yet
        comp = (j.get("competition") or {}).get("name") or code
        for t in j.get("teams") or []:
            tid = str(t.get("id"))
            if tid not in teams:
                teams[tid] = {"id": tid, "en": t.get("name") or "", "short": t.get("shortName") or "", "tla": t.get("tla") or "",
                              "league": comp}
    if not teams:
        raise RuntimeError("no teams")
    return list(teams.values())


def team_search(q):
    words = [w for w in re.split(r"\s+", q.lower()) if w]
    hits = []
    for t in CACHE.get("fd:teams", TTL["teams"], fd_teams):
        hay = f"{t['en']} {t['short']} {t['tla']}".lower()
        if all(w in hay for w in words):
            hits.append({"id": t["id"], "en": t["short"] or t["en"], "ar": "", "league": t["league"], "full": t["en"]})
    return hits[:10]


def shows(genres, langs, minimum):
    def week():
        days = [(date.today() + timedelta(days=i)).isoformat() for i in range(7)]
        lists = POOL.map(lambda d: _safe(lambda: fetch_json(f"https://api.tvmaze.com/schedule/web?date={d}", 20)) or [], days)
        return [e for lst in lists for e in lst]
    episodes = CACHE.get("shows:week", TTL["shows"], week)
    seen, out = set(), []
    for e in episodes:
        s = (e.get("_embedded") or {}).get("show") or {}
        if not s or s.get("id") in seen or s.get("type") in SKIP_TYPES:
            continue
        rating = (s.get("rating") or {}).get("average") or 0
        if rating < minimum or (genres and not set(genres) & set(s.get("genres") or [])) or (langs and s.get("language") not in langs):
            continue
        seen.add(s.get("id"))
        out.append({"name": s.get("name"), "season": e.get("season"), "number": e.get("number"), "date": e.get("airdate"), "rating": rating})
    return sorted(out, key=lambda x: -x["rating"])[:30]


def _safe(fn):
    try:
        return fn()
    except Exception:
        return None


def csv(qs, key, pattern, limit):
    vals = [v for v in (qs.get(key, [""])[0]).split(",") if v]
    return [v for v in vals if re.fullmatch(pattern, v)][:limit]


class Handler(BaseHTTPRequestHandler):
    server_version = "Luban/1"

    def log_message(self, fmt, *args):   # one short line per request, no client addresses
        print(f"{self.command} {self.path.split('?')[0]} {args[1] if len(args) > 1 else ''}", flush=True)

    def send(self, code, obj, max_age=60):
        body = json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", CORS)
        self.send_header("Cache-Control", f"public, max-age={max_age}")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", CORS)
        self.send_header("Access-Control-Allow-Methods", "GET")
        self.end_headers()

    def do_GET(self):
        url = urllib.parse.urlsplit(self.path)
        qs = urllib.parse.parse_qs(url.query)
        try:
            if url.path == "/health":
                return self.send(200, {"ok": True})
            if url.path == "/v1/markets":
                ids = [m for m in csv(qs, "ids", r"[a-z]{2,8}", 8) if m in MARKETS]
                return self.send(200, markets(ids), 300)
            if url.path == "/v1/matches":
                return self.send(200, matches(csv(qs, "teams", r"\d{1,9}", 10)), 120)
            if url.path == "/v1/shows":
                g = csv(qs, "genres", r"[A-Za-z-]{2,20}", 16)
                langs = csv(qs, "langs", r"[A-Za-z]{2,20}", 12)
                try:
                    m = max(0.0, min(10.0, float(qs.get("min", ["7"])[0])))
                except ValueError:
                    m = 7.0
                return self.send(200, shows(g, langs, m), 3600)
            if url.path == "/v1/teams":
                q = (qs.get("q", [""])[0]).strip()
                if not re.fullmatch(r"[\w .'-]{2,40}", q):
                    return self.send(400, {"error": "q: 2-40 letters"})
                return self.send(200, _safe(lambda: team_search(q)) or [], 3600)
            return self.send(404, {"error": "not found"})
        except Exception as e:  # noqa: BLE001
            return self.send(502, {"error": type(e).__name__})


def main():
    srv = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"Luban data server on :{PORT}" + ("" if FD_KEY else " (no FOOTBALL_DATA_KEY: no matches)"), flush=True)
    if FD_KEY:   # the team list takes a minute and a half to gather at the free pace: start before anyone searches
        threading.Thread(target=lambda: _safe(lambda: CACHE.get("fd:teams", TTL["teams"], fd_teams)), daemon=True).start()
    srv.serve_forever()


if __name__ == "__main__":
    main()
