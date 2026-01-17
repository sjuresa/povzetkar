# Development Process Checklist

## Revision History
| Version | Date             | Author       | Changes                                                                                                                             |
|---------|------------------|--------------|-------------------------------------------------------------------------------------------------------------------------------------|
| 1.5     | January 17, 2026 | Povzetkar AI | Added Dependency Security Check (IntelliJ) and expanded documentation review scope.                                                 |
| 1.4     | January 13, 2026 | Povzetkar AI | Restructured release phases for clarity (Finalization -> Release -> Post-Release).                                                  |
| 1.3     | January 7, 2026  | Povzetkar AI | Added step 2.7 for continuous retrospective and backlog updates during task execution.                                              |
| 1.2     | January 7, 2026  | Povzetkar AI | Added initialization of retrospective draft to Phase 1.                                                                             |
| 1.1     | January 6, 2026  | Povzetkar AI | Updated checklist based on v0.6.2 retrospective: added code review steps, post-version documentation updates, and specific prompts. |
| 1.0     | November 1, 2025 | Povzetkar AI | Initial version.                                                                                                                    |

## 1. Introduction

This document provides a comprehensive checklist for our development process. It serves as a step-by-step guide to ensure that all phases of a development cycle, from planning to release and review, are executed consistently and thoroughly.

---

## Phase 1: Version Planning

*This phase is executed at the beginning of a new version cycle (e.g., starting v0.7).*

- [ ] **1.1. Review Previous Retrospective:** Read the `docs/retrospectives/vX.Y-retrospective.md` from the previous version to ensure all action items are considered for the new cycle.
- [ ] **1.2. Define High-Level Goals:** Review the `roadmap.md` and agree on the primary objectives for the new version.
- [ ] **1.3. Define Testing Strategy:** For any new functionality (especially with a UI component), ask "How will we test this in a way that is proportional to the investment?". The decision must be documented in the `planning-process.md`.
- [ ] **1.4. Identify Spike Tasks:** Review goals for high technical uncertainty. If any exist, create a dedicated "Spike" task to de-risk future development.
- [ ] **1.5. Document Planning Process:** Create a `docs/versions/vX.Y-planning-process.md` file to log the decisions and rationale behind the plan, including the testing strategy.
- [ ] **1.6. Create Task Breakdown:** Create a new `docs/versions/vX.Y-tasks.md` file, breaking down the high-level goals into concrete, actionable tasks.
    - [ ] Ensure each task includes a `## Documentation` section (`Context` and `Update`), as defined in the v0.6 retrospective.
    - [ ] For every `feat` task, ensure a corresponding `test` sub-task is defined to explicitly outline the test scenarios.
- [ ] **1.7. Prepare Development Branch:** Create the new version branch (e.g., `v0.7`) from `main` and update the `pom.xml` to the new snapshot version (e.g., `0.7.0-SNAPSHOT`).
- [ ] **1.8. Initialize Retrospective Draft:** Create a `docs/retrospectives/vX.Y.Z-retrospective.md` file with initial sections to capture learnings and observations throughout the development cycle.
- [ ] **1.9. Dependency Security Check:** Run the IntelliJ IDEA Package Checker to identify and address vulnerabilities in the current dependencies.

---

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

---

## Phase 3: Version Finalization

*This phase is executed once all tasks for the current version are complete.*

- [ ] **3.1. Finalize Documentation Content:**
    - [ ] Finalize `docs/retrospectives/vX.Y.Z-retrospective.md`: Update date, clean up notes (remove internal meta-process noise), and ensure action items are clear.
    - [ ] Update `docs/overview/roadmap.md`: Mark version as `✅ Completed` and add a revision entry.
    - [ ] Update `docs/versions/vX.Y.Z-tasks.md`: Ensure all tasks are marked `(Completed)` and add a final revision entry.
    - [ ] Update `docs/versions/vX.Y.Z-planning-process.md`: Add a final revision entry.
    - [ ] Review and clean `docs/backlog/improvements.md` based on completed work and new findings.
- [ ] **3.2. Project-Wide Documentation Review:** Execute the following prompts to ensure project-wide documentation is up-to-date with any process changes or learnings from the completed version:
    - [ ] **General Refresh:** "We are finalizing version vX.Y.Z. Please review the documents for this version (`docs/versions/vX.Y.Z-planning-process.md`, `docs/versions/vX.Y.Z-tasks.md`, `docs/retrospectives/vX.Y.Z-retrospective.md`) and check if the project documentation in `docs/` needs to be refreshed based on this (specifically in directories: `docs/analysis`, `docs/overview`, `docs/process`, `docs/sdlc`, `docs/strategy`). Today is [Current Date]."
    - [ ] **Agent Protocol Check:** "Is it necessary to update `AGENT.md` based on changes in `11_development_checklist.md`?"
    - [ ] **Methodology Update:** "In `07_development_methodology_comparison.md`, section 7 lists possible improvements. Refresh this if anything was implemented or add new proposals for the future."
- [ ] **3.3. Finalize `pom.xml`:** Remove the `-SNAPSHOT` suffix from the project version (e.g., `0.7.0`).
- [ ] **3.4. Finalize `CHANGELOG.md`:**
    - [ ] **Editorial Cleanup:** Remove internal refactoring noise and consolidate duplicates. Ensure "Fixed" items refer to issues present in previous versions, not just temporary bugs encountered during development.
    - [ ] Move all changes from the `[Unreleased]` section to a new version section (e.g., `[0.7.0] - YYYY-MM-DD`).
    - [ ] Add a new, empty `[Unreleased]` section at the top.

---

## Phase 4: Release

*This phase is executed after the version is finalized.*

- [ ] **4.1. Create Release PR:** Create a Pull Request from the version branch (e.g., `v0.7`) to `main`.
- [ ] **4.2. Merge to `main`:** After approval, merge the release PR.
- [ ] **4.3. Create Git Tag:** On the `main` branch, create and push an annotated Git tag (e.g., `git tag -a v0.7.0 -m "Release version 0.7.0"`).
