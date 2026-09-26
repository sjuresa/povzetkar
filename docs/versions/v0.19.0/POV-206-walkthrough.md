# POV-206 Walkthrough — Upgrade build & runtime environment

**Task:** [POV-206](https://jumis.myjetbrains.com/youtrack/issue/POV-206) (Task) · **Branch:** `POV-206` → `v0.19.0` · **Date:** 2026-09-25 (brainstorm/planning) – 2026-09-26 (execution)

## 1. What was done

The build and runtime environment was brought up to date **within existing major lines**; majors are separate backlog tasks, so any regression maps to one cause. One commit per concern:

| Commit | Change |
| --- | --- |
| `09fc2c67` `test:` | Characterization test for `GET /api/v1/topics` paging with a collection fetch (3 topics × 2–3 sources; page 0 and 1 hold whole topics with all sources) — green on 3.36.3 **before** the upgrade. |
| `b54ebf33` `chore(deps):` | Quarkus **3.36.3 → 3.39.5** (Hibernate ORM 7.4.9). Netty, OpenTelemetry and pgjdbc overrides removed — the BOM resolves Netty 4.1.138, OTel 1.62.0, pgjdbc 42.7.13 (`dependency:tree`). **Hibernate 7.4 regression** worked around (see §4.1): `ContentItem.findByIdWithAssociations` → `singleResultOptional()`; admin history lists page over ids first, then load with fetches. |
| `06fce1b3` `chore(deps):` | Frontend `npm update` (all minor/patch, e.g. React 19.3, TanStack Query 5.104, Router 1.170.39, Tailwind 4.3.3, Playwright 1.63), orval pinned **exactly 8.5.3**, unused `ajv`/`ajv-formats` removed, **`npm ci`** in Dockerfile and CI. |
| `62abeb87` `feat(ci):` | orval `output.clean: true`; client regenerated from a `package -DskipTests` spec; CI step **"API client drift check"**; `frontend/.gitattributes` keeps the generated client LF. |
| `4766dd95` `chore(infra):` | Prometheus **v3.1.0 → v3.15.0**, Caddy **2.9.1 → 2.11.4**, Grafana **12.4.0 → 12.4.3**, cloudflared **`latest` → 2026.9.3** (flags `tunnel --no-autoupdate run --token` checked against the image); CI Node **25.8 → 24**, Postgres service **pg16 → pg17**; `scripts/qa/validate-infra-configs.sh` + CI step (compose config, `caddy validate`, `promtool check config` with the pinned prod images); root `.gitattributes` `*.sh eol=lf`. |
| `e7d521c6` `chore(security):` | `.trivyignore` audit: image scanned **without** the ignore file — none of the 7 older entries (no `exp:`) reported any more → removed; the 8 POV-207 entries (Debian 13, unfixed) still reported → kept with `exp:2026-12-31`. Backlog audit entry closed. |
| `16324ca4` `fix:` | Pre-commit `/code-review high` findings (§4.5). |
| `95656776` `chore:` | `OpenApiGeneratorTest` removed (PO) — the build already writes the spec; `frontend/.vite-react-ssg-temp/` (5 build artifacts tracked since POV-136, dead since POV-202) removed. |
| `075cf641` `chore(deps):` | Maven plugins: compiler 3.16.0, surefire 3.6.0, dependency 3.11.0, javadoc 3.12.0, SpotBugs 4.10.4.1, JaCoCo 0.8.15. |
| `b5577129` `chore(deps):` | `flexmark-all` → `flexmark` + `flexmark-ext-tables`; redundant explicit `quarkus-vertx` removed. Runtime `lib/main` **320 → 274 jars** (unused openhtmltopdf/pdfbox/icu4j/jsoup stack gone); image 501 MB → 459 MB. |
| `3e873ea5` `fix(api):` | `GET /api/v1/content-items?sort=id` returned **500** (`Ambiguous unqualified attribute reference 'id'` — the load query joins source and topics). Found by the unused-dependency/review follow-up; **pre-existing** (reproduced on 3.36.3). Sort fields now qualified with the item alias `c`; new test. |
| `4c564610` `fix:` | Second review round (PO-run): `sort=durationSeconds` → 400 instead of 500 (field exists only on `YouTubeVideo`; the frontend never sorts by it); `c.id` tiebreaker in `ContentItemApiResource`/`SourceItemApiResource` paging; admin history queries (12 per request) run only on the history tab; `User.findByIdentityWithRoles` → `singleResultOptional()`; `Summary.listByIdsWithAssociations`. |

Docs: RUNBOOK §2 (Node 24, `npm ci`), §8.2 (local Trivy, `exp:` policy), new §8.3 (API client regeneration + drift check) and §8.4 (infra config validation); CHANGELOG; backlog (deferred majors, orval upgrade — High, Grafana 13, PostgreSQL 18, Node 26, security-header test, review leftovers, upstream Hibernate report).

## 2. Planned vs. actual scope

Delivered all plan phases. The push was held by the PO from 11:16 to ~14:10 ("nadaljuj brez push"), so local equivalents of every CI step ran first; then PR #279 CI went green and production was deployed per plan Phase 8 (§5).

Additions beyond the plan: the Hibernate 7.4 workaround and its tests; `ajv` removal; two `.gitattributes`; `.vite-react-ssg-temp` removal; Maven plugin upgrade and flexmark narrowing (PO-approved scope extension — Maven plugins were missing from brainstorm decision #1).

## 3. Time delta

Estimated ~4 h. Actual: brainstorm + planning 2026-09-25 13:42 → 14:19 and 2026-09-26 08:39 → 09:21 (~1 h 20 min); execution 09:25 → 11:45 (~2 h 20 min, incl. first review and scope extension); close-out docs 11:45 → 11:55; second review round ~12:55 → 13:15 (~20 min); PR + CI 14:10 → 14:25, deploy 14:27 → 14:42 (~30 min incl. a retried build). Total ≈ 4 h 55 min vs ~4 h estimated — the Hibernate regression, two review rounds and the scope extension account for the difference.

## 4. Deviations

1. **Hibernate 7.4 regression outside the characterized query.** The Topic test stayed green, but 4 existing tests failed with HTTP 500 `column ci1_0.channel_id does not exist`: Hibernate 7.4.9 pages collection-fetch queries in a derived table that, for the SINGLE_TABLE root `ContentItem` (also joined to-one from `Summary`), omits the `YouTubeVideo` subclass columns. No opt-out setting exists; no upstream fix found. Fixed with the plan's "separate id query" technique (already used by `ContentItemApiResource`/`SourceItemApiResource`). Side effect: the `HHH90003004` in-memory-paging warning (41× per test run) is gone.
   *Correction (2026-09-26, standalone reproducer):* only a query **rooted** at `ContentItem` is affected. The to-one join from `Summary` generates correct SQL, so the admin Summary list uses the id-first pattern for consistency only. A per-query opt-out does exist: the hint `org.hibernate.limitInMemory` (there is no global setting). A second 7.4 bug was found: the plan cache shares one plan for the same HQL with and without a limit. Both bugs are unreported upstream; see [database.md](../../process/lessons/database.md#hibernate-74-paging-a-collection-fetch-over-a-single_table-hierarchy-pov-206).
2. **`ajv`/`ajv-formats` removed** — `npm update` hit ERESOLVE (`@hookform/resolvers` ≥5.5 peer `ajv-formats@^2`); neither is imported (grep), orval's parser carries its own.
3. **`OpenApiGeneratorTest` deletion blocked** for the agent by the Claude Code safety classifier; the PO deleted it.
4. **orval CRITICAL advisories** (`npm audit`, 12 GHSAs, ≤8.21.0) — not a regression (8.13.0 was affected too); build-time only, trusted input; PO's 8.5.3 pin kept, backlog entry priority High.
5. **Pre-commit `/code-review high`** (interim rule 1), 9 findings, 7 fixed: `prettier: true` without a prettier dependency made generator output machine-dependent → `false` (output byte-identical); `createdAt`-only ordering of paged ids → `createdAt DESC, id DESC`, alias-qualified (the new history test then caught an `Ambiguous unqualified attribute reference`); untested Google-login lookup (collection fetch + filtering join + limit) → extracted to `User.findByIdentityWithRoles` with a test (2 roles × 2 identities) — works on 7.4, query unchanged; three copies of the item loader → `ContentItem.listByIdsWithAssociations`; two admin paging helpers → one; redundant join in the summary id query; test cleanup scoped to its own rows. Not fixed → backlog: test-only endpoints in specs built with test classes; admin `page` overflow (pre-existing). `/security-review` not run — no auth/secrets change.
6. **Pre-existing API bug fixed in passing:** `?sort=id` on content items → 500; reproduced on the 3.36.3 POMs (same failure), so not an upgrade regression — the same run showed the Hibernate workaround also passes on 3.36.3. One-line fix (`QueryParser.parseSort(..., "c")`) plus test.
7. **Scope extension (PO):** `.vite-react-ssg-temp` removal, Maven plugins, flexmark narrowing, `quarkus-vertx`.
8. **Push held** (PO decision, 11:16 → ~14:10) — see §2.
9. **Second review round (PO-run, 7 findings):** 5 fixed (`4c564610`, above; both new tests red without the fix); #3 (test teardown `deleteAll` also removes the V32 demo seed — pre-existing pattern in 19 test classes) skipped by PO decision; #6 (Briefing list needs no id-first paging — only a to-one fetch) kept deliberately: one paging path, one extra query, now only on the history tab.
10. **Interim process rules** (POV-207 close-out, valid until POV-209) applied: pre-commit review, no PR reviewer, local Trivy FS + image with CI flags on a `--pull` build, YouTrack record per the POV-202 pattern at close-out.

## 5. Verification evidence

* **Characterization test** green on 3.36.3 (357 tests) and on 3.39.5.
* **Hibernate workaround red/green:** production fix stashed, new tests kept → 5 failures (`ContentItemApiResourceTest` ×3, `AdminResourceTest` ×2); fix restored → green.
* **Backend final:** clean `./mvnw verify -pl backend -am` — **362 tests, 0 failures, 3 skipped** (pre-existing `@Disabled`), **SpotBugs 4.10 0 bugs**, 0 `Unrecognized configuration key`, `QuarkusConfigKeysTest` green on 3.39.5 (no renamed keys).
* **Frontend final:** vitest 68/68, `npm run build` OK, Playwright E2E 11/11, `tsc -b` clean; lint identical to the baseline (32 errors / 2 warnings, message-level diff: none new, none gone).
* **Drift check red/green:** changed an `@Operation` description without regenerating → FAIL (`topics.ts` modified); committed client → PASS.
* **Infra validation red/green:** unknown Caddy directive → `caddy validate` fails; invalid Prometheus duration → `promtool` fails; real configs → pass. Production stack untouched (own compose project).
* **Docker:** `docker build --pull` of HEAD succeeds (`npm ci` in the build stage), image 459 MB.
* **Trivy (CI flags):** image `povzetkar:pov206-audit` exit 0; FS exit 0 — both on the Phase 6 build and re-run on the HEAD image (`--pull`, after the plugin/flexmark commits).
* **Build flake (environmental):** one `clean verify` failed with `No template found` for every Qute template — `target/classes/templates` vanished mid-build while the VS Code Java language server rebuilt after POM switches; the immediate re-run was green.
* **PR #279 CI:** green on `58a2ca74` (10 m 34 s) — incl. the new "API client drift check" and "Validate infra configs" steps; Greptile review 5/5, no inline threads.
* **Production deploy** (2026-09-26 14:27–14:42 CET, RUNBOOK §10.3): `pg_dump` → `%USERPROFILE%/povzetkar-backups/pre-POV-206-20260926-1427.sql` (125 MB, "dump complete"); rollback tag `povzetkar-app:0.19-SNAPSHOT-pre-POV-206` = `64c0a3d89a4d`; `build --pull app` (first attempt failed on a transient npm `ECONNRESET`, retry OK) → `24f9aa5505ab` (459 MB, was 498 MB); Trivy image (CI flags) exit 0; then `up -d --no-deps` per service:
  * **app** — healthy after ~35 s; "powered by Quarkus 3.39.5", started in 7.0 s, 0 `Unrecognized configuration key`, 0 WARN/ERROR, Flyway schema up to date; `https://povzetkar.juresa.si/` and `/api/v1/system/metadata` 200, `/q/metrics`, `/q/health`, `/q/openapi` 404.
  * **prometheus** — v3.15.0, target `povzetkar-backend` `up`, no error/warn.
  * **caddy** — v2.11.4, public URL 200. Pre-existing log noise, unchanged Caddyfile: ACME `challenge failed` for `grafana.juresa.si` (**NXDOMAIN** — the subdomain has no DNS record since the move off OCI; backlog), "Caddyfile input is not formatted", HTTP/2/3 skipped on :80.
  * **cloudflared** — 2026.9.3, 4 tunnel connections registered, 0 ERR, public URL 200.
  * **grafana** — 12.4.3, `/api/health` `database: ok`; dashboards provisioned; reaches Prometheus (`up` = 1); Prometheus holds app data (JVM and HTTP metrics). Two pre-existing provisioning errors (missing optional `plugins/`, `alerting/` dirs). Dashboard UI not opened (admin credentials not read by the agent).
  * Rollback per service: previous tag/image in the compose file (Prometheus v3.1.0, Caddy 2.9.1-alpine, cloudflared local `latest` image, Grafana 12.4.0) + `up -d --no-deps <svc>`; app: `povzetkar-app:0.19-SNAPSHOT-pre-POV-206`.

## 6. Traceability

N/A — infrastructure/dependency upgrade, no functional change.
