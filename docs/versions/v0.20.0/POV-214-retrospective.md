# POV-214 Retrospective — Remove Caddy: Security Headers, Caching and Proxy Trust in the Application

## 1. Planned vs. actual

The task was carried forward from v0.19.0 as "put Caddy on the production traffic path". The Brainstorm's first
question — what does Caddy still give us behind the Cloudflare tunnel? — reversed it: every OCI-era role of Caddy
is covered by Cloudflare/`cloudflared` except the security headers, and those are simpler and testable in the app.
The PO chose to remove Caddy (ADR-0012). Reading the live stack during the Brainstorm added two things nobody had
planned: production metrics had been down for a day (the dev compose had taken over the prod Prometheus/Grafana —
fixed operationally at once, structurally in Phase 1), and every static file was cached as `immutable` for 24 h
(caching policy added to the scope). Estimate ~15.25 h after the Brainstorm reviews; everything in scope was
delivered in the code and documents; the deploy (Phase 8) follows the push.

## 2. Deviations & why

- **Three internal review rounds on the task definition** (PO request after each): 8, 6 and 7 findings (R1–R15 plus
  corrections without an R number), most of them
  claims copied forward instead of re-checked — a single strict CSP would break the legacy UI, e2e could not see
  Quarkus' headers (Vite dev server), `/static` and the locale files had the same staleness as `index.html`, a
  fixed subnet needs a network recreate, the backlog entry named out of scope no longer existed. The third round,
  which checked every claim against code and live state instead of earlier notes, found as much as the first two.
- **One plan review** (V1–V8) against the Quarkus/Vert.x sources: it resolved the open question whether a filter can
  override the static handler's `Cache-Control` (yes — `addToMapIfAbsent`), found that overlapping filters merge
  values, and fixed three plan defects (a dev-only path in the pre-paint e2e tests, Swagger UI under the strict CSP,
  `tsconfig.node.json`).
- **Four discoveries during implementation**, all fixed within the task: CRLF checkouts would have changed the CSP
  hash (`.gitattributes`); Zod v4's JIT probe triggers a CSP violation (`jitless`); a fixed container address needs
  an `ip_range`; `compose run` in the infra validation would create a network overlapping the prod subnet.
- **Plan refinement P5:** the access-log file handler is enabled in `%prod`, not via compose environment, so handler
  and category routing can never be switched on separately.

## 3. Technical findings

- Docker Compose derives the project name from the directory: two compose files side by side are **one** project
  unless one sets `name:` — with shared service names, `up` of one recreates the other's containers.
- Quarkus `quarkus.http.filter.*` headers are set first (order `Integer.MIN_VALUE`) and merge across overlapping
  filters; Vert.x 4.5 `StaticHandler` only adds `Cache-Control`/`Last-Modified` if absent.
- `%u` in the Quarkus access log is the security identity's principal — and Quarkus' Google provider sets the
  principal claim to `name` (`KnownOidcProviders`), so `%u` logged the **display name**: user-controlled, with spaces
  and quotes, placed before the request line. The plan had inferred "Google `sub`" from `UserContextService`, which
  calls `jwt.getSubject()` explicitly; the API-key test could not show it. Found in the deploy smoke test after the PO
  signed in; fixed with `AccessLogPrincipalRoute` (sub / mapped e-mail, safe characters).
- Quarkus resolves `trusted-proxies` hostnames per request; a fixed IP is the recommended form.
- The browser under a real CSP is the only check that the build did not alter a hashed inline script and that no
  library needs `eval`.

**Process-weight evidence (POV-212 feed-forward):** review findings — task definition 8 / 6 / 7 over three rounds,
plan 8; pre-commit one round (PO decision): code review 9 (7 fixed, 1 rejected, 1 PO step), security review 0
security + 1 build defect (a missing `COPY` that no test could see). The pre-commit round found defects the earlier
document reviews could not (runtime/build behaviour); the three definition rounds found scope defects the code
review could not. YouTrack record and Time Log cost: see the work items.

## 4. Feed-forward

- **Deploy of POV-214:** the first deploy recreates the prod network (RUNBOOK §10.4a) — full stack down/up, PO picks
  the time; smoke test incl. Google login, `Secure` cookie, `access.log` with `user=<sub>`, Prometheus target `up`.
- **Every change to the pre-paint script in `frontend/index.html` needs a new hash** in `csp-strict`
  (`CspInlineScriptHashTest` prints the mismatch; the `csp` Playwright project confirms the built output).
- **New frontend libraries must pass the `csp` project** — no `'unsafe-eval'`, no new external origins without a
  policy change.
- **POV-215 (orval):** regenerated clients run under the strict CSP in the `csp` project.
- **PO steps:** `DB_PORT=5434` done in `backend/.env.example` (this commit) and the local `backend/.env`; remove
  `DOMAIN_NAME`/`CADDY_EMAIL` from `.env.docker` (backlog).
- **Re-check claims against the source, not against the previous note** — the review round that did so found the most.
- **`/security-review` cannot take a range** — it diffs the branch against `main`, while the pre-commit review runs
  on uncommitted changes against the version branch. Tell the subagent to apply the skill's passes to
  `git diff <base>` explicitly instead of "make the skill review X" plus "no manual review" (a contradiction that
  forced a rule break this time). Protocol change in the backlog (PO decision).
- **A Dockerfile that lists files by name** breaks silently when a config imports a new file — build the image stage
  (`docker build --target build-frontend`) whenever the frontend build inputs change.
