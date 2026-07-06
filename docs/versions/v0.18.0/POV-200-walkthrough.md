# POV-200 Walkthrough: Finalize Documentation for Release v0.18.0

**Date:** 2026-06-26
**Task:** POV-200
**Version:** v0.18.0

---

## 1. What Was Done

Finalized all release documentation for v0.18.0 and prepared the codebase for release, per Phase 3 of `11_development_checklist.md`. Two strands:

**Template sync (Step 0 — a POV-191 follow-up).** POV-191 evolved the methodology to task-first planning but left `docs/sdlc/release_task_template.md` aligned to the legacy version-gated model. Synced it to Phase 3: added the cross-task version retrospective and version-hub status/scope items, replaced legacy paths (`docs/retrospectives/`, `vX.Y.Z-tasks.md`, `vX.Y.Z-planning-process.md`) with the version-hub structure, expanded the General Refresh and methodology review scope, and bumped its revision history to 2.0.

**Release finalization (Steps 1–8).**
- **`CHANGELOG.md`** — moved `[Unreleased]` to `[0.18.0] - 2026-06-26` with a theme summary; added a fresh empty `[Unreleased]`.
- **`docs/versions/v0.18.0/retrospective.md`** — wrote the first cross-task version retrospective under the new model: a pattern-level synthesis across the nine task retrospectives (third-party-behavior surprises, persist-in-own-transaction pattern, mocks hiding bugs, caller-side semantics).
- **`docs/versions/v0.18.0/version.md`** — status → `Completed`, added scope statement, added POV-200 task row.
- **`docs/backlog/improvements.md`** — updated the transcript-cache item (POV-198 delivered the intermediate fix), de-staled the SMTP "v0.18.0 candidate" tag, added two new items from retrospectives (scheduler integration test; `POVZETKAR_BRIEFING_CRON` env cleanup).
- **`docs/overview/roadmap.md`** — rewrote the v0.18 section to reflect *actual* task-first delivery (it previously described an unshipped "Hardening & Operational Excellence" plan), marked it `✅ Completed`, added revision 3.8.
- **Project-wide review** — planning audit passed clean; refreshed stale version-gated references in `07_development_methodology_comparison.md`; confirmed `AGENT.md` needs no change.
- **Version finalization** — removed `-SNAPSHOT` across all five locations (root + backend pom, root + frontend package.json, frontend package-lock.json) → `0.18.0`.
- **`sync_metadata.py`** — regenerated `en.json`/`sl.json` landing-page metadata from the finalized CHANGELOG and roadmap.

## 2. Planned vs. Actual Scope

| Planned | Actual |
| --- | --- |
| Sync release template (Step 0) | Done — applied before finalization so `task.md` was generated from the corrected template |
| Finalize CHANGELOG, retrospective, version hub, backlog, roadmap | Done |
| Project-wide review (audit, refresh, AGENT.md, methodology) | Done — methodology doc refreshed; AGENT.md confirmed no-change |
| Remove `-SNAPSHOT` | Done — but via direct `mvnw.cmd versions:set` + manual package edits, not the project script (see Deviations) |
| Run `sync_metadata.py` | Done |

No scope was cut. The CHANGELOG/roadmap got slightly more rework than a mechanical finalization because the roadmap's pre-written v0.18 plan did not match what shipped.

## 3. Time Delta

| Phase | Estimate | Actual |
| --- | --- | --- |
| Template sync + finalization | ~45 min | ~25 min |

(Estimate was informal — this is a finalization task, not a feature build.)

## 4. Deviations

- **`update-version.cjs` could not run in this shell.** The script invokes `mvnw.cmd` without a `.\` prefix; with `NoDefaultCurrentDirectoryInExePath` set, the wrapper isn't found on PATH (the exact issue already logged in `improvements.md`). Worked around by running `.\mvnw.cmd --% versions:set -DnewVersion=0.18.0 -DgenerateBackupPoms=false` directly (the `--%` stop-parsing token was needed because PowerShell otherwise split `-DnewVersion=0.18.0` at the dot), then applying the two `package.json` version edits the script would have made. Net result is identical to a successful script run.
- **Lockfile synced surgically.** `npm install --package-lock-only` updated the version but also re-added unrelated optional bundled transitive entries (+68 lines). Reverted and edited only the two version fields to keep the release diff clean.
- **POV-191/POV-200 deliberately omitted from the CHANGELOG.** Both are process/docs-only; the changelog stays scoped to product/behavior changes (the summary already credits POV-191's methodology shift).
- **Roadmap v0.18 section rewritten, not just status-flipped.** The pre-written plan (DAST, Grafana alerting, full-text search, SMTP) never shipped; under the task-first model the version label is retrospective, so the section now describes actual delivery. The original hardening plan was carried forward to `v0.19: Hardening & Operational Excellence`, marked explicitly as a forecast (not a commitment) — a discussion with the PO during finalization concluded that all future roadmap phases are forecasts under the task-first model, and the next sequential version number (`v0.19`) is the honest label, corrected retrospectively when the work is actually done (exactly as v0.18 was here).

## 5. Traceability

N/A — retrospective/process documentation task; no system behavior change.
