# v0.19.0 Version Hub

**Status:** In Progress
**YouTrack Milestone:** [v0.19.0](https://youtrack.example.com/youtrack/agiles) *(update with actual milestone link)*

---

## Completed Tasks

| Task | Walkthrough | Retrospective |
| --- | --- | --- |
| POV-201 — Retrospective Review Process & v0.19.0 Version Initialisation | [walkthrough](POV-201-walkthrough.md) | [retrospective](POV-201-retrospective.md) |
| POV-202 — Optimize frontend (initial-load bundle) | [walkthrough](POV-202-walkthrough.md) | [retrospective](POV-202-retrospective.md) |
| POV-203 — Restore US/UC/TC Traceability Under the Task-First Process | [walkthrough](POV-203-walkthrough.md) | [retrospective](POV-203-retrospective.md) |
| POV-204 — Modernize AGENT.md and Agent Protocols | [walkthrough](POV-204-walkthrough.md) | [retrospective](POV-204-retrospective.md) |

## Carry-Forward Review

Source: [v0.18.0 retrospective — Carry-Forward Themes](../v0.18.0/retrospective.md). Reviewed at version initialisation (POV-201) per checklist step 1.5.

| Theme | Disposition | Reference |
| --- | --- | --- |
| Transcript pipeline resilient but unsupervised (no crash-restart, no log rotation, no proxy tests, single egress IP) | Backlog — already covered; deliberately deferred until proxy stability is proven in practice | `improvements.md`: "Transcript proxy crash-restart supervision + log rotation", "Transcript proxy pytest suite", "Transcript Cache Layer" |
| GenAI cost measured but not enforced (no pre-call RPD gate) | Backlog — already covered; conditional on RPD exhaustion becoming a real problem, which has not occurred | `improvements.md`: "GenAI RPD enforcement (not just metering)" |
| Scheduler/integration-boundary test coverage — standing blind spot (three v0.18.0 tasks hit mock-hidden bugs) | Task candidate for v0.19 (PO creates YouTrack task). Scope sketch: integration test for the `@Scheduled` orchestration path with real lock service + test DB; audit of the other three `@Scheduled` entry points; codify the no-mock rule in `docs/guides/testing.md` | `improvements.md`: "Scheduler integration test with real lock/DB service (POV-197 follow-up)" |
| `.trivyignore` libexpat entries — recurring debt (3 CVEs suppressed pending Debian backports) | New backlog entry — audit suppressions at each version finalisation; propose Trivy `exp:` dates so suppressions auto-expire | `improvements.md`: "Periodic `.trivyignore` suppression audit" |
