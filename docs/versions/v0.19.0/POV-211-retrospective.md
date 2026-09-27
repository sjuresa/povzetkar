# POV-211 Retrospective — v0.19.0 Version Finalisation & Release

**Date:** 2026-09-27

## 1. Planned vs. actual

Scoped (Brainstorm): a Step 0 for two tooling gaps, checklist Phase 3 and Phase 4 in one task. Delivered Step 0 and Phase 3 up to the commit as planned, with two PO-approved planning changes (template deleted instead of reduced; lockfiles added to the version script) and three small 2.13 additions (a stale strategy document deleted on the PO's decision, a RUNBOOK note for the Trivy `429`, backlog cleanup beyond the list). Phase 4 follows the commit.

## 2. Deviations & why

- **Template deleted, not reduced.** Evidence during planning: nothing referenced it, and reduced to a pointer it would have been an empty file. The Brainstorm decision ("reduce") was made before that grep — a reminder that the grep belongs in the Brainstorm when a decision is about a document's role.
- **`sync_metadata.py` output accepted as designed.** It dropped four old versions from the landing page and the final newline. Fixing the script mid-finalisation would have been a third unplanned code change; the defects are in the backlog entry that already tracks the script's i18n gap.
- **Trivy FS needed two runs.** The first died on Maven Central's `429` after ~7 min; the working recipe (local `~/.m2`, `--offline-scan`) is now in RUNBOOK §8.2.

## 3. Technical findings

- **The first run of 3.1a worked, and was mostly waiting.** The audit answered its question cleanly — all eight suppressions still needed, no new findings, pins current — but ~25 min of the task were Trivy runs. The FS secret scan walks the whole working tree, `node_modules` and `target/` included.
- **Maven Central rate-limits Trivy's POM resolution per IP.** `Retry-After: 1800` blocks the whole IP for 30 min, so a retry does not help; the local Maven cache does. The same can hit CI (POV-207 feed-forward).
- **A script that "works" can still fail the process step.** `update-version.cjs` succeeded in interactive shells and failed in the agent's; `sync_metadata.py` runs without error and silently drops content. Verifying a script means checking its *diff*, not its exit code — the Step 0 fix was verified by the 8-line diff, and the `sync_metadata` defects were found the same way.
- **Duplicated process text is the drift source, not careless editing.** The release template fell behind three times in three different tasks by three different change authors; each patch was correct at the time. Deleting the copy was the only fix that cannot regress.
- **Git Bash background shells may not find `./mvnw`** even with the right `cd` (exit 127, instant); `.\mvnw.cmd` from PowerShell works. Not investigated further.

## 4. Feed-forward

- **v0.20 initialisation (checklist 1.5)** reviews the four Carry-Forward Themes in `docs/versions/v0.19.0/retrospective.md` — the first time Phase 1 runs 1.4/1.5 from a finalisation that itself ran as a task.
- **TC-ING-006 turns CRITICAL at v0.20** in the planning audit (`--fail-on all` in CI) — the scheduler integration test is the first thing v0.20 has to decide on.
- **`.trivyignore` entries expire 2026-12-31**; CI will fail then on purpose.
- **The release finalisation is now defined only by the checklist.** A future finalisation copies nothing — it reads Phases 3–4 and executes them in order.
- **`sync_metadata.py`**: translate the new `sl.json` entries by hand again until the backlog item is done; check the diff for dropped entries and the final newline.
