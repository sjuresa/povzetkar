# Development Methodology Comparison

**Version:** 1.1
**Date:** September 1, 2025

## Revision History
| Version | Date            | Author       | Changes                                           |
|---------|-----------------|--------------|---------------------------------------------------|
| 1.1     | September 1, 2025 | Povzetkar AI | Added section on adopted practices for improvement. |
| 1.0     | September 1, 2025 | Povzetkar AI | Initial version of the methodology comparison.    |

## 1. Introduction

This document analyzes our current development process by comparing it to established agile methodologies. The goal is to understand the strengths of our hybrid approach and identify what makes it effective for our specific context.

Our process can be described as a **structured, asynchronous, single-flow process inspired by Agile principles**. It is tailored for a collaboration between a single developer (the AI) and a single product owner/technical lead (the user).

---

## 2. Comparison with Scrum

Scrum is a framework for developing complex products, emphasizing iterative progress in fixed-length iterations called Sprints.

### Similarities to Scrum

*   **Product Backlog:** Our `roadmap.md` serves the exact purpose of a Product Backlog. It is a prioritized list of high-level features and architectural goals for the entire project.
*   **Sprint Backlog:** For each version (e.g., v0.5, v0.6), the corresponding `vX.Y-tasks.md` file acts as a Sprint Backlog. It is a detailed plan of work selected from the roadmap that we commit to completing in the current development cycle.
*   **Increment:** Each successfully completed version is a valuable, potentially shippable increment of the product.
*   **Roles:**
    *   You act as the **Product Owner**: You define the vision (`roadmap.md`), prioritize features, and provide feedback and approval.
    *   I act as the **Development Team**: I execute the plan and deliver the increment. I also embody some aspects of a **Scrum Master** by enforcing the process defined in `AGENT.md`.

### Differences from Scrum

*   **No Fixed Time-Boxes (Sprints):** We do not operate in fixed-length Sprints. A version is complete when the tasks defined in its `*-tasks.md` file are done. This provides flexibility.
*   **No Formal Ceremonies:** We do not have formal, synchronous meetings like Daily Scrums, Sprint Reviews, or Sprint Retrospectives. Our entire interaction is an asynchronous, continuous feedback loop.

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

### Differences from XP

*   **On-site Customer:** XP traditionally calls for a customer to be on-site and available for immediate consultation. Our asynchronous model is a necessary adaptation.
*   **Collective Code Ownership:** While you have ultimate ownership, I am empowered to suggest and make changes across the entire codebase, which aligns with the spirit of collective ownership.

---

## 5. Alignment with Lean Software Development Principles

Lean is less of a methodology and more of a guiding philosophy. Our process aligns well with its core principles.

*   **Eliminate Waste:** Our decision to simplify the v0.5 migration script to a direct database write instead of building a throwaway API is a perfect example of eliminating waste (in this case, unnecessary, temporary code).
*   **Amplify Learning:** The creation of `*-planning-process.md` documents is a direct implementation of this principle. We are documenting our decisions and learnings to inform future work.
*   **Decide as Late as Possible:** We have a high-level `roadmap.md` but make detailed, binding decisions only for the immediate version we are working on. This allows us to adapt as we learn more.
*   **Build Integrity In:** Our strict adherence to TDD and automated verification steps ensures that quality is built into the process from the beginning, not added as an afterthought.

---

## 6. Conclusion

Our methodology is a pragmatic **hybrid** that successfully combines:

*   The **structured planning and version-based increments** from **Scrum**.
*   The **flow management, WIP limits, and explicit policies** from **Kanban**.
*   The **disciplined technical practices (TDD, Pair Programming, Simple Design)** from **Extreme Programming**.
*   The **overarching philosophy of eliminating waste and amplifying learning** from **Lean**.

It deliberately omits the formal, synchronous ceremonies of Scrum, as they are designed for coordinating teams and are unnecessary in our one-to-one, asynchronous collaboration. This makes our process highly efficient and tailored to our unique working relationship.

---

## 7. Adopted Practices for Future Improvement

To formalize our commitment to continuous improvement (Kaizen), we are adopting the following practices into our workflow, effective immediately.

### 7.1. Version Retrospectives (from Scrum)

*   **What it is:** A brief, formal review at the end of each version cycle to reflect on what went well, what could be improved, and what we will change.
*   **Our Implementation:** After each version is complete (e.g., v0.5), and before planning the next, we will create a `docs/retrospectives/vX.Y-retrospective.md` file. This file will document our findings and define a concrete, actionable improvement for the next version. This formalizes our learning process.

### 7.2. Spike Tasks (from Extreme Programming)

*   **What it is:** Time-boxed, research-oriented tasks designed to reduce technical uncertainty before committing to implementation. The deliverable is knowledge and a recommendation, not production code.
*   **Our Implementation:** For features with significant technical unknowns (e.g., integrating a new library, evaluating an API), we will create a dedicated "Spike" task in the relevant version plan. This will de-risk future development and lead to more accurate time estimates.

### 7.3. "See the Whole" Value Stream Focus (from Lean)

*   **What it is:** A mindset focused on optimizing the entire flow of value from idea to delivery, rather than just individual tasks.
*   **Our Implementation:** During the planning phase for each new version (`*-planning-process.md`), we will explicitly ask how the planned tasks contribute to the overall project goals defined in `roadmap.md` and whether they create any downstream impediments. This ensures we maintain a long-term architectural vision and avoid local optimizations that harm the overall system.