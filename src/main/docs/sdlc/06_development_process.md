# Development & Onboarding Guide

**Version:** 1.0
**Date:** August 16, 2025

## 1. Introduction

This document outlines the development workflow, coding standards, and onboarding process for Project Povzetkar. Its purpose is to ensure consistency, maintain high code quality, and enable new developers to become productive quickly.

## 2. Developer Onboarding

### 2.1 Prerequisites
- **Java:** JDK 21 or later.
- **Maven:** Version 3.8.x or later.
- **Docker & Docker Compose:** For running the local development environment.
- **IDE:** IntelliJ IDEA or VS Code are recommended.
- **Git:** For version control.

### 2.2 Local Environment Setup
The entire local environment is managed by Docker Compose.

1.  **Clone the repository:**
    ```bash
    git clone <repository-url>
    cd povzetkar
    ```
2.  **Start the environment:**
    This command will start the PostgreSQL database and any other required services.
    ```bash
    docker-compose up -d
    ```
3.  **Run the application in development mode:**
    Quarkus's dev mode provides live reloading, making development fast and efficient.
    ```bash
    ./mvnw quarkus:dev
    ```
    The application will be available, and you can start making changes.

### 2.3 Running Tests
To run the full test suite:
```bash
./mvnw verify
```

## 3. Development Process

### 3.1 Branching Strategy
We use a simplified GitFlow model:
-   `main`: This branch contains production-ready, tagged releases. Direct commits are forbidden. Merges happen only from the `develop` branch.
-   `develop`: This is the main integration branch for ongoing development. All feature branches are merged into `develop`.
-   **Feature Branches:** All new work (features, bug fixes, refactoring) must be done in a feature branch.
    -   Branch names should be descriptive, e.g., `feature/v0.5-quarkus-skeleton`, `fix/readme-typo`.
    -   Branches should be created from the `develop` branch.

### 3.2 Commit Message Convention
We follow the Conventional Commits specification. This helps create an explicit commit history and simplifies versioning.
-   **`feat:`** A new feature.
-   **`fix:`** A bug fix.
-   **`docs:`** Documentation-only changes.
-   **`refactor:`** A code change that neither fixes a bug nor adds a feature.
-   **`test:`** Adding missing tests or correcting existing tests.
-   **`chore:`** Changes to the build process or auxiliary tools.

### 3.3 Pull Request (PR) Process
1.  Create your feature branch from `develop`.
2.  Complete your work, ensuring it is covered by tests.
3.  Push your feature branch to the remote repository.
4.  Create a Pull Request from your feature branch to `develop`.
5.  The PR must pass all automated checks (build, tests).
6.  At least one other developer must review and approve the PR.
7.  Once approved, the PR can be merged into `develop`.

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
Creating a new release is a manual process performed when all tasks for a given version in the `roadmap.md` are complete and merged into the `develop` branch.

The steps are as follows:

1.  **Update `CHANGELOG.md`:**
    *   Change the `[Unreleased]` section to the new version number and date (e.g., `[0.5] - 2025-08-17`).
    *   Add a new `[Unreleased]` section at the top for future changes.
    *   Ensure all significant changes for the release are documented.

2.  **Update Project Version:**
    *   In the `pom.xml` file, update the `<version>` tag to the new release version (e.g., `<version>0.5</version>`).

3.  **Merge to `main`:**
    *   Create a Pull Request from `develop` to `main`. The PR should be named "Release vX.Y.Z".
    *   After approval, merge the PR into `main`.

4.  **Create and Push Git Tag:**
    *   On your local `main` branch, create an annotated Git tag: `git tag -a v0.5 -m "Release version 0.5"`
    *   Push the `main` branch and the new tag to the remote repository: `git push origin main --tags`

5.  **Prepare for Next Development Cycle:**
    *   Switch back to the `develop` branch.
    *   In `pom.xml`, update the version to the next development snapshot (e.g., `<version>0.6-SNAPSHOT</version>`).
    *   Commit and push this change to `develop` to start the next cycle.
