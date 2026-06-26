# Task: Finalize Documentation for Release vX.Y.Z

**Status:** Pending
**Date:** YYYY-MM-DD

## Description
Finalize all documentation for the vX.Y.Z release. This involves cleaning up the changelog, writing the cross-task version retrospective, marking the version hub as completed, updating the roadmap, reviewing project-wide documentation, and preparing the codebase for release (removing SNAPSHOT).

## Requirements
1.  **Changelog (`CHANGELOG.md`):**
    *   Update release date to today's date.
    *   **Editorial Cleanup:** Remove internal refactoring noise (e.g., specific class refactorings that don't affect public behavior), consolidate duplicates. Ensure "Fixed" items refer to issues present in previous versions, not just temporary bugs encountered during the current development cycle.
    *   Move all changes from the `[Unreleased]` section to a new version section (e.g., `[vX.Y.Z] - YYYY-MM-DD`).
    *   **Version Summary:** Write a concise paragraph under the new version header summarizing the main theme and key achievements of the release.
    *   Add a new, empty `[Unreleased]` section at the top.
2.  **Cross-Task Version Retrospective (`docs/versions/vX.Y.Z/retrospective.md`):**
    *   Write the version-level retrospective as a cross-task synthesis — trends, systemic patterns, process health. Input: all task retrospectives in `docs/versions/vX.Y.Z/`. This is not a collection of task findings; it is a pattern-level synthesis across them.
3.  **Version Hub (`docs/versions/vX.Y.Z/version.md`):**
    *   Update the status to `Completed`.
    *   Add a scope statement summarising what the version delivered.
4.  **Improvement Backlog (`docs/backlog/improvements.md`):**
    *   Review and clean up. Ensure items completed in vX.Y.Z are removed.
    *   Add new items identified in the retrospectives.
5.  **Roadmap (`docs/overview/roadmap.md`):**
    *   Mark vX.Y.Z as `✅ Completed`.
    *   Add a revision entry.
6.  **Project-Wide Documentation Review:**
    *   **Planning Audit:** Run `python scripts/qa/validate-planning.py --current-version vX.Y.Z --fail-on all`. Refer to `docs/process/validation-protocol.md` for release blocking criteria.
    *   **General Refresh:** Review `docs/analysis`, `docs/overview`, `docs/process`, `docs/sdlc`, `docs/strategy` and update if needed based on vX.Y.Z changes and the task retrospectives in `docs/versions/vX.Y.Z/`.
    *   **Agent Protocol:** Check if `AGENT.md` needs updates based on changes in `11_development_checklist.md`.
    *   **Methodology:** Check `07_development_methodology_comparison.md`, section 7 (possible improvements) — refresh if anything was implemented or add new proposals for the future.
7.  **Finalize Project Versions:**
    *   Remove the `-SNAPSHOT` suffix from the project version using `node scripts/update-version.cjs <version>` (e.g., `<version>` is `0.7.0` without the `v`).
8.  **Sync Landing Page Metadata:**
    *   Run `python scripts/sync_metadata.py` to ground the vision path and strategic updates in the latest `CHANGELOG.md` and `roadmap.md` changes.

## Revision History

| Version | Date | Author | Changes |
| --- | --- | --- | --- |
| 2.0 | 2026-06-26 | Povzetkar AI | Synced with task-first methodology (POV-191): added cross-task version retrospective (`docs/versions/vX.Y.Z/retrospective.md`) and version-hub status/scope update; replaced legacy `docs/retrospectives/`, `vX.Y.Z-tasks.md`, and `vX.Y.Z-planning-process.md` paths with the version-hub structure; expanded General Refresh and methodology review scope to match Phase 3 of `11_development_checklist.md`. |
| 1.0 | — | Povzetkar AI | Initial template (version-gated planning model). |
