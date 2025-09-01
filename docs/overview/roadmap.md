# Project Povzetkar: Development Roadmap

**Version:** 1.2
**Date:** September 1, 2025

## Revision History
| Version | Date            | Author       | Changes                                                                                                                                                           |
|---------|-----------------|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1.2     | September 1, 2025 | Povzetkar AI | Major roadmap alignment: deferred multi-topic support to v1.0, created new v0.6 plan, and renumbered migration completion to v0.9.                                  |
| 1.1     | August 27, 2025 | Povzetkar AI | Refined future versions (v0.6+) to align with Strangler Fig pattern, and updated v0.7 to use a unified API with `COMPLETED`, `FAILED`, and `RETRY_LATER` statuses. |
| 1.0     | August 15, 2025 | Povzetkar AI | Initial version of the roadmap.                                                                       |

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
    *   Create JPA entities for `Topic`, `Channel`, `Author`, and `ContentItem`.
    *   Create a one-time script to migrate existing topics and channels into the new database.
4.  **First "Strangling" Step:**
    *   Create a topic-aware API (`GET /api/v1/topics/{name}/channels`) in the new application.
    *   Refactor the legacy `AuthorService` to call this new API instead of reading from local files.

---

## v0.6: Database-Driven State Management

**Goal:** Completely replace the file-based `processing_state.properties` with database logic. The application will now track its state robustly in PostgreSQL.

### Key Tasks & Implementation Steps:
1.  **Implement Video Ingestion Service:**
    *   Create a service in the Quarkus app that replicates the logic of `fetchAllNewVideos`.
    *   This service will:
        1.  Fetch all monitored channels from the `channels` table (created in v0.5).
        2.  For each channel, query its own database to find the last processed video timestamp.
        3.  Call the YouTube API to get all new videos since that timestamp.
        4.  Persist these new videos as `ContentItem` records with a `PENDING` status.
        5.  Return a consolidated and chronologically sorted list of new `ContentItem` DTOs.
    *   Expose this logic via a single endpoint: `POST /api/v1/ingestion/fetch-new-videos`.
2.  **Refactor Old Application to Delegate Video Fetching:**
    *   Modify the `VideoSummarizationService.summarizeFromAuthors` method in the old application.
    *   The call to the internal `fetchAllNewVideos` method will be replaced with a REST client call to the new `POST /api/v1/ingestion/fetch-new-videos` endpoint.
    *   The rest of the method's logic (looping through the returned list, summarizing, etc.) remains unchanged for now.
3.  **Testing:**
    *   Write a `@QuarkusTest` integration test for the new ingestion endpoint. This test must mock the external YouTube API but use a real Testcontainer DB to verify the entire internal logic and the API response.

---

## v0.7: Database-Backed Summarization

**Goal:** Migrate the core summarization workflow to be fully database-centric. Summaries are now persisted in the database instead of `.md` files.

### Key Tasks & Implementation Steps:
1.  **Implement Unified Processing Result API:**
    *   Create the `Summary` JPA entity (as originally planned for this stage).
    *   Create a new, unified endpoint: `PUT /api/v1/content-items/{id}/processing-result`.
    *   This endpoint will accept a DTO (`ProcessingResultRequest`) containing two fields:
        - `status`: An enum (`COMPLETED`, `FAILED`, or `RETRY_LATER`).
        - `details`: A string containing either the full summary text (on success) or the error message (on failure).
    *   The service logic will:
        - If `status` is `COMPLETED`, create a new `Summary` record, link it to the `ContentItem`, and update the `ContentItem`'s status to `COMPLETED`.
        - If `status` is `FAILED`, log the error from `details` and update the `ContentItem`'s status to `FAILED`.
        - If `status` is `RETRY_LATER`, log the reason from `details` and update the `ContentItem`'s status to `RETRY_LATER`.
2.  **Refactor Old Application to Use the New Result API:**
    *   In the `VideoSummarizationService` loop, modify the logic to call the new unified endpoint:
        - On successful summary generation, call the `PUT` endpoint with `status: COMPLETED` and the summary text.
        - If a transcript is not available (`getContent` returns null), call the `PUT` endpoint with `status: RETRY_LATER` and a descriptive message.
        - On a non-recoverable AI error, call the `PUT` endpoint with `status: FAILED` and the error message.
    *   The logic for writing to `summaries_...md` and `failed_videos.log` will be removed.
3.  **Testing:**
    *   Write `@QuarkusTest` integration tests for the new unified endpoint.
    *   One test must verify the "success" path (a `Summary` is created, `ContentItem` is `COMPLETED`).
    *   Another test must verify the "failure" path (`ContentItem` is `FAILED`, no `Summary` is created).
    *   A third test must verify the "retry" path (`ContentItem` is `RETRY_LATER`, no `Summary` is created).

---

## v0.8: AI Logic Migration

**Goal:** Migrate the AI-driven summary generation logic into the new Quarkus application, making it fully self-contained.

### Key Tasks & Implementation Steps:
1.  **Implement Self-Contained Summarization Service:**
    *   Create a `SummarizationService` in the Quarkus app that encapsulates transcript fetching and GenAI calls.
    *   The service must include robust, internal error handling, updating the `ContentItem` status to `COMPLETED` or `FAILED` directly in the database.
    *   Expose this logic via a new endpoint: `POST /api/v1/summarization/trigger/{contentItemId}`.
2.  **Refactor Old Application to Delegate Summarization:**
    *   Modify the summarization loop in the old application's `VideoSummarizationService`.
    *   The direct call to the AI service (`videoSummary.generateContentWithRetry`) will be replaced with a REST client call to the new `POST /api/v1/summarization/trigger/{contentItemId}` endpoint for each video.
    *   The `google-genai` and `youtube-transcript-api` dependencies will be removed from the old application's `pom.xml`.
3.  **Testing:**
    *   Write `@QuarkusTest` integration tests for the new `SummarizationService`. The tests must verify that the `ContentItem` status is correctly updated on success and various mocked failure scenarios.

---

## v0.9: Database-Driven Reporting & Automation (Phase 1 Complete)

**Goal:** Re-implement the briefing generation and documentation updates, sourcing all data from the database. This brings the application to feature parity with the original version, but on the new, robust architecture.

### Key Tasks & Implementation Steps:
1.  **Implement Reporting Service:**
    *   Create a `ReportingService` in the Quarkus app. It will query the database for all necessary data (summaries, content items) and perform the Git operations to update documentation.
    *   Expose this logic via a new endpoint: `POST /api/v1/reports/generate`.
2.  **Establish New Trigger Mechanism:**
    *   Create a Quarkus Scheduler (`@Scheduled`) method that orchestrates the entire workflow by calling the `POST /api/v1/ingestion/fetch-new-videos` and `POST /api/v1/reports/generate` endpoints in the correct sequence.
3.  **Decommission Old Application:**
    *   The entire old application's source code (`src/main/java/si/jumis/...`) and its Maven module will be deleted.
    *   The project will now be a single-module, self-contained Quarkus application.
4.  **Documentation:**
    *   Update [`RUNBOOK.md`](../RUNBOOK.md) to reflect the new, fully automated execution process and remove references to the old application.

---
## v1.0: Multi-Topic Support

**Goal:** Introduce support for multiple topics or categories (e.g., AI, Programming, Politics). Each topic will have its own configurable list of channels and a dedicated output Git repository.

### Key Tasks & Implementation Steps: 
1.  **Implement Topic Configuration:** Create a mechanism to configure topics and their channels from `application.properties` and persist them.
2.  **Refactor Core Workflow:** Modify the main scheduled job to iterate over each topic and execute the ingestion/reporting workflow in a parameterized way for each one, using topic-specific output repositories.

## v1.5: Production Hardening
-   **Monitoring & Logging:** Define and implement a production monitoring and logging strategy (e.g., Prometheus metrics, structured JSON logging).
-   **Security Hardening:** Conduct a basic security review and implement initial security measures.

## v2.0: RAG & Conversational Interface (Phase 2) & User Experience

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

## v3.0: Expansion & Advanced Analytics (Phase 3)

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

## v4.0: Content Creation & Refinement (Phase 4)

*(Details to be added as Phase 3 nears completion)*
