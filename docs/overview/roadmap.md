# Project Povzetkar: Development Roadmap

This document outlines the concrete steps for implementing the features described in the [`specification.md`](specification.md). It breaks down the high-level phases into actionable tasks.

## v0.5: The Architectural Skeleton

**Goal:** Establish the foundational Quarkus project structure, database, and core data model, including support for Topics. This version completes the **first concrete migration step** by delegating the file-based author/channel fetching from the legacy application to a new, database-backed API.
**Status:** ✅ Completed

### Key Tasks:
1.  **Project Transformation:**
    *   Modify `pom.xml` to convert the existing Maven project into a Quarkus project.
    *   Add initial core dependencies: `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-config-yaml`, and testing libraries.
2.  **Environment Setup:**
    *   Create a `docker-compose.yml` with a PostgreSQL service, including the `pgvector` extension for future use.
    *   Configure `application.properties` to connect the Quarkus app to the Dockerized database.
3.  **Initial Data Model & Migration:**
    *   Create JPA entities for `Topic`, `Channel`, and `ContentItem`.
    *   Create a one-time script to migrate existing topics and channels into the new database.
4.  **First "Strangling" Step:**
    *   Create a topic-aware API (`GET /api/v1/topics/{name}/channels`) in the new application.
    *   Refactor the legacy `AuthorService` to call this new API instead of reading from local files.

## v0.6: Core Logic Migration & Automation

**Goal:** Migrate the entire core workflow into the new Quarkus application, making it fully autonomous. This includes fetching new content, creating summaries, and generating briefings. This version replaces the legacy Git publishing with a foundational web UI for reviewing results and delivers **full multi-topic support**.
**Status:** ✅ Completed

### Key Tasks & Implementation Steps:
1.  **Extend Data Model:** Create `Summary` and `Briefing` JPA entities with appropriate relationships.
2.  **Full Content Source Migration:** Migrate the logic for fetching content source details into a Quarkus service.
3.  **Implement Scheduled Orchestrator:** Create a `@Scheduled` service in Quarkus that periodically:
    *   Fetches new content for all sources across all active users and topics.
    *   Orchestrates summarization using GenAI APIs.
4.  **Implement Simple UI:** Create a server-side rendered HTML interface for reviewing generated briefings.
5.  **Refactor for Robustness:** Decouple briefing generation from content fetching and add resilience to handle transient errors, based on findings during the migration.
6.  **Decommission Legacy App:** Completely remove the `legacy-app` module, finalizing the migration.

## v0.6.1: Foundational UI/UX Refinement

**Goal:** Substantially improve the usability and visual appeal of the server-side rendered UI. This release establishes a clean, responsive, and navigable baseline experience, including a powerful Admin UI for system monitoring and management.
**Status:** ✅ Completed

### Key Tasks & Implementation Steps:
1.  **UI Foundation:** Implemented a master layout, integrated Pico.css, and refactored all pages to use semantic HTML.
2.  **Improved UX:** Added breadcrumbs, flash messages for user feedback, and confirmation dialogs for actions.
3.  **Admin UI:** Created a new `/ui/admin` page with a tabbed interface to trigger system actions (enrichment, orchestration) and monitor history (pending, recent, failed items).
4.  **Scalability:** Replaced the content source dropdown with a scalable, searchable page.
5.  **Code Quality:** Performed significant refactoring to centralize common logic (auditing, UI utils) and enforce consistent dependency injection patterns.


## v0.6.2: Orchestration Hardening & Reliability

**Goal:** Increase the robustness and reliability of the core orchestration and briefing generation processes. This version focuses on bug fixing, handling edge cases, and proactively addressing potential performance bottlenecks.
**Status:** ✅ Completed

### Key Tasks & Implementation Steps:
1.  **Orchestration Robustness:** Implement logic to re-process items with `RETRY_LATER` status and handle transient errors during briefing generation.
2.  **Briefing Flexibility:** Decouple briefing generation from content fetching to allow creating briefings from existing summaries (e.g., for new topics).
3.  **GenAI Pool Analysis (Spike):** Conduct a load-test analysis of the `GenAiPool` to understand its behavior under concurrent load and prevent future quota issues.
4.  **Test Suite Improvement:** Implement a `BaseUITest` class to standardize test setup. *(Failed - Moved to Backlog for further analysis)*.
5.  **Enhanced User Features:** Add the ability to generate briefings for custom date ranges. *(Deferred)*.
6.  **API Stability:** Implement rate limiting for external transcription APIs. *(Deferred)*.


## v0.7: Operational Excellence & Technical Modernization

**Goal:** Mature the platform from a "working prototype" to a robust, observable, and manageable system. This version focuses on operational tools (Topic management, Logging), recovering from failure modes (PENDING state), and updating to **Quarkus 3.27.x LTS**.

**Status:** ✅ Completed

### Key Tasks & Implementation Steps:
1.  **Technical Update:** Update the project to Quarkus 3.27.x LTS.
2.  **Orchestration Resilience:** Handle `PENDING` items left from crashes and improve logging with metadata (duration, URL).
3.  **Topic Management:** Add ability to enable/disable topics and trigger orchestration for specific topics.
4.  **Admin UI Improvements:** Enhance history table with duration, dates, and direct links.

## v0.8: Standalone Application Deployment

**Goal:** Prepare and document the process for building and running the Quarkus application as a standalone executable (JAR or native binary), moving away from development mode (`mvn quarkus:dev`).
**Status:** ✅ Completed

### Key Tasks & Implementation Steps:
1.  **Build Configuration:** Configure the Maven `pom.xml` to produce a runnable JAR (`quarkus-app/quarkus-run.jar`).
2.  **Containerization:** Create a `Dockerfile` to package the application into a container image for consistent and isolated deployments.
3.  **Deployment Documentation:** Update `RUNBOOK.md` with clear instructions on how to build the container image and run the application using Docker or `docker-compose`.
4.  **Configuration Management:** Ensure the application can be fully configured via environment variables passed to the Docker container, which is crucial for production environments.

## v0.9: Operational Refinement & Admin Tools

**Goal:** Stabilize the application operations and enhance the administrator's toolkit before introducing multi-user complexity. This version focuses on visibility (GenAI Usage, Orchestration Preview), resilience (Circuit Breaker), and process documentation.
**Status:** ✅ Completed

### Key Tasks & Implementation Steps:
1.  **Admin Visibility:**
    *   Implement "Orchestration Preview" to check candidates before processing.
    *   Display GenAI usage per API key on the dashboard.
    *   Implement pagination for history tables.
    *   Fix timezone display issues in history.
2.  **System Resilience:** Implement a Circuit Breaker pattern to handle consecutive 503 errors from external APIs.
3.  **Process & Research:**
    *   Create a comprehensive Methodology document.
    *   **Spike:** Research MCP (Model Context Protocol) for agent context integration.

## v0.10: User Management, Security & Developer Experience

**Goal:** Transform the application into a secure, multi-tenant platform while simultaneously improving developer experience, documentation, and operational logic.
**Status:** ✅ Completed

### Key Tasks & Implementation Steps:
1.  **User Management & Security:** Implement Authentication and Authorization to isolate user data (Multi-tenancy).
2.  **Developer Experience:** Standardize project structure, align testing guides, and expose API documentation (OpenAPI/Swagger).
3.  **Operational Logic:** Implement content filtering based on minimum video duration to skip short content.
4.  **Observability & UI:** Add auditing fields, and expose Changelog and Roadmap within the application.
5.  **Google OIDC:** Integrate Google OIDC for seamless user authentication and linking.

## v0.11: API-First & Advanced Security

**Goal:** Decouple the frontend by establishing a comprehensive REST API and implementing fine-grained security (PBAC) to support advanced user interactions.
**Status:** ✅ Completed

### Key Tasks & Implementation Steps:
1.  **Frontend Specification:**
    -   Create `frontend-specification.md` defining screens, user flows, data requirements, and PBAC implications (visibility/actions).
2.  **Frontend API:**
    -   Design and implement REST endpoints (`/api/v1/topics`, etc.) based strictly on the frontend specification.
    -   Ensure responses are JSON-formatted.
3.  **Advanced Security (PBAC):**
    -   Implement **Permission-Based Access Control** (Step 2 of ADR-003).
    -   Define granular permissions (e.g., `TOPIC_CREATE`, `BRIEFING_VIEW`) and map them to roles.
    -   Secure the new API endpoints using these permissions.
4.  **Automated Security Scanning & SAST:**
    -   Integrate dependency vulnerability scanning (e.g., Trivy, OWASP dependency-check).
    -   Integrate Static Application Security Testing (SAST) for code quality and flaws (e.g., SonarQube, SpotBugs).
5.  **Documentation & Developer Experience:**
    -   Generate OpenAPI/Swagger specifications for the new API.
    -   Implement Test Data Builder pattern to streamline testing.

## v0.12: Frontend MVP & Monorepo Foundations

**Goal:** Establish a functional, deployable frontend integrated into a monorepo structure.
**Status:** ✅ Completed

### Key Tasks & Implementation Steps:
1.  **Frontend Architecture ADR:** Formally decide on the FE stack (Framework, CSS, State).
2.  **Monorepo Transition:** Restructure project into `/backend` and `/frontend` sub-modules.
3.  **Dashboard MVP:** First functional screen integrated with the v0.11 BFF.
4.  **SSE Real-time Job Tracking:** Live UI updates for background orchestration.
5.  **Audit Log Admin UI:** Administrative visibility for technical request logs.
6.  **Production Orchestration & Scanning:** Multi-stage Docker optimization and integration of frontend dependency scanning into CI/CD.

## v0.13: Input Mastery & UX Foundation

**Goal:** Transition from MVP to a "Professional Grade" user experience. This version pivots from the original "Production Hardening" goal to focus on **Input Resilience** and establishing a solid UI foundation, while keeping mission-critical infra tasks (Secrets, Persistence).
**Status:** ✅ Completed

### Key Tasks & Implementation Steps:
1.  **Topic & Source Management:**
    *   Implement full Topic CRUD and custom prompt template editing.
    *   Create a "Content Source Library" to browse and search all added sources.
    *   Implement "Source Item Review" to inspect fetched items (e.g., YouTube video lists) and their statuses.
2.  **User Profile & Platform Foundations:**
    *   Implement a User Profile page (`/me`) and basic Preferences (Theme, Language infra).
    *   Unified "Global Error & Notification" system (Toasts, Error Boundaries).
3.  **UI/UX Refinement:**
    *   Implement Skeleton loading states for all major views.
    *   Refine the Landing Page and Sidebar navigation for a "Premium" feel.
    *   Full Internationalization (i18n) infrastructure setup.
4.  **Backend & Infra Resilience:**
    *   Integrate Secrets Management.
    *   Implement Persistent Job Auditing storage for historical analysis.

## v0.14: Output Mastery & UX Refinement
**Status:** ✅ Completed

**Goal:** Empower users with advanced reporting tools and give administrators full control over system operations and user management.

### Key Tasks & Implementation Steps:
1.  **UX & Orchestration Refinement:**
    *   Implement Advanced Topic Management with search-based source selection and persistent visibility (POV-153).
    *   Replace direct "Run Now" with a configurable Trigger Dialog (POV-154).
    *   Implement manual Content Status overrides and visibility for skipped items (POV-155).
2.  **Product Presentation & Metadata:**
    *   Implement a "High-Converting" Premium Landing Page (POV-158).
    *   Expose App Version, Changelog, and Roadmap within the SPA.
3.  **Security & Technical Quality:**
    *   Resolve identified CVEs in Docker images (POV-156).
    *   Establishing codebase quality baselines: JaCoCo coverage reports and Database ERD synchronization (POV-157).

## v0.15: Advanced Archive & Operational Excellence

**Status:** ✅ Completed

**Goal:** Finalize the transition to a full-featured analytical platform with historical archives and enterprise-grade observability.

### Key Tasks & Implementation Steps:
1.  **Briefing Mastery & User UX:**
    *   Implement a searchable "Briefing Library" archive with advanced filtering and mobile-first responsive layouts (POV-161).
    *   Implement Markdown/HTML export and internal link sharing with UUID-based security (POV-162).
    *   Implement real-time YouTube source validation with debounced existence checks (POV-163).
2.  **Hardening & Operational Excellence:**
    *   Implement Prometheus metrics and Grafana dashboards for system-wide monitoring (POV-164).
    *   **Self-Cleaning Infra (Audits)**: Implement a background job to purge `JobExecutionAudit` records with 90-day retention (POV-165).
    *   **QA Health & Master Test Suite**: Centralize all regression scenarios into a permanent suite and migrate remaining Integration Tests to the `@TestTransaction` pattern (POV-166).

## v0.16: Readability & User Experience

**Goal:** Transform the application into a "Professional Grade" SaaS by establishing perfect theme-awareness, sensory feedback, and zero-friction onboarding.
**Status:** ✅ Completed

### Key Tasks & Implementation Steps:
1.  **Sensory & Content Polish:**
    *   **Prompt Cleanup**: Remove redundant headings and source lists from AI output (POV-170).
    *   **Auditory Feedback**: Implement a full sound framework for Success, Error, and Info notifications (POV-171).
2.  **User Value & Portability:**
    *   **Summary Sharing**: Enable individual summary sharing via vanity short URLs (`/s/{uuid}`) following the briefing pattern (POV-172).
    *   **Actionable UX**: Add direct "View Job" actions to toast notifications (POV-177).
3.  **Onboarding & Discovery:**
    *   **Interactive Onboarding**: Build a 5-step gamified checklist with LocalStorage persistence and "Osebni asistent" tone (POV-173).
4.  **Theme Modernization (Zinc Palette):**
    *   **Semantic Migration**: Systematically replace all hardcoded Tailwind dark classes with semantic variables (POV-174/175).
    *   **Fluid Motion**: Implement smooth 300ms CSS transitions for all theme toggles (POV-176).

## v0.17: Administrative Control & User Onboarding

**Goal:** Establish operational governance via Admin User Management, an explicit account activation state machine, and live background job control. Harden the transcript ingestion pipeline against IP rate-limiting and polish two long-standing UX friction points (external links and session expiry).
**Status:** ✅ Completed

### Key Tasks & Implementation Steps:
1.  **Administrative Command Center:**
    *   **Admin User Management UI** (POV-184): roles, account statuses, and permissions; last-admin server-side guard.
    *   **User Approval Workflow** (POV-185): explicit `PENDING_APPROVAL` state machine ([ADR-0008](../adr/0008-account-activation-state-machine.md)) with admin-driven approval. No email integration (SMTP deferred to v0.18.0+).
    *   **Background Job Dashboard** (POV-188): restart and audit trail for `FAILED` jobs; replaces the placeholder `AdminJobsPage`. Cancel deferred to v0.18.0.
2.  **Resilience & Polish:**
    *   **Transcript Multi-Provider Pilot** (POV-189): `TranscriptProvider` abstraction ([ADR-0009](../adr/0009-multi-provider-transcript-strategy.md)) + Supadata pilot integration + randomized jitter delay on legacy YouTube path.
    *   **External Links Open in New Tab** (POV-186): `rehype-external-links` plugin in all ReactMarkdown call-sites.
    *   **Session Expiry Handling** (POV-187): 401 interceptor redirects to landing page (`/`) with `notice=session_expired`; `returnTo` preserved in `sessionStorage`; ErrorPage reserved for true fatal errors.

## v0.18: Transcript Ingestion Resilience & Scheduler Correctness

**Goal:** Harden the pipeline that feeds content into summarization — make transcript ingestion survive a hostile, rate-limiting upstream, and make the scheduler that drives it actually run reliably. This version was scoped task-first — the theme emerged from following the transcript-pipeline work rather than from up-front planning.
**Status:** ✅ Completed

### Key Tasks & Implementation Steps:
1.  **Transcript Ingestion Resilience:**
    *   **Transcript Proxy Service** (POV-193): standalone, stateless FastAPI proxy fetching a single YouTube transcript per request, deployable on a non-blocked host.
    *   **`ProxyTranscriptProvider`** (POV-194): Quarkus integration as the now-primary `TranscriptProvider`, with caller-side skip-first jitter throttle.
    *   **Proxy Relocation to Synology DS220j** (POV-196): moved from Railway (soft-throttled shared egress IP) to a self-hosted box reached over Tailscale; Railway kept as passive fallback.
    *   **Persist Transcript Before Gemini Call** (POV-198): best-effort pre-write so 503/429 retries no longer re-fetch from scratch.
2.  **Scheduler Correctness:**
    *   **Scheduled Orchestration Fix & Periodic Cron** (POV-197): fixed orchestration that had never run on a schedule (`BlockingOperationNotAllowedException`); consolidated into a single hourly periodic cron.
3.  **Cost & Accounting:**
    *   **RPD-Accurate GenAI Counting** (POV-195): daily usage now reflects true RPD consumption; disabled the Google SDK's hidden 5× retry; surfaced an "Errors" column.
4.  **Security & Access:**
    *   **Fix Trivy Security Vulnerabilities** (POV-199): cleared all blocking CVEs (Netty, form-data, Go builder, OpenTelemetry).
    *   **Fix Demo User Access** (POV-192): `SOURCE_VIEW` added to `DEMO_PERMISSIONS`; demo write controls hidden; demo topic seed corrected (V35).
5.  **Process:**
    *   **Task-First Planning** (POV-191): evolved the development methodology from version-gated to task-first delivery.

## v0.19: Hardening & Operational Excellence

**Goal:** Integrate advanced security auditing into the release cycle and complete observability/operational maturity work. *(Scope is a forecast — under the task-first model the actual scope and version label are confirmed retrospectively when the work is done.)*
**Status:** 🚧 In Progress

### Key Tasks & Implementation Steps:
1.  **Security Hardening:**
    *   **DAST Integration**: Integrate Automated Dynamic Security Testing (DAST) with OWASP ZAP into the release cycle.
2.  **Observability:**
    *   **Grafana Alerting**: Implement complex alerting thresholds for system health (transcript provider quota, job failure rates, latency SLOs).
3.  **Search & Data:**
    *   **Advanced Retention**: Implement specialized pg_trgm/Lucene full-text search as data volume justifies.
4.  **Transcript Providers (Expansion):**
    *   Add additional providers (ytscribe, transcriptapi) to the v0.17.0 abstraction.
5.  **Email Infrastructure (Candidate):**
    *   SMTP foundation (dev server like Mailpit, production provider, template engine) — unblocks user approval notifications and other transactional emails.


## v1.0: RAG & Conversational Interface

**Goal:** Deliver the core "interactive report" feature (RAG pipeline) and conclude the **Production Hardening** series with automated database backups and recovery procedures.

### Key Tasks & Implementation Steps

1.  **Evolve the Persistence Layer:**
    *   Update the `Summary` JPA entity to include the `embedding` field (`vector(1536)`).
    *   Implement the `Category` and `Sentiment` entities and their relationships with `Summary`.
    *   Configure `pgvector` indexing on the `embedding` column for efficient similarity searches.

2.  **Enhance the Processing & Enrichment Service:**
    *   Integrate `LangChain4j` for embedding generation.
    *   After generating `summary_text`, the service will use the AI Core to create a vector embedding from this text.
    *   The service will then persist the complete `Summary` object, including the embedding, to the database.
    *   Add steps to perform categorization and sentiment analysis using the AI Core and persist these relationships.

3.  **Build the RAG & Conversational API:**
    *   Create a new Quarkus REST endpoint (e.g., `/api/v1/chat`).
    *   This endpoint will accept a user's question as a JSON payload.
    *   The API method will orchestrate the RAG flow using `LangChain4j`:
        1.  Generate an embedding for the user's question.
        2.  Query the `summaries` table using a native query with `pgvector` functions (e.g., `<=>` for cosine distance) to find the top N most relevant summaries.
        3.  Inject the retrieved summaries as context into a new prompt for the LLM.
        4.  Send the augmented prompt to the LLM and return its final, context-aware answer to the client.

4.  **User Experience Planning for Advanced UI:**
    *   **ADR for Frontend:** Create an Architectural Decision Record for the choice of frontend framework.
    *   **Wireframes:** Develop basic wireframes for the user interface.
    *   **User Feedback:** Establish a process for gathering user feedback and creating initial user guides.

5.  **Documentation:**
    *   **API Documentation:** Create and maintain API documentation using the **OpenAPI (Swagger)** standard for the new `/api/v1/chat` endpoint.
    *   **Deployment Guide:** **Expand** the deployment guide to include the setup for the new frontend client and any additional services.
    *   **Architectural Decision Record (ADR):** Continue to log significant decisions, such as the choice of `LangChain4j` or specific RAG strategies.

## v1.1: Expansion & Advanced Analytics

**Goal:** Broaden the platform's intelligence-gathering capabilities by adding new data sources and analytical features.

### Key Tasks & Implementation Steps

1.  **Implement New Ingestion Services:**
    *   Create a `WebScraperService` that can extract article text from a given list of blog/news URLs.
    *   Develop a service for at least one social media platform (e.g., X/Twitter or Reddit) to fetch relevant posts based on keywords or user accounts.
    *   Ensure all new ingestion services are decoupled and publish their findings to the message queue for the central processing service to consume.

2.  **Develop Trend & Pattern Detection:**
    *   Implement the `Trend` and `TrendSummaries` JPA entities.
    *   Create a scheduled background task in Quarkus (using `@Scheduled`).
    *   This task will periodically (e.g., daily) query recent summaries, perform analysis (e.g., keyword frequency, topic modeling) to identify emerging trends, and populate the `trends` table.

3.  **Implement Author Bias Assessment:**
    *   Implement the `AuthorAssessment` JPA entity.
    *   Create another scheduled task or integrate it into the trend detection task.
    *   This logic will analyze the sentiment and topics of a single author's content over time to generate a `bias_score` and a textual `assessment_details`, storing the results in the `author_assessments` table.

4.  **Production Quality Testing:**
    *   **Performance Testing:** Conduct initial load tests on the RAG API to identify performance bottlenecks.
    *   **E2E UI Test Automation:** Introduce a framework (e.g., Playwright) for end-to-end testing of critical user flows, following the architectural decision made in v1.0.

## v1.2: Content Creation & Refinement

*(Details to be added as Phase 3 nears completion)*

## Revision History
| Version | Date               | Author       | Changes                                                                                                                                                                  |
|---------|--------------------|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 3.9     | 2026-07-03         | Povzetkar AI | Marked v0.19 as In Progress at version initialisation (POV-201).                                                                                                         |
| 3.8     | 2026-06-26         | Povzetkar AI | Marked v0.18.0 as ✅ Completed and rewrote its scope to reflect actual task-first delivery (Transcript Ingestion Resilience & Scheduler Correctness, POV-191…200). The originally-planned hardening scope (DAST, Grafana alerting, full-text search, additional providers, SMTP) was carried forward to `v0.19: Hardening & Operational Excellence` as a forecast — under the task-first model the actual scope and version label are confirmed retrospectively. |
| 3.7     | 2026-05-24         | Povzetkar AI | Marked v0.17.0 as ✅ Completed.                                                                                                                                                                                   |
| 3.6     | 2026-05-20         | Povzetkar AI | Refined v0.17.0 scope to "Administrative Control & User Onboarding". Inserted v0.18.0 for "Hardening & Operational Excellence" (DAST, Grafana alerts, full-text search, additional transcript providers, SMTP). |
| 3.5     | April 13, 2026     | Antigravity AI | Marked v0.16.0 as completed and finalized version documentation.                                                                          |
| 3.4     | April 1, 2026      | Povzetkar AI | Strategically pivoted v0.16.0 to "User Experience Focus" and restored v0.17.0 for "Administrative Control & Hardening". |
| 3.3     | March 30, 2026     | Povzetkar AI | Marked v0.15 as completed and finalized release notes.                                                                                    |
| 3.2     | March 28, 2026     | Povzetkar AI | Aligned v0.15.0/v0.16.0 scope, moved postponed items (Admin UI, DAST), and clarified job cleanup tasks.                                                                  |
| 3.1     | March 28, 2026     | Povzetkar AI | Marked v0.14.0 as completed (POV-159).                                                                                                                                   |
| 3.0     | March 25, 2026     | Povzetkar AI | Finalized v0.14.0 planning and task breakdown (POV-152). Shifted deferred features and backlog polish (Animations, i18n, Cleanup) to v0.15.                              |
| 2.9     | March 24, 2026     | Povzetkar AI | Marked v0.13.0 as completed and finalized release documentation.                                                                                                         |
| 2.8     | March 19, 2026     | Povzetkar AI | Split v0.13 into v0.13.0 (Input/UX) and v0.14.0 (Output/Admin).                                                                                                          |
| 2.7     | March 18, 2026     | Povzetkar AI | Marked v0.12.0 as completed and solidified v0.13.0 scope.                                                                                                                |
| 2.6     | March 12, 2026     | Povzetkar AI | Marked v0.11.0 as completed.                                                                                                                                             |
| 2.5     | March 5, 2026      | Povzetkar AI | Added Phase 1 Frontend Foundations to v0.11 and deferred remaining Hardening features to v0.13.                                                                          |
| 2.4     | February 26, 2026  | Povzetkar AI | Marked v0.11 as In Progress and refined scope (Frontend Spec, SAST).                                                                                                     |
| 2.3     | February 20, 2026  | Povzetkar AI | Marked v0.10 as completed.                                                                                                                                               |
| 2.2     | February 19, 2026  | Povzetkar AI | Inserted v0.11 for API & PBAC. Shifted Hardening to v0.12 and Advanced Content to v0.13.                                                                                 |
| 2.1     | February 13, 2026  | Povzetkar AI | Marked v0.10 as In Progress and expanded scope (DevEx, Ops).                                                                                                             |
| 2.0     | February 5, 2026   | Povzetkar AI | Marked v0.9 as complete.                                                                                                                                                 |
| 1.9     | January 21, 2026   | Povzetkar AI | Inserted v0.9 "Operational Refinement" based on user request. Shifted User Management to v0.10 and Advanced Content to v0.11.                                            |
| 1.8     | January 20, 2026   | Povzetkar AI | Marked v0.8 as complete.                                                                                                                                                 |
| 1.7     | January 13, 2026   | Povzetkar AI | Marked v0.7 as complete.                                                                                                                                                 |
| 1.6     | January 7, 2026    | Povzetkar AI | Redefined v0.7 scope to "Operational Excellence & Technical Modernization" based on planning. Shifted subsequent versions. Created new v0.9 for Advanced GenAI features. |
| 1.5     | January 4, 2026    | Povzetkar AI | Marked v0.6.2 as complete and updated task statuses.                                                                                                                     |
| 1.4     | November 1, 2025   | Povzetkar AI | Marked v0.6 as complete and inserted v0.6.1 for UI refinement. Restructured future versions to prioritize User Management and Production Hardening.                      |
| 1.3     | September 10, 2025 | Povzetkar AI | Major roadmap acceleration: v0.6 now includes full core logic migration. Redefined v0.7 for standalone deployment and re-aligned subsequent versions.                    |
| 1.2     | September 1, 2025  | Povzetkar AI | Major roadmap alignment: deferred multi-topic support to v1.0, created new v0.6 plan, and renumbered migration completion to v0.9.                                       |
| 1.1     | August 27, 2025    | Povzetkar AI | Refined future versions (v0.6+) to align with Strangler Fig pattern, and updated v0.7 to use a unified API with `COMPLETED`, `FAILED`, and `RETRY_LATER` statuses.       |
| 1.0     | August 15, 2025    | Povzetkar AI | Initial version of the roadmap.                                                                                                                                          |
