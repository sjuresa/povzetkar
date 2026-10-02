# POV-221 Retrospective — Edge Hardening: 404 for Probe Paths, Bounded `uri` Tag, 401 for Unauthenticated XHR, CORS Removed

## 1. Planned vs. actual

Three backlog entries (POV-208, POV-210) were bundled as one edge task; the Brainstorm added a fourth after asking
why the XHR 401 rewrite depended on `Origin`: the CORS mechanism and `FRONTEND_URL` served no client (every browser
client is same-origin) and trusted a development origin in production — removed in the same task (+~1 h). Estimate
~7.5 h. Implementation (62 min including two full-suite runs and the live dev check) delivered every in-scope item;
the deploy and prod checks follow the push. The task was handed over to a fresh session after the plan approval and
resumed from `task.md` and the plan file without loss.

## 2. Deviations & why

- **One plan finding was wrong** — "an HTTP-permission challenge happens before JAX-RS matching, so an
  unauthenticated `/api/...` request has no template". It was inferred from the code path, not observed. The red run
  showed existing endpoints keep their template under the OIDC 401; an exploration test then showed the real
  unbounded source (unauthenticated requests to non-existent `/api`/`/ui` paths). The decision (P1) survived, the
  justification and the test traffic changed.
- **A new test was wrong in the full suite only:** a substring assertion over the shared `MeterRegistry` matched
  templates other test classes had registered. Running the class alone could not show it.
- **Spec sync:** the plan said N/A; the POV-214 precedent (test cases for edge behaviour) made four test cases the
  consistent choice.
- **Live dev check:** the agent first claimed it could not start the backend because `backend/.env` is unreadable —
  a non sequitur the PO caught (Quarkus reads the file itself). The real question, which database dev mode uses, was
  answerable from `pg_stat_activity`. Starting dev mode then exposed two pre-existing defects (backlog).

## 3. Technical findings

- Quarkus Micrometer: a rerouted Vert.x request is tagged with its **original** path; an existing JAX-RS endpoint
  keeps its `uri` template even when the authentication challenge answers; only paths that match no endpoint are
  untemplated — 404 becomes `NOT_FOUND`, other 4xx the literal path unless `suppress4xx-errors=true`.
- Quarkus OIDC: `JavaScriptRequestChecker` is the supported hook for SPA requests; with
  `java-script-auto-redirect=false` its `getChallenge` answers instead of the redirect (default `499`, here `401`).
  The bean is picked up for the default tenant without further config.
- A Vert.x reroute to `/index.html` with POST/PUT/DELETE ends in 404 (the static handler serves GET/HEAD only).
- Tests that cannot see a production artefact (`index.html`) cannot see its bugs: a 3-line stand-in made the SPA
  fallback testable as in production.
- Assertions over a registry shared by the whole test run must match exactly what the test produced, not patterns.

**Process-weight evidence (POV-212 feed-forward):** Brainstorm 37 min (Q1–Q9), plan 32 min (P1–P4), no internal
document review rounds; implementation 62 min. Defects found during execution: one wrong plan finding (by the red
run), one test defect (by the full suite), one wrong claim to the PO (by the PO). Pre-commit review: see the
walkthrough §4.

## 4. Feed-forward

- **Deploy of POV-221:** after the deploy, `curl` the prod checks (probe 404, SPA 200, XHR 401, browser 302) and list
  the `uri` values via the management port (RUNBOOK §13.2). `FRONTEND_URL`/`CORS_*` in `.env.docker` are unused —
  removal optional.
- **Every frontend route change needs `SpaRoutingHandler.SPA_ROUTES`** — `SpaRouteAllowListTest` fails until the
  allow-list equals the router's URL patterns again (added, renamed or removed routes alike).
- **Separate frontend origin = CORS again:** if the SPA ever runs on another origin, CORS and absolute redirects must
  come back (ADR-0012 Update).
- **Suspicious-traffic alerting** can now alert on the `NOT_FOUND` rate without path filtering.
- **State a claim's evidence before stating a blocker** — "cannot do X because of Y" needs Y to actually block X;
  check the mechanism (who reads the file) before reporting it to the PO.
- **Plan findings inferred from code paths are hypotheses** — mark them as such, or observe them before the plan
  (a one-off exploration test is cheap).
