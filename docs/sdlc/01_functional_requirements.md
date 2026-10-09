# Functional Requirements Document (FRD) - Project Povzetkar

## Revision History

| Version | Date             | Author       | Changes                                                                                         |
|---------|------------------|--------------|-------------------------------------------------------------------------------------------------|
| 1.9     | October 9, 2026 | Povzetkar AI | POV-223: user roles updated to the SPA and per-user topics; US-17.7 text aligned with the implemented re-login (ID unchanged). |
| 1.8     | September 28, 2026 | Povzetkar AI | POV-220: minted US-UI-007 in §3.5 — landing page for prospective users; Implemented at task close-out. |
| 1.7     | September 27, 2026 | Povzetkar AI | POV-216: migrated the orphaned US-16.2, US-16.3, US-16.4, US-16.8 (referenced by UC-8/9/10) into new §3.8; US-16.4 notes the per-user guide state. |
| 1.6     | July 8, 2026     | Povzetkar AI | POV-205: minted US-API-001 in new §3.6 API & Integrations; §3.6 Administration & Hardening renumbered to §3.7. |
| 1.5     | July 6, 2026     | Povzetkar AI | POV-203: single US catalogue + lifecycle note in §3; migrated US-17.1–17.7 into new §3.6.       |
| 1.4     | March 30, 2026   | Povzetkar AI | Assigned unique IDs to all User Stories for Traceability Matrix hardening (POV-166). |
| 1.3     | January 17, 2026 | Povzetkar AI | Added requirements for topic management, granular orchestration, and error visibility (v0.7.0). |
| 1.2     | January 6, 2026  | Povzetkar AI | Marked self-healing requirement as implemented (v0.6.2).                                        |
| 1.1     | November 1, 2025 | Povzetkar AI | Synced with v0.6: marked stories as implemented, updated user roles, and refreshed terminology. |
| 1.0     | August 15, 2025  | Povzetkar AI | Initial version of the functional requirements based on the project specification.              |

## 1. Introduction

This document outlines the functional requirements for Project Povzetkar. It describes *what* the system will do from the perspective of the end-user and system administrators. It is based on the project vision of creating an automated intelligence platform.

## 2. User Roles

*   **User/Consumer:** The primary user who consumes the generated intelligence: manages their own topics and YouTube channels and reads the daily briefings in the web application (React SPA). In the future, this role may interact with a conversational interface.
*   **Administrator:** The user responsible for operating the system: approves and manages user accounts, monitors and restarts background jobs, and manages content items across all users.

## 3. User Stories

This section breaks down the system's functionality into user-centric stories. It is the **single User Story catalogue** for the project: new stories are minted only for user-facing functional changes, added here at task definition as `(Planned)`, and flipped to `(Implemented)` when the delivering task closes (checklist steps 2.1 / 2.14). The area-based ID scheme (`US-ING-xxx`, `US-UI-xxx`, …) continues for new stories; the legacy version-based scheme (`US-NN.x`) is retired but existing IDs remain valid referents. The full lifecycle policy is defined in the [Planning Protocol §2.1](../process/planning_protocol.md).

### 3.1. Data Ingestion & Processing

*   **US-ING-001** (Implemented) **As an Administrator, I want to** configure a list of content sources (e.g., YouTube channels) for a topic so that the system automatically monitors them for new content.
*   **US-ING-002** (Future) **As an Administrator, I want to** define keyword searches for YouTube so that the system finds relevant new videos on specific topics.
*   **US-ING-003** (Future) **As an Administrator, I want to** add a list of websites, blogs, and Reddit communities so the system can ingest articles and posts.
*   **US-ING-004** (Implemented) **As a System, I want to** automatically fetch new content from all configured sources on a periodic basis.
*   **US-ING-005** (Implemented) **As a System, I want to** generate a concise, accurate summary for every new piece of content.
*   **US-ING-006** (Implemented) **As a System, I want to** capture and persist detailed error codes and messages when processing fails, enabling targeted troubleshooting.
*   **US-ING-007** (Implemented) **As a System, I want to** automatically re-process content that failed due to transient errors so that the system can self-heal and ensure data completeness.
*   **US-ING-008** (Future) **As a System, I want to** generate vector embeddings for all content and summaries to enable semantic search.

### 3.2. Analysis & Reporting

*   **US-REP-001** (Future) **As a System, I want to** analyze the sentiment (positive, negative, neutral) of each piece of content.
*   **US-REP-002** (Future) **As a System, I want to** automatically categorize each summary based on a predefined taxonomy.
*   **US-REP-003** (Implemented) **As a User, I want to** view a daily aggregated report (briefing) that includes a meta-summary of all individual summaries from that period.
*   **US-REP-004** (Future) **As a User, I want to** see emerging trends and patterns identified from the content within a specific time frame.
*   **US-REP-005** (Future) **As a User, I want to** view an assessment of a content source's potential bias based on its historical content.

### 3.3. Conversational Interface (RAG)

*   **US-RAG-001** (Future) **As a User, I want to** ask natural language questions about the collected information.
*   **US-RAG-002** (Future) **As a User, I want to** receive answers that are synthesized from the underlying source documents.
*   **US-RAG-003** (Future) **As a User, I want to** see the sources (with links and timestamps) that were used to generate an answer.
*   **US-RAG-004** (Future) **As a User, I want to** have a continuous conversation, where the system remembers the context of my previous questions.

### 3.4. Content Creation

*   **US-CRT-001** (Future) **As a User, I want to** select a topic or a report and have the system generate a draft for a blog post.
*   **US-CRT-002** (Future) **As a User, I want to** generate a series of social media posts (e.g., for X/Twitter) based on a specific summary or trend.

### 3.5. User Interface & Management

*   **US-UI-001** (Implemented) **As a User, I want to** navigate a simple web interface to select a topic to view the latest briefings.
*   **US-UI-002** (Implemented) **As an Administrator, I want to** add, edit, and delete topics and manage their associated content sources through the web interface.
*   **US-UI-003** (Implemented) **As an Administrator, I want to** enable or disable specific topics to control which content is actively processed without deleting the topic configuration.
*   **US-UI-004** (Implemented) **As an Administrator, I want to** use a simple web interface to trigger system-level actions like content enrichment and orchestration.
*   **US-UI-005** (Implemented) **As an Administrator, I want to** granularly trigger specific phases of the orchestration process (e.g., only processing pending items or only generating briefings).
*   **US-UI-006** (Implemented) **As an Administrator, I want to** view specific error details for failed content items and manually trigger a retry for transcript fetching.
*   **US-UI-007** (Implemented) **As a** prospective **User, I want to** understand what Povzetkar does and what happens when I sign in, so that I can decide to request early access. *(Landing Page — Early Access)*

### 3.6. API & Integrations

*   **US-API-001** (Implemented) **As an** account owner, **I want to** have a personal read-only API key so that my automation agents can access my briefings, summaries and transcripts programmatically. *(Read-only API-Key Authentication — POV-205)*

### 3.7. Administration & Hardening (migrated from v0.17 planning)

*Origin note: US-17.1–17.7 were minted during v0.17.0 planning and previously lived only in the archived [v0.17.0-planning-process.md](../versions/v0.17.0/v0.17.0-planning-process.md). They were migrated here by POV-203 with their original IDs preserved, because UC-11…UC-15 reference them. The `US-NN.x` ID scheme is retired for new stories.*

*   **US-17.1** (Implemented) **As an Administrator, I want to** manage user accounts — edit roles and enable or disable accounts — so that system access remains controlled. *(Admin User Management — Roles & Statuses)*
*   **US-17.2** (Implemented) **As an Administrator, I want to** approve or reject newly registered user accounts so that only vetted users gain access. *(User Approval Workflow)*
*   **US-17.3** (Implemented) **As a** new **User, I want to** see a clear pending-approval status so that I understand why my access is limited. *(Pending Approval User Feedback)*
*   **US-17.4** (Implemented) **As an Administrator, I want to** view and restart background jobs with a full audit trail so that failed runs can be recovered accountably. *(Background Job Dashboard — Restart/Audit)*
*   **US-17.5** (Implemented) **As a System, I want to** fetch transcripts through an ordered multi-provider chain with automatic fallback so that a single provider failure does not lose content. *(Transcript Multi-Provider Resilience)*
*   **US-17.6** (Implemented) **As a User, I want to** have external links open in a new tab so that I keep my briefing context while reading sources. *(External Links Open in New Tab)*
*   **US-17.7** (Implemented) **As a User, I want to** be signed in again and returned to the page I was on when my session expires, instead of seeing a broken page. *(Session Expiry → Login Redirect; implemented as `/auth/login?redirect=<path>`, corrected in POV-223 like the roadmap in POV-220)*

### 3.8. User Experience (migrated from v0.16 planning)

*Origin note: US-16.2, US-16.3, US-16.4 and US-16.8 were minted during v0.16.0 planning and previously lived only as rows of the traceability table in the archived [v0.16.0-planning-process.md](../versions/v0.16.0/v0.16.0-planning-process.md), without story text. They were migrated here by POV-216 with their original IDs preserved, because UC-8…UC-10 reference them; the story text is reconstructed from that table, the v0.16 planning discussions and the use cases. The other US-16.x rows are not referenced by any use case and were not migrated.*

*   **US-16.2** (Implemented) **As a User, I want to** hear distinct sounds for success, error and information notifications so that I notice background results without watching the screen. *(Auditory Feedback — POV-171)*
*   **US-16.3** (Implemented) **As a User, I want to** share an individual summary through a public link so that others can read it without an account. *(Summary Sharing — POV-173)*
*   **US-16.4** (Implemented) **As a** new **User, I want** a dashboard guide that walks me through the first steps (topic, source, trigger, read, share) and remembers my progress so that I learn the system flow. *(Onboarding Checklist — POV-177; progress held per user on the server since POV-216)*
*   **US-16.8** (Deferred) **As a User, I want** job-completion toasts to carry an action button (e.g. *View Briefing*) so that I can jump straight to the result. *(Actionable Toasts — POV-182, deferred)*
