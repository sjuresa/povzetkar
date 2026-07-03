# Transcript Proxy

A standalone, stateless FastAPI service that fetches a single YouTube transcript per
request. It exists because YouTube blocks/throttles the OCI datacenter IP range where the
main Povzetkar application is deployed; this proxy runs on a host whose IP is not blocked
and is called by the Quarkus backend.

**Production host:** self-hosted on a **Synology DS220j**, reached by the backend over a
**Tailscale** overlay (POV-196). A residential egress IP is throttled far less than a shared
datacenter IP. **Railway** remains a passive, manual fallback (see [Fallback](#fallback)).

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

`GET /health` — no auth, returns `{"status": "ok"}` (used by host/healthcheck probes).

Every transcript request also logs its outcome with the exact cause (e.g.
`video_id=... blocked by YouTube: RequestBlocked -> 502 proxy_blocked`), distinguishing
`IpBlocked` from `RequestBlocked` and `upstream_error` without guessing.

## Local run

Requires Python **3.8+** (`youtube-transcript-api` supports `>=3.8,<3.15`). The production
Synology host uses its built-in Python 3.8; `requirements.txt` is pinned to the 3.8-compatible
set accordingly.

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

## Deploy (Synology DS220j, native + Tailscale)

The proxy runs natively (no Docker — the DS220j has no Container Manager) under the box's
built-in Python 3.8, supervised by a DSM Task Scheduler boot task, and is reached by the
backend over Tailscale.

1. **Install location.** Copy this directory to an **exec-capable** `/volume1` path, e.g.
   `/volume1/transcript-proxy`. Do **not** use `/tmp` — it is mounted `noexec` and compiled
   extensions (`pydantic_core`) will fail to load there.
2. **Virtualenv + deps** (built-in Python 3.8; bootstrap pip if missing):

   ```sh
   cd /volume1/transcript-proxy
   python3 -m venv .venv
   .venv/bin/python -m ensurepip --upgrade   # SynoCommunity/DSM Python ships without pip
   .venv/bin/python -m pip install -r requirements.txt
   ```

3. **API key.** Create `.env` (gitignored, `chmod 600`) with the **same** key the backend sends:

   ```sh
   printf 'API_KEY=%s\n' "<shared-key>" > .env && chmod 600 .env
   ```

4. **Boot supervision.** DSM → Control Panel → Task Scheduler → Create → Triggered Task →
   Boot-up, user `root`, run: `/volume1/transcript-proxy/run-proxy.sh`. The script sources
   `.env` and launches `uvicorn ... --host 0.0.0.0 --port 8000`, appending to `proxy.log`.
   (Started once at boot; no crash-restart yet — see the project backlog.)
5. **Tailscale.** Install Tailscale (SynoCommunity package) on the Synology and on the OCI
   backend host; `tailscale up` on both. Point the backend's `TRANSCRIPT_PROXY_URL` at the
   Synology's raw tailnet IP, `http://<tailnet-ip>:8000` (MagicDNS is not resolvable from the
   backend's Docker bridge network).

## Fallback

Railway remains deployed as a **passive, manual fallback** — only one host is active at a time.
The `railway.toml` (nixpacks build, `/health` healthcheck) is unchanged. To fall back, set the
backend's `TRANSCRIPT_PROXY_URL` to the Railway URL (or unset it — that is the compose default)
and recreate the backend container. Both hosts must accept the **same** `X-API-Key`. See the
project RUNBOOK §16.1b for the full flip-back procedure.
