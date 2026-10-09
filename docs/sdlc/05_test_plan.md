# Test Plan - Project Povzetkar

## Revision History
| Version | Date             | Author       | Changes                                                                       |
|---------|------------------|--------------|-------------------------------------------------------------------------------|
| 1.4     | October 9, 2026  | Povzetkar AI | POV-223: tools aligned with the real test setup (test DB, Playwright); note on the outdated vision parts. |
| 1.3     | September 27, 2026 | Povzetkar AI | §3.2 strategy points to QA rule 3 (test scope) instead of the retired no-mocking rule (POV-216). |
| 1.2     | January 17, 2026 | Povzetkar AI | Added dependency management strategy for test libraries (BOM).                |
| 1.1     | January 6, 2026  | Povzetkar AI | Refined integration testing strategy to discourage internal mocking.          |
| 1.0     | August 15, 2025  | Povzetkar AI | Initial version.                                                              |

## 1. Introduction

This document outlines the testing strategy for the Povzetkar project to ensure the delivery of a high-quality, reliable, and robust application.

> [!NOTE]
> Parts of this plan date from the original vision (RAG chat, message queue). In practice: backend tests run with
> `@QuarkusTest` against a PostgreSQL test database (the `povzetkar-db-test` container locally, a `pgvector` service
> container in CI); the frontend uses Vitest and Playwright; the regression scenarios live in the master test
> specification ([`test_specification.md`](../analysis/test_specification.md)). Rewriting §4 against the current
> scenarios is in the backlog.

## 2. Scope of Testing

Testing will cover all major components of the system:
- Data Ingestion pipeline
- Content Processing and AI Enrichment
- Persistence Layer
- Conversational API (RAG)
- User Interface (when developed)

## 3. Testing Levels

### 3.1. Unit Testing
- **Objective:** To verify the correctness of individual classes and methods in isolation.
- **Tools:** JUnit 5, Mockito.
- **Dependency Management:** Test libraries (JUnit, Mockito, etc.) must have their versions managed by the Quarkus BOM to prevent classpath conflicts (e.g., `NoSuchMethodError`). Explicit version overrides in `pom.xml` should be avoided unless strictly necessary.
- **Scope:** All business logic in services, utility classes, and data models. External dependencies (like database connections or external APIs) will be mocked.
- **Example:** A test for `VideoSummarizationService` would mock `YouTubeDataService` and `ProcessingStateService` to verify that the orchestration logic is correct.

### 3.2. Integration Testing
- **Objective:** To verify the interaction between different components of the system.
- **Tools:** Quarkus Test Framework (`@QuarkusTest`) against a PostgreSQL test database (no Testcontainers, no message broker).
- **Strategy:** What runs against the database and what is mocked is defined by QA rule 3 (test scope) in [`quality-mandates.md`](../protocols/quality-mandates.md).
- **Scope:**
    - **API Tests:** Testing REST endpoints by making real HTTP requests to the running application and asserting the responses.
    - **Persistence Tests:** Verifying that data is correctly written to and read from a real (containerized) PostgreSQL database.
    - **Messaging Tests:** *(not applicable — the system has no message queue; SSE events are tested with `@QuarkusTest`)*

### 3.3. End-to-End (E2E) Testing
- **Objective:** To test the entire application flow from the user's perspective.
- **Tools:** Playwright (`frontend/e2e/`, run in CI; a `csp` project runs the specs against the production build under the strict CSP).
- **Scope:** Simulating a user interacting with the frontend. A typical E2E test would involve:
    1. The user opens the web application.
    2. The user types a question into the chat interface.
    3. The test verifies that a valid, context-aware answer is displayed.
    4. The test verifies that the sources for the answer are correctly shown.

## 4. Key Test Scenarios

- **Happy Path - Summarize & Brief:**
    1. Given a new video is published by a tracked author.
    2. When the `summarizeFromAuthors` workflow runs.
    3. Then a summary is generated and persisted to the database, and the processing state is correctly updated in the relevant tables.
- **Failure - AI Service Unavailable:**
    1. Given the AI model API (e.g., Gemini) returns a 500 error.
    2. When the `VideoSummarizationService` attempts to generate a summary.
    3. Then the retry logic is triggered, and if it ultimately fails, the failure is logged, and the `processing_status` for the item in the `content_items` table is updated to `'FAILED'`. This prevents reprocessing loops while allowing for manual or automated retries.
- **RAG - Conversational Query:**
    1. Given the database contains several indexed summaries about "Quarkus".
    2. When a user asks "What are the benefits of Quarkus?".
    3. Then the API returns an answer synthesized from the relevant summaries and provides links to the original content.
- **Edge Case - No New Videos:**
    1. Given no new videos have been published since the last run.
    2. When the `summarizeFromAuthors` workflow runs.
    3. Then the system logs that no new videos were found and gracefully exits without creating empty files or reports.
