# Luban data server

One small service that fetches matches, markets and TV schedules once and answers every TV from cache. Without it,
each TV asks each source itself (through the app's allowlisted proxy). With it, a thousand TVs make one request
every 10 minutes instead of a thousand.

It also keeps the dinner plans linked from the [dinner guide](https://dinner.bassamailab.xyz): the guide writes its plan
with a secret key on every change, the TV reads it by an id derived from that key every 10 minutes (see the top of
`app.py` and `docs/SECURITY.md` §6).

## Deploy on Coolify

1. **New resource → Public/Private repository** → `AQBassam/Luban`, branch `main`.
2. **Build pack:** Dockerfile · **Base directory:** `/server` · **Port:** `8080`.
3. **Health check path:** `/health` (the process answers). `/ready` shows each source's state and data age.
4. Environment: `FOOTBALL_DATA_KEY`: a free token from <https://www.football-data.org/client/register> (without it the
   matches card stays empty; the token never leaves the server). Optional: `CORS_ORIGIN`, `DINNER_ORIGIN` (the dinner
   guide's address, `https://dinner.bassamailab.xyz` by default: the only page that may write a plan from a browser).
   **Persistent storage:** a volume mounted at `/data`. It keeps the linked dinner plans (one small JSON file each);
   without it they are lost whenever the container is rebuilt. `/ready` shows `"dinner": {"state": "on"}` when it is writable.
5. Give it a domain with HTTPS (Coolify does Let's Encrypt), e.g. `https://luban.example.com`.
6. The apps use `https://luban.bassamailab.xyz` by default (`web/js/settings.js`); a fork changes that line. Owners can clear it in phone settings → Advanced.

**Live instance:** `https://luban.bassamailab.xyz` (Coolify on a VPS behind the host's nginx: the container is published on `127.0.0.1:8090` only, nginx terminates TLS with certbot and rate-limits to 10 requests a second per address).

## Run locally

```
python3 server/app.py          # http://localhost:8080/health
```

No dependencies beyond Python 3.10+. It contacts only Yahoo Finance, CoinGecko, football-data.org and TVMaze.
