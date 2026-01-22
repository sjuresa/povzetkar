# The Povzetkar Methodology

**Last Updated:** 2026-01-22
**Status:** Active

## 1. Core Philosophy

Our development process is a **Pragmatic Hybrid Agile** approach, tailored specifically for a collaboration between a single human Product Owner and an AI Developer Agent. It is built on five pillars:

1.  **Structure over Chaos:** We use strict checklists (`11_development_checklist.md`) and protocols (`AGENT.md`) to maintain discipline in an asynchronous environment.
2.  **Flow over Batches:** We strictly limit Work In Progress (WIP) to **1**. We finish one task completely before starting the next.
3.  **Documentation as Code:** Documentation is not an afterthought; it is a first-class deliverable. Plans, retrospectives, and architectural decisions are committed alongside the code.
4.  **Continuous Learning:** Every version concludes with a retrospective that directly feeds action items into the next version's process.
5.  **Asynchronous Pair Programming:** You act as the "navigator" (strategic direction), and I act as the "driver" (tactical implementation).

---

## 2. Roles & Responsibilities

### 2.1. The Product Owner (User)
*   **Role:** The "Navigator".
*   **Responsibilities:**
    *   Defines the vision and roadmap (`roadmap.md`).
    *   Prioritizes tasks for each version.
    *   Reviews and **explicitly approves** every plan proposed by the Agent.
    *   Executes the tests and verifies the final behavior (since the Agent cannot run code).
    *   Provides the "Definition of Done" confirmation.

### 2.2. The Developer (Agent)
*   **Role:** The "Driver".
*   **Responsibilities:**
    *   Analyzes requirements and codebase.
    *   Proposes detailed implementation plans.
    *   Writes code and tests following TDD.
    *   Maintains all documentation.
    *   **Constraint:** Must strictly adhere to the protocols defined in `AGENT.md`.

---

## 3. The Development Lifecycle

Our lifecycle is divided into four distinct phases.

### Phase 1: Version Planning
*Goal: Define WHAT to build and HOW to test it.*
1.  **Retrospective Review:** We start by reviewing the previous version's learnings.
2.  **Goal Definition:** We select high-level goals from the `roadmap.md`.
3.  **Testing Strategy:** We explicitly decide the testing approach for new features (e.g., "Unit tests for logic, Integration tests for API, Manual plan for UI"). *Learned in v0.6.1.*
4.  **Spike Identification:** High-risk or uncertain tasks are isolated as "Spikes" (research tasks) to be tackled first. *Learned in v0.6.2.*
5.  **Output:** A `vX.Y-planning-process.md` (rationale) and `vX.Y-tasks.md` (execution list).

### Phase 2: Task Execution (The Core Loop)
*Goal: Implement one task at a time with high quality.*
1.  **Define:** The task is isolated in `agent_logs/task.md`.
2.  **Pre-Flight Check:** The Agent performs a mandatory context check (Usage Analysis, TDD strategy) before planning. *Learned in v0.8.0.*
3.  **Plan:** The Agent proposes a detailed plan. **Execution is forbidden without User approval.**
4.  **Execute (TDD):**
    *   **New Behavior:** Red -> Green -> Refactor.
    *   **Refactoring:** Green -> Refactor -> Green.
5.  **Verify & Code Review:** The User runs the tests and performs a code review (checking for blocking code, JPA identity, etc.).
6.  **Log:** The `CHANGELOG.md` and current retrospective are updated *immediately* after the task.

### Phase 3: Version Finalization
*Goal: Clean up and prepare for release.*
1.  **Documentation Review:** We review project-wide docs (`docs/sdlc`, `docs/analysis`) to ensure they match the new reality.
2.  **Cleanup:** We finalize the `pom.xml` (remove snapshots) and `CHANGELOG.md`.

### Phase 4: Release
*Goal: Ship it.*
1.  Merge to `main`.
2.  Tag the release.

---

## 4. Core Engineering Practices

### 4.1. Test-Driven Development (TDD)
TDD is mandatory for all logic changes. We adhere to two specific workflows:
*   **Red-Green-Refactor:** For bug fixes and new features. Write a failing test, make it pass, then refactor.
*   **Green-to-Green:** For internal refactoring. Verify tests pass, refactor, verify tests pass again.

**Key Mandates:**
*   **Strict TDD:** For complex logic, we write the test first.
*   **Integration Testing:** We prefer `@QuarkusTest` with real databases (Testcontainers). We avoid mocking internal services unless absolutely necessary. *Learned in v0.6.2.*
*   **UI Testing:** We avoid fragile inheritance patterns (`BaseUITest`) and prefer explicit transaction management in tests. *Learned in v0.6.2/v0.9.0.*

### 4.2. Spikes
A "Spike" is a time-boxed research task used when the path forward is unclear.
*   **Output:** Knowledge (documented in `docs/spikes/`), not production code.
*   **Outcome:** The spike results inform the actual implementation task.

### 4.3. Definition of Done (DoD)
A task is only complete when:
1.  Code is written and follows standards.
2.  Tests are passing (verified by User).
3.  **Documentation is Updated:** Context and Update sections from the task definition are addressed.
4.  **UI is Responsive:** Verified on mobile/desktop (if applicable).
5.  Commit message is formatted (Conventional Commits).

### 4.4. Code Refactoring Checklist
Before any refactoring, we explicitly identify:
1.  The Declaration (what changes).
2.  The Usages (what breaks).
3.  The Tests (what verifies it).
*Learned in v0.8.0.*

### 4.5. Strategic Deferral
We actively practice "Strategic Deferral". If a feature is found to be too complex or risky for the current version's goals, we explicitly defer it to a later version rather than rushing a poor implementation. This preserves the quality and focus of the current release.

---

## 5. Continuous Improvement

### 5.1. Retrospectives
At the end of every version, we write a `vX.Y-retrospective.md`.
*   **What Went Well:** To repeat success.
*   **What Didn't Go Well:** To identify systemic issues.
*   **Action Items:** Concrete changes to `AGENT.md` or `11_development_checklist.md` for the next version.

### 5.2. The Backlog
*   **Roadmap:** Strategic goals.
*   **Improvements:** Non-critical ideas (`docs/backlog/improvements.md`) discovered during execution.

---

## 6. Technical Mandates & Design Decisions

*   **Timezones:** Server/Logs in UTC. UI in User's Timezone (configured). *Decision v0.9.0.*
*   **Configuration:** All config via `application.properties` (and env vars). *Decision v0.8.0.*
*   **Logging:** Structured Logging (Logfmt) with numeric metrics. *Decision v0.8.0.*
*   **Dependencies:** Managed via Quarkus BOM. *Decision v0.8.0.*