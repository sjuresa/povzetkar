# POV-193 Walkthrough — Transcript Proxy Service

## What was done

Built a standalone, stateless FastAPI service in `transcript-proxy/` that fetches a single YouTube transcript per request. It exists to work around YouTube blocking the OCI IP range where the Povzetkar backend runs: the proxy is deployed on a host with a non-blocked IP (Railway initially) and is called by the Quarkus backend.

The service exposes `GET /transcripts/{video_id}` (authenticated via `X-API-Key`), preferring manually-created English captions and falling back to auto-generated ones. Responses are timestamped segments (`{start, text}`), mapping directly onto the backend's existing `TranscriptSegment` record. Status semantics: `200` with segments; `404 {reason: not_available_yet}` when a transcript may still appear later (caller should retry); `404 {reason: video_unavailable}` for permanently missing/private/invalid videos; `502` when the proxy itself cannot reach YouTube (IP blocked / upstream error). A `GET /health` endpoint (no auth) backs the Railway healthcheck.

**Files created:**

- `transcript-proxy/main.py` — FastAPI app, API-key dependency, exception→status mapping
- `transcript-proxy/requirements.txt` — `fastapi`, `uvicorn[standard]`, `youtube-transcript-api==1.2.4`
- `transcript-proxy/railway.toml` — nixpacks build, uvicorn start command, `/health` healthcheck
- `transcript-proxy/.gitignore`, `transcript-proxy/README.md`

## Planned vs. actual scope

Delivered as scoped. Quarkus integration (a `ProxyTranscriptProvider` adapter for the POV-189 abstraction, plus caller-side jitter) remains out of scope for a follow-up task, as planned.

## Time delta

- Estimated: not formally estimated (small service)
- Actual: ~2.5 hours including brainstorm, PPP interview, and dependency-version investigation.

## Deviations

- **Dependency versions.** The plan named `youtube-transcript-api==0.6.3`, which does not exist (last 0.6.x is 0.6.2; the maintained line is 1.2.x). Pinned `1.2.4`. The 1.x API is a rewrite: `fetch`/`list` are instance methods, and manual-vs-generated selection uses `TranscriptList.find_manually_created_transcript` / `find_generated_transcript`. `main.py` was written against the verified 1.2.4 surface, not the plan's assumed 0.6.x.
- **Python version.** The machine's default Python (3.14) is unsupported by the library (`requires-python <3.14`). Used Python 3.13.5 for the venv. Documented in the README as a local-run requirement.
- **Exception bucket widened.** The plan mapped only `VideoUnavailable` to `video_unavailable`. Implementation also routes `InvalidVideoId`, `AgeRestricted`, and `VideoUnplayable` there — all permanent per-video failures with the same caller semantics.
