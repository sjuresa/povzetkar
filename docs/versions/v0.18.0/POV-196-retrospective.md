# POV-196 Retrospective — Relocate transcript proxy from Railway to Synology DS220j

## Planned vs. actual

Scoped as "diagnose, then relocate the proxy to the Synology DS220j if the cause is an IP-level
block." The diagnosis was confirmed from PO-supplied Railway logs as **soft volume-throttling of
Railway's shared datacenter egress IP** (not a hard ban, not cold-start), and the relocation was
delivered end-to-end: proxy live on the DS220j, reached over Tailscale, with production traffic
cut over and Railway retained as a passive fallback. The one substantive change vs. plan was the
**runtime**: built-in Python 3.8 instead of the assumed SynoCommunity 3.12 — decided after the
Phase 0 preflight probe.

## Deviations & why

- **Built-in Python 3.8 over SynoCommunity 3.12.** The plan assumed the python312 package was/would
  be installed. Phase 0 found it absent and the built-in 3.8 sufficient (working
  `venv`/`ensurepip`; library supports `>=3.8`). Installing a second runtime on a box already
  swapping at idle was the wrong trade — 3.8 was lighter and adequate. Consequences: relax
  `requirements.txt` (`uvicorn` `0.34.0`→`0.33.0`, the only version without a 3.8 build) and a
  one-line `main.py` fix (`str | None` → `Optional[str]`, since PEP 604 unions are 3.10+).
- **`/tmp noexec` misdiagnosis, then correction.** The dependency probe first failed with
  `pydantic_core ... failed to map segment from shared object`. The initial hypothesis was RAM
  exhaustion (the box had ~77 MB free); the `free -m` numbers at the moment of failure disproved
  it (~150 MB free). The real cause was `/tmp` mounted `noexec`, blocking executable mmap of the
  compiled `.so`. Corrected by installing on an exec-capable `/volume1` path.
- **`nsenter` instead of `docker exec` for the reachability gate.** The production image is
  shell-less, so the planned in-container curl could not run. Entering the container's network
  namespace from the host (`nsenter -n`) tested the exact routing the backend sees — and is the
  stronger check.

## Technical findings (durable record)

- **Synology DS220j (rtd1296, aarch64, 512 MB) can host a small FastAPI/uvicorn service** on its
  **built-in Python 3.8** — but only from an **exec-capable `/volume1` path**. `/tmp` is
  `noexec`; any venv/pip-cache there fails to load compiled extensions (`pydantic_core`) with
  `failed to map segment from shared object`. This error is easy to misread as memory pressure;
  check `mount` flags first.
- **SynoCommunity/DSM Python ships without `pip`** — bootstrap with `python3 -m ensurepip
  --upgrade` (or `get-pip.py`). `venv` is present on the built-in 3.8.
- **`uvicorn==0.34.0`+ dropped Python 3.8**; max on 3.8 is `0.33.0`. `fastapi` and
  `youtube-transcript-api 1.2.4` still install on 3.8/aarch64 (manylinux wheels exist). Pydantic
  v2's `pydantic_core` is the one compiled native dependency — it is what surfaces `noexec`
  problems.
- **A Docker bridge-network container CAN reach a host-level Tailscale node by raw `100.x` IP** —
  verified via `nsenter -t <pid> -n curl`. So Tailscale-on-host + raw tailnet IP (no
  Tailscale-in-Docker, no compose change) is sufficient. MagicDNS is *not* resolvable from the
  bridge network, hence raw IP, not the MagicDNS name.
- **Plain HTTP over the tailnet is fine** — WireGuard already encrypts; no TLS termination needed
  on the Synology, so the backend's REST client needs no truststore change.
- **The throttling signature**: `502`/`200` interleaved on the *same* video within seconds =
  volume-based soft throttle of a shared egress IP; a hard ban fails consistently and a cold-start
  fails in a contiguous post-restart block. The rotating `100.64.0.x` inbound IPs in Railway's
  uvicorn access log are its internal CGNAT load-balancer mesh, irrelevant to the diagnosis — the
  throttled IP is the (invisible) shared *egress* IP.

## Feed-forward

- **Crash-restart supervision is deferred (B1).** The proxy is launched once at boot with no
  restart loop; if uvicorn dies mid-run it stays down until reboot, and recovery is a manual
  flip to Railway. If this proves flaky, add a restart loop/backoff to `run-proxy.sh` or a
  watchdog task (logged in improvements.md).
- **Single residential egress IP** can still be soft-throttled under heavy batch bursts. The
  existing 15–45s proxy jitter and the still-open **refetch-waste** item (transcript re-fetched
  on every Gemini retry, improvements.md) remain the levers if throttling reappears — now
  diagnosable via the new per-branch logging in `proxy.log`.
- **No automated tests for the proxy** — the logging change was verified manually (contract
  unchanged). A `pytest` suite is logged in improvements.md; note the runtime is Python 3.8, so
  avoid 3.10+ syntax (this task already hit the `str | None` trap).
- **Reboot trigger not yet exercised** — the DSM boot task was validated via "Run on demand"
  (correct command + root ownership); a full power-cycle test should be done at a convenient time
  (the provider fallback chain covers the brief gap).
