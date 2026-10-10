# v0.20.0 Version Hub

**Status:** Completed
**YouTrack Fix version:** 0.20.0

---

## Scope

v0.20.0 delivered the outside view of the application — what a browser, a scanner, another user, a visitor,
YouTube and a reader of the repository see:

- **Production edge:** Caddy removed; the Cloudflare tunnel is the only entry. The app sends the security headers,
  an enforced CSP and the cache policy itself, and trusts forwarded headers only from the tunnel (POV-214). Probe
  paths get 404, the `uri` metric tag is bounded, expired-session XHRs get 401, and CORS is removed (POV-221).
- **Per-user data:** sources and content are scoped to the user's topics (POV-219). The onboarding guide is held per
  user on the server, and the pending-approval banner detects approval by itself (POV-216).
- **Product:** a landing page around the product, with honest feature claims; the first visit follows the browser
  language and system theme; one Slovenian vocabulary across the app (POV-220).
- **Operations:** serial, human-like pacing of YouTube calls with bounded per-video retries and any-language
  transcripts (POV-222). The scheduled run is tested the way the scheduler fires it, and retention and enrichment
  run in the daytime (POV-213).
- **Supply chain:** orval 8.41.0 and Vitest 5.0.3 fix the critical build-time advisories (POV-215). `shell-quote`
  is fixed, and CI now scans npm development dependencies with Trivy 0.75.0 (POV-224). The CI workflow token is
  read-only (POV-225).
- **Public repository:** a showcase README, `SECURITY.md`, production operations moved to a private runbook, and the
  public-content rule (POV-223).

The finalisation (POV-225) wrote the cross-task [retrospective](retrospective.md). Its §3 holds the process-weight
evidence and the proposals that v0.19's carry-forward theme 4 asked for.

## Completed Tasks

| Task | Walkthrough | Retrospective |
| --- | --- | --- |
| POV-212 — v0.20.0 Version Initialisation | [walkthrough](POV-212-walkthrough.md) | [retrospective](POV-212-retrospective.md) |
| POV-216 — Pending-approval Banner and Per-user Onboarding Guide | [walkthrough](POV-216-walkthrough.md) | [retrospective](POV-216-retrospective.md) |
| POV-219 — Sources and Content Scoped to the User's Topics | [walkthrough](POV-219-walkthrough.md) | [retrospective](POV-219-retrospective.md) |
| POV-220 — Landing Page Redesign | [walkthrough](POV-220-walkthrough.md) | [retrospective](POV-220-retrospective.md) |
| POV-214 — Remove Caddy: Security Headers, Caching and Proxy Trust in the Application | [walkthrough](POV-214-walkthrough.md) | [retrospective](POV-214-retrospective.md) |
| POV-221 — Edge Hardening: 404 for Probe Paths, Bounded `uri` Tag, 401 for Unauthenticated XHR, CORS Removed | [walkthrough](POV-221-walkthrough.md) | [retrospective](POV-221-retrospective.md) |
| POV-222 — Human-Like Serial Pacing of YouTube Calls, Bounded Per-Video Retries, Any-Language Transcripts | [walkthrough](POV-222-walkthrough.md) | [retrospective](POV-222-retrospective.md) |
| POV-213 — Scheduler-Fired Integration Test for the Scheduled Run; Daytime Cron for Retention and Enrichment | [walkthrough](POV-213-walkthrough.md) | [retrospective](POV-213-retrospective.md) |
| POV-215 — Upgrade orval to 8.41.0 and Vitest to 5.0.3 (CRITICAL Advisories) | [walkthrough](POV-215-walkthrough.md) | [retrospective](POV-215-retrospective.md) |
| POV-223 — Prepare the Repository for Public Release | [walkthrough](POV-223-walkthrough.md) | [retrospective](POV-223-retrospective.md) |
| POV-224 — Upgrade shell-quote, Scan npm Dev Dependencies in CI, Trivy 0.75.0 | [walkthrough](POV-224-walkthrough.md) | [retrospective](POV-224-retrospective.md) |
| POV-225 — v0.20.0 Version Finalisation & Release | [walkthrough](POV-225-walkthrough.md) | [retrospective](POV-225-retrospective.md) |

## Carry-Forward Review

Source: [v0.19.0 retrospective — Carry-Forward Themes](../v0.19.0/retrospective.md). Reviewed at version initialisation (POV-212) per checklist step 1.5; dispositions approved by the PO on 2026-09-27.

| Theme | Disposition | Reference |
| --- | --- | --- |
| The production edge is not yet what the architecture describes | **Task candidate** — put Caddy on the traffic path, together with the entries the backlog already folds into it — the security-header delivery test, restricting forwarded-header trust to Caddy, the `grafana` DNS record and the OCI claims in the RUNBOOK. The other edge items stay **backlog**: SPA fallback answering 200 to probe paths, Micrometer `uri`-tag explosion, suspicious-traffic alerting, 302 instead of 401 for expired-session XHRs, ownership scoping on detail endpoints. | [POV-214](https://jumis.myjetbrains.com/youtrack/issue/POV-214); `docs/backlog/improvements.md` |
| The scheduler integration test is now forced | **Task candidate** — the next task. Until it is done, TC-ING-006 carries a time-bound `validate:skip until v0.21.0` (PO decision), which the task removes. | [POV-213](https://jumis.myjetbrains.com/youtrack/issue/POV-213); `docs/analysis/test_specification.md` |
| Dependency debt has a deadline | **Task candidate** — orval ≥ 8.22. **Backlog:** grouped frontend majors, frontend lint debt, Hibernate 7.4 paging bugs (reported upstream as HHH-20942 and HHH-20943, awaiting response). **Conscious drop:** the `.trivyignore` expiry (`exp: 2026-12-31`) — CI fails on purpose at expiry, and the per-version supply-chain audit (checklist 3.1a) reviews the entries anyway. | [POV-215](https://jumis.myjetbrains.com/youtrack/issue/POV-215); `docs/backlog/improvements.md` |
| Process weight versus value | **Conscious drop** as a separate item — it is an observation directive, not work: every v0.20 task retrospective records the evidence (review findings per round, cost of the YouTrack record and the Time Log), and the v0.20 finalisation decides with that data. | [v0.19.0 retrospective §4](../v0.19.0/retrospective.md) |
