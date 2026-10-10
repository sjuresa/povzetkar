# v0.21.0 Version Hub

**Status:** In Progress
**YouTrack Fix version:** 0.21.0
**Retrospective:** [retrospective.md](retrospective.md) — one per version since POV-226: the task notes, then the synthesis at finalisation.

---

## Completed Tasks

| Task | Walkthrough |
| --- | --- |
| POV-233 — v0.21.0 Version Initialisation | [walkthrough](POV-233-walkthrough.md) |
| POV-226 — Apply the v0.20 Process-Weight Decisions | [walkthrough](POV-226-walkthrough.md) |

## Carry-Forward Review

Source: [v0.20.0 retrospective — Carry-Forward Themes](../v0.20.0/retrospective.md) (§4). Reviewed at version initialisation (POV-233) per checklist step 1.5; dispositions approved by the PO on 2026-10-10. The task candidates are the next tasks, not a version plan — their Fix version `0.21.0` is the current proposal and may move. The last three rows are candidates from outside the four themes: two backlog entries promoted during the review, and one raised by the PO.

| Theme | Disposition | Reference |
| --- | --- | --- |
| Decide and apply the process-weight proposals | **Task candidate** — one process task changing `workflow-mandatory.md` and the checklist together: the adopted decisions 1, 2 (amended), 4, 5, 6 and 7 of the v0.20 retrospective §3. Decision 3 was rejected and stays out. | POV-226 |
| Per-user isolation is not finished | **Task candidates** — job events per user; inline HQL into named methods; API-key identities (`/users/me` reports the mapped user's permissions instead of the key's, and audit entries lose the user when the username differs from the e-mail). **Backlog:** per-user summary sharing — needs its own model; low urgency while users are admin-vetted. | POV-217, POV-218, POV-227; `docs/backlog/improvements.md` |
| Supply-chain gates have dates and gaps | **Task candidate** — pin the remaining GitHub Actions by commit SHA and add Dependabot for `github-actions`. **Conscious drop:** the `.trivyignore` expiry (`exp:2026-12-31`) — CI fails on purpose at expiry, and the finalisation's supply-chain audit (checklist 3.1a) re-checks every entry. **Backlog:** `postcss-selector-parser` (no clean fix, build-time only), `i18next-http-backend` (low; fixed only by the i18next major-upgrade group), `npm-run-all2`. | POV-230; `docs/backlog/improvements.md` |
| Design documents drift as a family | **Task candidate** — retire or re-synchronise each design document (default: retire where a machine source replaces it, e.g. the API catalogue by the OpenAPI document), and give every surviving document a named sync obligation in the Definition of Done. Root cause (PO): no protocol or checklist step names these documents, so no task ever updated them. | POV-231 |
| *(backlog promotion)* No privacy notice | **Task candidate** — a privacy notice (what is stored, why, for how long), linked from the landing page; decide an age limit for the access log. | POV-228 |
| *(backlog promotion)* Dev stack ports bound to all interfaces | **Task candidate** — bind the dev stack's host ports to `127.0.0.1`. | POV-229 |
| *(raised by the PO)* Manual image version; production rebuilds instead of deploying the scanned image | **Task candidate** — CI pushes the image it scanned to GHCR (tags `sha-<commit>`, plus `X.Y.Z` on `main`); production pulls it instead of rebuilding. | POV-232 |
