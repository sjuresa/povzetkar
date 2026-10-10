# Context Gathering

Load what the work actually needs; do not front-load the whole documentation tree.

## [per task] — load when a task is active

- `agent_logs/task.md` — the task definition. **Caveat:** it describes the *last worked* task and may already be complete; check its Status header before treating it as current. A new task starts at checklist step 2.0.
- `docs/versions/vX.Y.Z/version.md` — the version hub: completed tasks and the Carry-Forward Review.
- The **Feed-forward** parts of the task blocks under `## Task Notes` in the current version's retrospective (`docs/versions/vX.Y.Z/retrospective.md`) — mandatory Brainstorm input; a version-level knowledge pool, not a chain between consecutive tasks.
- `docs/sdlc/11_development_checklist.md` — the step-by-step process for the current phase.

## [on demand] — load when the task touches the area

- `docs/overview/roadmap.md` — delivered versions and the forecast.
- `docs/overview/project-structure.md` — repository layout; `docs/overview/architecture_overview.md` — components, responsibilities and key classes.
- `docs/guides/testing.md` — when writing or changing tests.
- `docs/process/lessons-learned.md` and `docs/process/lessons/` — before working in an area with known gotchas.
- `docs/backlog/improvements.md` — when triaging discoveries or planning scope.
- `docs/RUNBOOK.md` — technical setup and operational procedures.
- `docs/sdlc/07_development_methodology_comparison.md` — the agreed methodology and its rationale.
- `docs/versions/<previous version>/retrospective.md` — the previous version's cross-task synthesis, formally reviewed at version initialisation (checklist 1.5).
- Context7 MCP (`resolve-library-id` → `query-docs`) — external library documentation.

The source code and its tests remain the ultimate truth for implementation details.
