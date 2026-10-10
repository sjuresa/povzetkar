# Development & Onboarding Guide

**Version:** 1.2

## Revision History

| Version | Date             | Author       | Changes                                                              |
|---------|------------------|--------------|----------------------------------------------------------------------|
| 1.2     | November 3, 2025 | Povzetkar AI | Added rule for lowercase commit message descriptions.                |
| 1.1     | November 1, 2025 | Povzetkar AI | Added section on handling database migrations with Flyway.           |
| 1.0     | August 16, 2025  | Povzetkar AI | Initial version of the development and onboarding guide.             |

## 1. Introduction

This document outlines the development workflow, coding standards, and onboarding process for Project Povzetkar. Its purpose is to ensure consistency, maintain high code quality, and enable new developers to become productive quickly.

## 2. Developer Onboarding

To get started with the project, set up your local environment, and run the application, please refer to the official **[Application Runbook](../RUNBOOK.md)**.
That document contains all the necessary technical steps, including prerequisites and commands for running the application and its tests.

## 3. Development Process

### 3.1 Branching Strategy
We use a version-based branching model to manage development for specific releases.

-   `main`: This branch contains only production-ready, tagged releases. Direct commits are strictly forbidden. All merges into `main` must come from a completed version branch via a Pull Request.

-   **Version Branches** (e.g., `v0.20.0`):
    -   Each major or minor version from the [`roadmap.md`](../overview/roadmap.md) gets its own long-lived branch, created from `main`.
    -   This branch serves as the integration point for all features and fixes planned for that specific version.

-   **Task Branches** (named after the task ID, e.g., `POV-223`):
    -   All new work (features, bug fixes, refactoring) must be done in a short-lived task branch.
    -   Task branches are created from the corresponding version branch (e.g., create `POV-223` from `v0.20.0`).
    -   Once work is complete, the task branch is merged back into its parent version branch via a Pull Request.

### 3.2 Commit Message Convention
We follow the Conventional Commits specification. This helps create an explicit commit history and simplifies versioning.
-   **`feat:`** A new feature.
-   **`fix:`** A bug fix.
-   **`docs:`** Documentation-only changes.
-   **`refactor:`** A code change that neither fixes a bug nor adds a feature.
-   **`test:`** Adding missing tests or correcting existing tests.
-   **`chore:`** Changes to the build process or auxiliary tools.
-   **Style:** The description following the type/scope **must** begin with a lowercase letter (e.g., `feat(api): add new user endpoint`).

### 3.3 Pull Request (PR) Process
The PR process is used for two distinct merge events:

**1. Merging a Task Branch into a Version Branch:**
1.  Create your task branch (e.g., `POV-223`) from the relevant version branch (e.g., `v0.20.0`).
2.  Complete your work, ensuring it is covered by tests.
3.  Push your task branch to the remote repository.
4.  Create a Pull Request from your task branch to the parent version branch.
5.  The PR must pass CI and go through the PR review loop (`docs/protocols/workflow-mandatory.md`, Step 8 — a review bot and the PO).
6.  The PO merges.

**2. Merging a Version Branch into `main` (Release):**
1.  When all tasks for a version are complete, create a Pull Request from the version branch (e.g., `v0.20.0`) to `main`.
2.  This PR represents a release candidate. It must undergo final review and testing.
3.  Once approved, the version branch is merged into `main`, and a new release tag is created on `main`.

### 3.4 Testing Philosophy
This project is committed to **Test-Driven Development (TDD)**.
-   Every new feature must be accompanied by corresponding unit and/or integration tests.
-   Every bug fix must include a regression test that fails before the fix and passes after.
-   The goal is to maintain high test coverage to enable confident refactoring and continuous integration.

### 3.5 Database Migrations (Flyway)
Since version 0.6.0, the project uses **Flyway** to manage database schema evolution. This ensures that schema changes are versioned, automated, and consistent across all environments.

**Rule:** Any change to a JPA entity that results in a DDL (Data Definition Language) change (e.g., adding a column, creating a table, adding a constraint) **must** be accompanied by a new SQL migration script.

-   **Location:** Scripts must be placed in `backend/src/main/resources/db/migration`.
-   **Naming:** Scripts must follow the Flyway naming convention: `V<VERSION>__<DESCRIPTION>.sql` (e.g., `V2__add_user_email.sql`).

## 4. Release Management & Versioning

This section describes the process for creating and publishing a new version of the application.

### 4.1 Versioning Scheme
The project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). All versions prior to `1.0.0` are considered initial development and may introduce breaking changes.

### 4.2 Release Process
A release is declared by an explicit PO decision that the version is complete (a version is a retrospective label, not a pre-planned task list). It consists of **Version Finalisation** (retrospective, documentation review, supply-chain audit, removing `-SNAPSHOT`, finalising `CHANGELOG.md`) and the **Release** itself (release PR to `main`, merge, annotated tag, production deploy, YouTrack release). The next cycle starts with Version Initialisation (new version branch, `-SNAPSHOT` version).

The authoritative step-by-step procedure is [`11_development_checklist.md`](11_development_checklist.md), Phases 1, 3 and 4 — this section deliberately does not repeat the steps, so the two cannot drift apart.
