# Project Planning Protocol (PPP): Standard Tier

**Status:** Active
**Revision:** 1.0
**Description:** This protocol defines the mandatory artifacts and quality standards for the Version Planning phase of the Povzetkar project. It serves as the "Standard" implementation of the planning phase defined in the **[Development Methodology](./methodology.md)** and is enforced by **[Phase 1 of the Development Checklist](../sdlc/11_development_checklist.md)**. Any new version (vX.Y.Z) MUST adhere to these rules before implementation begins.

---

## 1. Mandatory Planning Artifacts

To achieve "Perfection Tier" planning, every version must produce or update the following documents:

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

### 1.4 Detailed Task Breakdown (POV-ID)
- **Goal:** Actionable, high-context task list.
- **Requirements:** 
    - **Time Estimation Summary**: Individual estimates for each task ID with a Total and Contingency (20%).
    - **Global Definition of Done (DoD)**: Mandatory section covering Code Quality, i18n, Testing, and **Documentation Refresh** (updating Runbooks, Specifications, and Architecture docs).
    - **Task Metadata**: Each task must include:
        - Git-style titles (feat/fix/style/test).
        - **ASCII Wireframes** (for UI tasks).
        - **Resilience & Error States**: System behavior for failure scenarios.
        - **Context / Documentation**: Links to related ADRs, Specifications, or Use Cases.
        - **Required Skills**: Tagging with specific agent skills (e.g., `@[skills/clean-code]`).
- **Location:** `docs/versions/vX.Y.Z/tasks.md`

### 1.5 Version Master Hub (SSoT)
- **Goal:** Unified entry point for the release.
- **Requirements:** 
    - **Version At A Glance**: Table with SSoT Decisions (link to ADRs) and Baseline Audit statuses.
    - **Strategic Discussions**: Documentation of technical debates, strategic pivots (Pivot/Critique/Decision), and rationale.
    - **Risk Registry**: Table documentings potential technical or project risks and their mitigations.
- **Location:** `docs/versions/vX.Y.Z/planning-process.md`

### 1.6 Operational Constraints (NFR Alignment)
- **Goal:** Define *how well* the feature must perform to meet project standards.
- **Requirements:** Every P0/P1 task must specify a performance budget (e.g., "Latency < 500ms", "Maximum bundle size increase per feature < 10KB"). 
- **Reference:** [Non-Functional Requirements](../sdlc/02_non_functional_requirements.md).

---

## 2. Infrastructure Standards (Directory Pattern)

All version-specific planning artifacts MUST be isolated in a dedicated directory:
- **Path Pattern:** `docs/versions/vX.Y.Z/`
- **Mandatory Files:** `planning-process.md`, `tasks.md`.
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

To ensure pragmatism and speed, the protocol is **Elastic**. Not every version requires every artifact:

- **UI/UX Manifesto:** Mandatory only for versions with user-facing interface changes.
- **ADRs:** Mandatory only for architectural pivots or security-critical flows.
- **i18n Audit:** Mandatory only if new strings are introduced.

**Requirement:** If an artifact is skipped, a brief justification MUST be included in the `vX.Y.Z-planning-process.md` under the "Scope" or "Justification" section.

---

## Revision History
| Version | Date       | Author       | Changes                                                                 |
|:--------|:-----------|:-------------|:------------------------------------------------------------------------|
| 1.0     | 2026-04-01 | Povzetkar AI | Initialized Planning Protocol.    |
