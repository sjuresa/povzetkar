# POV-234 Walkthrough — Security Dependency Bumps after the Java4Shell Rebuild

**Task:** POV-234 (Task, Major) · **Branch:** `POV-234` → `v0.21.0` · **Date:** 2026-11-20

## 1. What was done

After the Java4Shell emergency rebuild, CI on `v0.21.0` was red on the Trivy filesystem scan (12 HIGH), and because
that step failed, CI had not built or scanned the image either. This task fixes the findings and the CI blind spot.

- **Quarkus 3.40.1 → 3.40.1.2** (`pom.xml`): the LTS emergency release brings jackson-databind 2.21.8 and brotli4j
  1.24.0 (CVE-2026-107912, CVE-2026-110879, CVE-2026-111497).
- **Guava 33.7.2-jre** (CVE-2026-102554): the Quarkus BOM, also in 3.40.1.2, manages 33.6.0-jre; the only path is
  `google-api-services-youtube` → `google-api-client` 2.7.2. A direct `dependencyManagement` entry with the BOM
  entry's exclusions (`jsr305`, `listenablefuture` — a direct entry replaces the BOM's as a whole) and a comment
  (remove once the BOM brings ≥ 33.7.2).
- **Healthcheck builder `golang:1.26.10-alpine`** (CVE-2026-111943, Go stdlib `crypto/x509`).
- **Frontend** (`package-lock.json`): `follow-redirects` 1.16.3 (via `axios`), `seroval` 1.8.2 / `seroval-plugins`
  1.8.0 (via `@tanstack/router-core`, whose range `^1.6.2` allows 1.8), `vite` 6.4.6 (CVE-2026-112942 — not in the
  issue text, found in the CI log) — all within the declared ranges. `undici` 7.30.2 through an `overrides` entry
  scoped to `@scalar/json-magic` (`^7.30.1`): `@scalar/json-magic@0.15.6`, via the deliberately pinned `orval` 8.41.0, pins 7.29.1 exactly. Backlog:
  drop the override at the next orval bump.
- **`.trivyignore`**: a fresh `distroless/java25-debian13` (Debian 13.7) no longer reports the five libexpat1 entries
  of POV-207/POV-215 (libexpat1 2.9.0-1~deb13u1) — removed; the four libuuid1 entries are still `affected` — kept; four
  new libexpat1 findings without a Debian fix (CVE-2026-113351, -115506, -117610, -118370) added with
  `exp:2026-12-31`.
- **Dockerfile `JAVA_OPTS` removed**: the entrypoint runs `java -jar`, and the `java` launcher does not read
  `JAVA_OPTS`. Its value was not needed either: `QuarkusEntryPoint.main` (3.40.1 and 3.40.1.2, `javap -c`) sets
  `java.util.logging.manager` to the JBoss LogManager as its first instruction. A comment names `JDK_JAVA_OPTIONS` as
  the place for JVM flags — a mitigation flag set through `JAVA_OPTS` would silently not have applied.
- **CI** (`ci.yml`): the infra validation and the image build run also when the FS scan fails
  (`!cancelled() && (success() || steps.trivy-fs.outcome == 'failure')`); the image scan runs whenever the image was
  built (`!cancelled() && steps.build-image.outcome == 'success'`). The job stays red; one run shows both scans. A
  failure before the FS scan (tests) still skips all of them, as before.
- **Process**: every deploy checks `java -version` in the running container against the latest Temurin 25 release
  (`verification.md` *Pre-deploy Verification* items 3 and 4, checklist 2.5b revision 3.9, deploy evidence in
  `workflow-mandatory.md`); a lagging JDK is reported to the PO, not rolled back.
- **Documentation**: RUNBOOK §8.2 (image scan after a red FS scan; Trivy does not see the Temurin JDK in the distroless
  image — check `java -version`), §9.y (`JDK_JAVA_OPTIONS` replaces the `JAVA_OPTS` bullet, which called it
  "essential"); `LEGAL.md` (golang tag); backlog (undici override); lessons learned (a failing scan step hides the
  scans after it). Master Specification Sync: N/A (no US/UC/TC); planning audit (`validate-planning.py
  --current-version 0.21.0 --fail-on all`) passed.

**Verification:**

- `dependency:tree` (backend): guava 33.7.2-jre, jackson-databind 2.21.8, brotli4j 1.24.0, quarkus-core 3.40.1.2.
  `npm ls`: follow-redirects 1.16.3, seroval 1.8.2, seroval-plugins 1.8.0, undici 7.30.2, vite 6.4.6.
- API client drift check locally (`mvnw package -DskipTests`, `npm run gen:api`): no change in
  `frontend/src/api/generated`.
- Backend `mvnw clean verify`: exit 0, 645 tests, 0 failures or errors, 3 skipped; SpotBugs 0. Frontend
  `npx vitest run`: 151/151; `npm run build`: exit 0.
- CI failure path: run 44803971654 (`workflow_dispatch` on a temporary branch = `v0.21.0` + the CI change only, so
  the FS scan failed on the known findings): FS `failure` → infra validation `success`, image build `success`, image
  scan executed (`failure`), job `failure`. Its image findings for the unfixed state — four libexpat1 (Debian),
  guava 33.6.0-jre (jar), Go stdlib 1.26.9 (`healthcheck`) — are all covered by this task.

## 2. Planned vs. actual scope

| Planned | Actual |
| --- | --- |
| Issue: three frontend packages | Four — `vite` 6.4.3 (CVE-2026-112942) was in the CI log, not in the issue (Brainstorm) |
| Issue: Quarkus bump, Guava "check where it comes from" | The 3.40.1.2 BOM still manages Guava 33.6.0-jre — explicit entry needed (Brainstorm) |
| Issue: libexpat out of scope / wait | `.trivyignore` entries (otherwise "Done when" is unreachable); five old entries found fixed and removed (Brainstorm #1) |
| Not in the issue | CI runs the image scan after a red FS scan (Brainstorm #5) |
| Guava entry before the BOM import (Brainstorm #4) | After it — the rationale was wrong (§4) |
| `seroval` 1.6.x | 1.8.x, within `router-core`'s declared range (planning) |
| No process change | JDK check added to `verification.md` (items 3 and 4), checklist 2.5b and the deploy-evidence list in `workflow-mandatory.md` (review rounds 1–2, PO decision) |
| Deploy (2.5b) after the merge | Follows the merge; evidence in the walkthrough addendum (YouTrack) |

## 3. Time delta

Estimate 4 h 30 min (comparable: POV-206 4 h 46 min, POV-224 3 h 33 min, POV-229 2 h 35 min — booked).

| Phase (2026-11-20, CET) | Actual |
| --- | --- |
| Brainstorm 20:04–20:30 | 26 min |
| Planning 20:30–20:37 | 7 min |
| Implementation and verification 20:37–20:49 | 12 min |
| Close-out documents 20:49–20:51 | 2 min |
| Pre-commit review round 1 20:51–20:57 | 6 min |
| Review fixes, suites re-run, PO decision 20:57–21:04 | 7 min |
| Pre-commit review round 2 21:04–21:08 | 4 min |
| Review fixes 21:08–21:09 | 1 min |
| **Up to the commit proposal** (20:04–21:09) | **1 h 5 min** — the estimate (4 h 30 min) covers the whole task incl. PR loop, deploy and record; the comparison is in the booked time |

## 4. Deviations

- **Guava placement:** the Brainstorm decision (#4) placed the entry *before* the BOM import because "the first
  declaration wins". That rule holds between imported BOMs; a direct `dependencyManagement` entry wins over an imported
  one regardless of position. Verified with the entry after the import (`dependency:tree` → 33.7.2-jre); it stays
  there, with the other explicit entries, and its comment states the actual rule.
- **Temporary CI branch** (planning #1, refined): `v0.21.0` + the CI change only instead of the task state without the
  Guava override — the FS scan was red anyway, and the run also showed the image findings of the unfixed state. The
  branch could not be deleted by the agent (permission classifier); the PO deletes it.
- **Pre-commit review, round 1** (subagent, `/code-review high --max-findings all` on `git diff v0.21.0`, plus
  `/security-review`): no security finding; six verified findings. Fixed: the CI conditions gained `!cancelled()`; the
  Guava entry lost the BOM entry's exclusions (`listenablefuture` reappeared in the tree) — copied; the lesson's
  wording claimed more than the change does (a test failure still skips the scans) — narrowed; the `undici` override
  is scoped to `@scalar/json-magic`. The time table was completed at the commit. One finding concerned
  `verification.md`: the JDK check had no trigger or pass criterion in the normative pre-deploy steps. The PO decided
  to add it there: after the smoke check, `java -version` against the latest Temurin 25 release; an older JDK is
  reported to the PO, the deploy is not rolled back for it.
- **Pre-commit review, round 2** (PO confirmed: round 1's fixes changed code and added normative text): no code or
  build defect; six findings. Fixed: the JDK check also in `verification.md` item 3 (the local-scan hotfix path — the
  one an urgent JDK fix takes), in checklist 2.5b and in the deploy-evidence list of `workflow-mandatory.md`; RUNBOOK
  §9.y says `JDK_JAVA_OPTIONS` is passed at runtime; two wording fixes (CHANGELOG, walkthrough). Rejected: an
  infra-validation failure with a green FS scan still skips the image build and scan — unchanged behaviour, outside
  this task. Fixes in documentation only; the planning audit passed again.
- **First backend suite run** failed on the environment: the test database container was not running (connection to
  `localhost:5433` refused). Started, re-run green.

## 5. Traceability

N/A — security/infra task, no user-facing functional change (no US/UC/TC).
