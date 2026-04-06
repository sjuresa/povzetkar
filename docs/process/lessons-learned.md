# Project Lessons Learned & Developer Knowledge Base

This centralized document encapsulates the critical technical discoveries and optimization patterns formulated during the development of `Povzetkar`. 

It serves as the definitive runbook and "portal" for any developer or AI Agent touching the codebase to ensure consistency and prevent the recurrence of complex systemic issues.

---

## 🚀 Recent Highlights (v0.16.0)

> [!IMPORTANT]
> **AI Prompt Architecture (POV-170)**: 
> We have migrated from positional string formatting to an explicit **named variable** syntax (e.g., `{{transcript}}`) using a high-performance **single-pass Matcher strategy**. This decouples AI prompts from backend logic and eliminates O(N^2) replacement overhead.

> [!TIP]
> **Auditory Feedback (POV-171)**: 
> UI sounds are now synthesized dynamically using the **Native Web Audio API**. This provides a 0KB asset weight and instant low-latency feedback without external asset dependencies.

> **Documentation Modularization (POV-172)**: 
> The monolithic Knowledge Base has been decomposed into specialized domain guides to improve discovery and maintainability.

> [!TIP]
> **Polymorphic UI Components (POV-173)**: 
> We have unified sharing/export logic into a single, type-aware component. This ensures feature parity across entities (Briefings vs. Summaries) and eliminates context-specific UI debt.

> [!IMPORTANT]
> **N+1 Optimization & Differential Testing (POV-175)**: 
> We have eliminated O(N) database bottlenecks in the Briefing API using **HQL Join Fetching**. Performance is now verified via **Differential Query Assertions** (math proof of O(1) complexity), preventing regressions in the CI/CD pipeline.

> [!TIP]
> **Bundle Optimization & Render Consolidation (POV-176)**: 
> We unified HTML export generation onto `ReactDOMServer.renderToString(<ReactMarkdown>)`. This eliminates the need for separate Markdown parsers (`marked`) and HTML sanitizers (`dompurify`), delegating XSS safety directly to React's native string serialization engines.

---

## 📚 Domain Directories

Select a domain below to view the full specification of "Standards" and "Traps":

| Domain | Description | Link |
|:---:|:---|:---|
| **Backend API** | Security, DTO mapping, and API design. | [backend-api.md](./lessons/backend-api.md) |
| **Database** | Hibernate, JPQL projections, and N+1 fixes. | [database.md](./lessons/database.md) |
| **Frontend UX** | React, Tailwind v4, Routing, and Themes. | [frontend-ux.md](./lessons/frontend-ux.md) |
| **Reactive** | Mutiny pipelines and real-time SSE events. | [reactive.md](./lessons/reactive.md) |
| **Testing** | Awaitility, Master Spec, and Traceability. | [testing.md](./lessons/testing.md) |
| **Infra & Ops** | Docker, Maven, CI/CD, and Observability. | [infrastructure-ops.md](./lessons/infrastructure-ops.md) |

---

## 📜 Historical Records

For sprint migration logs and legacy architectural notes (pre-v0.15.0), refer to the consolidated archive:
- [Legacy Process Logs](./archive/legacy-logs.md)
