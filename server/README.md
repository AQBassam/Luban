# Luban data server

One small service that fetches matches, markets and TV schedules once and answers every TV from cache. Without it,
each TV asks each source itself (through the app's allowlisted proxy). With it, a thousand TVs make one request
every 10 minutes instead of a thousand.

## Deploy on Coolify

1. **New resource → Public/Private repository** → `AQBassam/Luban`, branch `main`.
2. **Build pack:** Dockerfile · **Base directory:** `/server` · **Port:** `8080`.
3. **Health check path:** `/health`.
4. Optional environment: `SPORTSDB_KEY` (a TheSportsDB Patreon key lifts the free key's limits), `CORS_ORIGIN`.
5. Give it a domain with HTTPS (Coolify does Let's Encrypt), e.g. `https://luban.example.com`.
6. On each TV: phone settings → Advanced → Data server → that address.

## Run locally

```
python3 server/app.py          # http://localhost:8080/health
```

No dependencies beyond Python 3.10+. It contacts only Yahoo Finance, CoinGecko, TheSportsDB and TVMaze.
