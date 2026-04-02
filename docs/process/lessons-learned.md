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

> [!NOTE]
> **Documentation Modularization (POV-172)**: 
> The monolithic Knowledge Base has been decomposed into specialized domain guides to improve discovery and maintainability.

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
