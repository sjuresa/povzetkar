# Development Methodology Comparison

## Revision History
| Version | Date              | Author       | Changes                                                                                                                                         |
|---------|-------------------|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| 1.9     | June 26 2026      | Povzetkar AI | Refreshed for the task-first model (POV-191, v0.18.0): updated Scrum "Sprint Backlog"/time-box and "Asynchronous Ceremonies" (two-level retrospective) and Lean "Amplify Learning"/"See the Whole" to reflect task-first delivery and the new `docs/versions/vX.Y.Z/` artifact structure. Section 7 remains empty. |
| 1.8     | March 18, 2026    | Povzetkar AI | Removed "Automated Dependency Vulnerability Scanning" from proposed practices as it was adopted in v0.12.0. |
| 1.7     | February 20, 2026 | Povzetkar AI | Removed "Architectural Decision Records (ADRs)" from proposed practices as it was adopted in v0.10.0.           |
| 1.6     | January 20, 2026  | Povzetkar AI | Removed "Codified Testing Patterns" from proposed practices as it was adopted in v0.8.0.                                                        |
| 1.5     | January 17, 2026  | Povzetkar AI | Updated "Amplify Learning" section to reflect the adopted Project-Wide Documentation Review practice.                                           |
| 1.4     | January 6, 2026   | Povzetkar AI | Added "Codified Testing Patterns" to proposed improvements based on v0.6.2 retrospective.                                                       |
| 1.3     | November 1, 2025  | Povzetkar AI | Integrated adopted practices (Retrospectives, Value Stream Focus) into the core methodology. Section 7 now only contains proposed improvements. |
| 1.2     | September 9, 2025 | Povzetkar AI | Refined Section 7 to clarify the adoption status of new practices.                                                                              |
| 1.1     | September 1, 2025 | Povzetkar AI | Added section on adopted practices for improvement.                                                                                             |
| 1.0     | September 1, 2025 | Povzetkar AI | Initial version of the methodology comparison.                                                                                                  |

## 1. Introduction

> **Note:** This document provides the historical context and rationale for our chosen approach. For the definitive, operational guide on *how* we work today, please refer to **[`docs/process/methodology.md`](../process/methodology.md)**.

---

This document analyzes our current development process by comparing it to established agile methodologies. The goal is to understand the strengths of our hybrid approach and identify what makes it effective for our specific context.

Our process can be described as a **structured, asynchronous, single-flow process inspired by Agile principles**. It is tailored for a collaboration between a single developer (the AI) and a single product owner/technical lead (the user).

---

## 2. Comparison with Scrum

Scrum is a framework for developing complex products, emphasizing iterative progress in fixed-length iterations called Sprints.

### Similarities to Scrum

*   **Product Backlog:** Our `roadmap.md` serves the exact purpose of a Product Backlog. It is a prioritized list of high-level features and architectural goals for the entire project.
*   **Sprint Backlog:** Historically (through v0.17.0), a per-version `docs/versions/vX.Y-tasks.md` file acted as a Sprint Backlog. As of the **task-first model** (POV-191, v0.18.0), the task — defined in `agent_logs/task.md` and closed out in the version hub `docs/versions/vX.Y.Z/version.md` — is the primary planning and delivery unit; the version is a retrospective label, not a pre-committed batch.
*   **Increment:** Each successfully completed version is a valuable, potentially shippable increment of the product.
*   **Roles:**
    *   You act as the **Product Owner**: You define the vision (`roadmap.md`), prioritize features, and provide feedback and approval.
    *   I act as the **Development Team**: I execute the plan and deliver the increment. I also embody some aspects of a **Scrum Master** by enforcing the process defined in `AGENT.md`.

*   **Definition of Done (DoD):** As of v0.6.1, we have formally adopted a DoD. This is an explicit checklist that a task must meet to be considered complete, ensuring quality and consistency.
### Differences from Scrum

*   **No Fixed Time-Boxes (Sprints):** We do not operate in fixed-length Sprints. Under the task-first model (POV-191), a version is declared complete by an explicit PO decision — not by exhausting a pre-defined task list. This provides flexibility.
*   **Asynchronous Ceremonies:** We do not have formal, synchronous meetings like Daily Scrums or Sprint Reviews. However, we have adopted an asynchronous, **two-level retrospective** practice: a per-task retrospective (`docs/versions/vX.Y.Z/POV-XXX-retrospective.md`) written as each task closes, and a cross-task version retrospective (`docs/versions/vX.Y.Z/retrospective.md`) synthesising trends and process health when the version is finalised. (Prior to v0.18.0 these lived as a single `docs/retrospectives/vX.Y-retrospective.md` file.)

---

## 3. Comparison with Kanban

Kanban is a method for managing knowledge work with an emphasis on continuous delivery and not overloading the development team.

### Similarities to Kanban

*   **Visualize the Workflow:** We use an external issue tracking system (YouTrack/Jira) with columns like `Submitted`, `In Progress`, and `Fixed`. This is a direct implementation of a Kanban board and serves as the primary tool for visualizing the state of work items.
*   **Limit Work In Progress (WIP):** This is a core principle of our process. Our WIP limit is strictly **1**. I only ever work on a single task at a time, from planning to execution. This ensures maximum focus and quality.
*   **Manage Flow:** We focus on getting one task from "planned" to "done" as efficiently as possible.
*   **Make Policies Explicit:** This is one of our greatest strengths. `AGENT.md` is the ultimate explicit policy document. It defines every rule of our engagement, leaving no room for ambiguity.
*   **Implement Feedback Loops & Improve Collaboratively:** Our entire interaction is a feedback loop. This discussion, which leads to process improvements, is a core tenet of Kanban (and Kaizen).

### Differences from Kanban

*   **Continuous Delivery vs. Batches:** A pure Kanban system often focuses on a continuous flow of small, individual items. We tend to batch our work into larger, cohesive "versions" (like v0.5). In this respect, we are slightly more like Scrum.

---

## 4. Comparison with Extreme Programming (XP)

Extreme Programming is a highly disciplined methodology focused on technical practices that produce high-quality code. Many of its core practices are surprisingly similar to our own.

### Similarities to XP

*   **Pair Programming:** Our interaction is a form of **asynchronous pair programming**. You act as the "navigator," providing direction, reviewing plans, and catching strategic errors. I act as the "driver," focusing on the tactical implementation of the code. This separation of concerns is a key benefit of pairing.
*   **Test-Driven Development (TDD):** This is a non-negotiable part of my process, as mandated by `AGENT.md`. We write tests before or alongside the code, not after.
*   **Simple Design:** We actively strive to find the simplest solution that works, as demonstrated by our discussions to simplify the v0.5 migration plan. We avoid over-engineering.
*   **Refactoring:** We continuously refactor the code and even the documentation to improve clarity and maintainability.
*   **Small Releases:** Our version-based increments (v0.5, v0.6) are analogous to XP's concept of small, frequent releases that deliver value.
*   **Spike Tasks:** For features with significant technical unknowns (e.g., analyzing performance under load), we create dedicated, time-boxed "Spike" tasks. The deliverable is knowledge and a documented recommendation, which de-risks future development.

### Differences from XP

*   **On-site Customer:** XP traditionally calls for a customer to be on-site and available for immediate consultation. Our asynchronous model is a necessary adaptation.
*   **Collective Code Ownership:** While you have ultimate ownership, I am empowered to suggest and make changes across the entire codebase, which aligns with the spirit of collective ownership.

---

## 5. Alignment with Lean Software Development Principles

Lean is less of a methodology and more of a guiding philosophy. Our process aligns well with its core principles.

*   **Eliminate Waste:** Our decision to simplify the v0.5 migration script to a direct database write instead of building a throwaway API is a perfect example of eliminating waste (in this case, unnecessary, temporary code).
*   **Amplify Learning:** We have institutionalized this through two mechanisms:
    1.  **Per-Task Brainstorm & Walkthrough/Retrospective:** Each task captures scope rationale in `agent_logs/task.md` (Brainstorm) and closes with a walkthrough and retrospective in `docs/versions/vX.Y.Z/`. (Prior to the task-first model, rationale lived in per-version `docs/versions/*-planning-process.md` files.)
    2.  **Project-Wide Documentation Review:** A mandatory phase at the end of every version (Phase 3.2 in the checklist) where we systematically review and refresh core documentation (`docs/sdlc`, `docs/analysis`, etc.) to prevent knowledge rot.
*   **Decide as Late as Possible:** We have a high-level `roadmap.md` but make detailed, binding decisions only for the immediate version we are working on. This allows us to adapt as we learn more.
*   **Build Integrity In:** Our strict adherence to TDD and automated verification steps ensures that quality is built into the process from the beginning, not added as an afterthought.
*   **See the Whole:** During the per-task Brainstorm and PPP planning interviews, we explicitly consider how the task contributes to the overall project goals defined in `roadmap.md`. This ensures we maintain a long-term architectural vision and avoid local optimizations that harm the overall system.

---

## 6. Conclusion

Our methodology is a pragmatic **hybrid** that successfully combines:

*   The **structured planning and version-based increments** from **Scrum**.
*   The **flow management, WIP limits, and explicit policies** from **Kanban**.
*   The **disciplined technical practices (TDD, Pair Programming, Simple Design)** from **Extreme Programming**.
*   The **overarching philosophy of eliminating waste and amplifying learning** from **Lean**.

It deliberately omits the formal, synchronous ceremonies of Scrum, as they are designed for coordinating teams and are unnecessary in our one-to-one, asynchronous collaboration. This makes our process highly efficient and tailored to our unique working relationship.

---

## 7. Proposed Practices for Continuous Improvement

*(Section empty - all previous proposals have been adopted)*
