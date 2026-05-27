# POV-191 Retrospective: Development Process Evolution — Task-First Planning

**Date:** 2026-05-27
**Task:** POV-191
**Version:** v0.18.0

---

## 1. Planned vs. Actual

**Planned:** Formalise task-first delivery by rewriting four process documents and initialising the v0.18.0 version hub. Estimated 3–4 hours.

**Actual:** All five deliverables completed in ~3.5 hours. One scope addition (`workflow-mandatory.md` Step 8 timestamp mandate) and one location change (retrospective moved from `docs/retrospectives/` to `docs/versions/v0.18.0/`) relative to the original task.md — both resolved in the planning interview before implementation.

---

## 2. Deviations & Why

**Retrospective location:** The acceptance criteria named `docs/retrospectives/POV-191-retrospective.md`. During the interview, we concluded that `docs/retrospectives/` was a structural mistake — retrospectives belong co-located with version artifacts. The new location is `docs/versions/vX.Y.Z/POV-XXX-retrospective.md`. The acceptance criteria was updated before implementation began so there was no rework.

**`workflow-mandatory.md` in scope:** Originally out-of-scope. Added because the timestamp mandate for execution logs is directly coupled to the Time Log feature agreed during the interview. It was a 5-line addition with zero risk — deferring it to a separate task would have created a gap between the agreed model and the documented protocol.

---

## 3. Technical Findings

**Interview-first approach works for process tasks too.** Running a full relentless interview before touching any file surfaced 14 non-trivial design decisions (retrospective format, walkthrough structure, Time Log location, version hub trigger, Phase 3 trigger, etc.). Each was resolved with a clear rationale before implementation. Zero mid-execution reversals.

**Acceptance criteria in task.md can be wrong.** The retrospective path in task.md contradicted the decision reached during the interview. This confirms that task.md is a starting point for the interview, not a final specification — the interview output supersedes it. The checklist step 2.0 Brainstorm is the correct place to resolve these conflicts.

**Lint consistency matters.** The existing `workflow-mandatory.md` uses `*   ` (3-space) list markers throughout. New content was written in the same style to avoid introducing inconsistency, even though the linter prefers 1-space. Pre-existing warnings are not introduced by this task.

**`docs/retrospectives/` is now deprecated for new artifacts.** Existing files remain. New retrospectives go to `docs/versions/vX.Y.Z/`. This is a soft migration — no existing file was deleted or moved.

---

## 4. Feed-forward

- The next task should validate that the Brainstorm timestamp mechanism works in practice: agent writes `## Brainstorm` timestamps to `task.md` at session boundaries, transfers them to YouTrack, and leaves them in the archived file.
- The version hub (`version.md`) pattern is new — the next task will exercise it for the first time as a consumer (update rather than create). Watch for friction in the update step (2.13).
- `workflow-mandatory.md` Step 9 references `docs/retrospectives/vX.Y-retrospective.md` — this path is now outdated. Update in a future task when `workflow-mandatory.md` is next touched.
