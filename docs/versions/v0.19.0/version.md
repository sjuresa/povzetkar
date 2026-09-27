# v0.19.0 Version Hub

**Status:** Completed
**YouTrack Fix version:** [0.19.0](https://jumis.myjetbrains.com/youtrack/issues/POV?q=Fix%20versions:%200.19.0)
**Scope Statement:** v0.19.0 — **Security Hardening, Process Maturity & Operations.** Closed the public exposure of the Quarkus technical endpoints (`/q/metrics`, `/q/health`, `/q/openapi` moved to the management interface), removed 13 silently ignored Quarkus config keys and made the build fail on unknown keys, added read-only API-key authentication for programmatic access, made production request and application logs survive redeploys, pinned the CI vulnerability scanner to an immutable release, cut the initial-load JS bundle by 55 % (Lighthouse 85 → 93), and upgraded the build and runtime environment (Quarkus 3.39.5 with a Hibernate 7.4 workaround, images, CI, a generated-client drift check). On the process side: retrospectives are now consumed (version-initialisation review, Brainstorm feed-forward), US/UC/TC traceability is machine-validated and blocking again, the agent protocols were consolidated, every task now passes a pre-commit review, a reviewer-agnostic PR loop and a YouTrack task record, and every production deploy passes pre-deploy verification; the release steps live only in the checklist. See [retrospective.md](retrospective.md) for the cross-task synthesis.

---

## Completed Tasks

| Task | Walkthrough | Retrospective |
| --- | --- | --- |
| POV-201 — Retrospective Review Process & v0.19.0 Version Initialisation | [walkthrough](POV-201-walkthrough.md) | [retrospective](POV-201-retrospective.md) |
| POV-202 — Optimize frontend (initial-load bundle) | [walkthrough](POV-202-walkthrough.md) | [retrospective](POV-202-retrospective.md) |
| POV-203 — Restore US/UC/TC Traceability Under the Task-First Process | [walkthrough](POV-203-walkthrough.md) | [retrospective](POV-203-retrospective.md) |
| POV-204 — Modernize AGENT.md and Agent Protocols | [walkthrough](POV-204-walkthrough.md) | [retrospective](POV-204-retrospective.md) |
| POV-205 — Read-only API-key authentication for programmatic access | [walkthrough](POV-205-walkthrough.md) | [retrospective](POV-205-retrospective.md) |
| POV-206 — Upgrade build & runtime environment | [walkthrough](POV-206-walkthrough.md) | [retrospective](POV-206-retrospective.md) |
| POV-207 — Close public exposure of `/q/*` technical endpoints | [walkthrough](POV-207-walkthrough.md) | [retrospective](POV-207-retrospective.md) |
| POV-208 — Silently ignored Quarkus config keys + unknown-key guard | [walkthrough](POV-208-walkthrough.md) | [retrospective](POV-208-retrospective.md) |
| POV-209 — Process: reviewer-agnostic PR loop, pre-commit review, pre-deploy Trivy, YouTrack record, task pause, release finalisation | [walkthrough](POV-209-walkthrough.md) | [retrospective](POV-209-retrospective.md) |
| POV-210 — Persistent production access and application logs | [walkthrough](POV-210-walkthrough.md) | [retrospective](POV-210-retrospective.md) |
| POV-211 — v0.19.0 Version Finalisation & Release | [walkthrough](POV-211-walkthrough.md) | [retrospective](POV-211-retrospective.md) |

## Carry-Forward Review

Source: [v0.18.0 retrospective — Carry-Forward Themes](../v0.18.0/retrospective.md). Reviewed at version initialisation (POV-201) per checklist step 1.5.

| Theme | Disposition | Reference |
| --- | --- | --- |
| Transcript pipeline resilient but unsupervised (no crash-restart, no log rotation, no proxy tests, single egress IP) | Backlog — already covered; deliberately deferred until proxy stability is proven in practice | `improvements.md`: "Transcript proxy crash-restart supervision + log rotation", "Transcript proxy pytest suite", "Transcript Cache Layer" |
| GenAI cost measured but not enforced (no pre-call RPD gate) | Backlog — already covered; conditional on RPD exhaustion becoming a real problem, which has not occurred | `improvements.md`: "GenAI RPD enforcement (not just metering)" |
| Scheduler/integration-boundary test coverage — standing blind spot (three v0.18.0 tasks hit mock-hidden bugs) | Task candidate for v0.19 (PO creates YouTrack task). Scope sketch: integration test for the `@Scheduled` orchestration path with real lock service + test DB; audit of the other three `@Scheduled` entry points; codify the no-mock rule in `docs/guides/testing.md` | `improvements.md`: "Scheduler integration test with real lock/DB service (POV-197 follow-up)" |
| `.trivyignore` libexpat entries — recurring debt (3 CVEs suppressed pending Debian backports) | New backlog entry — audit suppressions at each version finalisation; propose Trivy `exp:` dates so suppressions auto-expire | `improvements.md`: "Periodic `.trivyignore` suppression audit" |
