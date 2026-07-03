# POV-200 Retrospective — Finalize Documentation for Release v0.18.0

## Planned vs. actual

Scoped as a Phase 3 finalization plus a POV-191 follow-up (sync the release template to the task-first model). Delivered exactly that. The only work that exceeded a mechanical finalization was the roadmap v0.18 section — it had to be rewritten rather than status-flipped, because the pre-written plan did not match what shipped.

## Deviations & why

- **Version bump done outside the project script.** `update-version.cjs` failed in this shell (`mvnw.cmd` not on PATH without `.\`, the logged sandbox-compat issue). Ran the underlying `versions:set` goal directly and made the package.json edits manually — same end state, all five version locations at `0.18.0`.
- **Lockfile edited surgically, not via `npm install`.** `--package-lock-only` introduced unrelated metadata churn; reverted and changed only the two version fields to keep the release commit clean.

## Technical findings

- **A methodology change must, in the same task, audit every downstream template/checklist that encodes the old lifecycle.** POV-191 changed the lifecycle but missed `release_task_template.md` and the stale version-gated references in `07_development_methodology_comparison.md`. These surfaced only when the template was *used* (this task). The lesson: when a process task rewrites the lifecycle, grep the whole `docs/` tree for the artifacts it renames/removes (`*-tasks.md`, `*-planning-process.md`, `docs/retrospectives/`) and fix them all then — don't let them rot until the next consumer trips over them.
- **The roadmap is not a release artifact to rubber-stamp.** Under the task-first model the version label is retrospective, so a pre-written roadmap "plan" for a version will frequently *not* describe what shipped. Finalization must reconcile the roadmap against actual delivery, not just flip the status emoji. v0.18.0's planned scope (DAST/Grafana/FTS/SMTP) and delivered scope (transcript resilience/scheduler) had near-zero overlap.
- **All future roadmap phases are forecasts — number them honestly and correct retrospectively.** A PO discussion during finalization surfaced an inconsistency: it makes no sense to label one unshipped plan as a numberless placeholder (`v0.x`) while others keep version numbers (`v1.0`, `v1.1`), because under task-first *every* future phase is equally a forecast. The resolution: give the carried-forward hardening plan the next honest sequential number (`v0.19`) with an explicit "Planned — may change" note, and accept that the roadmap↔delivery gap is a permanent, expected property of the model, reconciled at each finalization (exactly as v0.18 was). This also kept `sync_metadata.py` working with no parser change — `v0.19` is valid semver; the discarded `v0.x` would have silently fallen through the `v\d+\.\d+` regex and never reached the landing page.
- **`update-version.cjs` sandbox-compat is now a confirmed, repeatedly-hit blocker**, not a theoretical one. The `.\`-prefix fix in `improvements.md` should be promoted — every release finalization in a restricted shell will hit it. The PowerShell `--%` stop-parsing token is also required for `-DnewVersion=X.Y.Z` (the dot triggers arg-splitting otherwise).
- **`validate-planning.py` passes its logic but crashes on output encoding under Windows cp1252** (the `✅` emoji). Exit code is 1 despite "Passed". Run with `PYTHONIOENCODING=utf-8` to get a true exit code. Minor, but it can mask a real pass/fail in CI on Windows runners.

## Feed-forward

- **Promote the `update-version.cjs` `.\`-prefix hardening to an actual task.** It's been logged as a low-priority improvement but is hit on every sandboxed release; the fix is one line (`process.platform === 'win32' ? '.\\mvnw.cmd' : './mvnw'`).
- **First clean run of the synced release template.** The *next* version finalization will be the first to consume `release_task_template.md` v2.0 end-to-end. Watch whether the cross-task retrospective and version-hub items read naturally as task requirements, and whether anything in Phase 3 is still missing.
- **This was the first cross-task version retrospective.** Its structure (theme-in-hindsight, systemic patterns, process health, carry-forward themes) is a candidate format for future versions — confirm it holds up when the next version has a different shape (e.g. a UI-heavy version rather than an infra one).
