# Development Process Checklist

## 1. Introduction

This document provides a comprehensive checklist for our development process. It serves as a step-by-step guide to ensure that all phases of a development cycle, from version initialisation to release, are executed consistently and thoroughly.

The **Task** is the primary planning and delivery unit. A **Version** is a retrospective label declared after the work — not a plan made before it.

## Phase 1: Version Initialisation

*This phase is executed once at the beginning of a new version cycle. It opens the branch, registers the version, creates the version hub, and reviews the previous version's retrospective — planning happens at the task level.*

- [ ] **1.1. Prepare Development Branch**: Create the new version branch (e.g., `vX.Y.0`) from `main` and update version numbers using `node scripts/update-version.cjs <version>-SNAPSHOT`.
- [ ] **1.2. Create Milestone** *(PO step — the YouTrack MCP cannot manage versions)*: Register the version as a Fix-version value in YouTrack.
- [ ] **1.3. Roadmap Entry**: Add the version to `docs/overview/roadmap.md` as in-progress.
- [ ] **1.4. Create Version Hub**: Create `docs/versions/vX.Y.Z/version.md` with the following initial content:
  - Header with **Status:** `In Progress` and the YouTrack Fix version (created by the PO in 1.2).
  - Empty task list section with this exact table structure: `| Task | Walkthrough | Retrospective |` followed by a separator row `| --- | --- | --- |`.
  - Empty `## Carry-Forward Review` section with this exact table structure: `| Theme | Disposition | Reference |` followed by a separator row `| --- | --- | --- |`.
- [ ] **1.5. Review Previous Version Retrospective**: Read the *previous* version's retrospective — `docs/versions/<previous version>/retrospective.md` (focus: the Carry-Forward Themes section). For each theme, the agent proposes a disposition — **backlog entry** (`docs/backlog/improvements.md`), **task candidate** (YouTrack), or **conscious drop with a stated reason** — and the PO explicitly approves or amends each one. Record the outcomes in the new version hub's `## Carry-Forward Review` table (theme → disposition → reference), citing the source retrospective.

## Phase 2: Task Execution (Core Loop)

*This phase is repeated for every task in the version. It is the primary phase of the lifecycle.*

**Execution order** (step numbers are stable identifiers, not the sequence): 2.0 → 2.1 → 2.2 → 2.3 → 2.3a → 2.4 → 2.5 → close-out documents 2.6, 2.7, 2.10–2.12, 2.14 → 2.5a → 2.8 → 2.9 → 2.8a → 2.5b (if deploying) → 2.15. Step 2.13 applies whenever a discovery occurs. This matches `workflow-mandatory.md` Steps 7–8.

- [ ] **2.0. Brainstorm:** First, read the **Feed-forward** sections of all task retrospectives in the current version's directory (`docs/versions/vX.Y.Z/POV-XXX-retrospective.md`) — feed-forward knowledge is a version-level pool, not a chain between consecutive tasks, so any earlier task of the version may hold context relevant to this one. Then conduct a scope-alignment interview with the PO **before** writing or finalising `agent_logs/task.md`. Use the following prompt:

    > *"Interview me relentlessly about every aspect of this task's scope until we reach a shared understanding. Walk down each branch of the requirements tree, resolving dependencies between decisions one-by-one. For each question, provide your recommended answer. Ask the questions one at a time. If a question can be answered by exploring the codebase, explore the codebase instead."*

    Focus: **WHAT** will be done — scope boundaries, risks, whether the task is correctly defined. At the start, set the YouTrack issue to `In Progress` and record the Brainstorm start in the `## Time Log` section of `agent_logs/task.md` (all phase timestamps live there — see `workflow-mandatory.md` Step 1). The Brainstorm ends with the PO's approval of the task definition (2.1), not with the interview.

- [ ] **2.1. Define Task:** Finalise the task definition in `agent_logs/task.md`, including the `## Documentation` section specifying context and required updates. If the task delivers a **user-facing functional change**, mint a new User Story in the catalogue (`docs/sdlc/01_functional_requirements.md` §3) as `(Planned)`, using the area-based ID scheme — see [Planning Protocol §2.1](../process/planning_protocol.md). Fix/infra/perf/security/process tasks mint no US.

- [ ] **2.2. Agent Proposes Plan:** The AI agent conducts a PPP planning interview (HOW it will be done) following `AGENT.md` and `docs/protocols/workflow-mandatory.md`. Use the following prompt:

    > *"Interview me relentlessly about every aspect of this plan until we reach a shared understanding. Walk down each branch of the design tree, resolving dependencies between decisions one-by-one. For each question, provide your recommended answer. Ask the questions one at a time. If a question can be answered by exploring the codebase, explore the codebase instead."*

    Focus: **HOW** it will be done — technical plan grounded in codebase evidence, ADRs where warranted, acceptance criteria.

- [ ] **2.3. User Approves Plan:** The plan is reviewed and explicitly approved.
- [ ] **2.3a. Pre-Execution Gate:** Confirm that an explicit approval for the plan has been received. **Execution is forbidden without this approval.**
- [ ] **2.4. Agent Executes Plan:** The agent executes the plan step-by-step, including all necessary code changes and documentation updates.
- [ ] **2.5. Verify Tests:** After code modifications, the agent runs the full test suite for every touched layer itself (`docs/protocols/verification.md`) and reports the result. The PO may optionally re-run the suite before confirming completion (2.9).
  - [ ] **QA Check:** Ensure no blocking code (e.g., `Thread.sleep`) exists in reactive pipelines.
  - [ ] **QA Check:** Verify JPA entities implement `equals()` and `hashCode()` based on their primary key (or business key), not the default object identity.
  - [ ] **QA Check:** Verify that integration tests do not mock internal services or Panache entities.
  - [ ] **QA Check:** Ensure explicit dependency versions in pom.xml do not conflict with the framework's BOM (Bill of Materials). *Note: Investigate maven-enforcer-plugin (e.g., dependencyConvergence, requireUpperBoundDeps) during Task POV-89 to automate this.*
  - [ ] **QA Check (Local Security):** Run `mvn spotbugs:check` (or ensure it ran successfully during `mvn verify`) to validate that no static security flaws or basic bugs exist.
  - [ ] **Protocol:** Run a full `mvn clean verify` locally before declaring the task complete when the backend layer was touched (frontend: `npx vitest run` — see `verification.md`).
- [ ] **2.5a. Pre-commit Review:** After the suite is green and the close-out documents are written, before the first commit — `/code-review high` for every task, documentation included, plus `/security-review` when auth/authz/secrets/crypto/attack surface is touched. Runs in a subagent by default (PO separate session as fallback); findings and dispositions in `## Deviations`, summary in the walkthrough — see `workflow-mandatory.md` Step 7, *Pre-commit Review*.
- [ ] **2.5b. Pre-deploy Verification** *(only for tasks that build or deploy a production image; runs after the push in 2.8a — it needs a pushed commit — before or after the merge)*: a green `push`/`workflow_dispatch` CI run that built the deployed commit (a `pull_request` run builds the merge ref and does not qualify; re-scan if it started more than 7 days ago), `build --pull`, rollback tag, deploy on the PO's explicit "deploy"; a HIGH/CRITICAL finding stops the deploy — see `docs/protocols/verification.md`, *Pre-deploy Verification*.
- [ ] **2.6. Update `CHANGELOG.md`:** Add a brief entry for the completed task under the `[Unreleased]` section.
- [ ] **2.7. Update Backlog & Lessons Learned:**
  - [ ] Log any non-critical improvements discovered to `docs/backlog/improvements.md`.
  - [ ] If any systemic bug fixes, architectural choices, or technical hurdles were overcome, append them to `docs/process/lessons-learned.md` as mandated by `workflow-mandatory.md` Step 7.
- [ ] **2.8. Agent Proposes Commit Message:** The agent proposes a commit message following the Conventional Commits standard.
- [ ] **2.8a. Push & PR Review Loop:** On the PO's go, push the task branch, open the PR into the version branch, and run `workflow-mandatory.md` Step 8 until its exit condition holds. The PO merges.
- [ ] **2.9. User Confirms Task Completion:** The user confirms the task is fully complete, and the changes are committed.
- [ ] **2.10. Walkthrough:** Write `docs/versions/vX.Y.Z/POV-XXX-walkthrough.md` with the following sections:
    1. **What was done** — the primary deliverable; what changed and what was built.
    2. **Planned vs. actual scope** — any additions or removals relative to the original task definition.
    3. **Time delta** — estimated vs. actual time (from the `## Time Log` in `agent_logs/task.md`).
    4. **Deviations** — any decisions made mid-execution that deviated from the plan.
    5. **Traceability** — the task's US/UC/TC IDs, or an explicit `N/A — <reason>`. Convention: labelled bullet lines (`**US:**` / `**UC:**` / `**TC:**`), or a single `N/A — <reason>` line. This section is mandatory and machine-validated: `validate-planning.py` flags a walkthrough with no Traceability section (or one declaring neither IDs nor N/A) as a WARNING, which blocks CI (`--fail-on all`); broken IDs are CRITICAL.
- [ ] **2.11. Task Retrospective:** Write `docs/versions/vX.Y.Z/POV-XXX-retrospective.md` with the following sections:
    1. **Planned vs. actual** — what was scoped vs. what was delivered.
    2. **Deviations & why** — decisions made mid-execution and their rationale.
    3. **Technical findings** — patterns, gotchas, and constraints discovered; the durable record for future tasks.
    4. **Feed-forward** — what the next task should know.
- [ ] **2.12. Update Version Hub:** Add a link to this task's walkthrough and retrospective under the task list section of `docs/versions/vX.Y.Z/version.md`. (Safety net: if the hub does not exist — e.g. the version was started without a full Phase 1 — create it first per step 1.4 and flag the gap to the PO.)
- [ ] **2.13. Handle Unplanned Discoveries:** If a new issue or potential improvement is discovered during task execution, follow this process:
  - **Assess the issue:** (1) Is it a **critical usability blocker** that prevents core functionality? (2) Is the solution **minimal, low-risk, and aligned** with the current version's goals?
  - **If both YES:** Pause, discuss, and agree to address it within the current task or as an immediate follow-up task.
  - **If either NO:** Do not let it derail the current task. Log the issue in `docs/backlog/improvements.md` for future planning.
- [ ] **2.14. Master Specification Sync**: Close the task's traceability loop:
  - [ ] If the task minted a `(Planned)` US at step 2.1, flip it to `(Implemented)` in `docs/sdlc/01_functional_requirements.md` §3.
  - [ ] Update `docs/analysis/use_cases.md` and `docs/analysis/test_specification.md` so they accurately reflect the task's final outcome (new/changed UC flows, TC rows with honest automation status) — or record a justified `N/A` in the walkthrough's Traceability section (step 2.10). Ensure all new P0 Test Cases are marked for automation within one version.
  - [ ] **Planning Audit**: Run `python scripts/qa/validate-planning.py --current-version vX.Y.Z` to verify the traceability chain (Task → US/UC/TC, walkthrough Traceability sections) remains intact. Refer to [Documentation Validation Protocol](../process/validation-protocol.md) for mandatory sync requirements.
- [ ] **2.15. YouTrack Task Record:** Last step — after 2.9, the PR review loop (2.8a) and the deploy (2.5b), if any. The walkthrough is not edited after the commit; its time delta covers the task up to the commit, PR-loop and deploy time go into the work items, deploy evidence into an addendum to the walkthrough comment. The agent writes the YouTrack task record (`workflow-mandatory.md` Step 7): three verbatim comments (task definition, approved plan, walkthrough) read back against their files; State `Fixed`, Fix version, Estimation; per-phase work items derived from the `## Time Log` in `agent_logs/task.md` (Brainstorm/planning and close-out docs → **Documentation**; implementation, fixes between review rounds, PR-loop work and the deploy → **Development**; `[wait: …]` intervals and the pre-commit review rounds → **Testing** — all task time is booked, each minute under exactly one type; `[paused: …]` intervals excluded and listed to the PO).

## Phase 3: Version Finalisation

*This phase is triggered by an explicit PO decision that the version is complete — there is no longer an objective task list to signal completion.*

*The finalisation runs as a task (`agent_logs/task.md` per `workflow-mandatory.md` Step 1) whose requirements are the Phase 3 and Phase 4 steps of this checklist, executed in order; the Phase 2 close-out steps (pre-commit review, walkthrough, retrospective, PR review loop, YouTrack task record) apply to it. Order: Phase 3 → the task's commit, PR into the version branch and PR review loop (2.8a), merged **before** 4.1 so the release carries the finalisation → 4.1–4.4 → the task's YouTrack record (2.15), after the release deploy (4.4) and **before** the PO releases the version in YouTrack (4.5), so no issue of the released version is still open. Step 3.1b therefore excludes the finalisation task itself, which 2.15 closes. There is no separate release template — this checklist is the single definition of the release steps.*

- [ ] **3.1. Finalise Documentation Content:**
  - [ ] Write `docs/versions/vX.Y.Z/retrospective.md` as a cross-task synthesis — trends, systemic patterns, process health. Input: all task retrospectives in `docs/versions/vX.Y.Z/`. This is not a collection of task findings; it is a pattern-level synthesis across them. It **must** end with a **Carry-Forward Themes** section — version-level threads for the next version, which are formally reviewed and dispositioned at the next version's initialisation (step 1.5).
  - [ ] Update the status to `Completed` and add a scope statement to `docs/versions/vX.Y.Z/version.md` summarising what the version delivered.
  - [ ] Update `docs/overview/roadmap.md`: Mark version as `✅ Completed` and add a revision entry.
  - [ ] Review and clean `docs/backlog/improvements.md` based on completed work and new findings.
- [ ] **3.1a. Supply-chain Audit:**
  - [ ] `.trivyignore`: run Trivy image + FS locally **without** the ignore file (RUNBOOK §8.2; once per version). Remove entries that are no longer reported; every remaining entry keeps a rationale and an `exp:` date.
  - [ ] Check for newer releases of the pinned `aquasecurity/trivy-action` (commit SHA in `.github/workflows/ci.yml`) and the Trivy binary (`version:` there and the `aquasec/trivy:<version>@sha256:<digest>` reference in RUNBOOK §8.2); bump deliberately — immutable releases only, action SHA dereferenced from the tag, image digest taken from the registry.
- [ ] **3.1b. YouTrack Fix-version Check:** every issue with Fix version X.Y.Z is `Fixed`; unfinished ones are re-targeted or lose the Fix version (PO confirms).
- [ ] **3.2. Project-Wide Documentation Review**: Execute the following prompts to ensure project-wide documentation is up-to-date with any process changes or learnings from the completed version:
  - [ ] **Planning Audit**: Perform a final whole-project audit using `python scripts/qa/validate-planning.py --current-version vX.Y.Z --fail-on all`. Refer to [Documentation Validation Protocol](../process/validation-protocol.md) for release blocking criteria.
  - [ ] **General Refresh**: "We are finalizing version vX.Y.Z. Please check if the project documentation in `docs/` needs to be refreshed based on the completed tasks and retrospectives in `docs/versions/vX.Y.Z/` (specifically in directories: `docs/analysis`, `docs/overview`, `docs/process`, `docs/sdlc`). Today is [Current Date]."
  - [ ] **Agent Protocol Check**: "Is it necessary to update `AGENT.md` based on changes in `11_development_checklist.md`?"
  - [ ] **Methodology Update**: "In `07_development_methodology_comparison.md`, section 7 lists possible improvements. Refresh this if anything was implemented or add new proposals for the future."
- [ ] **3.3. Finalise Project Versions**: Remove the `-SNAPSHOT` suffix using `node scripts/update-version.cjs <version>`.
- [ ] **3.4. Finalise `CHANGELOG.md`:**
  - [ ] **Editorial Cleanup:** Remove internal refactoring noise and consolidate duplicates. Ensure "Fixed" items refer to issues present in previous versions, not just temporary bugs encountered during development.
  - [ ] Move all changes from the `[Unreleased]` section to a new version section (e.g., `[X.Y.Z] - YYYY-MM-DD`).
  - [ ] Write a `### Summary` paragraph under the new version header — the release's main theme and key achievements. `sync_metadata.py` (3.5) takes the landing-page description from it.
  - [ ] Add a new, empty `[Unreleased]` section at the top.
- [ ] **3.5. Sync Landing Page Metadata:** Run `python scripts/sync_metadata.py` to ground the vision path and strategic updates in the latest `CHANGELOG.md` and `roadmap.md` changes. Then review the diff of `frontend/public/locales/en.json` / `sl.json`: the script writes English into `sl.json`, so translate the new changelog and roadmap descriptions by hand (backlog: "`sync_metadata.py` copies English into `sl.json`").

## Phase 4: Release

*This phase is executed after the version is finalised.*

- [ ] **4.1. Create Release PR:** Create a Pull Request from the version branch (e.g., `vX.Y.0`) to `main`.
- [ ] **4.2. Merge to `main`:** Merge criteria: green CI (including the Trivy scans and SAST), the PR review loop completed (`workflow-mandatory.md` Step 8). The PO merges.
- [ ] **4.3. Create Git Tag:** After the merge, update the local `main` (`git checkout main && git pull`), then create and push an annotated Git tag (e.g., `git tag -a vX.Y.0 -m "Release version X.Y.0"`).
- [ ] **4.4. Deploy the Release:** Deploy the tagged `main` commit to production per `docs/protocols/verification.md`, *Pre-deploy Verification*, on the PO's explicit "deploy". The image carries the release version (no `-SNAPSHOT`), so production shows which release runs.
- [ ] **4.5. Release in YouTrack** *(PO step)*: mark version X.Y.Z as released.

## Revision History

| Version | Date | Author | Changes |
| --- | --- | --- | --- |
| 3.5 | 2026-09-27 | Povzetkar AI | Release template retired (POV-211): Phase 3 intro states that the finalisation runs as a task whose requirements are the Phase 3–4 steps, and its order (task PR merged before 4.1, YouTrack record after 4.4 and before 4.5; 3.1b excludes the finalisation task); `release_task_template.md` deleted — it duplicated these steps and drifted after POV-191, POV-201 and POV-209. Its one requirement missing here moved in: 3.4 CHANGELOG `### Summary`; 3.5 gains the manual `sl.json` translation. 3.2 General Refresh no longer lists `docs/strategy` (its only document, `STRATEGIC_NEXT_STEPS.md`, stale since 2025-08, deleted). |
| 3.4 | 2026-09-26 | Povzetkar AI | Review, deploy and release gates (POV-209): 2.0 — YouTrack `In Progress` at start, Brainstorm ends with the PO's approval of the task definition; explicit Phase 2 execution order (numbers stay stable identifiers); new 2.5a Pre-commit Review (every task, after the close-out documents) and 2.5b Pre-deploy Verification (conditional, after the push); new 2.8a Push & PR Review Loop; 2.9 reduced to the PO confirmation; new 2.15 YouTrack Task Record (last step: verbatim comments, State/Fix version/Estimation, `[wait]`/`[paused]` work-item rules); 1.2 marked as a PO step; new 3.1a Supply-chain Audit (`.trivyignore`, pinned Trivy action/binary) and 3.1b Fix-version check; 4.2 explicit merge criteria; new 4.4 release deploy and 4.5 YouTrack release (PO step). No renumbering. |
| 3.3 | 2026-07-06 | Povzetkar AI | Agent protocol modernization (POV-204): step 2.5 — the agent runs the full test suite itself (manual PO test runs were a relic of agents unable to run tests; PO re-run optional); step 2.9 gains post-confirmation YouTrack `log_work` (per-phase work items from the Time Log); execution.md retired — phase timestamps live in `agent_logs/task.md` `## Time Log` (2.0, 2.10/3 re-pointed), deviations in `## Deviations`; `workflow-mandatory.md` renumbered (old Step 9 → Step 7 in 2.7). Approval gates 2.3/2.3a/2.9 semantics unchanged. |
| 3.2 | 2026-07-06 | Povzetkar AI | Traceability restoration (POV-203): step 2.1 gains the US-minting rule (user-facing functional change → `(Planned)` US in the FRD §3 catalogue); step 2.10 gains the mandatory Traceability walkthrough section (5th section, machine-validated); step 2.14 reworded — flip US to `(Implemented)`, sync `use_cases.md`/`test_specification.md` or record justified N/A. |
| 3.1 | 2026-07-03 | Povzetkar AI | Retrospective Review Process (POV-201): Phase 1 gains 1.4 Create Version Hub and 1.5 Review Previous Version Retrospective (carry-forward disposition into version.md); step 2.0 mandates reading Feed-forward sections of the current version's task retrospectives; conditional step 2.10 Version Hub Check removed, 2.11–2.15 renumbered to 2.10–2.14; Phase 3.1 mandates the Carry-Forward Themes section. |
| 3.0 | 2026-05-27 | Povzetkar AI | Task-First Planning (POV-191): Phase 1 shrunk to branch+milestone+roadmap; Phase 2 gains Brainstorm (2.0), PPP interview prompts, Walkthrough (2.10), Task Retrospective (2.11), Version Hub (2.12–2.13); Phase 3 trigger is explicit PO decision; version retrospective is cross-task synthesis. |
| 2.7 | 2026-04-01 | Povzetkar AI | Integrated [Project Planning Protocol (PPP)](../process/planning_protocol.md) as a mandatory standard for Phase 1. |
| 2.6 | 2026-03-30 | Povzetkar AI | Relocated script to `scripts/qa/`, removed hardcoded filenames, and hardened ID extraction logic. |
| 2.5 | 2026-03-30 | Povzetkar AI | Linked [Documentation Validation Protocol](../process/validation-protocol.md) for rule enforcement. |
| 2.4 | 2026-03-30 | Povzetkar AI | Mandated Planning Audit via `validate-planning.py` across all phases. |
| 2.3 | 2026-03-30 | Povzetkar AI | Mandated Master Spec Sync (2.11) and US->UC Traceability (1.6) for Operational Excellence. |
| 2.2 | 2026-03-28 | Povzetkar AI | Mandated ASCII Wireframes (1.5), Resilience Analysis (1.7), and Dependency Audits (1.11). |
| 2.1 | 2026-03-28 | Povzetkar AI | Mandated Master Test Suite (1.6), P-levels/Skill Mapping (1.7), and Expert Review (1.11). |
| 2.0 | 2026-03-28 | Povzetkar AI | Integrated `update-version.cjs` for monorepo version synchronization (Phase 1 & Phase 3). |
| 1.9 | 2026-03-27 | Povzetkar AI | Added Phase 3 mandate for syncing landing page metadata via `sync_metadata.py`. |
| 1.8 | 2026-03-25 | Povzetkar AI | Formalized document formatting (Revision History at end) and refined Planning/Task document standards based on v0.14.0 learnings. |
| 1.7 | 2026-03-19 | Povzetkar AI | Upgraded Phase 1 planning standards: added Evolution/Justification logic and mandatory time breakdown with contingency. |
| 1.6 | 2026-01-18 | Povzetkar AI | Added BOM alignment check to QA steps based on v0.7.0 retrospective. Added note on automating BOM check with maven-enforcer-plugin. |
| 1.5 | 2026-01-17 | Povzetkar AI | Added Dependency Security Check (IntelliJ) and expanded documentation review scope. |
| 1.4 | 2026-01-13 | Povzetkar AI | Restructured release phases for clarity (Finalization -> Release -> Post-Release). |
| 1.3 | 2026-01-07 | Povzetkar AI | Added step 2.7 for continuous retrospective and backlog updates during task execution. |
| 1.2 | 2026-01-07 | Povzetkar AI | Added initialization of retrospective draft to Phase 1. |
| 1.1 | 2026-01-06 | Povzetkar AI | Updated checklist based on v0.6.2 retrospective: added code review steps, post-version documentation updates, and specific prompts. |
| 1.0 | 2025-11-01 | Povzetkar AI | Initial version. |
