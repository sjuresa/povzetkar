# POV-203 Walkthrough — Restore US/UC/TC Traceability Under the Task-First Process

**Git-style title:** `docs(process): restore US/UC/TC traceability under task-first model, harden validator + CI`
**Date:** 2026-07-06

## 1. What was done

Restored the User Story → Use Case → Test Case traceability chain that had silently stopped being maintained since the task-first transition (POV-191). Six-part delivery:

1. **Validator + config** (`scripts/qa/validate-planning.py`, `planning-validator-config.json`): new `walkthroughs_glob` (`docs/versions/*/POV-*-walkthrough.md`, git-tracked → CI-visible) and `functional_requirements` config keys; a `validate_walkthroughs()` rule that flags a walkthrough with no `Traceability` section, or one declaring neither an ID nor `N/A`, as **WARNING**, and a broken US/UC/TC reference inside the section as **CRITICAL**; US-catalogue parsing from `01_functional_requirements.md`; and `sys.stdout.reconfigure(encoding='utf-8', errors='replace')` cp1252 hardening in `main()`. Section detection tolerates numbered headings (`## 5. Traceability`) and validates IDs **section-only** (prose mentions are ignored).
2. **Master spec backfill** (v0.18 drift): UC-14 rewritten for the `ProxyTranscriptProvider` primary chain + persist-before-summarize; new UC-16 "Scheduled Orchestration & Periodic Content Fetch"; six new TC rows (TC-ING-004/005/006/007, TC-MON-005, TC-SEC-006) with automation status grounded in the actual test files.
3. **US catalogue**: US-17.1–17.7 migrated into `01_functional_requirements.md` §3.6 with original IDs preserved (UC-11…15 reference them); the forward lifecycle policy (`(Planned)` → `(Implemented)`, area-based IDs, version scheme retired) documented in the FRD §3 intro and normatively in Planning Protocol §2.1.
4. **Process docs**: checklist 2.1/2.10/2.14, workflow-mandatory Step 9, Planning Protocol §2.1/§2.4, validation-protocol Phases 2/3 + severity semantics, methodology, all updated with revision entries.
5. **CI** (`.github/workflows/ci.yml`): the audit step now derives `--current-version` from `package.json` (`jq -r .version | sed 's/-SNAPSHOT$//'`) instead of the hardcoded `v0.15.0`, and **`continue-on-error: true` was removed** so the audit is genuinely blocking.
6. **Walkthrough backfill**: `Traceability` sections added to all 12 existing v0.18/v0.19 walkthroughs per the disposition map.

**Verification (all green):** red test — removing a `Traceability` section yields a WARNING (exit 1 under `--fail-on all`, exit 0 under local default); a broken US reference yields a CRITICAL (exit 1 even locally). cp1252 console proof: passing run exits 0 without `PYTHONIOENCODING`. Reference sweep clean. CI-parity dry-run: derived `v0.19.0` + full CI arg set → exit 0. Full validator run green on the v0.19 branch.

## 2. Planned vs. actual scope

Delivered as planned. Two additions surfaced during planning and were PO-approved into scope: **removing `continue-on-error: true`** (a new finding — without it the blocking-CI story was theater) and **fixing the stale "Cancel is deferred to v0.18.0" note in all three locations** (TC-ADM-004, UC-13, PW-014), not just TC-ADM-004. One automation-status correction relative to the plan: TC-MON-005 and TC-ING-007 are `@QuarkusTest` **Integration** tests, not the "Unit" the plan tentatively proposed — corrected after reading the test files. `mvn clean verify` waived (pre-approved; docs+tooling task, no Java/frontend change) in favor of the validator red/green demonstration.

## 3. Time delta

**Estimated:** ~4.5–5.5 h base, ~6–7 h with contingency.
**Actual:** ~1.5 h focused execution. Under estimate — the dominant flagged risks (TC backfill grounding and PS 5.1 mojibake) were mitigated cheaply: test-file grounding was a handful of targeted greps, and all markdown edits went through the Edit tool / a node script rather than raw PowerShell writes (POV-201 lesson), so no mojibake occurred.

## 4. Deviations

1. **Local Python environment broken.** The `python` scoop shim and the repo venv both pointed at a removed 3.13.5 base interpreter. All local validator runs used `python311` (3.11.9) explicitly. CI is unaffected (`setup-python` 3.12).
2. **`jq` absent from local Git Bash.** The CI-parity dry-run proved the `sed` transform against the real `0.19.0-SNAPSHOT` and confirmed `jq -r .version` output via an equivalent JSON read; `jq` itself is preinstalled on `ubuntu-latest`.
3. **cp1252 banner renders as mojibake but no longer crashes.** The hardening goal was the exit code, not glyph fidelity — `errors='replace'` means the `✅` degrades visually on a legacy console while the run exits 0, which is the fix.
4. **Line endings.** The node backfill script wrote LF into CRLF files; `core.autocrlf=true` normalizes the committed blob to LF, so the staged diff is clean (insertions only).

## 5. Traceability

N/A — process/tooling task; no system behavior change. (POV-203 is the first consumer of the mandatory `Traceability` section rule it introduces.)
