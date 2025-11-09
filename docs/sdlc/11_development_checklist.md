# Development Process Checklist

**Version:** 1.0
**Date:** November 1, 2025

## 1. Introduction

This document provides a comprehensive checklist for our development process. It serves as a step-by-step guide to ensure that all phases of a development cycle, from planning to release and review, are executed consistently and thoroughly.

---

## Phase 1: Version Planning

*This phase is executed at the beginning of a new version cycle (e.g., starting v0.7).*

- [ ] **1.1. Review Previous Retrospective:** Read the `docs/retrospectives/vX.Y-retrospective.md` from the previous version to ensure all action items are considered for the new cycle.
- [ ] **1.2. Define High-Level Goals:** Review the `roadmap.md` and agree on the primary objectives for the new version.
- [ ] **1.3. Define Testing Strategy:** For any new functionality (especially with a UI component), ask "How will we test this in a way that is proportional to the investment?". The decision must be documented in the `planning-process.md`.
- [ ] **1.4. Document Planning Process:** Create a `docs/versions/vX.Y-planning-process.md` file to log the decisions and rationale behind the plan, including the testing strategy.
- [ ] **1.5. Create Task Breakdown:** Create a new `docs/versions/vX.Y-tasks.md` file, breaking down the high-level goals into concrete, actionable tasks.
    - [ ] Ensure each task includes a `## Documentation` section (`Context` and `Update`), as defined in the v0.6 retrospective.
- [ ] **1.6. Prepare Development Branch:** Create the new version branch (e.g., `v0.7`) from `main` and update the `pom.xml` to the new snapshot version (e.g., `0.7.0-SNAPSHOT`).

---

## Phase 2: Task Execution (Core Loop)

*This phase is repeated for every task defined in the `*-tasks.md` file.*

- [ ] **2.1. Define Task:** The task is clearly defined in `agent_logs/task.md`, including the `## Documentation` section specifying context and required updates.
- [ ] **2.2. Agent Proposes Plan:** The AI agent follows `AGENT.md` to gather information and present a detailed plan for approval.
- [ ] **2.3. User Approves Plan:** The plan is reviewed and explicitly approved.
- [ ] **2.4. Agent Executes Plan:** The agent executes the plan step-by-step, including all necessary code changes and documentation updates.
- [ ] **2.5. User Verifies Tests:** After code modifications, the user runs the test suite and confirms that all tests are passing.
- [ ] **2.6. Update `CHANGELOG.md`:** Add a brief entry for the completed task under the `[Unreleased]` section.
- [ ] **2.7. Agent Proposes Commit Message:** The agent proposes a commit message following the Conventional Commits standard.
- [ ] **2.8. User Confirms Task Completion:** The user confirms the task is fully complete, and the changes are committed.

- [ ] **2.9. Handle Unplanned Discoveries:** If a new issue or potential improvement is discovered during task execution, follow this process:
    - **Assess the issue:**
        1.  Is it a **critical usability blocker** that prevents core functionality?
        2.  Is the solution **minimal, low-risk, and aligned** with the current version's goals (e.g., "non-locking investment" for v0.6.1)?
    - **Decide:**
        -   If **both** answers are YES: Pause, discuss, and agree to update the current version's task plan (`*-tasks.md`) to include the fix.
        -   If **either** answer is NO: Do not let it derail the current task. Log the issue in the appropriate backlog file (e.g., `docs/backlog/ui-improvements.md`) for future planning.

---

## Phase 3: Version Completion & Release

*This phase is executed once all tasks for the current version are complete.*

- [ ] **3.1. Finalize `CHANGELOG.md`:**
    - [ ] Move all changes from the `[Unreleased]` section to a new version section (e.g., `[0.7.0] - YYYY-MM-DD`).
    - [ ] Add a new, empty `[Unreleased]` section at the top.
- [ ] **3.2. Finalize `pom.xml`:** Remove the `-SNAPSHOT` suffix from the project version (e.g., `0.7.0`).
- [ ] **3.3. Create Release PR:** Create a Pull Request from the version branch (e.g., `v0.7`) to `main`.
- [ ] **3.4. Merge to `main`:** After approval, merge the release PR into the `main` branch.
- [ ] **3.5. Create Git Tag:** On the `main` branch, create and push an annotated Git tag (e.g., `git tag -a v0.7.0 -m "Release version 0.7.0"`).

---

## Phase 4: Post-Version Review

*This phase is executed immediately after a version has been released.*

- [ ] **4.1. Conduct Retrospective:** Create a new `docs/retrospectives/vX.Y-retrospective.md` file to document what went well, what could be improved, and define action items for the next cycle.
