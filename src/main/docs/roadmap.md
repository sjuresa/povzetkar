# Project Povzetkar: Development Roadmap

This document outlines the concrete steps for implementing the features described in the `specification.md`. It breaks down the high-level phases into actionable tasks.

---

## v0.5: The Architectural Skeleton

**Note:** This roadmap is iterative. As development progresses, new tasks or requirements may emerge. These will be incorporated into the versions leading up to `v1.0` to ensure the best possible outcome for the first major release.

**Goal:** Establish the foundational Quarkus project structure and database connectivity. This version is a "deployable skeleton" that proves the core architecture works.

### Key Tasks:
1.  **Project Transformation:**
    *   Modify `pom.xml` to convert the existing Maven project into a Quarkus project.
    *   Add initial core dependencies: `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-config-yaml`, and testing libraries.
2.  **Environment Setup:**
    *   Create a `docker-compose.yml` with a PostgreSQL service, including the `pgvector` extension for future use.
    *   Configure `application.properties` to connect the Quarkus app to the Dockerized database.
3.  **Initial Data Model:**
    *   Create the first JPA entities based on `04_database_design.md`: `Author`, `ContentItem`, and `Summary`. These will be simple classes extending `PanacheEntity` without all relationships yet.
4.  **Initial Test:**
    *   Create a basic `@QuarkusTest` that verifies the application can start and connect to a Testcontainers-managed database.
5.  **Foundational Process Documentation:**
    *   Create a `06_development_process.md` document outlining onboarding, branching strategy, and contribution guidelines.

---

## v0.6: Database-Driven State Management

**Goal:** Completely replace the file-based `processing_state.properties` with database logic. The application will now track its state robustly in PostgreSQL.

### Key Tasks:
1.  **Refactor `ProcessingStateService`:**
    *   Rewrite the service to use Panache entities.
    *   **Read Operation:** Implement `findLastProcessedVideoIdBefore` by querying the `ContentItem` table.
    *   **Write Operation:** Implement `updateLastProcessedVideoForDate` by persisting new `ContentItem` entities.
    *   The dependency on `processing_state.properties` is removed.
2.  **Finalize Core Entities:**
    *   Flesh out the `Author` and `ContentItem` entities with all necessary columns, constraints, and relationships as defined in the database design.
3.  **Integration Testing:**
    *   Write `@QuarkusTest` cases for the new `ProcessingStateService` to ensure it correctly finds, creates, and updates records in the test database.

---

## v0.7: Database-Backed Summarization

**Goal:** Migrate the core summarization workflow to be fully database-centric. Summaries are now persisted in the database instead of `.md` files.

### Key Tasks:
1.  **Create CDI Services:**
    *   Refactor `VideoSummarizationService` and `VideoSummary` into `@ApplicationScoped` CDI beans.
    *   Inject dependencies (like the new `ProcessingStateService` and AI services) using `@Inject`.
2.  **Update Summarization Logic:**
    *   The main workflow will no longer write to files using `FileUtils`.
    *   After generating a summary, it will create a `Summary` entity, link it to the corresponding `ContentItem`, and persist it to the database within a `@Transactional` method.
3.  **Retire File-Based Logic:**
    *   Remove calls to `FileUtils.appendToFile` for summaries from the core workflow.
4.  **Comprehensive Testing:**
    *   Write a full integration test for `VideoSummarizationService`.
    *   This test will mock external API calls (YouTube, GenAI) but will use a real Testcontainer database to verify that `ContentItem` and `Summary` records are created correctly.

---

## v1.0: Database-Driven Reporting (Phase 1 Complete)

**Goal:** Re-implement the briefing generation and documentation updates, sourcing all data from the database. This brings the application to feature parity with the original version, but on the new, robust architecture.

### Key Tasks:
1.  **Create Reporting Service:**
    *   Refactor the logic from the `Povzetkar` class responsible for briefings and documentation into a new, dedicated CDI service (e.g., `ReportingService`).
2.  **Rewrite Data Sourcing:**
    *   **Briefing Generation:** Instead of reading from a summary file, the service will query the `summaries` table for a given date range to gather content for the daily briefing.
    *   **Documentation Update:** The logic for updating `README.md` and `updates.md` will also fetch its data (latest videos, past briefings) directly from the database.
3.  **Preserve Output Logic:**
    *   The final steps of writing the generated `.md` content to `tldrly/` and committing to Git will be preserved in this new service.
4.  **Testing:**
    *   Write unit and integration tests for the `ReportingService` to ensure it correctly queries the database and generates the expected Markdown content.
5.  **Initial Foundational Documentation:**
    *   **Architectural Decision Record (ADR):** Create the first ADR to document the decision to migrate to a Quarkus, PostgreSQL, and Docker-based architecture.
    *   **Deployment Guide:** Create an initial `DEPLOYMENT.md` that explains how to set up and run the `v1.0` application using `docker-compose`.

---

## v2.0: RAG & Conversational Interface (Phase 2)

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

5.  **Documentation:**
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

---

## v4.0: Content Creation & Refinement (Phase 4)

*(Details to be added as Phase 3 nears completion)*
