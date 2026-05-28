# Transcript Proxy

A standalone, stateless FastAPI service that fetches a single YouTube transcript per
request. It exists because YouTube blocks the OCI IP range where the main Povzetkar
application is deployed; this proxy runs on a host whose IP is not blocked (e.g.
Railway) and is called by the Quarkus backend.

## Contract

`GET /transcripts/{video_id}` — requires header `X-API-Key: <key>`.

| Status | Body | Meaning |
| --- | --- | --- |
| `200` | `{"video_id": "...", "segments": [{"start": <seconds>, "text": "..."}]}` | Transcript found |
| `404` | `{"detail": {"reason": "not_available_yet"}}` | No transcript yet — may appear later; retry |
| `404` | `{"detail": {"reason": "video_unavailable"}}` | Video deleted/private/invalid — permanent |
| `502` | `{"detail": {"reason": "proxy_blocked"}}` | Proxy IP blocked by YouTube |
| `502` | `{"detail": {"reason": "upstream_error"}}` | Other failure reaching YouTube |
| `401` | `{"detail": "Invalid or missing API key."}` | Bad/missing `X-API-Key` |

Manual English captions are preferred; auto-generated English is the fallback.

`GET /health` — no auth, returns `{"status": "ok"}` (for the Railway healthcheck).

## Local run

Requires Python 3.13 (the `youtube-transcript-api` library does not yet support 3.14).

```sh
python -m venv .venv
.venv/Scripts/activate      # Windows
# source .venv/bin/activate # Linux/macOS
pip install -r requirements.txt
API_KEY=dev-key uvicorn main:app --reload
```

```sh
curl -H "X-API-Key: dev-key" http://localhost:8000/transcripts/dQw4w9WgXcQ
```

## Deploy (Railway)

1. Point a Railway service at this directory (`transcript-proxy/`).
2. Set the `API_KEY` environment variable in the Railway dashboard.
3. Railway uses `railway.toml` for the start command and `/health` healthcheck.

If YouTube blocks Railway's IPs too, the same service deploys unchanged on Fly.io or a
Hetzner/DigitalOcean VPS.
