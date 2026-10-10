# POV-225 Walkthrough — v0.20.0 Version Finalisation & Release

**Task:** POV-225 (Task, Major) · **Branch:** `POV-225` → `v0.20.0` · **Date:** 2026-10-10

## 1. What was done

The PO declared v0.20.0 complete. The finalisation ran as one task covering checklist Phase 3 (Version Finalisation)
and Phase 4 (Release). It also included one PO decision from POV-224: a read-only token for the CI workflow.

**CI token.** `.github/workflows/ci.yml` now declares `permissions: contents: read` at the top level. Before, the
workflow declared no permissions, so its `GITHUB_TOKEN` got whatever default the repository settings grant. The
workflow only reads with it (checkout; the setup actions and Trivy use it for release downloads); nothing in
`.github/` writes — no SARIF upload, no `gh`, no PR write.

**Phase 3 — Version Finalisation.**
- **3.1 documents:**
  - `docs/versions/v0.20.0/retrospective.md` — the cross-task synthesis, "what the outside sees". It has four
    systemic patterns and four Carry-Forward Themes. Process Health holds the evidence on v0.19 carry-forward
    theme 4 (process weight versus value): booked time per task and type, review rounds with their findings, and
    YouTrack-record minutes. It also holds seven keep / reduce / drop proposals with the PO's decision per point.
  - `version.md` → `Completed`, with a scope statement and the POV-225 row; the Fix-version line is now a plain
    `0.20.0` instead of a tracker link.
  - `roadmap.md` rev. 4.5: v0.20 rewritten to actual delivery and `✅ Completed`; a minimal v0.21 forecast
    (`📋 Planned`).
  - `improvements.md`: three completed items removed (the scheduler integration test, done in POV-213; the
    `.env.docker` cleanups of the Caddy keys and of `POVZETKAR_BRIEFING_CRON`, done by the PO).
  - `07_development_methodology_comparison.md` §7 rev. 2.1: the v0.20 outcome of the three v0.19 proposals, and a
    pointer to the v0.20 proposals.
- **3.1a supply-chain audit (Trivy 0.75.0, image pinned by digest, without `.trivyignore`):**
  - Image (`povzetkar:pov225-audit`, built with `--pull`): exactly the nine suppressed CVEs (`libexpat1` ×5,
    `libuuid1` ×4), all HIGH and still reported. Nothing else was HIGH or CRITICAL.
  - FS: 0 HIGH/CRITICAL and 0 secrets in five targets (`pom.xml`, `backend/pom.xml`, both npm lockfiles,
    `transcript-proxy/requirements.txt`). The online run stopped on a Maven Central `429`. The offline variant from
    RUNBOOK §8.2 completed in 2 min 10 s after a local build of the same tree; its only warning was `[pip]
    site-packages`, with no unresolved POM.
  - → `.trivyignore` unchanged. `trivy-action` v0.36.0 and Trivy v0.75.0 are the latest releases (GitHub API,
    2026-10-10) → no bump.
- **3.1b Fix-version check:** POV-212 … POV-216 and POV-219 … POV-224 are all `Fixed`. POV-225 is the only open
  0.20.0 issue; 2.15 closes it.
- **3.2 project-wide review:**
  - Planning audit `--fail-on all` passed for v0.20.0. It also passed as a lookahead for v0.21.0, so the next
    version bump makes no P0 automation due (backlog "Check P0 automation deadlines before the version bump").
  - General Refresh: a search of the living documents for stale v0.20 claims (Caddy, CORS/`FRONTEND_URL`, the
    former proxy host, Trivy 0.74, `-SNAPSHOT`, Vitest 3) found only accurate or historical mentions.
  - `AGENT.md` needs no change, because the checklist is unchanged.
  - `check-links.py`: no broken link.
- **3.3** `node scripts/update-version.cjs 0.20.0`: exactly eight version fields in six files; no `-SNAPSHOT` left.
- **3.4 CHANGELOG:** `[0.20.0] - 2026-10-10` with a Summary and a new empty `[Unreleased]`. The POV-215 Vitest
  entry was trimmed of test-internal detail, and a stray blank line removed. POV-225 entry under *Security*.
- **3.5** `sync_metadata.py`, then manual Slovenian translation of the 0.20.0 changelog description and the v0.21
  roadmap description; the final newline restored in both locale files. The script again dropped the oldest
  landing-page version (v0.11.0) — the known defect in the backlog entry.
- **Public content:** production-detail sweep (private pattern list, plus tracker links and e-mail addresses) over
  the whole task diff, also with lines joined: 0 hits.

**Verification:**
- Backend `mvnw.cmd clean verify`: 645 tests, 0 failures/errors, 3 skipped; SpotBugs 0; reactor built as
  `0.20.0`.
- Frontend `vitest run`: 29 files / 151 tests green; `npm run build` OK.
- Planning audit and link check green.
- The `permissions` block is to be proven by the task PR's CI run (checkout with `contents: read`).

## 2. Planned vs. actual scope

| Planned | Actual |
| --- | --- |
| CI `permissions: contents: read` | Done |
| 3.1 documents incl. the theme-4 synthesis and proposals | Done; PO decisions recorded in the retrospective §3 |
| 3.1a audit | Done; no `.trivyignore` change, no pin bump; FS via the offline variant (Maven Central `429`) |
| 3.2 review | Done; no stale claim found; planning audit also run for v0.21.0 (not planned, cheap) |
| 3.3–3.5 | Done |
| Phase 4 | After the commit — Time Log, work items and the YouTrack walkthrough addendum |

## 3. Time delta

Estimate 4.5 h for the whole task (Phase 4 included). Up to the commit, the Phase 3 share was planned at about
2.75 h (documents 1.75 h, audit 0.5 h, review 0.5 h).

| Phase (2026-10-10, CET) | Actual |
| --- | --- |
| Brainstorm 13:28–13:47 | 19 min |
| Planning 13:47–13:50 | 3 min |
| Implementation 13:50–14:18 (documents, Trivy, suites, the PO's theme-4 decisions) | 28 min |
| Pre-commit review round 1 14:18–14:27 + fixes 14:27–14:28 | 10 min |
| **Up to the commit** | **~1 h** (vs. ~2.75 h planned for this share) |

## 4. Deviations

- **Trivy FS online run hit Maven Central `429`:** the offline variant (RUNBOOK §8.2) was run after the backend
  suite had filled the local Maven cache with this tree's POMs.
- **Planning audit run for v0.21.0 as well:** a cheap check for the backlog item on P0 deadlines; it passed.
- **Pre-commit review (2.5a):** round 1 (`/code-review high --max-findings all` in a subagent over
  `git diff v0.20.0`, 17 files) returned 13 verified findings; 12 fixed, 1 rejected (it was checked against an
  outdated copy of the task log). The three substantive ones were facts in the version retrospective: the PO had
  stopped or limited the review rounds in every task, not in four; the first whole-diff round, not round 1, found
  POV-215's runtime defect, and POV-216's round 1 was a manual review; and not every other task was found by its
  predecessor. The rest were wording, a restored CHANGELOG half-sentence, the landing-page badge for 0.20.0 (`new`
  instead of `fixed`, a `sync_metadata.py` defect added to its backlog entry) and two Slovenian terms.
- **Plan self-review:** the plan's draft assumed a "Planned" roadmap status convention; none existed, so
  `📋 Planned` was grounded in `sync_metadata.py`, which skips only `Completed`.

## 5. Traceability

N/A — release/process task; no system behaviour change (version string, CI token scope, documentation).
