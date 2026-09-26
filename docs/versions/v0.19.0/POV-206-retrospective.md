# POV-206 Retrospective — Upgrade build & runtime environment

## 1. Planned vs. actual

Scoped: Quarkus 3.36.3 → 3.39.5 with override cleanup, frontend minor/patch updates, image and CI alignment, `.trivyignore` audit, infra-config validation in CI, frontend client kept in sync with the API (clean regeneration + CI drift check, exact orval pin, `npm ci`), and a guarded production deploy. Delivered everything except the deploy: the PO stopped the push, so there is no PR CI, which the plan requires before deploying; every CI step was run locally on HEAD instead. Scope grew by a Hibernate 7.4 workaround, Maven plugin upgrades and dependency pruning (PO-approved), and one pre-existing API bug. Estimate ~4 h; execution itself ~2 h 20 min, plus ~1 h 20 min brainstorm/planning spread over two days around POV-207/208.

## 2. Deviations & why

* **Hibernate 7.4 broke queries the characterization test did not cover.** The plan anticipated the paging change and wrote a test for the one query it named (Topic). The failure came from a *different shape* — SINGLE_TABLE `ContentItem` under a collection fetch, including `firstResultOptional()` lookups. The existing API tests caught it; the targeted test alone would not have. Fixed with the id-first technique the plan already allowed.
* **Maven plugins were not in the brainstorm.** Decision #1 listed Quarkus, npm, images and CI; the Maven plugins (dependency-plugin could not even read Java 25 classes) surfaced only when an analysis tool failed. "Environment upgrade" needs an inventory checklist of every version source (BOM, overrides, plugins, npm, images, CI toolchain), not a list from memory.
* **Safety classifier blocked a planned test deletion.** Deleting `OpenApiGeneratorTest` was approved in the plan (P5) but refused as "security test removal"; the PO deleted it. Planned deletions of tests are worth naming explicitly to the PO at the start of execution.
* **Review found real issues again:** machine-dependent generator output (`prettier: true` with no prettier dependency — the drift check would have failed for anyone with a global prettier), an unstable paging order, an untested login query under the new Hibernate. The suspicion list it produced also led to a pre-existing 500 (`?sort=id`).
* **No push / no deploy** — PO decision during close-out.

## 3. Technical findings

* **A "safe" minor ORM upgrade can change SQL generation for whole query classes.** Look for the *shape* the release note describes (limit + collection fetch) across the codebase — `.page(`, `firstResult`, `setMaxResults` — not only the query that came to mind.
* **`firstResult*()` is paging.** It adds a limit of 1; with a collection fetch that means the new derived-table path. For unique keys `singleResult*()` is both correct and cheaper.
* **Unqualified Panache sort fields are ambiguous as soon as a query joins entities that share the name** (`id`, `createdAt`). Qualify with the alias.
* **Generated artifacts need a deterministic generator and a mechanical freshness check.** Three separate causes of drift were found: a test overwriting the spec, orval never deleting files, and formatting depending on a global tool.
* **`npm audit` and Trivy FS look at different things.** Trivy's npm scan skips devDependencies; orval (build-time code execution from a spec) has CRITICAL advisories only `npm audit` shows.
* **Aggregator artifacts are expensive.** `flexmark-all` put a full PDF stack (pdfbox, openhtmltopdf, icu4j) into the runtime image for four imported classes — 46 jars, ~40 MB of image.
* **The IDE shares `target/` with Maven.** Concurrent Java language-server builds after POM changes produced impossible failures (missing templates, broken generated sources).

## 4. Feed-forward

* **Deploy POV-206** once the PO decides (push → PR CI → Phase 8 of the plan: rollback tag + `pg_dump`, `build --pull`, Trivy, per-service `up -d --no-deps`, checks). Until then production runs Quarkus 3.36.3 and the old images.
* **orval ≥8.22 is the most urgent follow-up** (CRITICAL advisories; backlog, priority High).
* **Frontend majors** are grouped in the backlog; do the lint-debt cleanup before ESLint 10/react-hooks 7.
* **Future upgrades:** start from an inventory of all version sources; for ORM/framework minors, grep for the affected query shapes and make sure a test executes each.
* **Report the Hibernate bug upstream** (backlog) — the workaround stays either way.
* **POV-209** (reviewer-agnostic process, pre-commit review, pre-deploy Trivy, YouTrack record) can now draw on two tasks run under the interim rules.
