# Transcript Proxy

A standalone, stateless FastAPI service that fetches a single YouTube transcript per
request. It exists because YouTube blocks or throttles many cloud IP ranges; this proxy runs
on a host whose IP is not blocked and is called by the Quarkus backend (`proxy` transcript
provider, `TRANSCRIPT_PROXY_URL` + `TRANSCRIPT_PROXY_API_KEY`).

**Hosting:** any host whose egress IP YouTube does not block — a self-hosted Linux box reached
over a VPN, or a PaaS (`railway.toml` describes a Railway deployment). A residential egress IP
is throttled far less than a shared datacenter IP. Only one host is active at a time (see
[Fallback](#fallback)).

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

Requires Python **3.8+** (`youtube-transcript-api` supports `>=3.8,<3.15`);
`requirements.txt` is pinned to a 3.8-compatible set so the proxy also runs on hosts with an
older built-in Python.

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

## Deploy on a self-hosted Linux host (native, no Docker)

1. **Install location.** Copy this directory to an **exec-capable** path. Do **not** use a
   `noexec` mount such as `/tmp` on some systems — compiled extensions (`pydantic_core`) will
   fail to load there.
2. **Virtualenv + deps** (bootstrap pip if the system Python ships without it):

   ```sh
   cd "<install-dir>"
   python3 -m venv .venv
   .venv/bin/python -m ensurepip --upgrade
   .venv/bin/python -m pip install -r requirements.txt
   ```

3. **API key.** Create `.env` (gitignored, `chmod 600`) with the **same** key the backend sends:

   ```sh
   printf 'API_KEY=%s\n' "<shared-key>" > .env && chmod 600 .env
   ```

4. **Start at boot.** Run `run-proxy.sh` from a boot-time task of the host (systemd unit,
   cron `@reboot`, or a NAS task scheduler). The script sources `.env` and launches
   `uvicorn ... --host 0.0.0.0 --port 8000`, appending to `proxy.log`. It starts the proxy
   once; there is no crash-restart yet (see the project backlog).
5. **Reachability.** Do not expose port 8000 publicly. Connect the backend host and the proxy
   host over a VPN and point the backend's `TRANSCRIPT_PROXY_URL` at the proxy's VPN address,
   `http://<vpn-ip>:8000`. Use the raw IP if the backend's Docker bridge network cannot resolve
   the VPN's DNS names.

## Fallback

A second deployment (e.g. on a PaaS via `railway.toml` — nixpacks build, `/health`
healthcheck) can stay as a **passive, manual fallback**. To fall back, set the backend's
`TRANSCRIPT_PROXY_URL` to the fallback host's URL and recreate the backend container. Both
hosts must accept the **same** `X-API-Key`. The backend's `proxy` provider is off by default —
`TRANSCRIPT_PROXY_ENABLED=true` turns it on (RUNBOOK 14.1a).
