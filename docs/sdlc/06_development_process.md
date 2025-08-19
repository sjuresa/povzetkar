# Development & Onboarding Guide

**Version:** 1.0
**Date:** August 16, 2025

## 1. Introduction

This document outlines the development workflow, coding standards, and onboarding process for Project Povzetkar. Its purpose is to ensure consistency, maintain high code quality, and enable new developers to become productive quickly.

## 2. Developer Onboarding

To get started with the project, set up your local environment, and run the application, please refer to the official **[Application Runbook](../RUNBOOK.md)**.
That document contains all the necessary technical steps, including prerequisites and commands for running the application and its tests.

## 3. Development Process

### 3.1 Branching Strategy
We use a version-based branching model to manage development for specific releases.

-   `main`: This branch contains only production-ready, tagged releases. Direct commits are strictly forbidden. All merges into `main` must come from a completed version branch via a Pull Request.

-   **Version Branches** (e.g., `v0.5`, `v0.6`):
    -   Each major or minor version from the `roadmap.md` gets its own long-lived branch, created from `main`.
    -   This branch serves as the integration point for all features and fixes planned for that specific version.

-   **Task Branches** (e.g., `feature/task-name`, `fix/bug-name`):
    -   All new work (features, bug fixes, refactoring) must be done in a short-lived task branch.
    -   Task branches are created from the corresponding version branch (e.g., create `feature/new-api` from `v0.5`).
    -   Once work is complete, the task branch is merged back into its parent version branch via a Pull Request.

### 3.2 Commit Message Convention
We follow the Conventional Commits specification. This helps create an explicit commit history and simplifies versioning.
-   **`feat:`** A new feature.
-   **`fix:`** A bug fix.
-   **`docs:`** Documentation-only changes.
-   **`refactor:`** A code change that neither fixes a bug nor adds a feature.
-   **`test:`** Adding missing tests or correcting existing tests.
-   **`chore:`** Changes to the build process or auxiliary tools.

### 3.3 Pull Request (PR) Process
The PR process is used for two distinct merge events:

**1. Merging a Task Branch into a Version Branch:**
1.  Create your task branch (e.g., `feature/new-api`) from the relevant version branch (e.g., `v0.5`).
2.  Complete your work, ensuring it is covered by tests.
3.  Push your task branch to the remote repository.
4.  Create a Pull Request from your task branch to the parent version branch.
5.  The PR must pass all automated checks and be reviewed by at least one other developer.
6.  Once approved, the PR can be merged.

**2. Merging a Version Branch into `main` (Release):**
1.  When all tasks for a version are complete, create a Pull Request from the version branch (e.g., `v0.5`) to `main`.
2.  This PR represents a release candidate. It must undergo final review and testing.
3.  Once approved, the version branch is merged into `main`, and a new release tag is created on `main`.

### 3.4 Testing Philosophy
This project is committed to **Test-Driven Development (TDD)**.
-   Every new feature must be accompanied by corresponding unit and/or integration tests.
-   Every bug fix must include a regression test that fails before the fix and passes after.
-   The goal is to maintain high test coverage to enable confident refactoring and continuous integration.


## 4. Release Management & Versioning

This section describes the process for creating and publishing a new version of the application.

### 4.1 Versioning Scheme
The project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). All versions prior to `1.0.0` are considered initial development and may introduce breaking changes.

### 4.2 Release Process
Creating a new release is a manual process performed when all tasks for a given version in the `roadmap.md` are complete and merged into its corresponding version branch.

The steps are as follows:

1.  **Finalize the Version Branch (e.g., `v0.5`):**
    *   Ensure all task branches for the version are merged.
    *   Update `CHANGELOG.md`: Change the `[Unreleased]` section to the new version number and date (e.g., `[0.5] - 2025-08-17`) and add a new `[Unreleased]` section at the top.
    *   Update Project Version: In `pom.xml`, remove the `-SNAPSHOT` suffix from the `<version>` tag (e.g., change `<version>0.5-SNAPSHOT</version>` to `<version>0.5</version>`).
    *   Commit these final changes to the version branch with a message like `chore(release): prepare for release v0.5`.

2.  **Merge to `main` via Pull Request:**
    *   Create a Pull Request from the version branch (e.g., `v0.5`) to `main`. The PR should be named "Release v0.5".
    *   This PR serves as the final release candidate. After approval and passing all checks, merge it into `main`.

3.  **Create and Push Git Tag:**
    *   On your local `main` branch, pull the latest changes.
    *   Create an annotated Git tag: `git tag -a v0.5 -m "Release version 0.5"`
    *   Push the tag to the remote repository: `git push origin v0.5`

4.  **Prepare for Next Development Cycle:**
    *   Create a new version branch (e.g., `v0.6`) from the `main` branch.
    *   On the new `v0.6` branch, update the `pom.xml` version to the next development snapshot (e.g., `<version>0.6-SNAPSHOT</version>`).
    *   Commit and push this change to the new version branch to officially start the next development cycle.
