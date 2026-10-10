# POV-212 Walkthrough — v0.20.0 Version Initialisation

**Task:** [POV-212](https://jumis.myjetbrains.com/youtrack/issue/POV-212) (Task, Major) · **Branch:** `POV-212` → `v0.20.0` · **Date:** 2026-09-27

## 1. What was done

v0.19.0 was released; this task opens v0.20.0 per checklist Phase 1.

- **1.1** — branch `v0.20.0` from `main` (`c7826b7a`), pushed; `node scripts/update-version.cjs 0.20.0-SNAPSHOT` — the diff is exactly the eight version fields in six files (poms, both `package.json`, both lockfiles).
- **1.2** — Fix version `0.20.0` created in YouTrack by the PO.
- **1.3** — `roadmap.md` rev. 4.1: v0.20 `🚧 In Progress`, its three key tasks linked to POV-213/214/215.
- **1.4 / 1.5** — `docs/versions/v0.20.0/version.md` with the Carry-Forward Review of the four v0.19.0 themes, PO-approved: three task candidates created in YouTrack during the review — [POV-213](https://jumis.myjetbrains.com/youtrack/issue/POV-213) scheduler integration test (TC-ING-006), [POV-214](https://jumis.myjetbrains.com/youtrack/issue/POV-214) Caddy on the production traffic path, [POV-215](https://jumis.myjetbrains.com/youtrack/issue/POV-215) orval ≥ 8.22; the remaining edge and dependency items stay in the backlog; the `.trivyignore` expiry and the process-weight theme are conscious drops with a stated reason.
- **Checklist 4.3** (rev. 3.6) — release tags are `X.Y.Z` without a `v`, pushed as `refs/tags/X.Y.Z`; the old example produced the wrong tag `v0.19.0` in POV-211.
- **TC-ING-006** — time-bound `validate:skip until v0.21.0` with a justification naming POV-213 (`test_specification.md` rev. 2.10). Without it the bump turns the CI planning audit CRITICAL (P0 manual since v0.18, threshold one version). POV-213 removes it.
- **Backlog** — the Hibernate 7.4 paging bugs are reported upstream as HHH-20942 and HHH-20943 (`New`), awaiting a maintainer response.

**Verification:**
- Planning audit `--current-version v0.20.0 --fail-on all`: passed. Before the skip (on `main`): 1 CRITICAL (TC-ING-006). With `--current-version v0.21.0`: TC-ING-006 CRITICAL again — the skip expires as intended.
- Backend `mvnw.cmd clean verify`: 364 tests, 0 failures/errors, 3 skipped; SpotBugs 0 bugs; reactor built as `0.20.0-SNAPSHOT`.
- Frontend `vitest run`: 15 files / 68 tests green.

## 2. Planned vs. actual scope

| Planned | Actual |
| --- | --- |
| Phase 1 (1.1–1.5) | Done |
| Checklist 4.3 tag fix | Done, own commit |
| TC-ING-006 time-bound skip | Done, in the bump commit |
| Hibernate backlog status | Done |

No additions or removals.

## 3. Time delta

Estimate ~1.5 h for the whole task; the share up to the commit ~0.85 h.

| Phase (2026-09-27, CET) | Actual |
| --- | --- |
| Brainstorm 17:43–17:58 | 15 min |
| Planning 17:58–18:04 | 6 min |
| Implementation 18:04–18:09 (incl. suites) | 5 min |
| Pre-commit review round 1 18:09–18:13 + fixes 18:13–18:15 | 6 min |
| Pre-commit review round 2 18:15–18:19 + fixes 18:19–18:22 | 7 min |
| **Up to the commit** | **~49 min** (vs ~0.85 h) |

## 4. Deviations

- **CI consequence of the bump:** the agent first proposed merging with a known-red planning audit, which contradicts `workflow-mandatory.md` Step 8 (green CI always); withdrawn. PO decision: time-bound `validate:skip until v0.21.0`, removed by POV-213.
- **Pre-commit review round 1** (`/code-review high` in a subagent over `git diff v0.20.0`): 7 findings, none high. Fixed: a corrupted path in the Hibernate backlog entry, the 4.3 wording (the `v`-less name avoids the branch clash; the full ref only makes the push unambiguous), promoted backlog entries marked "→ POV-21x", the roadmap "Forecast" note, `until v0.21.0` in the documented `vX.Y.Z` form, and a new backlog item for the root cause (check P0 automation deadlines before the version bump). Round 2 over the whole diff: 5 findings, none high, all fixed — `grafana` DNS and the OCI RUNBOOK entries belong to POV-214 as the backlog already said (hub and backlog aligned), the skip comment kept free of automation keywords the validator matches, TC-ING-006 in Traceability, the retrospective no longer claims the skip leaves the rule intact, two lessons appended to `lessons-learned.md`. The PO then stopped further review rounds. Rejected in round 1: a new release gate for the skip — POV-213 carries Fix version 0.20.0, so checklist 3.1b already blocks the release until it is `Fixed` or re-targeted by the PO.
- **Backend suite run 1** failed in `clean` (`backend/target` locked, most likely by an IDE); the unchanged re-run passed.

## 5. Traceability

- **US:** N/A — version initialisation / process task, no system behaviour change.
- **UC:** N/A
- **TC:** TC-ING-006 — time-bound `validate:skip until v0.21.0` added (automation deferred to POV-213, which removes the skip).
