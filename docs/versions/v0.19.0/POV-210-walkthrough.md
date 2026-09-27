# POV-210 Walkthrough — Persistent production access and application logs

**Task:** [POV-210](https://jumis.myjetbrains.com/youtrack/issue/POV-210) (Task, Major) · **Branch:** `POV-210` → `v0.19.0` · **Date:** 2026-09-27

## 1. What was done

Cloudflare showed a wave of bots probing povzetkar for secrets and vulnerabilities (the PO then
restricted non-domestic traffic in Cloudflare). The local logs could not answer what the bots
requested or what they got: the Cloudflare tunnel routes straight to `app:8080`, so Caddy's access
log never saw production traffic; the app had no access log (`AuditLogFilter` records only matched
`/api/v1` calls in `audit_log`, not the probes); and Docker's `json-file` log is
discarded on every container recreation (the whole stack was recreated on 2026-09-26).

- **HTTP access log (`application.properties`):** `%prod.quarkus.http.access-log.enabled=true`
  with the pattern
  `%t cf-ip=%{i,CF-Connecting-IP} country=%{i,CF-IPCountry} "%<r" %s %b %D xff="%{i,X-Forwarded-For}" "%{i,User-Agent}"`,
  `consolidate-rerouted-requests=true` (SPA-rerouted probes are logged once under their original
  path; Vert.x `reroute` rewrites the request in place and the handler runs again) and
  `record-request-start-time=true` (otherwise `%D` prints `-`). Only the timestamp precedes the
  Cloudflare fields; the client-controlled request line, `X-Forwarded-For` chain and User-Agent
  follow them. `%h` (the client's own, unvalidated first `X-Forwarded-For` value) is not logged;
  `X-API-Key` is never in the pattern. Pattern and rerouting settings are unprefixed
  (inert while the log is off) so the test runs the production values;
  `record-request-start-time` applies in every profile (one timestamp per request).
- **Persistent file log:** rotation in `application.properties` (10M × 10 backups, size only,
  `rotate-on-boot=false` — the host restarts daily); switched on only in the prod container
  (`QUARKUS_LOG_FILE_ENABLED`, `QUARKUS_LOG_FILE_PATH=/var/log/app/quarkus.log`) on the new
  `app_logs` volume. The Dockerfile creates `/var/log/app` owned by `nonroot` (copied as the content
  of a parent directory — distroless has no shell), so a new volume inherits the ownership.
- **Docker log caps:** `x-logging` anchor (`json-file`, 5 × 20 MB) on all six services of
  `docker-compose.prod.yml`.
- **Test:** `AccessLogTest` (`@QuarkusTest` + a profile that only enables the access log; a JUL
  handler on `io.quarkus.http.access-log`): a probe `GET /.git/config?pov210=probe` with Cloudflare
  headers and a forged `X-Forwarded-For` prefix is logged exactly once as `[<time>] cf-ip=… country=…
  "GET /.git/config?pov210=probe HTTP/1.1" 404 … <ms> xff="<forged>" …` (nothing before the
  timestamp) and never as `/index.html`; a request with the API key is logged without the key.
- **RUNBOOK:** §13.5 "Production Logs & Request History" (real traffic path, where the logs live,
  field meaning and trust, reading the volume from an `alpine:3.24.2` container), §13.6 "Checking
  for Suspicious Traffic" (Cloudflare, access-log `awk` recipes, the Prometheus query and its
  limits); eight `[!WARNING] Out of date (POV-210)` marks on claims that no longer hold (Caddy on
  the path, Grafana `localhost:3000` / `grafana.<domain>`, OCI host ×3, `Secure` cookie via Caddy).
- **Backlog:** new — "Put Caddy on the traffic path" (High), "Suspicious-traffic detection and
  alerting", "SPA fallback answers 200 to probe paths", "Micrometer `uri`-tag explosion",
  "Privacy policy"; pointers added to the OCI-RUNBOOK, `grafana.juresa.si`, security-header-test and
  Grafana-alerting entries.
- **Outside the repo (PO-approved, before the task):** Cloudflare "Always Use HTTPS" enabled
  (`http://` → `301`); a prototype Caddy file-log config deployed and reverted.

## 2. Incident record — the 2026-09 bot wave

Source: Prometheus `http_server_requests_seconds_count`, `max_over_time(…[30d])` exported
2026-09-27 (`agent_logs/POV-210 prometheus-http-requests-2026-09-27.json`, 428 series, 338 distinct
`uri` values, samples from 2026-09-11 12:59 UTC). **Lower bounds only:** counters reset on every
app restart, Micrometer stops creating `uri` tags after 100 distinct paths per run, and the metrics
hold no IPs, User-Agents or query strings.

Counted as probes: **202 paths** — every `uri` value except the app's own routes and buckets
(`/api/v1/*`, `/assets/*`, `/locales/*`, `/onboarding/*`, `/static/*`, `/ui*`, `/dashboard`, `/login`,
`/favicon.svg`, `root`, `REDIRECTION`, `NOT_FOUND`) and 14 `/.well-known/acme-challenge/*` paths
(Caddy's failing Let's Encrypt challenges, answered by the SPA shell). Unmatched 404s without their
own tag fall into Quarkus' `NOT_FOUND` bucket: **866 requests**.

| Category | Probe paths | Requests (≥) | Status (requests) | Examples |
| --- | --- | --- | --- | --- |
| WordPress / PHP | 69 | 106 | 200 × 86, 404 × 17, 400 × 3 | `/wp-admin`, `/wp-json`, `/wp/v2/users`, `/index.php`, `/admin.php`, `/cgi-bin/admin.php` |
| Admin / login panels | 18 | 37 | 200 × 37 | `/admin`, `/admin/login`, `/console`, `/backoffice`, `/panel`, `/signin` |
| Debug / profiler / actuator | 26 | 36 | 200 × 36 | `/actuator/env`, `/actuator/heapdump`, `/_profiler/phpinfo`, `/_ignition/logs`, `/debug/pprof`, `/server-status` |
| Other | 19 | 31 | 200 × 31 | `/__vite_rsc_findSourceMapURL`, `/_image`, `/_src`, random tokens |
| SSH / cloud credentials | 24 | 29 | 200 × 29 | `/.ssh/id_rsa`, `/.ssh/id_ed25519`, `/.aws/credentials`, `/.kube/config`, `/.config/gcloud/…`, `/.gem/credentials` |
| Git / VCS metadata | 19 | 26 | 200 × 26 | `/.git/config`, `/.git/HEAD`, `/.git/index`, `/.git/logs/HEAD`, `/.svn/entries`, `/.hg/hgrc` |
| Env / config files | 16 | 19 | 200 × 17, 404 × 2 | `/.env` (404), `/env`, `/config`, `/Dockerfile`, `/Procfile`, `/Vagrantfile` |
| Process / container secrets | 7 | 10 | 200 × 10 | `/@fs/proc/self/environ`, `/@fs/..%252f…proc/self/environ`, `/proc/self/cmdline`, `/var/run/secrets/kubernetes.io/serviceaccount/token` |
| GraphQL / APIs | 4 | 5 | 200 × 5 | `/graphql`, `/v1/graphql`, `/model/info`, `/pools/default/buckets` |
| **Total** | **202** | **299** | | |

**What the bots got:** **188 of the 202 probe paths were answered `200`** — by
`SpaRoutingHandler`, which reroutes every path without an extension in its last segment (outside
`/api`, `/q`, `/static`, `/assets`, …) to `/index.html`, i.e. the SPA shell. The other 14 never got
`200`: `/.env`, eleven other `*.php` paths and `/wp-json/batch/v1` → `404`, `/index.php` → `400`. Static
files are served only from `META-INF/resources` (favicon, locales, onboarding PNGs, `static/css|js`,
the built `assets/`) — no dotfiles, no filesystem paths. **No evidence that a secret or file
content was served.** Not verified byte-by-byte for each path (a live probe was refused as
credential exploration; the PO may compare e.g. `/.git/config` with `/index.html`). Matched
`/api/v1` calls are also in the `audit_log` table (`AuditLogFilter`); it was not queried for this
record.

## 3. Planned vs. actual scope

The approved scope (Brainstorm, 10 decisions) grew in planning by the Caddy direction (3a/3b: long
term Caddy becomes the HTTP layer — backlog) and shrank by planning decision 11: after two plan
review rounds the PO dropped age-based retention (the planned `LogFileRetentionTask`, daily
rotation and the GDPR retention statements) — logs rotate by size only. Implementation matched
the approved plan rev 3.

## 4. Time delta

Estimated ~4.5 h (incl. ~1 h CI + deploy + live verification, still pending). Actual up to the
commit proposal ≈ **3 h 03 min** of work:

| Phase | Time (CET) | Minutes |
| --- | --- | --- |
| Brainstorm | 07:50 → 08:46 | 56 |
| Planning incl. plan review rounds 1 (09:24 → 09:31) and 2 (09:43 → 09:51), rev 2/3 | 08:46 → 10:02 | 76 |
| Implementation (phases 1–4) + close-out drafts | 10:04 → 10:14 | 10 |
| Code review round 1 + fixes | 10:14 → 10:26 | 12 |
| Code review round 2 (active; the first attempt stopped at an API session limit, restarted ~11:41) | 10:26 → 11:50 | ~10 |
| Fixes for round 2 (PO decision at 13:04) | 13:04 → 13:08 | 4 |
| Code review round 3 | 13:08 → 13:19 | 11 |
| Fixes for round 3 + lessons + this delta (PO decision ~13:20) | ~13:20 → 13:24 | ~4 |

Elapsed 07:50 → 13:24 (5 h 34 min) includes ~1 h 14 min session-limit pause and ~1 h 15 min
waiting for PO decisions. Planning took longer than estimated (two plan review rounds, the
retention cut); implementation was far shorter (~60 changed lines after decision 11); the three
pre-commit review rounds (~40 min incl. fixes) exceeded the ~0.5 h review allowance slightly.

## 5. Deviations

- **D1 — YouTrack issue created after the Brainstorm** (PO decision), not at its start.
- **D2 — Caddy direction:** the backlog entry is "put Caddy on the traffic path", not "remove
  Caddy" (planning 3a); Brainstorm decisions 6 and 10 superseded in that respect.
- **D3 — Whole-stack recreate at deploy** (planning 6) instead of `up --no-deps app`, so the
  `json-file` caps take effect on all six services.
- **D4 — Plan review round 1** (subagent, 15 findings) — all applied (planning 8–10), incl. the
  SPA probe answering 404 in tests, `record-request-start-time`, `%h` spoofability, OIDC callback
  code in the query (PO kept the full request line after weighing forensics, planning 9).
- **D5 — Plan review round 2** (subagent, 9 findings) → planning 11: the PO dropped age-based
  retention; field order `cf-ip`/`country` before the client-controlled fields; Phase 6 deploy
  rules; API test asserts 401.
- **D6 — Test DB container** `povzetkar-db-test` had to be started (stopped since the host restart).
- **D7 — Time Log entry corrected:** Phase 3 was first logged with an estimated time (10:14) and
  corrected to 10:09 from the image build timestamp.
- **D8 — Pre-commit review round 1** (`/code-review high` on `git diff v0.19.0`; the
  `security-review` skill's built-in diff covered the wrong range and was replaced by an equivalent
  manual security review): 7 findings, all fixed — the RUNBOOK `sed` could be hijacked by a
  User-Agent carrying a forged log prefix (now anchored, `-n … p`); a stale proxy comment in
  `application.properties` (client `X-Forwarded-*` headers reach the app through the tunnel —
  backlog); public share UUIDs in logged paths (RUNBOOK, privacy backlog); the incident counts
  (recomputed with stated exclusions); `audit_log` as an existing partial record; reading the log
  via `--volumes-from`; this time delta.
- **D9 — Pre-commit review round 2** (`code-review` skill on `git diff HEAD` = `v0.19.0`, all 10
  files; manual security review against the Quarkus 3.39.5 / Vert.x 4.5.34 sources): 4 findings,
  all fixed — **`%h` first in the pattern let a client-sent `X-Forwarded-For` forge a whole line
  prefix** (Medium; `%h` dropped, `xff="%{i,X-Forwarded-For}"` logged after the status — a change
  to the approved pattern, PO-approved; test and RUNBOOK recipes updated); "twelve" → eleven
  `*.php` paths here; 333 → 338 `uri` values in the backlog; the spoof recipe relabelled (it also
  lists requests that bypassed the tunnel).
- **D10 — Pre-commit review round 3** (same scope and method): the round-2 change is sound; 6
  findings — 5 fixed in the RUNBOOK/backlog/this walkthrough: the `sed` anchored on the fixed log
  prefix (Medium: `access-log] …` text in another logger's message, e.g. a topic name, could pose
  as an entry; newline injection noted as not covered), the bypass-path recipe claim softened
  (such lines can be dropped or look clean), the dot-segment recipe ignores the query string,
  `record-request-start-time` added to the backlog removal list, the full-suite result added
  here; the sixth (lessons learned) is part of close-out. The PO waived a fourth round.

## 6. Verification evidence

- **TDD red (Phase 1, before the config):** the probe was collected as
  `127.0.0.1 - - [27/Sep/2026:10:05:24 +0200] "GET /index.html HTTP/1.1" 404 240` — **twice**; the
  API request as `… "GET /api/v1/system/metadata HTTP/1.1" 401 -` (no User-Agent, `cf-ip`, country
  or duration).
- **Green (Phase 2):** `AccessLogTest` 2/2, `QuarkusConfigKeysTest` 1/1 (all new `quarkus.*` keys
  are real config).
- **Full backend suite** (`./mvnw test -pl backend`) after Phase 4 and after each review-fix
  round (1, 2): 364 tests, 0 failures, 0 errors, 3 skipped (pre-existing: `GenAiPoolConcurrencySpikeTest`,
  `ContentSourceResourceTest`, `TopicResourceTest`). Round 3 changed documentation only.
- **Infra:** `scripts/qa/validate-infra-configs.sh` → "Infra configs valid." (compose, Caddyfile,
  Prometheus); six `logging: *default-logging` references.
- **Image:** local build `povzetkar-app:pov210-check` → `drwxr-xr-x 65532/65532 … var/log/app/`.
- **RUNBOOK recipes:** the `sed` extraction and all four `awk` recipes, copied verbatim from §13.5 /
  §13.6, run on synthetic access-log lines: fields extracted correctly, a forged `cf-ip=` inside the
  User-Agent ignored, a spoofed `%h` detected. After review round 1 the `sed` is anchored at the line
  start (`^[^"]*access-log\]`, `-n … p`): a User-Agent carrying a whole forged prefix
  (`access-log] (t) 1.1.1.1 [..] cf-ip=9.9.9.9 … "GET /harmless" 204`) no longer replaces the real
  fields, and an unparsable line is dropped. After review round 2 (`%h` dropped, `xff=` added) the
  recipes, again copied verbatim, were re-run on new synthetic lines: an `X-Forwarded-For` carrying
  a forged `… cf-ip=1.1.1.1 country=SI "GET /harmless …" 204` prefix leaves the real
  `198.51.100.9 RU 200 GET /.ssh/id_rsa` intact and is listed by the `cf-ip ≠ first hop` recipe, as
  are a bypass line (`cf-ip=-`) and missing/empty `xff`. Red first: before the pattern change
  `AccessLogTest` captured the line starting `198.51.100.1 [x] cf-ip=198.51.100.1 country=XX
  "GET /harmless HTTP/1.1" 204 [27/Sep/2026:13:05:17 +0200] cf-ip=203.0.113.7 …`. After review
  round 3 (prefix-anchored `sed`, query-aware dot recipe), copied verbatim again: a `TopicResource`
  line carrying a forged `access-log] … "GET /forged …" 200` is rejected, console- and file-format
  (`host process[pid]`) entries parse, `/x?u=https://a.b/.well-known` is no longer a dot probe. The `audit_log` query was run against the test
  database (syntax only). The `--volumes-from povzetkar-app:ro` read is verified live after the
  deploy (the running container has no volume yet).
- **Line endings:** the changed files keep CRLF (`file`); scripted edits (compose, RUNBOOK marks)
  inspected with `git diff` — additions only.

## Traceability

N/A — infrastructure/security task, no user-facing functional change.
