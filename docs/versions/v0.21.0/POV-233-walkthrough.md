# POV-233 Walkthrough — v0.21.0 Version Initialisation

**Task:** POV-233 (Task, Major) · **Branch:** `POV-233` → `v0.21.0` · **Date:** 2026-10-10

## 1. What was done

v0.20.0 was released; this task opens v0.21.0 per checklist Phase 1.

- **1.1** — branch `v0.21.0` from `main` (`5056cab0`), pushed; `node scripts/update-version.cjs 0.21.0-SNAPSHOT` — the diff is exactly the eight version fields in six files (both POMs, both `package.json`, both lockfiles).
- **1.2** — Fix version `0.21.0` already existed in YouTrack.
- **1.3** — `roadmap.md` rev. 4.6: v0.21 `🚧 In Progress`, retitled "Per-User Isolation, Privacy & Lighter Process"; its key tasks are the next task candidates, explicitly not a version plan; the `.trivyignore` expiry is re-checked at the finalisation (3.1a) instead of the earlier "renewed before they expire". The in-app roadmap strings (`en.json`/`sl.json`) keep the old title until `sync_metadata.py` runs at the finalisation (3.5), as in v0.20.
- **1.4 / 1.5** — [`version.md`](version.md) with the Carry-Forward Review of the four v0.20.0 themes, PO-approved; seven candidates created in YouTrack during the review:
  - from the themes: POV-226 process-weight decisions, POV-227 API-key identities, POV-230 pinned actions + Dependabot, POV-231 design documents + DoD; POV-217 and POV-218 already existed;
  - backlog promotions: POV-228 privacy notice, POV-229 dev ports on loopback;
  - raised by the PO: POV-232 deploy the CI-scanned image from GHCR.
- **Backlog** — the six entries promoted to a candidate are marked `→ POV-xxx`.

**Verification:**
- Planning audit `--current-version v0.21.0 --fail-on all`: passed (also on `main` before the bump — no time-bound skip needed this time).
- Backend `mvnw.cmd clean verify`: 645 tests, 0 failures/errors, 3 skipped; SpotBugs 0 bugs; reactor built as `0.21.0-SNAPSHOT` (2 min 41 s).
- Frontend `vitest run`: 29 files / 151 tests green.
- `check-links.py`: 0 broken links. Public-content sweep (`sweep-patterns.txt`, 28 patterns) over all added lines of the task diff, re-run after the review fixes: no hit.

## 2. Planned vs. actual scope

| Planned | Actual |
| --- | --- |
| Phase 1 (1.1–1.5) | Done |
| Backlog markers | Done (six entries) |

No additions or removals. The candidate list grew during the Brainstorm (whole-backlog pass, POV-232), before the task definition was approved.

## 3. Time delta

Estimate 2 h for the whole task.

| Phase (2026-10-10, CEST) | Actual |
| --- | --- |
| Brainstorm 15:48–16:43 (incl. internal review of the definition) | 55 min |
| Planning 16:43–16:48 | 5 min |
| Implementation 16:48–16:54 (incl. suites and close-out documents) | 6 min |
| Pre-commit review round 1 and fixes 16:54–16:58 | 4 min |

## 4. Deviations

None from the approved plan. Pre-commit review (`/code-review high`, whole `git diff v0.21.0`), round 1: 4 low findings, all fixed — the sweep claim named a stale line count; the retrospective said "five" design documents (six files in five areas); the roadmap's POV-229 line left out Prometheus; the `i18next-http-backend` advisory had no backlog record (now noted in the i18next upgrade group).

## 5. Traceability

N/A — process/version task, no system behaviour change.
