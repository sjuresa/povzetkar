# POV-201 Retrospective — Retrospective Review Process & v0.19.0 Version Initialisation

**Date:** 2026-07-03

## 1. Planned vs. actual

Scoped: anchor a retrospective review at version start (Phase 1), install Feed-forward consumption in the Brainstorm, remove the conditional Version Hub step from the core loop, fix stale references, and execute the first application for v0.19.0. Delivered exactly that, plus a small ring of coherence fixes the plan had not enumerated (Phase 3.1 Carry-Forward Themes mandate; four additional stale pre-POV-191 references found in the PO-requested pre-commit review).

## 2. Deviations & why

- **Branching:** PO amended the plan at approval — Phase A on `v0.19.0`, Phases B/C on a `POV-201` branch, PR into `v0.19.0`. Reason: consistency with the established task-branch convention.
- **Commit signatures:** PO rejected the `Co-Authored-By` footer; project convention is plain Conventional Commits. Preference persisted so it does not recur.
- **Verification:** `mvn clean verify` waived (docs-only); replaced by the planning validator + reference grep sweep. This deviation was pre-approved in the planning interview, not improvised.

## 3. Technical findings

- **POV-200's template lesson recurred within the very task built on it.** The strict review found `release_task_template.md` still lacked the Carry-Forward Themes mandate this task introduced — a process change again failed to propagate to the downstream template on the first pass. The lesson "when a process task changes the lifecycle, audit every template that encodes it" evidently needs to be a checklist behavior (it is now exercised via the review), not just a remembered principle.
- **A process document that writes an artifact nobody reads is a silent failure mode.** Both retrospective levels were write-only after POV-191 — not by decision, but as an unnoticed side effect of deleting the step that consumed them. When a task removes a process step, explicitly ask *what consumed this step's output and what consumes it now*.
- **Verify claims about the process against the documents, not memory.** The brainstorm initially asserted task retrospectives were "read during context gathering" — the PO asked for verification and the claim was false (`context-gathering.md` did not mention retrospectives at all, and still pointed at two documents deleted by POV-191). The "Code is Truth" principle applies to process docs too.
- **PS 5.1 `Get-Content`/`Set-Content` round-trips corrupt BOM-less UTF-8 files** (ANSI default read → double-encoded write; every emoji/en-dash in the file becomes mojibake). Recover with `git checkout --`; do mechanical markdown transforms with a node one-liner (`utf8` in/out, preserve CRLF). Recorded in `lessons/infrastructure-ops.md`.
- **`update-version.cjs` fails in sandboxed shells** because it invokes `mvnw.cmd` without a path prefix; prepending the repo root to `PATH` is the non-invasive workaround (hardening already tracked in `improvements.md`).
- **`validate-planning.py` success output crashes on cp1252 consoles** (`UnicodeEncodeError` on the ✅ banner) even though validation passed — the failure is in the *reporting*, and the exit code is misleading (1). Run with `PYTHONIOENCODING=utf-8` on Windows. Candidate one-line hardening for the script.

## 4. Feed-forward

- v0.19.0 now has a populated Carry-Forward Review table in `version.md` — consult it before scoping any hardening-related task; theme 3 (scheduler/integration-boundary test coverage) is an approved **task candidate** awaiting YouTrack creation by the PO.
- The renumbered checklist is live: Walkthrough is now 2.10, Task Retrospective 2.11, Version Hub update 2.12, Unplanned Discoveries 2.13, Master Spec Sync 2.14. Historical documents intentionally retain old numbering.
- The next version's initialisation (v0.20) is the first that will run Phase 1 steps 1.4/1.5 from scratch; watch for friction in the hub template and the disposition flow.
- When editing markdown on this Windows setup: Edit tool for content, node one-liners for mechanics, never PS 5.1 content cmdlets.
