# v0.20.0 Version Hub

**Status:** In Progress
**YouTrack Fix version:** [0.20.0](https://jumis.myjetbrains.com/youtrack/issues/POV?q=Fix%20versions:%200.20.0)

---

## Completed Tasks

| Task | Walkthrough | Retrospective |
| --- | --- | --- |
| POV-212 — v0.20.0 Version Initialisation | [walkthrough](POV-212-walkthrough.md) | [retrospective](POV-212-retrospective.md) |
| POV-216 — Pending-approval Banner and Per-user Onboarding Guide | [walkthrough](POV-216-walkthrough.md) | [retrospective](POV-216-retrospective.md) |
| POV-219 — Sources and Content Scoped to the User's Topics | [walkthrough](POV-219-walkthrough.md) | [retrospective](POV-219-retrospective.md) |
| POV-220 — Landing Page Redesign | [walkthrough](POV-220-walkthrough.md) | [retrospective](POV-220-retrospective.md) |
| POV-214 — Remove Caddy: Security Headers, Caching and Proxy Trust in the Application | [walkthrough](POV-214-walkthrough.md) | [retrospective](POV-214-retrospective.md) |

## Carry-Forward Review

Source: [v0.19.0 retrospective — Carry-Forward Themes](../v0.19.0/retrospective.md). Reviewed at version initialisation (POV-212) per checklist step 1.5; dispositions approved by the PO on 2026-09-27.

| Theme | Disposition | Reference |
| --- | --- | --- |
| The production edge is not yet what the architecture describes | **Task candidate** — put Caddy on the traffic path, together with the entries the backlog already folds into it — the security-header delivery test, restricting forwarded-header trust to Caddy, the `grafana` DNS record and the OCI claims in the RUNBOOK. The other edge items stay **backlog**: SPA fallback answering 200 to probe paths, Micrometer `uri`-tag explosion, suspicious-traffic alerting, 302 instead of 401 for expired-session XHRs, ownership scoping on detail endpoints. | [POV-214](https://jumis.myjetbrains.com/youtrack/issue/POV-214); `docs/backlog/improvements.md` |
| The scheduler integration test is now forced | **Task candidate** — the next task. Until it is done, TC-ING-006 carries a time-bound `validate:skip until v0.21.0` (PO decision), which the task removes. | [POV-213](https://jumis.myjetbrains.com/youtrack/issue/POV-213); `docs/analysis/test_specification.md` |
| Dependency debt has a deadline | **Task candidate** — orval ≥ 8.22. **Backlog:** grouped frontend majors, frontend lint debt, Hibernate 7.4 paging bugs (reported upstream as HHH-20942 and HHH-20943, awaiting response). **Conscious drop:** the `.trivyignore` expiry (`exp: 2026-12-31`) — CI fails on purpose at expiry, and the per-version supply-chain audit (checklist 3.1a) reviews the entries anyway. | [POV-215](https://jumis.myjetbrains.com/youtrack/issue/POV-215); `docs/backlog/improvements.md` |
| Process weight versus value | **Conscious drop** as a separate item — it is an observation directive, not work: every v0.20 task retrospective records the evidence (review findings per round, cost of the YouTrack record and the Time Log), and the v0.20 finalisation decides with that data. | [v0.19.0 retrospective §4](../v0.19.0/retrospective.md) |
