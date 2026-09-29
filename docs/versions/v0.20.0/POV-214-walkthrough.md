# POV-214 Walkthrough — Remove Caddy: Security Headers, Caching and Proxy Trust in the Application

**Task:** [POV-214](https://jumis.myjetbrains.com/youtrack/issue/POV-214) (Task, Major) · **Branch:** `POV-214` → `v0.20.0` ·
**Date:** 2026-09-29 · **Decision record:** [ADR-0012](../../adr/0012-cloudflare-tunnel-only-edge.md)

## 1. What was done

The carry-forward "put Caddy on the production traffic path" was reshaped in the Brainstorm: behind the Cloudflare
tunnel Caddy's only missing function was the security headers, so Caddy was **removed** and the app now owns the
HTTP edge concerns.

**Dev and prod compose separated** (`backend/docker-compose.yml`) — `name: povzetkar-dev`, own container names
(`povzetkar-dev-prometheus`, `povzetkar-dev-grafana`), dev DB on 5434, dev Grafana on 3001. Both files had formed
compose project `backend`: a dev `up` on 2026-09-28 had replaced the prod Prometheus/Grafana (restored operationally
during the Brainstorm, 10:38). Applied locally with `docker rm` of the exited dev containers (not `down`, which would
have hit the prod services) — prod containers unchanged.

**Security headers and CSP** (`application.properties`, section *HTTP SECURITY HEADERS & CACHING*):
HSTS, `X-Frame-Options`, `nosniff`, `Referrer-Policy`, `Permissions-Policy` via `quarkus.http.header`; two
Content-Security-Policies as `quarkus.http.filter`s with mutually exclusive regexes — strict (SPA, API, assets;
`script-src 'self' 'sha256-…'`) and legacy (`/ui/*`, `/login`, `/logout`); `/q` excluded (Swagger UI in dev).
`frontend/index.html` is checked out with LF everywhere (`.gitattributes`) so the hash does not depend on the checkout;
`frontend/src/zodConfig.ts` switches Zod to `jitless` (its `new Function` probe violated the policy).

**Caching:** `/assets/*` `public, immutable, max-age=31536000` (Vert.x static handler, `static-resources.max-age=365D` — only on a served file, never on a 404); every other static path `no-cache` (was 24 h
`immutable` for everything); `/api` and the legacy pages untouched.

**Proxy trust:** `quarkus.http.proxy.trusted-proxies=127.0.0.1,[::1]` by default; the prod compose sets
`QUARKUS_HTTP_PROXY_TRUSTED_PROXIES=10.203.0.10` and gives `cloudflared` that address in a fixed subnet
(`10.203.0.0/24`, dynamic range `10.203.0.128/25`).

**Access log:** pattern with `ray=` and `user=` before the request line, `host=`/`ref=` after the status; own file
`/var/log/app/access.log` (20 × 10 MB) via a `%prod` named handler, out of `quarkus.log` and `docker logs`.

**Caddy removed:** compose service and volumes, `infra/caddy/`, the Caddy step of `validate-infra-configs.sh`
(`promtool` now via `docker run --network none`, no network with the prod subnet), CI step name, comments. Grafana
published on `127.0.0.1:3000` only.

**Documentation:** ADR-0012; RUNBOOK (§8.4, §10 incl. new §10.4a full-restart procedure, §13.2, §13.3, §13.5 access
log and recipes, §13.6, §14.1a, §16.1b, session cookie) — all eight "Out of date (POV-210)" notes resolved; NFR
SEC-1 restated, SEC-5/SEC-6; TC-SEC-009, TC-SEC-010, TC-MON-006, TC-UI-007; roadmap; backlog (five entries deleted,
two added); lessons (`lessons/infrastructure-ops.md`, highlight in `lessons-learned.md`).

**Verification**

- Red first, then green: `SecurityHeadersTest` (18/18 red before the config), `CachingHeadersTest` (8/13 red),
  `CspInlineScriptHashTest`, `TrustedProxyAcceptedTest.defaultTrustsOnlyLoopback`, `AccessLogTest` (2 cases red).
- Red demonstrations of the guards: one changed character in the pre-paint script fails `CspInlineScriptHashTest`
  (2 of 3 tests); a wrong hash in `application.properties` fails the Playwright `csp` project, and the browser names
  the real hash — equal to the source hash, so Vite does not alter the script.
- Full suite 2026-09-29 12:01: backend 479 tests, 0 failures, 3 skipped; Vitest 149/149; Playwright 28/28 (projects
  `chromium` and `csp`); `validate-infra-configs.sh` green (a broken `prometheus.yml` still fails); planning audit
  passed; `git grep -i caddy` outside docs/CHANGELOG empty.
- After the review fixes, 2026-09-29 12:20: backend 480 tests, 0 failures, 3 skipped; Vitest 149/149; Playwright
  28/28; `docker build --target build-frontend` OK; infra validation and planning audit green.
- Access-log sample (test): `cf-ip=- country=- ray=- user=apikey@test.local "GET /api/v1/system/metadata HTTP/1.1" 200
  220 32 host="localhost:8081" ref="-" xff="-" "pov214-user-test"`.
- Production verification is part of the deploy (after the push): headers via `curl -I`, Google login with a `Secure`
  `q_session`, `user=<Google sub>` in `access.log`, Prometheus target `up`, no host port but `127.0.0.1:5432/3000`.

## 2. Planned vs. actual scope

- Delivered as planned: Phases 1–7 (dev compose, headers/CSP, caching, proxy trust, access log, Caddy removal,
  documentation). Phase 8 (deploy) follows the push.
- Added during execution (2.13, all fixed within the task): `.gitattributes` for `frontend/index.html` (CRLF would
  change the CSP hash); `zodConfig.ts` (`jitless`); `ip_range` for the fixed address; `promtool` without `compose run`;
  `TrustedProxyAcceptedTest.defaultTrustsOnlyLoopback` (the profile tests alone did not pin the default);
  `QuarkusConfigKeysTest` allowlist for the four `filter.*.header` map entries.
- Refined in the plan (P5): access-log handler enabled in `%prod`, not via compose environment.
- Done by the PO (the agent may not read `.env*` files): `backend/.env.example` `DB_PORT=5434`, in this commit.
- Not done: `.env.docker` cleanup (`DOMAIN_NAME`/`CADDY_EMAIL`, backlog, PO step); the `/security-review` protocol
  fix (backlog, PO decision).

## 3. Time delta

Estimate ~15.25 h for the whole task (incl. PR loop, deploy and YouTrack record).

| Phase (2026-09-29, CET) | Actual |
| --- | --- |
| Brainstorm 09:47–10:59 (Q1–Q8, live checks, monitoring restored, three internal review rounds) | 72 min |
| Planning 10:59–11:34 (source reading, P1–P6, plan, plan review V1–V8) | 35 min |
| Implementation 11:34–11:57 (Phases 1–7, tests first, four discoveries) | 23 min |
| Full suite 11:57–12:01 | 4 min |
| Close-out documents 12:01– | see the Time Log |

Pre-commit review rounds: see §4.

## 4. Deviations

- Operational action in the Brainstorm (PO request): production Prometheus/Grafana recreated from the prod compose
  at 10:38; target `app:9000` `up` again.
- The four implementation discoveries and the P5 refinement listed in §2 (details and evidence in the task's
  `## Deviations`).
- `CachingHeadersTest`: the plan expected `/login` to keep its own `no-store`; anonymous `/login` sends no
  `Cache-Control` (only the signed-in redirect does), so the test asserts that the filters leave legacy pages alone.
- `npm run test` (root) cannot run on Windows (`cmd` and `./mvnw`) — pre-existing; the suite ran as
  `./mvnw test -pl backend -am` + `npx vitest run` (backlog entry added).
- **Pre-commit review round 1** (one round — the PO decided on no further rounds):
  - `/security-review`: no security findings. The skill's own diff was the branch against `main` (it takes no
    range), so the subagent applied its passes to `git diff v0.20.0`. One build finding fixed: the Dockerfile's
    frontend stage did not copy `csp-policy.ts` (image build would fail) — verified with
    `docker build --target build-frontend`.
  - `/code-review high v0.20.0...HEAD` (uncommitted diff, 43 files): 9 findings — 7 fixed (subnet moved to
    `10.203.0.0/24` outside Docker's default pools; 404s under `/assets/` no longer cached as immutable —
    `static-resources.max-age=365D` instead of a filter; zod out of the entry chunk; CSP violations reported once;
    tolerant property parsing; order-independent trust-list test; documented that the remote address is the
    client-settable first `X-Forwarded-For` hop), 1 rejected (preview build on every Playwright run — CI runs both
    projects), 1 PO step (`backend/.env.example` `DB_PORT=5434`, before the commit). A stray stopped
    `cloudflared:latest` container (`gifted_davinci`) on the prod network was found and added to the deploy
    procedure (RUNBOOK §10.4a).

## 5. Traceability

- **US:** N/A — infrastructure/security task, no user-facing functional change.
- **UC:** N/A — no use case changes; the edge concerns are non-functional (NFR SEC-1, SEC-5, SEC-6).
- **TC:** TC-SEC-009, TC-SEC-010, TC-MON-006, TC-UI-007, TC-UI-006
