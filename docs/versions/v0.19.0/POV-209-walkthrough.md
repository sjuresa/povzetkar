# POV-209 Walkthrough — Process: reviewer-agnostic PR loop, pre-commit review, pre-deploy Trivy, YouTrack record, task pause, release finalisation

**Task:** [POV-209](https://jumis.myjetbrains.com/youtrack/issue/POV-209) (Task, Major) · **Branch:** `POV-209` → `v0.19.0` · **Date:** 2026-09-26

## 1. What was done

The interim process rules the PO approved on 2026-09-25 (after POV-207; exercised by POV-208 and POV-206) became the permanent protocols, and the gaps found while preparing to finalise v0.19.0 were closed. One code change: the Trivy GitHub Action is pinned.

- **CI (`fix(ci)`, `53b4868c`):** both Trivy steps in `.github/workflows/ci.yml` moved from `aquasecurity/trivy-action@master` to `@ed142fd0673e97e23eac54620cfb913e5ce36c25 # v0.36.0` (immutable release, commit dereferenced from the annotated tag; it pins `setup-trivy` to the advisory's safe v0.2.6 internally) with `version: 'v0.74.0'`. Evidence gathered on the way: in the March 2026 TeamPCP compromise 76/77 `trivy-action` tags were force-pushed to credential-stealing code (GHSA-69fq-xp46-6x23); `ci.yml` used the tag `@0.24.0` then, CI ran ~25 times on 19–20 March, `4d71a3cc` (2026-03-20 07:42) moved to `@v0.28.0` — another poisoned tag — and `cd55e106` (08:01) to `@master` "to bypass broken tags". Run logs have expired (HTTP 410), so execution of the malicious code can neither be confirmed nor ruled out. Only `TEST_DB_*` and a read-only `GITHUB_TOKEN` were in the job; `GOOGLE_API_KEYS`/`YOUTUBE_API_KEY` were never referenced by `ci.yml`.
- **`workflow-mandatory.md`:**
  - **Step 1** — the Brainstorm ends with the PO's approval of the task definition; Time Log rules for machine waits ≥ 10 min (`[wait: …]` → Testing) and PO pauses (≥ 30 min or declared → `[paused: PO]`, excluded); new *Pausing, resuming and session hand-over* (WIP = 1 stated explicitly).
  - **Step 7** — *Pre-commit Review* (every task, documentation included; subagent by default, PO separate session as fallback); *YouTrack task record* (States, three verbatim comments read back, Fix version, Estimation, work items); the unfounded "process/tooling tasks are exempt by precedent" CHANGELOG clause removed.
  - **Step 8** — reviewer-agnostic: inline threads *and* the reviewer's summary comment (Greptile posts only a conversation comment when it has no findings — the old loop never saw it), wait for the summary on the last pushed commit, ~15 min no-response rule, late reviews still handled.
- **`verification.md`:** new *Pre-deploy Verification* — CI is the scan (green run on the exact deployed commit, re-triggered after 7 days), local Trivy only by exception, `build --pull`, HIGH/CRITICAL stops the deploy, `.trivyignore` entries only with PO approval.
- **Checklist v3.4:** 2.0 (YouTrack `In Progress`, Brainstorm boundary), new 2.5a / 2.5b, 2.9 (task record), 1.2 marked as a PO step, new 3.1a Supply-chain Audit (`.trivyignore` + pinned Trivy versions), 3.1b Fix-version check, 4.2 explicit merge criteria, new 4.4 release deploy and 4.5 YouTrack release (PO step). No renumbering.
- **Single source for the release:** `06_development_process.md` §4.2 (still described `v0.5` branches and roadmap-driven releases) and `methodology.md` Phases 3/4 keep only the intent and point to the checklist.
- **RUNBOOK:** §8.2 — CI is the pre-deploy scan, local scans by exception, image pinned to `aquasec/trivy:0.74.0@sha256:62b1e65e…` (tag and digest verified on Docker Hub); §10.3 — points to Pre-deploy Verification.
- **Knowledge:** new lesson "Pin Third-Party CI Actions and Tool Images by Digest, Not Tag or Branch" (`infrastructure-ops.md`) + v0.19.0 highlight; the "Scan Before Every Production Build" standard now says the scan is CI's.
- **Also:** `AGENT.md` protocol index; backlog — "WIP > 1 execution: git worktree + live task file per task" and "Pin the remaining GitHub Actions by commit SHA + Dependabot"; CHANGELOG — POV-209 (Security + Documentation) and the missing POV-203/POV-204 entries.

## 2. Planned vs. actual scope

The YouTrack scope sketch (Step 1 pause/resume, pre-commit review, reviewer-agnostic Step 8, Step 7 YouTrack record, pre-deploy Trivy) grew in the Brainstorm by the version-finalisation/release gaps (#1, #9), the Trivy action pin (#5, found while analysing CI as the scan) and the Time Log wait/pause rules (#6b/#6c); the PO's Brainstorm-boundary rule (#10) and the CHANGELOG-exemption correction (P3a) were added during definition and planning. WIP > 1 was deliberately left out (#8, backlog). Implementation matched the approved plan; additions within its intent are listed in Deviations.

## 3. Time delta

Estimated ~3 h. Brainstorm 16:27 → definition approved 17:15 (48 min, incl. the CI incident analysis); planning 17:15 → 17:29; implementation + consistency check 17:29 → 17:34; close-out from 17:34. PR loop and YouTrack record follow. *(Final figures are added at completion.)*

## 4. Deviations

- **D1:** the YAML check of the CI commit ran after the commit (a missing Python `yaml` module and a `;`-chained command let the commit through); re-validated with the Node `yaml` parser — both steps pinned, no fix needed.
- **D2:** the agent first applied the docs-only rule (consistency check only: one finding fixed — `methodology.md` Phase 1 "milestone label"; the lesson heading "Scan Before Every Production Build" kept, still true, anchor stable) although `ci.yml` changed. Full `mvn verify` / `vitest` not run — no application layer touched; CI runs them on the PR.
- **D4 (pre-commit review, after the PO's question):** `/code-review high` in a subagent — 12 findings, all verified by the agent against the repository, all fixed:
  - **High:** checklist 2.5b ran before the commit → now "after the push, once CI on the commit is green"; the YouTrack record at 2.9 preceded the walkthrough and the PR loop → now written last, after the Step 8 exit and the final walkthrough.
  - **Medium:** "exact commit" made provable — the CI run's `headSha` must equal the deployed commit, stale runs are re-run with `gh run rerun` (not `workflow run --ref`, which scans the moving head); Step 8 exit condition now includes the no-response case; incident record corrected (`@0.24.0` → `@v0.28.0` in `4d71a3cc` → `@master` in `cd55e106`); local Trivy image pinned by digest (`aquasec/trivy:0.74.0@sha256:62b1e65e…`), checklist 3.1a updated.
  - **Low:** floating Dockerfile bases make "`build --pull` matches CI" untrue → reworded (7-day bound, re-run CI on deploy day for security-sensitive deploys); 4.3 pulls `main` before tagging; pre-commit review time mapped to Testing; pause commits the WIP on the task branch before switching; methodology updated.
  - **PO decision after the review (D5):** no docs-only exemption at all — `/code-review high` runs for every task, documentation included (this task's high findings were in documents); for normative documents the review also checks contradictions, step ordering and executability. The grep consistency check is no longer a separate rule.
- **D3:** within plan intent — RUNBOOK §8.2 names the per-version `.trivyignore` audit as a legitimate local run; checklist 3.1a titled "Supply-chain Audit"; a comment on the pinned `uses:` line points to 3.1a.

## 5. Verification evidence

- **CI YAML:** parsed with the Node `yaml` package — `Run Trivy vulnerability scanner (FS)` and `(Image)` both `aquasecurity/trivy-action@ed142fd…`, `version: v0.74.0`. Functional proof is the green CI run on the PR.
- **Pin provenance:** `gh api repos/aquasecurity/trivy-action/git/ref/tags/v0.36.0` → tag object `a9c7b0f…` → commit `ed142fd0673e97e23eac54620cfb913e5ce36c25` (2026-04-22, release marked immutable); `action.yaml@v0.36.0` pins `setup-trivy@3fb12ec…` (v0.2.6). Trivy `v0.74.0` release immutable; Docker Hub `aquasec/trivy:0.74.0` digest `sha256:62b1e65e…`.
- **Consistency grep** over the live process docs, RUNBOOK, lessons and `.github`: no Gemini-as-reviewer reference, no "exempt by precedent", no `trivy-action@` tag/branch, no unversioned `aquasec/trivy`; new step references (2.5a, 2.5b, 3.1a, 4.4) resolve to existing checklist steps; no live `Step 9`/`execution.md` reference.
- **Pre-commit review:** `/code-review high` on `v0.19.0...POV-209` in a subagent (skill invoked via the Skill tool, ran to completion) plus a semantic review of the protocol text for contradictions/ordering; CI change confirmed correct (`version` input exists at `ed142fd…`, default `v0.70.0`; YAML valid; only behavioural change besides the pin is the Trivy binary version). 12 findings — see D4.
- **Planning audit:** `python scripts/qa/validate-planning.py --current-version v0.19.0 --fail-on all` → "Planning Validation Passed! No violations found."
- **Line endings:** all 11 changed files CRLF-consistent.

## Traceability

N/A — process/CI task, no user-facing functional change.
