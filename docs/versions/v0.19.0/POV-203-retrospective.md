# POV-203 Retrospective — Restore US/UC/TC Traceability

**Date:** 2026-07-06

## 1. Planned vs. actual

Scoped: an honest traceability validator (walkthrough glob + mandatory-section rule + US validation + cp1252 hardening), a selective v0.18 UC/TC backfill, US-17.x catalogue migration, a forward US policy, dynamic CI version derivation, and 12-walkthrough backfill. All delivered. Two PO-approved scope additions during planning: removing CI `continue-on-error: true`, and fixing the stale cancel note in all three occurrences (not one). No scope removed.

## 2. Deviations & why

- **`continue-on-error: true` removal** — discovered during evidence-gathering that the audit step could not block CI at all regardless of `--fail-on`. Removing it was the difference between the enforcement being real and being theater; the same failure mode the task exists to kill. PO-approved.
- **Integration vs. Unit automation labels** — the plan tentatively labelled TC-MON-005/TC-ING-007 as "Unit"; reading `VideoSummaryServiceTest` and `TranscriptPersistenceTest` showed both are `@QuarkusTest` (Integration). Corrected to keep the spec honest ("Code is Truth").
- **Broken local Python** — the scoop `python` shim and repo venv both referenced a removed base interpreter; used `python311` explicitly. Not a task problem, but worth flagging for anyone running the validator locally on this machine.

## 3. Technical findings

- **The validator's `tasks_glob` scans whole files** (`parse_tasks`), including the gitignored `agent_logs/POV-* task.md`. During Phase 1 this produced 6 bootstrap CRITICALs because POV-203's own task file mentions UC-16 before Phase 2 creates it. This is expected for any task that forward-references an ID it will mint — the CRITICALs cleared once the spec backfill landed. Contrast with the **new** `walkthroughs_glob` rule, which is deliberately **section-only** to avoid exactly this stale-prose trap.
- **cp1252 hardening is about exit code, not glyphs.** `sys.stdout.reconfigure(encoding='utf-8', errors='replace')` stops the `UnicodeEncodeError` that turned a passing run into exit 1 on a legacy Windows console. The `✅` still renders as mojibake there; that is acceptable — the misleading non-zero exit is what mattered.
- **`core.autocrlf=true` makes LF-vs-CRLF working-tree mixing harmless.** A script that appends LF lines to CRLF files produces a visually mixed working tree, but git normalizes the committed blob to LF and the staged diff shows insertions only. No `.gitattributes` in this repo.
- **Severity split needs no new config** — CI's blocking behavior on WARNINGs comes entirely from `--fail-on all` vs. the local default `--fail-on critical`. This was already in the tool; POV-203 just documented it.

## 4. Feed-forward

- **TC-ING-006 is a live P0 tripwire.** It is `[v0.18]` P0 Manual with **no** `validate:skip`. The P0-automation guardrail (`p0_automation_max_version_delta=1`) will flip it to CRITICAL at **v0.20**, deliberately forcing the already-approved scheduler integration test (see the standing "Scheduler integration test" backlog entry). The next version's team should expect this and either implement the test or consciously bypass with a justified `validate:skip until`.
- **US-16.x orphans remain** — UC-8/9/10 reference US-16.2/.3/.4/.8, which live only in the archived v0.16 planning doc. POV-203 restricted US validation to walkthrough sections precisely so these don't break CI. A backlog entry exists to migrate them into §3 if US validation is ever widened.
- **Every future walkthrough now needs a `Traceability` section** — the checklist (2.10) and workflow-mandatory (Step 9) mandate it and CI enforces it. `N/A — <reason>` is a first-class, honest answer for fix/infra/perf/process tasks.
