# The Povzetkar Methodology

**Last Updated:** 2026-05-27
**Status:** Active

## 1. Core Philosophy

Our development process is a **Pragmatic Hybrid Agile** approach, tailored specifically for a collaboration between a single human Product Owner and an AI Developer Agent. It is built on five pillars:

1.  **Structure over Chaos:** We use strict checklists (`11_development_checklist.md`) and protocols (`AGENT.md`) to maintain discipline in an asynchronous environment.
2.  **Flow over Batches:** We strictly limit Work In Progress (WIP) to **1**. We finish one task completely before starting the next.
3.  **Documentation as Code:** Documentation is not an afterthought; it is a first-class deliverable. Retrospectives and architectural decisions (`ADRs`) are committed alongside the code to ensure a permanent and searchable source of truth.
4.  **Continuous Learning:** Every task concludes with a retrospective. Every version concludes with a cross-task synthesis retrospective that identifies systemic patterns and process health.
5.  **Asynchronous Pair Programming:** You act as the "navigator" (strategic direction), and I act as the "driver" (tactical implementation).

---

## 2. Roles & Responsibilities

### 2.1. The Product Owner (User)
*   **Role:** The "Navigator".
*   **Responsibilities:**
    *   Defines the vision and roadmap (`roadmap.md`).
    *   Prioritizes tasks for each version.
    *   Reviews and **explicitly approves** every plan proposed by the Agent.
    *   Reviews test results and verifies the final behavior. The Agent runs the test suite itself; the User is the quality gate that confirms the outcome and approves the result.
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

Our lifecycle is divided into four distinct phases. The **Task** is the primary planning and delivery unit. A **Version** is a retrospective label applied after the work — not a plan made before it.

```text
[Version Initialisation] ──→ [Task Execution Loop] ──→ [version.md updated]
                                      ↑                        ↓
                                      └────────────────────────┘
                               (PO decision: version complete)
                                              ↓
                                [Version Finalisation] ──→ [Release]
```

### Phase 1: Version Initialisation

*Goal: Open the branch, register the version, create the version hub, and consume the previous version's retrospective.*

1.  **Create Branch:** Create the new version branch (e.g., `v0.18.0`) from `main` and update version numbers.
2.  **Create Milestone:** The User registers the version as a Fix-version value in YouTrack.
3.  **Roadmap Entry:** Add the version to `docs/overview/roadmap.md` as in-progress.
4.  **Create Version Hub:** Create `docs/versions/vX.Y.Z/version.md` (status, task list table, empty Carry-Forward Review section).
5.  **Review Previous Retrospective:** The Agent reads the previous version's retrospective (Carry-Forward Themes) and proposes a disposition per theme — backlog entry, task candidate, or conscious drop with a reason; the User approves each. Dispositions are recorded in the new hub's Carry-Forward Review table.

### Phase 2: Task Execution (The Core Loop)

*Goal: Implement one task at a time with high quality. This is the primary phase — it repeats for every task in the version.*

1.  **Brainstorm:** Read the Feed-forward sections of all task retrospectives in the current version (the version-level knowledge pool), then conduct a scope-alignment interview (WHAT will be done). The task is not final until this is complete.
2.  **Define:** The task is finalised in `agent_logs/task.md`.
3.  **Plan:** The Agent conducts a PPP interview (HOW it will be done) and proposes a detailed plan grounded in codebase evidence. **Execution is forbidden without User approval.**
4.  **Execute (TDD):**
    *   **New Behavior:** Red -> Green -> Refactor.
    *   **Refactoring:** Green -> Refactor -> Green.
5.  **Verify & Review:** The Agent runs the full test suite, then a proportional pre-commit review (`/code-review`, plus `/security-review` for security-relevant changes). After the push, every PR reviewer's findings — human or bot — are handled in the PR review loop; the User merges.
6.  **Log:** The `CHANGELOG.md` is updated immediately after the task.
7.  **Walkthrough:** A walkthrough document (`POV-XXX-walkthrough.md`) is written summarising what was delivered, including its mandatory Traceability section (the task's US/UC/TC IDs, or `N/A — <reason>`; machine-validated).
8.  **Task Retrospective:** A task-level retrospective (`POV-XXX-retrospective.md`) is written capturing technical findings for future tasks.
9.  **Version Hub:** `docs/versions/vX.Y.Z/version.md` is updated with links to the new walkthrough and retrospective (the hub itself is created at version initialisation).

### Phase 3: Version Finalisation

*Goal: Declare the version complete and prepare for release. Triggered by an explicit PO decision — there is no task list to signal completion.*

Its core is the **Version Retrospective** (`docs/versions/vX.Y.Z/retrospective.md`) — a cross-task synthesis of trends, systemic patterns and process health, ending with the Carry-Forward Themes consumed at the next version's initialisation — followed by a documentation review, a supply-chain audit, and the release cleanup (`-SNAPSHOT` removed, `CHANGELOG.md` finalised).

### Phase 4: Release

*Goal: Ship it* — release PR to `main`, merge, annotated tag, production deploy, YouTrack release.

The step-by-step procedure for Phases 3 and 4 lives only in [`11_development_checklist.md`](../sdlc/11_development_checklist.md) — it is deliberately not repeated here, so the two cannot drift apart.

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

We operate a two-level retrospective model:

**Task Retrospective** (`docs/versions/vX.Y.Z/POV-XXX-retrospective.md`) — written at the end of every task.

*   **Planned vs. Actual:** What was scoped vs. what was delivered.
*   **Deviations & Why:** Decisions made mid-execution and their rationale.
*   **Technical Findings:** Patterns, gotchas, and constraints discovered — the durable record for future tasks.
*   **Feed-forward:** What the next task should know. (Consumed by the Brainstorm of every subsequent task in the version.)

**Version Retrospective** (`docs/versions/vX.Y.Z/retrospective.md`) — written at the end of every version as a cross-task synthesis.

*   **Trends:** Patterns that appeared across multiple tasks.
*   **Systemic Issues:** Process or architectural issues that transcend individual tasks.
*   **Action Items:** Concrete changes to `AGENT.md` or `11_development_checklist.md` for the next version.
*   **Carry-Forward Themes:** Version-level threads for the next version. (Consumed at the next version's initialisation: each theme is dispositioned — backlog, task candidate, or conscious drop — into the new version hub's Carry-Forward Review table.)

### 5.2. The Backlog
*   **Roadmap:** Strategic goals.
*   **Improvements:** Non-critical ideas (`docs/backlog/improvements.md`) discovered during execution.

---

## 6. Technical Mandates & Design Decisions

*   **Timezones:** Server/Logs in UTC. UI in User's Timezone (configured). *Decision v0.9.0.*
*   **Configuration:** All config via `application.properties` (and env vars). *Decision v0.8.0.*
*   **Logging:** Structured Logging (Logfmt) with numeric metrics. *Decision v0.8.0.*
*   **Dependencies:** Managed via Quarkus BOM. *Decision v0.8.0.*
