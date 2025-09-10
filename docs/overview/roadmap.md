# Project Povzetkar: Development Roadmap

**Version:** 1.3

## Revision History
| Version | Date              | Author       | Changes                                                                                                                                                            |
|---------|-------------------|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1.3     | September 9, 2025 | Povzetkar AI | Integrated Multi-Topic Support directly into v0.6-v0.8, removing the separate v0.9 MTS version.                                                                    |
| 1.2     | September 1, 2025 | Povzetkar AI | Major roadmap alignment: deferred multi-topic support to v1.0, created new v0.6 plan, and renumbered migration completion to v0.9.                                 |
| 1.1     | August 27, 2025   | Povzetkar AI | Refined future versions (v0.6+) to align with Strangler Fig pattern, and updated v0.7 to use a unified API with `COMPLETED`, `FAILED`, and `RETRY_LATER` statuses. |
| 1.0     | August 15, 2025   | Povzetkar AI | Initial version of the roadmap.                                                                                                                                    |

This document outlines the concrete steps for implementing the features described in the [`specification.md`](specification.md). It breaks down the high-level phases into actionable tasks.

---

## v0.5: The Architectural Skeleton

**Goal:** Establish the foundational Quarkus project structure, database, and core data model, including support for Topics. This version completes the **first concrete migration step** by delegating the file-based author/channel fetching from the legacy application to a new, database-backed API.

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

## v0.6: Full Channel Data & Multi-Topic Model

**Goal:** Fully centralize all channel/author data into the new database and prepare the data model for full multi-topic support.

### Key Tasks & Implementation Steps:
1.  **Extend Channel Data Model:** Add fields for channel details (title, description, thumbnail) to the `Channel` entity.
2.  **Extend Topic Data Model:** Add configuration fields to the `Topic` entity (e.g., `outputRepositoryPath`).
2.  **Implement Enrichment Service:** Create a service in Quarkus that uses the YouTube API to fetch and persist full details for all channels in the database.
3.  **Refactor Legacy App:** Modify the legacy application to get all channel details from a new Quarkus API, fully deprecating the old `AuthorService`.

---

## v0.7: Topic-Aware Summarization Workflow

**Goal:** Migrate the core business logic, ensuring it is topic-aware from the start.

### Key Tasks & Implementation Steps:
1.  **Extend Data Model:** Create `Summary` and `Briefing` JPA entities with appropriate relationships.
2.  **Implement Topic-Aware Orchestrator:** Create a `@Scheduled` service in Quarkus that **iterates over all active topics** and, for each one, fetches new videos, orchestrates summarization, and persists `Summary` and `Briefing` entities.
3.  **Decommission Legacy Services:** The core logic from `NewVideoFetcherService`, `VideoSummarizationService`, etc., is now fully migrated and can be removed from the legacy app.

---

## v0.8: Topic-Aware Presentation Layer (UI & Git)

**Goal:** Make the generated briefings accessible, ensuring that both the UI and Git integration respect the multi-topic architecture.

### Key Tasks & Implementation Steps:
1.  **API for UI:** Create a set of read-only REST endpoints for retrieving briefings and summaries.
2.  **Simple UI:** Implement a basic, server-side rendered web interface (e.g., using Quarkus Qute templates) to display a list of briefings and their content.
3.  **Topic-Aware Git Integration:** Implement the service for publishing briefings, ensuring it uses the topic-specific repository path from the `Topic` entity.

---

## v0.9: Production Hardening
-   **Monitoring & Logging:** Define and implement a production monitoring and logging strategy (e.g., Prometheus metrics, structured JSON logging).
-   **Security Hardening:** Conduct a basic security review and implement initial security measures.

---

## v1.0: RAG & Conversational Interface (Phase 2) & User Experience

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

4.  **Develop a Simple Frontend Client:**
    *   Create a basic web-based frontend (e.g., using plain HTML/JavaScript or a simple Angular app).
    *   The frontend will have a simple chat input field and a display area for the conversation.
    *   It will make asynchronous calls to the `/api/v1/chat` endpoint and display the returned answers.

5.  **User Experience Planning:**
    *   **ADR for Frontend:** Create an Architectural Decision Record for the choice of frontend framework.
    *   **Wireframes:** Develop basic wireframes for the user interface.
    *   **User Feedback:** Establish a process for gathering user feedback and creating initial user guides.

6.  **Documentation:**
    *   **API Documentation:** Create and maintain API documentation using the **OpenAPI (Swagger)** standard for the new `/api/v1/chat` endpoint.
    *   **Deployment Guide:** **Expand** the deployment guide to include the setup for the new frontend client and any additional services.
    *   **Architectural Decision Record (ADR):** Continue to log significant decisions, such as the choice of `LangChain4j` or specific RAG strategies.

---

## v1.5: Expansion & Advanced Analytics (Phase 3)

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

---

## v2.0: Content Creation & Refinement (Phase 4)

*(Details to be added as Phase 3 nears completion)*
