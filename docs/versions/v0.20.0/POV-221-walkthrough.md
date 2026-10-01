# POV-221 Walkthrough — Edge Hardening: 404 for Probe Paths, Bounded `uri` Tag, 401 for Unauthenticated XHR, CORS Removed

**Task:** [POV-221](https://jumis.myjetbrains.com/youtrack/issue/POV-221) (Task) · **Branch:** `POV-221` → `v0.20.0` ·
**Date:** 2026-10-01 · **Decision record:** [ADR-0012](../../adr/0012-cloudflare-tunnel-only-edge.md) (Update POV-221)

## 1. What was done

Three production defects at the application edge from the backlog (POV-208, POV-210) plus one found in the
Brainstorm, all fixed in the app (it is the edge since POV-214):

**SPA fallback as an allow-list** (`ui/util/SpaRoutingHandler`): `index.html` is served only for the frontend's
top-level routes (`SPA_ROUTE_PREFIXES` = `/dashboard`, `/me`, `/topics`, `/briefings`, `/sources`, `/admin`, `/s`,
matched as a whole first segment and case-insensitively like the TanStack router, no dot-segments, no file
extension — `isSpaRoute`). Any other path that reaches the fallback answers a bare `404` (was `200` with the SPA
shell for every extensionless path). `/`, files and the excluded prefixes (`/api`, `/ui`, `/q`, `/auth`, `/assets`,
… — whole first segment) keep their own handlers. `SpaRouteAllowListTest` reads every `createRoute` in
`frontend/src/router.tsx` with its parent and fails if a route reachable as a top-level URL (including a relative
path under a pathless layout) is not on the allow-list, or if a route has no literal `path`/`id`;
`RepositoryFiles.findInRepository` (test utility, extracted from `CspInlineScriptHashTest`) locates the file.

**Bounded `uri` tag** (`config/HttpMetricsConfig`, a `MeterFilter` on `http.server.*`): untemplated SPA routes →
`SPA`, files outside `/api` → `STATIC`; `quarkus.micrometer.binder.http-server.suppress4xx-errors=true` turns
untemplated 4xx into `UNKNOWN`. API templates and `NOT_FOUND`/`REDIRECTION`/`root` stay. Traffic can no longer
reach Micrometer's 100-value limit.

**401 for unauthenticated XHR** (`security/XhrAuthenticationChallenge`, a `JavaScriptRequestChecker`, with
`quarkus.oidc.authentication.java-script-auto-redirect=false`): a request with `X-Requested-With: XMLHttpRequest`
or `JavaScript` and no session gets `401` + `WWW-Authenticate: OIDC`, independent of `Origin`; a browser navigation
keeps the 302 to Google. The SPA's existing 401 interceptor (ADR-0010) now fires in production. The Vite dev-proxy
rewrite (302→401), which masked the bug in dev, is removed.

**CORS and `FRONTEND_URL` removed:** `CorsPreflightHandler`, `OriginValidator`, the `povzetkar.cors.*` keys,
`LoginResource`'s absolute redirect base (login/logout redirects are relative; the open-redirect guard in
`resolveSafeDestination` is unchanged and the redundant `/auth/login` fallback branch is gone), `VITE_API_BASE_URL`
(`client.ts`, `useJobEvents.ts`), `FRONTEND_URL` in `docker-compose.prod.yml` and `validate-infra-configs.sh`. The PO
removed the lines from both `.env.example` files (permission-blocked for the agent).

**Documentation:** ADR-0012 "Update (POV-221)"; RUNBOOK (dev env samples, `.env.docker` table + note on the unused
variables, §13.2 `uri` cardinality check, §13.6 probe and metrics text); `application.properties` comments; backlog
(three entries removed, "Suspicious-traffic detection" updated, "Friendly SPA 404 page" added, two pre-existing
dev-start defects recorded); TC-SEC-011/012/013, TC-MON-007; CHANGELOG.

**Verification**

- Red first, then green: `SpaRoutingHandlerTest` (9 probe paths answered 200 with the shell; 26/26 green after),
  `SpaRouteAllowListTest` (red on all 9 route paths before `isSpaRoute`; red again with a fake `path: '/reports/$id'`
  line in `router.tsx`, reverted), `HttpMetricsTagsTest` (literal `/sources/1`, `/dashboard`, `/s/<uuid>`,
  `/assets/test-asset.js` before the filter; with `-Dquarkus.micrometer.binder.http-server.suppress4xx-errors=false`
  the meters carry `/api/v1/no-such/1`, `/2`), `UnauthenticatedXhrTest` (same-origin and cross-origin XHR → 302, the
  production bug; 5/5 green after), `NoCorsTest` (`Access-Control-Allow-Origin: http://localhost:5173`; 2/2 after),
  `LoginResourceRedirectTest` (`Location: http://localhost:5173/...`; 7/7 after).
- A test `index.html` stand-in (`src/test/resources/META-INF/resources/index.html`) lets tests tell the rerouted
  shell from a 404 — before, a rerouted probe answered 404 in tests and 200 in production, which hid the bug.
- Full suite 2026-10-01: backend `clean test` 532 tests, 0 failures, 3 skipped (run 1: one failure in the new
  `HttpMetricsTagsTest`, a test defect — see §4); `tsc -b` clean; ESLint 27 errors / 2 warnings (= baseline);
  Vitest 29 files green; Playwright 28 passed (projects `chromium` and `csp`); `validate-infra-configs.sh` green.
- After the review fixes (rounds 1 and 2): backend `clean test` 549 → 546 tests (duplicate parameterised cases
  merged), 0 failures, 3 skipped; planning audit passed. The frontend was not touched by the fixes.
- Live dev check (Quarkus dev + Vite, through `:5173`): XHR without session → `401` `WWW-Authenticate: OIDC`, no
  `Location`; browser GET → `302` Google; `/logout` with `Origin`/`Referer` `:5173` → `Location: /`;
  `/auth/login?redirect=/sources/7` → Google with `redirect_uri=http://localhost:5173/api/oidc/callback`; preflight
  → no `Access-Control-*`; probes on 8080 → 404. PO in the browser: Google login lands on `:5173/dashboard`, SSE
  indicator green ("connected"), logout returns to `:5173/`.
- Production verification is part of the deploy (after the push): probe paths 404, SPA routes 200, XHR without
  session 401, browser GET 302, `uri` values via the management port.

## 2. Planned vs. actual scope

- Delivered as planned: Phases 0–6 (branch, SPA allow-list, bounded `uri` tag, XHR 401, CORS removal, docs,
  verification). Phase 7 (deploy and prod checks) follows the push.
- Corrected during execution: the plan's finding that an unauthenticated `/api` request carries no template was
  wrong for **existing** endpoints (they keep `/api/v1/briefings/{id}`); the untemplated 4xx come from requests to
  paths that match **no** endpoint (`/api/v1/no-such/7`, `POST /api/v1/briefings/9`, `/ui/no-such-page/7` → 401),
  which a scanner chooses freely — `suppress4xx-errors` stays, with that justification, and the test sends those
  paths.
- Settled as the plan anticipated (P4): Quarkus tags a rerouted path with its original path, so `SPA` works.
- Added: `backend/.env.example` and RUNBOOK `CORS_METHODS`/`CORS_HEADERS` rows (not in the plan's footprint);
  test cases TC-SEC-011/012/013 and TC-MON-007 (the plan had "spec sync N/A"; POV-214 set the precedent of test cases
  for edge behaviour); two backlog entries for pre-existing dev-start defects.
- Not done: removing the unused `FRONTEND_URL`/`CORS_*`/`VITE_API_BASE_URL` values from the untracked local files
  (`.env.docker`, `backend/.env`, `frontend/.env`) — harmless, optional PO cleanup (RUNBOOK note).

## 3. Time delta

Estimate ~7.5 h for the whole task (incl. PR loop, deploy and YouTrack record).

| Phase (2026-10-01, CET) | Actual |
| --- | --- |
| Brainstorm 19:34–20:11 (Q1–Q9, task definition approved) | 37 min |
| Planning 20:11–20:43 (P1–P4, plan approved, archived) | 32 min |
| Session hand-over 20:43–20:50 | 7 min |
| Implementation 20:50–21:52 (Phases 0–6 incl. two full suite runs and the live dev check with the PO) | 62 min |
| Close-out documents 21:52– | see the Time Log |

Pre-commit review rounds: see §4.

## 4. Deviations

- Plan finding on untemplated `/api` 401s corrected (§2); a temporary exploration test (deleted) established which
  requests produce untemplated non-404 4xx.
- Test utility in the existing `test.util` package (`RepositoryFiles`), not a new `testutil` package;
  `AccessLogFileTest` was a second caller of `CspInlineScriptHashTest.findInRepository` and uses it too.
  `AccessLogTest.spaReroutedProbeIsLogged…` renamed to `probeIsLogged…`.
- `/api/v1/no-such-endpoint` dropped from the "excluded prefixes keep their own 404" cases — unauthenticated, it is
  answered by the OIDC challenge before routing.
- Full suite run 1: `HttpMetricsTagsTest.noHttpServerMeterCarriesARequestPath` failed in the full suite only — its
  substring check matched legitimate API templates registered by other test classes (`/api/v1/sources/{id}`). Fixed
  with an exact match against the requested paths (constants); still red without `suppress4xx-errors`.
- Live dev check moved after the docs/full suite (one PO interaction). Starting dev mode hit two pre-existing defects
  (backlog): root `npm run dev` fails under Windows `cmd` (`../mvnw`), and `quarkus:dev` fails while the
  `config-guard` profile is active (`dependency-tree` mojo, `bootstrapId`) — worked around with `-DskipTests=true`.
- **Pre-commit review round 1** (2026-10-01, both on `git diff v0.20.0`, 36 files — not the empty
  `v0.20.0...HEAD` and not the skill's default diff against `main`):
  - `/security-review`: no security findings (open redirects, path tricks against the allow-list, CSRF after the
    CORS removal, OIDC flow, exposure). Its note on case-sensitive matching became code-review finding 4.
  - `/code-review high`: 6 findings, all verified — 5 fixed, 1 to the backlog. (1, Medium) files under an SPA prefix
    (`/admin/config.php`, `/s/shell.php`) got the shell with 200 and were tagged `SPA` — a regression against the
    task's goal; `isSpaRoute` now rejects files. (2) excluded prefixes matched character prefixes (`/quiz` → `/q`,
    `/apix` → `/api`), so such paths got a downstream 404 (JSON envelope) instead of the bare one — now whole
    segments. (3) the guard regex missed relative paths under the pathless protected layout and non-single quotes —
    now parsed per `createRoute` with parent resolution; red demonstrated with `path: 'settings'` under
    `protectedLayoutRoute` and `path: "/reports/$id"` (reverted). (4) case-sensitive matching while TanStack matches
    case-insensitively (`/Dashboard` 404) — now lower-cased. (5) RUNBOOK §13.6 wording contradicted (1)/(2) and
    omitted legitimate 200s — rewritten. (6) `EventSource` cannot send `X-Requested-With`, so an expired session
    still answers the SSE stream with 302 — pre-existing, backlog. Red before the fixes: 12 cases in
    `SpaRoutingHandlerTest`/`HttpMetricsTagsTest`; green after (42 + 2 + 2).
- **Pre-commit review round 2** (`/code-review high`, whole diff): no high/medium findings, round-1 fixes confirmed;
  6 low/nit — 4 fixed (RUNBOOK §13.6: `/logout` is a 302, `/favicon.svg` not `.ico`, public `/api` paths; a new
  `AccessLogTest` case for a rerouted deep link — the probe case no longer exercised the reroute; the guard now fails
  when any `createRoute` call is not parsed, red demonstrated with a typed binding; root `*.html` files go to the
  static handler), 1 nit fixed (duplicate test inputs), 2 nits rejected (case-sensitive `X-Requested-With` = Quarkus
  default and the Axios value; per-test traffic in `HttpMetricsTagsTest`). While fixing, a `perl` substitution
  inserted stray lines into `SpaRoutingHandler.java` and the IDE compiler left a broken class in `target/classes`;
  the build caught both, rebuilt with `clean`.

## 5. Traceability

- **US:** N/A — security/infrastructure fix, no user-facing functional change.
- **UC:** N/A — no use case changes; the session-expiry flow of TC-GRX-006 (ADR-0010) now works in production.
- **TC:** TC-SEC-011, TC-SEC-012, TC-SEC-013, TC-MON-007, TC-GRX-006
