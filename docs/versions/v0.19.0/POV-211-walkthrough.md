# POV-211 Walkthrough — v0.19.0 Version Finalisation & Release

**Task:** [POV-211](https://jumis.myjetbrains.com/youtrack/issue/POV-211) (Task, Major) · **Branch:** `POV-211` → `v0.19.0` · **Date:** 2026-09-27

## 1. What was done

The PO declared v0.19.0 complete. The finalisation ran as one task covering checklist Phase 3 (Version Finalisation) and Phase 4 (Release), preceded by a Step 0 that removed two tooling gaps the finalisation would otherwise have tripped over.

**Step 0 — tooling gaps.**
- **`docs/sdlc/release_task_template.md` deleted.** It duplicated the checklist's release steps and had fallen behind three times (POV-191, POV-201, POV-209 — this time it lacked 3.1a, 3.1b and all of Phase 4). No document referenced it. The Phase 3 intro of `11_development_checklist.md` now states that the finalisation runs as a task whose requirements are the Phase 3–4 steps, in which order (the task's PR merged before the release PR 4.1, its YouTrack record after the release deploy 4.4, so 3.1b excludes the finalisation task), and that there is no separate template. The one template requirement the checklist lacked moved in — 3.4 now requires the CHANGELOG `### Summary` that `sync_metadata.py` reads — and 3.5 now names the manual `sl.json` translation (checklist rev. 3.5).
- **`scripts/update-version.cjs`** runs `.\mvnw.cmd` on Windows (a bare `mvnw.cmd` fails where `NoDefaultCurrentDirectoryInExePath` is set — POV-200 and POV-201 had to work around it) and now also sets `version` and `packages[""].version` in both `package-lock.json` files (a new `updateLockfileVersion` helper; POV-200 had edited them by hand because `npm install --package-lock-only` re-resolves the tree). The backlog entry is removed.

**Phase 3 — Version Finalisation.**
- **3.1 documents:** `docs/versions/v0.19.0/retrospective.md` (cross-task synthesis: "declared versus effective"; four systemic patterns; process health; four Carry-Forward Themes). `version.md` → `Completed`, scope statement, POV-211 row, the placeholder milestone link replaced by the YouTrack Fix-version query. `roadmap.md` rev. 4.0: v0.19 rewritten to actual delivery and `✅ Completed`; a minimal v0.20 forecast (Caddy on the traffic path, orval ≥ 8.22, scheduler integration test); the unshipped v0.19 forecast left the roadmap. `improvements.md`: DAST (OWASP ZAP), additional transcript providers and an upgrade-inventory item added; two long-completed items and the `update-version.cjs` item removed; two `sync_metadata.py` defects appended to its entry.
- **3.1a supply-chain audit:** image built with `--pull` (`povzetkar:pov211-audit`, `ddbfb62c`); Trivy 0.74.0 (image pinned by digest) **without** `.trivyignore`:
  - Image: exactly the eight suppressed CVEs (`libexpat1` CVE-2026-66046/-76956/-76957/-93990, `libuuid1` CVE-2026-76642/-78408/-78409/-78410), all still `affected` with no fixed version; nothing else HIGH/CRITICAL in the OS packages, jars or Go binaries.
  - FS: 0 HIGH/CRITICAL, 0 secrets (targets `pom.xml`, `backend/pom.xml`, `frontend/package-lock.json`, `transcript-proxy/requirements.txt`). The first run failed after ~7 min on a Maven Central `429` (IP blocked for 30 min); the second mounted the local `~/.m2` read-only and used `--offline-scan`.
  - → `.trivyignore` unchanged (all eight entries still needed, `exp:2026-12-31`). `trivy-action` v0.36.0 and Trivy v0.74.0 are the latest immutable releases (GitHub API, 2026-09-27) → no bump.
- **3.1b Fix-version check:** POV-201 … POV-210 all `Fixed`; POV-211 is the only open 0.19.0 issue (closed in 2.15).
- **3.2 project-wide review:** planning audit `--fail-on all` passed. General Refresh: `07_development_methodology_comparison.md` §7 (empty) gained three proposals from the retrospective (negative test per control, a stopping rule for review rounds, measuring the cost of the gates; rev. 2.0); `docs/strategy/STRATEGIC_NEXT_STEPS.md` (unchanged since 2025-08, wholly stale, unreferenced) deleted on the PO's decision, and `docs/strategy` dropped from the 3.2 directory list; `methodology.md`, `lessons-learned.md`, the overview documents and `AGENT.md` needed no change.
- **3.3** `node scripts/update-version.cjs 0.19.0` from PowerShell with `NoDefaultCurrentDirectoryInExePath=1` — the diff is exactly the eight version fields in six files; no `-SNAPSHOT` left.
- **3.4 CHANGELOG:** `[0.19.0] - 2026-09-27` with a Summary, new empty `[Unreleased]`; POV-201 moved to *Documentation*, a process-noise sentence removed from POV-207; POV-211 entry under *Documentation*.
- **3.5** `sync_metadata.py`, then the manual Slovenian translation of the 0.19.0 changelog description and the v0.20 roadmap description in `sl.json`; the missing final newline restored in both locale files.

**Verification:** frontend `vitest run` 15 files / 68 tests green, `npm run build` OK; backend `mvnw.cmd clean verify` green (364 tests, 0 failures/errors, 3 skipped; SpotBugs 0 bugs; reactor built as `0.19.0`); planning audit green.

## 2. Planned vs. actual scope

| Planned | Actual |
| --- | --- |
| Step 0: reduce the release template to a reference | **Deleted** instead (planning decision 1, PO) |
| Step 0: `update-version.cjs` `.\` prefix | Done, **plus lockfile versions** (planning decision 2, PO) |
| 3.1 documents, backlog, roadmap | Done; backlog cleanup went beyond the list (two completed items removed, upgrade-inventory item added) |
| 3.1a audit | Done; no `.trivyignore` change, no pin bump |
| 3.2 review | Done; `STRATEGIC_NEXT_STEPS.md` deleted (unplanned, 2.13; PO decision) |
| 3.3–3.5 | Done; `sync_metadata.py` dropped the four oldest landing-page versions (accepted as designed, defects logged) |
| Phase 4 | After the commit — recorded in the Time Log, the work items and the YouTrack walkthrough addendum |

## 3. Time delta

Estimate ~5 h for the whole task (Phase 4 included); the Phase 3 share up to the commit was ~3 h (Step 0 ~0.25 h, docs ~1.5 h, audit ~0.75 h, review ~0.5 h).

| Phase (2026-09-27, CET) | Actual |
| --- | --- |
| Brainstorm 14:22–14:42 | 20 min |
| Planning 14:42–14:50 | 8 min |
| Implementation 14:50–15:27 (incl. Trivy runs, suites) | 37 min |
| Pre-commit review round 1 15:27–15:38 + fixes 15:38–15:41 | 14 min |
| Pre-commit review round 2 15:41–15:45 (stopped by the PO) | 4 min |
| **Up to the commit** | **~1 h 23 min** (vs ~3 h) |

The Trivy FS audit took two runs (~7 min lost to the Maven Central `429`, 18 min for the successful run), overlapping document work. PR loops, the release and the deploy are booked from the Time Log after the commit.

## 4. Deviations

- **Template deleted, not reduced** (planning decision 1): unreferenced and empty once reduced; the checklist is the single definition.
- **`update-version.cjs` also updates the lockfiles** (planning decision 2): otherwise four `-SNAPSHOT` occurrences would have needed a manual edit again.
- **`STRATEGIC_NEXT_STEPS.md` deleted** (2.13): first marked historical (a rewrite is outside a finalisation); at the commit proposal the PO chose to delete it. It was the only document in `docs/strategy`, so checklist 3.2 no longer lists that directory.
- **`sync_metadata.py` output accepted:** its 10-block slice counts the empty `[Unreleased]`, so 9 versions remain and v0.10.0 … 0.7.0 dropped off the landing page; `json.dump` omits the final newline (restored by hand). Both defects are appended to the existing backlog entry rather than fixed here.
- **Trivy FS run 1 hit Maven Central `429`:** re-run with the local Maven cache (`-v ~/.m2:/root/.m2:ro`, `--offline-scan`); RUNBOOK §8.2 now documents this for local scans (unplanned, 2.13).
- **Pre-commit review (2.5a):** round 1 (`/code-review high` in a subagent over `git diff v0.19.0`) returned 7 findings, none high — all fixed: the template's CHANGELOG-Summary requirement moved into checklist 3.4 (and the `sl.json` translation into 3.5); the Phase 3 intro now orders the finalisation task against Phase 4; a miscount in the version retrospective; an overclaim in the `version.md` scope; this Time delta table; a tighter RUNBOOK §8.2 offline-scan note; the two `update-version.cjs` helpers merged. Round 2 was stopped by the PO before it returned.
- **Backend suite:** the Git-Bash `./mvnw` start failed instantly (`No such file or directory`); run with `.\mvnw.cmd` from PowerShell instead.

## 5. Traceability

N/A — release/process task; no system behaviour change (version string only).
