# POV-196 Walkthrough — Relocate transcript proxy from Railway to Synology DS220j

## What was done

The transcript proxy (`transcript-proxy/`, the **primary** transcript provider) was moved from
Railway to a self-hosted **Synology DS220j**, reached by the OCI backend over a **Tailscale**
overlay. The relocation was driven by a confirmed diagnosis: Railway access logs showed `502`s
interleaved with `200`s on the *same* video IDs within seconds (e.g. `y4KvpNDgK4w` → `404`, then
`502 ×4`, then `200`) — the signature of YouTube **soft volume-throttling on Railway's shared
datacenter egress IP**, not a cold-start and not a hard ban. The DS220j serves from a
residential, always-on egress IP, which YouTube throttles far less.

Concrete changes:

- **`transcript-proxy/main.py`** — added per-branch failure logging (the only code change). A
  module logger now records `video_id` + `type(exc).__name__` + status/reason in every `except`
  branch (404→INFO, 502→WARNING, catch-all→`logger.exception` ERROR+traceback), plus an INFO
  success line. `IpBlocked` vs `RequestBlocked` (both `502 proxy_blocked`) and `upstream_error`
  are now distinguishable without inference. The HTTP contract is unchanged. The
  `str | None` annotation was changed to `Optional[str]` for Python 3.8 compatibility.
- **`transcript-proxy/requirements.txt`** — relaxed to the Synology-proven 3.8 set:
  `fastapi==0.124.4`, `uvicorn[standard]==0.33.0` (was `0.34.0`, which has no 3.8 build),
  `youtube-transcript-api==1.2.4`.
- **`transcript-proxy/run-proxy.sh`** (new) — thin boot launcher: resolves its own directory,
  sources a gitignored `.env` (the API key), and `exec`s `uvicorn ... --host 0.0.0.0 --port
  8000` with output appended to `proxy.log`.
- **`transcript-proxy/.gitignore`** (new) — `.venv/`, `.env`, `proxy.log`, `__pycache__/`.
- **Synology host setup** — proxy installed under `/volume1/transcript-proxy` (exec-capable;
  `/tmp` is `noexec`) on the box's **built-in Python 3.8**; supervised by a DSM Task Scheduler
  **Boot-up** task running as root. Tailscale installed on both the Synology (tailnet IP
  `100.95.217.75`) and the OCI backend host.
- **Cutover** — `TRANSCRIPT_PROXY_URL` pointed at `http://100.95.217.75:8000`; backend recreated;
  a live orchestration fetch confirmed `Transcript fetched successfully via provider 'proxy'`.
- **Documentation** — RUNBOOK §16.1b (proxy host, Tailscale model, flip-back procedure, DS220j
  gotchas), `transcript-proxy/README.md` rewrite (Synology + Tailscale deploy), ADR-0009 update
  note, and two backlog items (crash-restart supervision, proxy `pytest` suite).

## Planned vs. actual scope

Delivered as scoped, with the runtime baseline changing during execution. The plan assumed the
SynoCommunity **Python 3.12** package; the Phase 0 preflight probe found it was not installed and
that the box's **built-in Python 3.8** already had working `venv`/`ensurepip`. Since
`youtube-transcript-api` supports `>=3.8`, 3.8 was used instead — avoiding a second runtime
install on a RAM-constrained box. This pulled in a `requirements.txt` relaxation (the original
`uvicorn==0.34.0` pin has no 3.8 build) and a one-line `main.py` 3.8 fix (`Optional[str]`).
Connectivity used Tailscale-on-host + raw tailnet IP (option C), as planned — Tailscale-in-Docker
was considered and rejected. No scope was added or removed; the host model (Synology primary,
Railway passive fallback) is exactly as defined.

## Time delta

- Estimated: ~6.5h (5.0h + 1.5h contingency for the DS220j RAM / venv / bridge-reachability
  unknowns).
- Actual: the contingency was largely consumed by Phase 0 — the runtime baseline turned out to
  be 3.8-not-3.12, and a `/tmp noexec` gotcha caused a misleading
  `pydantic_core ... failed to map segment` error that took several probes to attribute
  correctly (it looked like RAM exhaustion; it was the mount flag). The repo-side code/doc work
  and the operator phases (Tailscale, deploy, cutover) ran close to estimate. The
  bridge-reachability risk (H6) resolved cleanly on the first `nsenter` probe.

## Deviations

- **Runtime: built-in Python 3.8 instead of SynoCommunity 3.12** (Phase 0 finding). Lighter, no
  install, but forced the `requirements.txt` relaxation and the `Optional[str]` fix. Decided
  with the PO mid-execution.
- **`/tmp noexec` gotcha** — the initial dependency probe failed in `/tmp` with a
  `failed to map segment from shared object` error on the compiled `pydantic_core` `.so`. Root
  cause was the `noexec` mount, not RAM or Python version; the deploy path was constrained to an
  exec-capable `/volume1` location. Documented in the RUNBOOK as a durable gotcha.
- **Container-reachability verified via `nsenter`** — the production backend image is
  shell-less/distroless, so the planned `docker exec ... curl` gate could not run. Verified
  instead from the container's network namespace (`nsenter -n curl`), which proved the Docker
  bridge routes tailnet traffic and is a more rigorous check than the original probe.
- **Incidental positive evidence** — a manual fetch on the DS220j returned a real 61-segment
  transcript directly from the residential IP, confirming YouTube does not block it (supporting
  the relocation hypothesis) before the cutover.
