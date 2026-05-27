# Project Planning Protocol (PPP): Standard Tier

**Status:** Active
**Revision:** 1.1
**Description:** This protocol defines the mandatory artifacts and quality standards for task planning in the Povzetkar project. It serves as the "Standard" implementation of the planning interview defined in the **[Development Methodology](./methodology.md)** and is enforced by **[Phase 2 (step 2.2) of the Development Checklist](../sdlc/11_development_checklist.md)**. Every task MUST adhere to these rules before implementation begins, applying artifacts where warranted (see §5 Protocol Elasticity).

---

## 1. Mandatory Planning Artifacts

To achieve "Perfection Tier" planning, every task must produce or update the following documents where applicable (see §5 Protocol Elasticity for conditions):

### 1.1 Architecture Decision Records (ADRs)
- **Goal:** Document the context and rationale for major technical pivots.
- **Trigger:** Any new database table, a new architectural pattern (e.g., sound framework), or a security-sensitive flow (e.g., sharing).
- **Location:** `docs/adr/`

### 1.2 UI/UX Manifesto
- **Goal:** Define design tokens and micro-animations.
- **Requirements:** 
    - Color scale (Zinc/Slate preference).
    - Typography choices.
    - Animation speed & easing.
- **Location:** `docs/versions/vX.Y.Z/ui-ux-manifesto.md`

### 1.3 Localization String Audit
- **Goal:** Eliminate guesswork in localization.
- **Requirements:** List of all new translation keys for both `en.json` and `sl.json`.
- **Location:** `docs/versions/vX.Y.Z/i18n-audit.md`

### 1.4 Task Definition (POV-ID)

- **Goal:** Actionable, high-context task definition in YouTrack and `agent_logs/task.md`.
- **Requirements:**
    - **Time Estimation**: Estimate for this task with a contingency note.
    - **Definition of Done (DoD)**: Covering Code Quality, i18n, Testing, and **Documentation Refresh** (updating Runbooks, Specifications, and Architecture docs).
    - **Task Metadata**:
        - Git-style title (feat/fix/style/test).
        - **ASCII Wireframes** (for UI tasks).
        - **Resilience & Error States**: System behavior for failure scenarios.
        - **Context / Documentation**: Links to related ADRs, Specifications, or Use Cases.
        - **Required Skills**: Tagging with specific agent skills (e.g., `@[skills/clean-code]`).
- **Location:** `agent_logs/task.md` (active) and YouTrack ticket.

### 1.5 Version Hub Entry

- **Goal:** Keep `docs/versions/vX.Y.Z/version.md` current as a navigational SSoT for the version.
- **Requirements:**
    - Link to this task's walkthrough (`POV-XXX-walkthrough.md`).
    - Link to this task's retrospective (`POV-XXX-retrospective.md`).
- **Location:** `docs/versions/vX.Y.Z/version.md`

### 1.6 Operational Constraints (NFR Alignment)
- **Goal:** Define *how well* the feature must perform to meet project standards.
- **Requirements:** 
    - **Baseline Audit**: Before setting constraints, the current state MUST be measured (e.g., using Lighthouse, Micrometer, or Build stats).
    - **Performance Budget**: Every P0/P1 task must specify a budget based on the baseline (e.g., "Latency < 500ms", "Maximum bundle size increase per feature < 10KB"). 
- **Reference:** [Non-Functional Requirements](../sdlc/02_non_functional_requirements.md).

---

## 2. Infrastructure Standards (Directory Pattern)

All version-specific artifacts MUST be isolated in a dedicated directory:

- **Path Pattern:** `docs/versions/vX.Y.Z/`
- **Mandatory File:** `version.md` — the version hub, created at the end of the first task of the version.
- **Per-Task Files:** `POV-XXX-walkthrough.md`, `POV-XXX-retrospective.md` — one pair per task.
- **Optional Files:** `ui-ux-manifesto.md`, `i18n-audit.md` (as per Protocol Elasticity).

### 2.1 Traceability Matrix
Every User Story in the planning document MUST be mapped to:
1.  A **Use Case** (`use_cases.md`).
2.  A **Test Case** (`test_specification.md`).
3.  A specific **Task ID** (`POV-XXX`).

### 2.2 Expert Triad Review
Mandatory evaluation section in the planning process document covering:
- **Security Audit**: Port exposures, data masking, and access guards.
- **i18n Readiness**: Impact on localization files and string management.
- **Ops Readiness**: Rollback/panic-switch procedures in `RUNBOOK.md`.

### 2.3 Semantic Self-Check (AI Planner)
- **Goal:** Ensure the proposed architecture actually solves the user problem.
- **Requirement:** Before finalizing, the AI Planner must perform a critical "Internal Audit" to verify that ADRs, UI Manifestos, and Tasks are logically consistent and fulfill the User Stories.

### 2.4 Automated Audit
The planning suite MUST pass the automated validator check:
`python scripts/qa/validate-planning.py --current-version vX.Y.Z`

### 2.5 Pre-Interview Codebase Audit
**Before finalizing any task definition that touches existing code**, the relevant source files MUST be read. Task descriptions written from memory or assumed codebase state produce plausible-sounding but factually wrong acceptance criteria, wrong API surface descriptions, and missed architectural constraints.

**Mandatory reads before task interview:**
- The primary file(s) the task modifies (e.g., the API resource, the frontend component, the service).
- Any callers or consumers that will be affected by the change.
- Existing patterns the task is expected to follow (e.g., the AuditLogPage template for a new admin page).

**What to look for:**
- Does the assumed endpoint / route / component actually exist?
- Does the assumed model field / enum / method exist?
- Are there existing patterns (interceptor structure, pagination hooks, error handling) that the task must follow?
- Are there constraints that would block the proposed approach (e.g., lifecycle methods that overwrite state)?

**Origin:** v0.17.0 planning produced task definitions with a non-existent `/login` route, wrong ReactMarkdown call-site count, and a cancel mechanism that would have been silently overwritten by `executeJobLifecycle` — all discovered only in a second interview round with deliberate source reading. See [v0.17.0 retrospective §4](../retrospectives/v0.17.0-retrospective.md) for the full incident record.

---

## 3. Communication Standards
- **Phased Approach:** 1. Strategic Selection -> 2. Technical Design -> 3. Testing Philosophy.
- **Discussion Points:** Document "Why we chose B over A" in the planning process doc.
- **Expert Triad Review:** Mandatory section for Security and i18n audits.

---

## 4. Discovery & User Feedback Phase
- **Goal:** Validate assumptions with the Product Owner before committing to code.
- **Requirements:** 
    - Every major feature cycle starts with a **Brainstorming phase** (tagged with `U-` for user-driven requirements).
    - Feedback from these sessions (e.g., CTA clarity, terminology adjustments) MUST be reflected in the final plan.
    - For high-stakes UI, low-fidelity mockups (ASCII or screenshots) must be approved by the PO.

---

## 5. Protocol Elasticity

To ensure pragmatism and speed, the protocol is **Elastic**. Not every task requires every artifact:

- **UI/UX Manifesto:** Mandatory only for tasks with user-facing interface changes.
- **ADRs:** Mandatory only for architectural pivots or security-critical flows.
- **i18n Audit:** Mandatory only if new strings are introduced.

**Requirement:** If an artifact is skipped, a brief justification MUST be included in `agent_logs/task.md` under the "Scope" or "Justification" section.

---

## Revision History

| Version | Date | Author | Changes |
| --- | --- | --- | --- |
| 1.1 | 2026-05-27 | Povzetkar AI | Task-First Planning (POV-191): scoped §1 artifacts from version to task level; updated §2 mandatory files to `version.md` + per-task walkthrough/retrospective; updated §5 elasticity justification location. §2.5 unchanged. |
| 1.0 | 2026-04-01 | Povzetkar AI | Initialized Planning Protocol. |
