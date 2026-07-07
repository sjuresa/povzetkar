# POV-204 Retrospective — Modernize AGENT.md and Agent Protocols

**Date:** 2026-07-06

## 1. Planned vs. actual

Scoped: consolidate the protocol suite 11 → 5 files (≤ 2,800 words), delete obsolete weak-agent crutches, resolve the triple test-running contradiction, retire execution.md with all consumers re-pointed, create CLAUDE.md, sweep 10 living docs — approval gates untouched. Delivered all of it, at 2,274 words (−59%), in ≈ 1.4 h against a 3.5–4.5 h base estimate. No scope added or removed; the only surprises were in the agent's favor (sweep shrank from 10 files to 2 — see findings).

## 2. Deviations & why

- **8 deletions, not 7:** the plan's Phase 2 enumeration omitted `mandates-supporting.md` even though Phase 1.4 fully specified migrating its content. Caught at execution; recorded at decision time in task.md `## Deviations` — incidentally the first live use of that mechanism.
- **MD036 lint warnings accepted:** the `**Step N**` emphasis-heading style was kept for consistency with the previous file and sibling process docs rather than silencing the linter with a style change mid-task.

## 3. Technical findings

- **Grep before you plan a sweep.** The brainstorm assumed lowercase `agent.md` references and execution-log mentions across ~10 living docs; content-level grep during execution showed all references were already uppercase and two "hits" were substring false positives (`execution logic`, `execution logical unit`). A sweep estimate based on `files_with_matches` alone overstates work — always inspect match content first.
- **The validator has no opinion on protocol files.** `planning-validator-config.json` globs only task files, walkthroughs, use_cases, test_spec, and the FRD — protocol restructuring is invisible to CI. The only CI-relevant artifact of a process task is its walkthrough's Traceability section.
- **POV-203's cp1252 hardening held:** `validate-planning.py` printed its ✅ banner and exited 0 under `python311` without ceremony. The broken `python` shim on this machine persists; `python311` remains the reliable interpreter.
- **Word budgets turn rewriting into assembly.** Pre-allocating words per file in the plan (~1,100 workflow, ~600 quality, …) removed all drafting hesitation; the suite landed 19% under budget on the first pass.
- **A consolidation needs a rule-by-rule coverage audit, not just a disposition map.** Drafting from a keep/drop/merge map silently lost 2 of ~60 normative rules (Proactive Improvement principle; previous-version retrospective pointer) — caught only when the PO asked whether content loss had been explicitly checked. For any N→M document consolidation, enumerate every normative item of the old set and assign each an explicit disposition *after* drafting, before review sign-off.

## 4. Feed-forward

- **The new process is live from the next task onward:** CLAUDE.md auto-loads; task.md carries Time Log (CET) + Deviations; there is no execution.md; the agent runs the full suite itself (checklist 2.5); spent time goes to YouTrack per phase at 2.9. The next task is the first *full* consumer — watch for friction in the Time Log discipline and log_work mapping.
- **~130 historical execution files remain in `agent_logs/`** by design (history); do not "clean them up".
- **Historical docs intentionally reference the old protocol structure** (workflow-tdd.md, Step 9, execution logs) — do not fix them; the convention is established (POV-201, POV-203, POV-204).
- **TC-ING-006 tripwire (from POV-203) still stands:** the P0-automation guardrail flips CRITICAL at v0.20, forcing the scheduler integration test task.
