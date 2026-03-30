# Development Process Checklist

## 1. Introduction

This document provides a comprehensive checklist for our development process. It serves as a step-by-step guide to ensure that all phases of a development cycle, from planning to release and review, are executed consistently and thoroughly.

## Phase 1: Version Planning

*This phase is executed at the beginning of a new version cycle (e.g., starting v0.7).*

- [ ] **1.1. Review Previous Retrospective:** Read the `docs/retrospectives/vX.Y-retrospective.md` from the previous version to ensure all action items are considered for the new cycle.
- [ ] **1.2. Define High-Level Goals:** Review the `roadmap.md` and agree on the primary objectives for the new version.
- [ ] **1.3. Define Testing Strategy:** For any new functionality (especially with a UI component), ask "How will we test this in a way that is proportional to the investment?". The decision must be documented in the `planning-process.md`.
- [ ] **1.4. Identify Spike Tasks:** Review goals for high technical uncertainty. If any exist, create a dedicated "Spike" task to de-risk future development.
- [ ] **1.5. Document Planning Process**: Create a `docs/versions/vX.Y-planning-process.md` file using the "Decision Log" style.
    - [ ] Include **Planning Evolution & Justification**: Document brainstorming discussions and pro/con analysis.
    - [ ] Include **Testing Strategy**: Proportional complexity mapping to `use_cases.md`.
    - [ ] Include **UI/UX Discovery (Skeletons)**: For all UI tasks, define the layout grid and element placement using **ASCII Wireframes**.
    - [ ] Include **API & Dependency Audit**: Check status of external services (e.g., YouTube API) and library security *before* planning implementation.
- [ ] **1.6. Define Use Cases & Test Cases**: 
    - [ ] For all user-facing or complex features, create/update `docs/analysis/use_cases.md` to map **User Story IDs (US-X)** to concrete interaction flows.
    - [ ] Update **`docs/analysis/test_specification.md`** (Master Suite). Map every new Use Case to at least one Test Case (TC) and define the testing methodology (Manual, Unit, Playwright).
- [ ] **1.7. Create Task Breakdown**: Create a new `docs/versions/vX.Y-tasks.md` file.
    - [ ] Include a **Time Estimation Summary** with per-task estimates and contingency.
    - [ ] Assign **Prioritization (P-levels)**: P0 (Critical/Core), P1 (High), P2 (Enhancement), P3 (Maint).
    - [ ] **Task Specification**: Every task must have a `## Context` and a `## Definition of Done`.
    - [ ] **Resilience & Error States**: Every task must define system behavior for **failure scenarios** (e.g., network timeout, API down, database locked).
    - [ ] **Skill Mapping**: Tag each task with required AI skills (e.g., `@[skills/api-patterns]`).
    - [ ] For every `feat` task, ensure a corresponding `test` sub-task is defined.
- [ ] **1.8. Initialize Retrospective Draft**: Create a `docs/retrospectives/vX.Y.Z-retrospective.md` file with initial sections to capture learnings and observations throughout the development cycle.
- [ ] **1.9. Prepare Development Branch**: Create the new version branch (e.g., `v0.7.0`) from `main` and update version numbers using `node scripts/update-version.cjs <version>-SNAPSHOT`.
- [ ] **1.10. Document Standard (Formatting)**: For all non-trivial documents created or updated, ensure that the **Revision History** section is placed at the **END** of the file to prioritize immediate access to core content.
- [ ] **1.11. Final Expert Review**: Perform a mandatory final review of the version plan, specifically checking for:
    - [ ] **Planning Audit**: Run `python scripts/qa/validate-planning.py --current-version vX.Y.Z` to ensure all task links and traceability mappings are valid. Refer to [Documentation Validation Protocol](../process/validation-protocol.md) for failure response.
    - [ ] **i18n Readiness**: Impact on localization files (`en.json`, `sl.json`).
    - [ ] **Security Audit**: Port exposures, sensitive data in logs, and Docker isolation.
    - [ ] **Ops Readiness**: Verification of rollback/panic-switch procedures in `RUNBOOK.md`.
    - [ ] **Legacy Sync**: Audit `frontend/e2e/` for undocumented tests and merge them into the Master Suite.

## Phase 2: Task Execution (Core Loop)

*This phase is repeated for every task defined in the `*-tasks.md` file.*

- [ ] **2.1. Define Task:** The task is clearly defined in `agent_logs/task.md`, including the `## Documentation` section specifying context and required updates.
- [ ] **2.2. Agent Proposes Plan:** The AI agent follows `AGENT.md` to gather information and present a detailed plan for approval.
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
- [ ] **2.7. Update Retrospective & Backlog:**
    - [ ] Append observations from this task to the current `docs/retrospectives/vX.Y-retrospective.md` (do not create a new file).
    - [ ] Log any non-critical improvements discovered to `docs/backlog/improvements.md`.
- [ ] **2.8. Agent Proposes Commit Message:** The agent proposes a commit message following the Conventional Commits standard.
- [ ] **2.9. User Confirms Task Completion:** The user confirms the task is fully complete, and the changes are committed.
- [ ] **2.10. Handle Unplanned Discoveries:** If a new issue or potential improvement is discovered during task execution, follow this process:
    - **Assess the issue:**
        1.  Is it a **critical usability blocker** that prevents core functionality?
        2.  Is the solution **minimal, low-risk, and aligned** with the current version's goals (e.g., "non-locking investment" for v0.6.1)?
    - **Decide:**
        -   If **both** answers are YES: Pause, discuss, and agree to update the current version's task plan (`*-tasks.md`) to include the fix.
        -   If **either** answer is NO: Do not let it derail the current task. Log the issue in `docs/backlog/improvements.md` for future planning.
- [ ] **2.11. Master Specification Sync**: Verify that the task's final outcome (feature or logic fix) is accurately reflected in `docs/analysis/test_specification.md`. Ensure all new P0 Test Cases are marked for automation within one version.
    - [ ] **Planning Audit**: Run `python scripts/qa/validate-planning.py --current-version vX.Y.Z` to verify the traceability chain (Task -> UC -> TC) remains intact. Refer to [Documentation Validation Protocol](../process/validation-protocol.md) for mandatory sync requirements.

## Phase 3: Version Finalization

*This phase is executed once all tasks for the current version are complete.*

- [ ] **3.1. Finalize Documentation Content:**
    - [ ] Finalize `docs/retrospectives/vX.Y.Z-retrospective.md`: Update date, clean up notes (remove internal meta-process noise), and ensure action items are clear.
    - [ ] Update `docs/overview/roadmap.md`: Mark version as `✅ Completed` and add a revision entry.
    - [ ] Update `docs/versions/vX.Y.Z-tasks.md`: Ensure all tasks are marked `(Completed)` and add a final revision entry.
    - [ ] Update `docs/versions/vX.Y.Z-planning-process.md`: Add a final revision entry.
    - [ ] Review and clean `docs/backlog/improvements.md` based on completed work and new findings.
- [ ] **3.2. Project-Wide Documentation Review**: Execute the following prompts to ensure project-wide documentation is up-to-date with any process changes or learnings from the completed version:
    - [ ] **Planning Audit**: Perform a final whole-project audit using `python scripts/qa/validate-planning.py --current-version vX.Y.Z --fail-on all`. Refer to [Documentation Validation Protocol](../process/validation-protocol.md) for release blocking criteria.
    - [ ] **General Refresh**: "We are finalizing version vX.Y.Z. Please review the documents for this version (`docs/versions/vX.Y.Z-planning-process.md`, `docs/versions/vX.Y.Z-tasks.md`, `docs/retrospectives/vX.Y.Z-retrospective.md`) and check if the project documentation in `docs/` needs to be refreshed based on this (specifically in directories: `docs/analysis`, `docs/overview`, `docs/process`, `docs/sdlc`, `docs/strategy`). Today is [Current Date]."
    - [ ] **Agent Protocol Check**: "Is it necessary to update `AGENT.md` based on changes in `11_development_checklist.md`?"
    - [ ] **Methodology Update**: "In `07_development_methodology_comparison.md`, section 7 lists possible improvements. Refresh this if anything was implemented or add new proposals for the future."
- [ ] **3.3. Finalize Project Versions**: Remove the `-SNAPSHOT` suffix using `node scripts/update-version.cjs <version>`.
- [ ] **3.4. Finalize `CHANGELOG.md`:**
    - [ ] **Editorial Cleanup:** Remove internal refactoring noise and consolidate duplicates. Ensure "Fixed" items refer to issues present in previous versions, not just temporary bugs encountered during development.
    - [ ] Move all changes from the `[Unreleased]` section to a new version section (e.g., `[0.7.0] - YYYY-MM-DD`).
    - [ ] Add a new, empty `[Unreleased]` section at the top.
- [ ] **3.5. Sync Landing Page Metadata:** Run `python scripts/sync_metadata.py` to ground the vision path and strategic updates in the latest `CHANGELOG.md` and `roadmap.md` changes.

## Phase 4: Release

*This phase is executed after the version is finalized.*

- [ ] **4.1. Create Release PR:** Create a Pull Request from the version branch (e.g., `v0.7`) to `main`.
- [ ] **4.2. Merge to `main`:** After approval, merge the release PR. Ensure that the CI pipeline explicitly passes the Trivy vulnerability scan and SAST routines.
- [ ] **4.3. Create Git Tag:** On the `main` branch, create and push an annotated Git tag (e.g., `git tag -a v0.7.0 -m "Release version 0.7.0"`).

## Revision History
| Version | Date             | Author       | Changes                                                                                                                             |
|---------|------------------|--------------|-------------------------------------------------------------------------------------------------------------------------------------|
| 2.6     | March 30, 2026   | Povzetkar AI | Relocated script to `scripts/qa/`, removed hardcoded filenames, and hardened ID extraction logic. |
| 2.5     | March 30, 2026   | Povzetkar AI | Linked [Documentation Validation Protocol](../process/validation-protocol.md) for rule enforcement. |
| 2.4     | March 30, 2026   | Povzetkar AI | Mandated Planning Audit via `validate-planning.py` across all phases.       |
| 2.3     | March 30, 2026   | Povzetkar AI | Mandated Master Spec Sync (2.11) and US->UC Traceability (1.6) for Operational Excellence. |
| 2.2     | March 28, 2026   | Povzetkar AI | Mandated ASCII Wireframes (1.5), Resilience Analysis (1.7), and Dependency Audits (1.11). |
| 2.1     | March 28, 2026   | Povzetkar AI | Mandated Master Test Suite (1.6), P-levels/Skill Mapping (1.7), and Expert Review (1.11). |
| 2.0     | March 28, 2026   | Povzetkar AI | Integrated `update-version.cjs` for monorepo version synchronization (Phase 1 & Phase 3). |
| 1.9     | March 27, 2026   | Povzetkar AI | Added Phase 3 mandate for syncing landing page metadata via `sync_metadata.py`.                    |
| 1.8     | March 25, 2026   | Povzetkar AI | Formalized document formatting (Revision History at end) and refined Planning/Task document standards based on v0.14.0 learnings.   |
| 1.7     | March 19, 2026   | Povzetkar AI | Upgraded Phase 1 planning standards: added Evolution/Justification logic and mandatory time breakdown with contingency.             |
| 1.6     | January 18, 2026 | Povzetkar AI | Added BOM alignment check to QA steps based on v0.7.0 retrospective. Added note on automating BOM check with maven-enforcer-plugin. |
| 1.5     | January 17, 2026 | Povzetkar AI | Added Dependency Security Check (IntelliJ) and expanded documentation review scope.                                                 |
| 1.4     | January 13, 2026 | Povzetkar AI | Restructured release phases for clarity (Finalization -> Release -> Post-Release).                                                  |
| 1.3     | January 7, 2026  | Povzetkar AI | Added step 2.7 for continuous retrospective and backlog updates during task execution.                                              |
| 1.2     | January 7, 2026  | Povzetkar AI | Added initialization of retrospective draft to Phase 1.                                                                             |
| 1.1     | January 6, 2026  | Povzetkar AI | Updated checklist based on v0.6.2 retrospective: added code review steps, post-version documentation updates, and specific prompts. |
| 1.0     | November 1, 2025 | Povzetkar AI | Initial version.                                                                                                                    |
