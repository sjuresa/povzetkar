# Task: Finalize Documentation for Release vX.Y.Z

**Status:** Pending
**Date:** YYYY-MM-DD

## Description
Finalize all documentation for the vX.Y.Z release. This involves cleaning up the changelog, updating the retrospective status, marking tasks as completed, updating the roadmap, reviewing project-wide documentation, and preparing the codebase for release (removing SNAPSHOT).

## Requirements
1.  **Changelog (`changelog.md`):**
    *   Update release date to today's date.
    *   **Editorial Cleanup:** Remove internal refactoring noise (e.g., specific class refactorings that don't affect public behavior), consolidate duplicates. Ensure "Fixed" items refer to issues present in previous versions, not just temporary bugs encountered during the current development cycle.
    *   Move all changes from the `[Unreleased]` section to a new version section (e.g., `[vX.Y.Z] - YYYY-MM-DD`).
    *   **Version Summary:** Write a concise paragraph under the new version header summarizing the main theme and key achievements of the release.
    *   Add a new, empty `[Unreleased]` section at the top.
2.  **Improvement Backlog (`docs/backlog/improvements.md`):**
    *   Review and clean up. Ensure items completed in vX.Y.Z are removed.
    *   Add new items identified in the retrospective.
3.  **Retrospective (`docs/retrospectives/vX.Y.Z-retrospective.md`):**
    *   Change status to `Final` or `Completed`.
    *   Update date to today's date.
    *   **Cleanup:** Remove internal agent meta-process notes (e.g., "Agent failed to follow protocol") unless they are relevant to the project's SDLC. Focus on product and technical learnings.
    *   Finalize "Action Items" based on the "What Have We Learned" section.
4.  **Planning Process (`docs/versions/vX.Y.Z-planning-process.md`):**
    *   Add a final revision entry indicating the version is concluded.
5.  **Tasks (`docs/versions/vX.Y.Z-tasks.md`):**
    *   Mark all tasks as `(Completed)`.
    *   Add a final revision entry.
6.  **Roadmap (`docs/overview/roadmap.md`):**
    *   Mark vX.Y.Z as `✅ Completed`.
    *   Update the revision history.
7.  **Project-Wide Documentation Review:**
    *   **General Refresh:** Review `docs/analysis`, `docs/overview`, `docs/sdlc` and update if needed based on vX.Y.Z changes.
    *   **Agent Protocol:** Check if `AGENT.md` needs updates based on process changes.
    *   **Methodology:** Check `07_development_methodology_comparison.md` for updates.
8.  **Finalize `pom.xml`:**
    *   Remove the `-SNAPSHOT` suffix from the project version (e.g., `0.7.0`).
