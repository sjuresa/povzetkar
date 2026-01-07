# Project Povzetkar: Development Roadmap

**Version:** 1.6

## Revision History
| Version | Date               | Author       | Changes                                                                                                                                                                  |
|---------|--------------------|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1.6     | January 7, 2026    | Povzetkar AI | Redefined v0.7 scope to "Operational Excellence & Technical Modernization" based on planning. Shifted subsequent versions. Created new v0.9 for Advanced GenAI features. |
| 1.5     | January 4, 2026    | Povzetkar AI | Marked v0.6.2 as complete and updated task statuses.                                                                                                                     |
| 1.4     | November 1, 2025   | Povzetkar AI | Marked v0.6 as complete and inserted v0.6.1 for UI refinement. Restructured future versions to prioritize User Management and Production Hardening.                      |
| 1.3     | September 10, 2025 | Povzetkar AI | Major roadmap acceleration: v0.6 now includes full core logic migration. Redefined v0.7 for standalone deployment and re-aligned subsequent versions.                    |
| 1.2     | September 1, 2025  | Povzetkar AI | Major roadmap alignment: deferred multi-topic support to v1.0, created new v0.6 plan, and renumbered migration completion to v0.9.                                       |
| 1.1     | August 27, 2025    | Povzetkar AI | Refined future versions (v0.6+) to align with Strangler Fig pattern, and updated v0.7 to use a unified API with `COMPLETED`, `FAILED`, and `RETRY_LATER` statuses.       |
| 1.0     | August 15, 2025    | Povzetkar AI | Initial version of the roadmap.                                                                                                                                          |

This document outlines the concrete steps for implementing the features described in the [`specification.md`](specification.md). It breaks down the high-level phases into actionable tasks.

---

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

---

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

---

## v0.6.1: Foundational UI/UX Refinement

**Goal:** Substantially improve the usability and visual appeal of the server-side rendered UI. This release establishes a clean, responsive, and navigable baseline experience, including a powerful Admin UI for system monitoring and management.
**Status:** ✅ Completed

### Key Tasks & Implementation Steps:
1.  **UI Foundation:** Implemented a master layout, integrated Pico.css, and refactored all pages to use semantic HTML.
2.  **Improved UX:** Added breadcrumbs, flash messages for user feedback, and confirmation dialogs for actions.
3.  **Admin UI:** Created a new `/ui/admin` page with a tabbed interface to trigger system actions (enrichment, orchestration) and monitor history (pending, recent, failed items).
4.  **Scalability:** Replaced the content source dropdown with a scalable, searchable page.
5.  **Code Quality:** Performed significant refactoring to centralize common logic (auditing, UI utils) and enforce consistent dependency injection patterns.


---

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


---

## v0.7: Operational Excellence & Technical Modernization

**Goal:** Mature the platform from a "working prototype" to a robust, observable, and manageable system. This version focuses on operational tools (Topic management, Logging), recovering from failure modes (PENDING state), and updating to **Quarkus 3.27.x LTS**.

### Key Tasks & Implementation Steps:
1.  **Technical Update:** Update the project to Quarkus 3.27.x LTS.
2.  **Orchestration Resilience:** Handle `PENDING` items left from crashes and improve logging with metadata (duration, URL).
3.  **Topic Management:** Add ability to enable/disable topics and trigger orchestration for specific topics.
4.  **Admin UI Improvements:** Enhance history table with duration, dates, and direct links.

---

## v0.8: Standalone Application Deployment

**Goal:** Prepare and document the process for building and running the Quarkus application as a standalone executable (JAR or native binary), moving away from development mode (`mvn quarkus:dev`).

### Key Tasks & Implementation Steps:
1.  **Build Configuration:** Configure the Maven `pom.xml` to produce a runnable JAR (`quarkus-app/quarkus-run.jar`).
2.  **Containerization:** Create a `Dockerfile` to package the application into a container image for consistent and isolated deployments.
3.  **Deployment Documentation:** Update `RUNBOOK.md` with clear instructions on how to build the container image and run the application using Docker or `docker-compose`.
4.  **Configuration Management:** Ensure the application can be fully configured via environment variables passed to the Docker container, which is crucial for production environments.

---

## v0.9: Advanced Content Capabilities

**Goal:** Implement advanced AI features that were deferred from v0.7 to prioritize deployment. This includes sophisticated rate limiting and handling large data volumes for briefings.

### Key Tasks & Implementation Steps:
1.  **Advanced Rate Limiting:** Implement Daily Quotas and TPM limits for GenAI.
2.  **Recursive Briefings:** Implement "Map-Reduce" summarization for long time periods to handle context window limits.
3.  **Spikes:** Execute research tasks for Transactional Testing patterns and Agent Context (MCP).

---

## v0.10: User Management & Security

**Goal:** Implement a robust user management, authentication, and authorization system to secure the application and its APIs, preparing it for multi-user production environments.

### Key Tasks & Implementation Steps:
1.  **Authentication:** Implement user registration and login endpoints using a secure mechanism (e.g., JWT tokens).
2.  **Authorization:** Integrate Quarkus Security to protect all relevant API endpoints. Define and enforce user roles (e.g., `USER`, `ADMIN`).
3.  **Secure UI:** Modify the web interface to be multi-tenant, ensuring users can only see their own topics and briefings after logging in.
4.  **User Management:** Provide basic administrative capabilities for managing users.

---

## v0.11: Production Hardening

**Goal:** Stabilize the application and prepare it for a reliable and secure production deployment.

### Key Tasks & Implementation Steps:
1.  **Monitoring & Alerting:** Implement Prometheus metrics and Grafana dashboards. Configure Alertmanager for critical failure notifications.
2.  **Secrets Management:** Integrate a secrets management solution (e.g., HashiCorp Vault) to handle API keys and database credentials securely, removing them from `.env` files in production.
3.  **Backup & Recovery:** Establish and document an automated database backup and recovery process.
4.  **Dependency Scanning:** Integrate an automated dependency vulnerability scanner (e.g., Snyk, Dependabot) into the CI pipeline.

---

## v1.0: RAG & Conversational Interface

**Goal:** Deliver the core "interactive report" feature by implementing the RAG pipeline and a user-facing API.

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

---

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

---

## v1.2: Content Creation & Refinement

*(Details to be added as Phase 3 nears completion)*
