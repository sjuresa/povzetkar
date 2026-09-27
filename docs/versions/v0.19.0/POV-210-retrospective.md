# POV-210 Retrospective — Persistent production access and application logs

## 1. Planned vs. actual

Planned (Brainstorm): an app access log, persistence with 90-day retention, Docker log caps, an
incident record, documentation and a deploy. Delivered up to the commit: all of it except the
90-day retention, which the PO dropped in planning (decision 11) as disproportionate for a
personal application — logs rotate by size. No code in `src/main`; configuration, one test,
Dockerfile, compose, documentation and backlog.

## 2. Deviations & why

- **The work started outside the process.** "Enable logging" looked like one setting, so a
  prototype went straight into the working tree (and its Caddy part into production). Its review
  found nine defects, and the investigation showed the premise itself was wrong: the tunnel
  bypasses Caddy. The PO stopped, discarded the prototype (kept as a diff) and restarted at the
  Brainstorm. The process then took ~2 h 15 min to the approved plan for ~60 changed lines — but
  every design decision it changed (reroute logging, `%D`, `%h` trust, field order, rotation on
  boot, the test expecting 404) would have shipped wrong from the prototype.
- **Retention grew into a cascade and was cut.** 90 days → a retention task → daily rotation
  (size-only could not bound age at ~25 KB/day) → a console filter (IPs also sit in `docker logs`).
  Each step was correct in isolation; together they cost two review rounds before the PO asked
  whether any of it was worth it. The question should have come after the first safeguard.
- **Two plan review rounds** (PO request, subagents): 15 and 9 findings, several load-bearing:
  every SPA probe would have been logged twice as `GET /index.html`, `%D` would print `-`, `%h` is
  client-controllable, the volume assumption (1 MB/day) was 40× too high.
- **Three pre-commit review rounds** (7, 4, 6 findings; the PO waived a fourth). Round 2 found the
  one real security defect of the design — `%h` first in the pattern let a client forge a log line
  prefix — which both plan reviews had circled (they called `%h` spoofable) without drawing the
  consequence. Round 3 found the same class of defect in the parse recipe (another logger's text
  posing as an entry). Testing recipes against forged input, not only real lines, would have
  caught both at once.
- **A Time Log entry was estimated, not read** (10:14 instead of 10:09) — corrected; the rule is
  to take the time from the clock at the boundary.

## 3. Technical findings

- **Read the ingress before diagnosing a proxy.** The first explanation ("Caddy's stdout log does
  not work") was wrong: Caddy logged fine and received nothing. `docker logs povzetkar-cloudflared`
  prints the tunnel's ingress on every start — one line settled it, and it also exposed that
  production has sent no security headers since the move off OCI and served plain `http://`
  (Cloudflare "Always Use HTTPS" fixed the latter the same day).
- **Quarkus' access log and a SPA reroute:** `RoutingContext.reroute` rewrites the request in
  place and re-runs the handler chain, so the default access log records the rerouted path, twice.
  `consolidate-rerouted-requests=true` + `%<r` gives one entry with the original request line.
  `%D` needs `quarkus.http.record-request-start-time=true`. `%h` behind proxy forwarding without
  `trusted-proxies` is the client's own first `X-Forwarded-For` value — unvalidated, spaces and
  quotes included.
- **Put trusted fields before client-controlled ones** in a text log: a forged `cf-ip=` in a URL
  or header then cannot shadow the real one for `grep`/`awk`. "Client-controlled" has to include
  every placeholder, not only the obvious ones: plan review marked `%h` spoofable yet kept it first
  in the pattern, and review round 2 showed a crafted `X-Forwarded-For` replacing a whole line
  prefix. `%h` was dropped; the full chain is logged as `xff=` after the status.
- **Measure volume before sizing rotation.** Prometheus request counts gave ~25 KB/day; the design
  had assumed ~1 MB/day.
- **Distroless + named volume:** a new volume copies the ownership of the image directory it is
  mounted on; without a `nonroot`-owned `/var/log/app` in the image the app cannot write its log.
  With no shell, the directory is created in the build stage and copied as the content of a
  parent so `--chown` covers the directory itself.
- **Prometheus kept the only evidence** of the bot wave (per-path counts on a persistent volume) —
  and would have expired it after 15 days; exporting it early preserved the incident record.

## 4. Feed-forward

- **Deploy is still pending** (after the PR loop, on the PO's "deploy"): whole-stack recreate,
  `backend_app_logs` must not exist beforehand, DB dump outside the repo, live checks incl. a
  spoofed `X-Forwarded-For` and the Google login.
- **Backlog priorities from this task:** "Put Caddy on the traffic path" (High — security headers
  are not delivered today); "SPA fallback answers 200 to probe paths" and "Micrometer `uri`-tag
  explosion" (both degrade detection); "Suspicious-traffic detection and alerting"; "Privacy policy".
- **Proportionality check:** when a requirement starts generating safeguards for its own
  safeguards, stop and ask the PO whether the requirement is worth it.
- **Local environment:** after a host restart `povzetkar-db-test` is stopped — `docker start
  povzetkar-db-test` before running the backend suite.
- **TC-ING-006 tripwire (POV-203/204/205/207/208 carry-over) still stands** for v0.20.
