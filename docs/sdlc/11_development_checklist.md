# Development Process Checklist

## 1. Introduction

This document provides a comprehensive checklist for our development process. It serves as a step-by-step guide to ensure that all phases of a development cycle, from version initialisation to release, are executed consistently and thoroughly.

The **Task** is the primary planning and delivery unit. A **Version** is a retrospective label declared after the work — not a plan made before it.

## Phase 1: Version Initialisation

*This phase is executed once at the beginning of a new version cycle. Its only purpose is to open the branch and register the version — planning happens at the task level.*

- [ ] **1.1. Prepare Development Branch**: Create the new version branch (e.g., `vX.Y.0`) from `main` and update version numbers using `node scripts/update-version.cjs <version>-SNAPSHOT`.
- [ ] **1.2. Create Milestone**: Register the version as a milestone label in YouTrack.
- [ ] **1.3. Roadmap Entry**: Add the version to `docs/overview/roadmap.md` as in-progress.

## Phase 2: Task Execution (Core Loop)

*This phase is repeated for every task in the version. It is the primary phase of the lifecycle.*

- [ ] **2.0. Brainstorm:** Conduct a scope-alignment interview with the PO **before** writing or finalising `task.md`. Use the following prompt:

    > *"Interview me relentlessly about every aspect of this task's scope until we reach a shared understanding. Walk down each branch of the requirements tree, resolving dependencies between decisions one-by-one. For each question, provide your recommended answer. Ask the questions one at a time. If a question can be answered by exploring the codebase, explore the codebase instead."*

    Focus: **WHAT** will be done — scope boundaries, risks, whether the task is correctly defined. Record Brainstorm start and end timestamps in `task.md` under `## Brainstorm`.

- [ ] **2.1. Define Task:** Finalise the task definition in `agent_logs/task.md`, including the `## Documentation` section specifying context and required updates.

- [ ] **2.2. Agent Proposes Plan:** The AI agent conducts a PPP planning interview (HOW it will be done) following `AGENT.md` and `docs/protocols/workflow-mandatory.md`. Use the following prompt:

    > *"Interview me relentlessly about every aspect of this plan until we reach a shared understanding. Walk down each branch of the design tree, resolving dependencies between decisions one-by-one. For each question, provide your recommended answer. Ask the questions one at a time. If a question can be answered by exploring the codebase, explore the codebase instead."*

    Focus: **HOW** it will be done — technical plan grounded in codebase evidence, ADRs where warranted, acceptance criteria.

- [ ] **2.3. User Approves Plan:** The plan is reviewed and explicitly approved.
- [ ] **2.3a. Pre-Execution Gate:** Confirm that an explicit approval for the plan has been received. **Execution is forbidden without this approval.**
- [ ] **2.4. Agent Executes Plan:** The agent executes the plan step-by-step, including all necessary code changes and documentation updates.
- [ ] **2.5. User Verifies Tests:** After code modifications, the user runs the test suite and confirms that all tests are passing.
  - [ ] **QA Check:** Ensure no blocking code (e.g., `Thread.sleep`) exists in reactive pipelines.
  - [ ] **QA Check:** Verify JPA entities implement `equals()` and `hashCode()` based on their primary key (or business key), not the default object identity.
  - [ ] **QA Check:** Verify that integration tests do not mock internal services or Panache entities.
  - [ ] **QA Check:** Ensure explicit dependency versions in pom.xml do not conflict with the framework's BOM (Bill of Materials). *Note: Investigate maven-enforcer-plugin (e.g., dependencyConvergence, requireUpperBoundDeps) during Task POV-89 to automate this.*
  - [ ] **QA Check (Local Security):** Run `mvn spotbugs:check` (or ensure it ran successfully during `mvn verify`) to validate that no static security flaws or basic bugs exist.
  - [ ] **Protocol:** Run a full `mvn clean verify` locally before declaring the task complete.
- [ ] **2.6. Update `CHANGELOG.md`:** Add a brief entry for the completed task under the `[Unreleased]` section.
- [ ] **2.7. Update Backlog & Lessons Learned:**
  - [ ] Log any non-critical improvements discovered to `docs/backlog/improvements.md`.
  - [ ] If any systemic bug fixes, architectural choices, or technical hurdles were overcome, append them to `docs/process/lessons-learned.md` as mandated by `workflow-mandatory.md` Step 9.
- [ ] **2.8. Agent Proposes Commit Message:** The agent proposes a commit message following the Conventional Commits standard.
- [ ] **2.9. User Confirms Task Completion:** The user confirms the task is fully complete, and the changes are committed.
- [ ] **2.10. Walkthrough:** Write `docs/versions/vX.Y.Z/POV-XXX-walkthrough.md` with the following sections:
    1. **What was done** — the primary deliverable; what changed and what was built.
    2. **Planned vs. actual scope** — any additions or removals relative to the original task definition.
    3. **Time delta** — estimated vs. actual time (from Time Log in `execution.md`).
    4. **Deviations** — any decisions made mid-execution that deviated from the plan.
- [ ] **2.11. Task Retrospective:** Write `docs/versions/vX.Y.Z/POV-XXX-retrospective.md` with the following sections:
    1. **Planned vs. actual** — what was scoped vs. what was delivered.
    2. **Deviations & why** — decisions made mid-execution and their rationale.
    3. **Technical findings** — patterns, gotchas, and constraints discovered; the durable record for future tasks.
    4. **Feed-forward** — what the next task should know.
- [ ] **2.12. Version Hub Check:** If `docs/versions/vX.Y.Z/version.md` does not exist, create the directory and file now (this is the first task of the version) with the following initial content:
  - Link to the YouTrack milestone for this version.
  - Empty task list section with this exact table structure: `| Task | Walkthrough | Retrospective |` followed by a separator row `| --- | --- | --- |`
- [ ] **2.13. Update Version Hub:** Add a link to this task's walkthrough and retrospective under the task list section of `docs/versions/vX.Y.Z/version.md`.
- [ ] **2.14. Handle Unplanned Discoveries:** If a new issue or potential improvement is discovered during task execution, follow this process:
  - **Assess the issue:** (1) Is it a **critical usability blocker** that prevents core functionality? (2) Is the solution **minimal, low-risk, and aligned** with the current version's goals?
  - **If both YES:** Pause, discuss, and agree to address it within the current task or as an immediate follow-up task.
  - **If either NO:** Do not let it derail the current task. Log the issue in `docs/backlog/improvements.md` for future planning.
- [ ] **2.15. Master Specification Sync**: Verify that the task's final outcome (feature or logic fix) is accurately reflected in `docs/analysis/test_specification.md`. Ensure all new P0 Test Cases are marked for automation within one version.
  - [ ] **Planning Audit**: Run `python scripts/qa/validate-planning.py --current-version vX.Y.Z` to verify the traceability chain (Task -> UC -> TC) remains intact. Refer to [Documentation Validation Protocol](../process/validation-protocol.md) for mandatory sync requirements.

## Phase 3: Version Finalisation

*This phase is triggered by an explicit PO decision that the version is complete — there is no longer an objective task list to signal completion.*

- [ ] **3.1. Finalise Documentation Content:**
  - [ ] Write `docs/versions/vX.Y.Z/retrospective.md` as a cross-task synthesis — trends, systemic patterns, process health. Input: all task retrospectives in `docs/versions/vX.Y.Z/`. This is not a collection of task findings; it is a pattern-level synthesis across them.
  - [ ] Add a scope statement to `docs/versions/vX.Y.Z/version.md` summarising what the version delivered.
  - [ ] Update `docs/overview/roadmap.md`: Mark version as `✅ Completed` and add a revision entry.
  - [ ] Review and clean `docs/backlog/improvements.md` based on completed work and new findings.
- [ ] **3.2. Project-Wide Documentation Review**: Execute the following prompts to ensure project-wide documentation is up-to-date with any process changes or learnings from the completed version:
  - [ ] **Planning Audit**: Perform a final whole-project audit using `python scripts/qa/validate-planning.py --current-version vX.Y.Z --fail-on all`. Refer to [Documentation Validation Protocol](../process/validation-protocol.md) for release blocking criteria.
  - [ ] **General Refresh**: "We are finalizing version vX.Y.Z. Please check if the project documentation in `docs/` needs to be refreshed based on the completed tasks and retrospectives in `docs/versions/vX.Y.Z/` (specifically in directories: `docs/analysis`, `docs/overview`, `docs/process`, `docs/sdlc`, `docs/strategy`). Today is [Current Date]."
  - [ ] **Agent Protocol Check**: "Is it necessary to update `AGENT.md` based on changes in `11_development_checklist.md`?"
  - [ ] **Methodology Update**: "In `07_development_methodology_comparison.md`, section 7 lists possible improvements. Refresh this if anything was implemented or add new proposals for the future."
- [ ] **3.3. Finalise Project Versions**: Remove the `-SNAPSHOT` suffix using `node scripts/update-version.cjs <version>`.
- [ ] **3.4. Finalise `CHANGELOG.md`:**
  - [ ] **Editorial Cleanup:** Remove internal refactoring noise and consolidate duplicates. Ensure "Fixed" items refer to issues present in previous versions, not just temporary bugs encountered during development.
  - [ ] Move all changes from the `[Unreleased]` section to a new version section (e.g., `[X.Y.Z] - YYYY-MM-DD`).
  - [ ] Add a new, empty `[Unreleased]` section at the top.
- [ ] **3.5. Sync Landing Page Metadata:** Run `python scripts/sync_metadata.py` to ground the vision path and strategic updates in the latest `CHANGELOG.md` and `roadmap.md` changes.

## Phase 4: Release

*This phase is executed after the version is finalised.*

- [ ] **4.1. Create Release PR:** Create a Pull Request from the version branch (e.g., `vX.Y.0`) to `main`.
- [ ] **4.2. Merge to `main`:** After approval, merge the release PR. Ensure that the CI pipeline explicitly passes the Trivy vulnerability scan and SAST routines.
- [ ] **4.3. Create Git Tag:** On the `main` branch, create and push an annotated Git tag (e.g., `git tag -a vX.Y.0 -m "Release version X.Y.0"`).

## Revision History

| Version | Date | Author | Changes |
| --- | --- | --- | --- |
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
