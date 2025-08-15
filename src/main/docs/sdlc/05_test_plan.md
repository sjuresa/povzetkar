# Test Plan - Project Povzetkar

**Version:** 1.0
**Date:** August 15, 2025

## 1. Introduction

This document outlines the testing strategy for the Povzetkar project to ensure the delivery of a high-quality, reliable, and robust application.

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
- **Scope:** All business logic in services, utility classes, and data models. External dependencies (like database connections or external APIs) will be mocked.
- **Example:** A test for `VideoSummarizationService` would mock `YouTubeDataService` and `ProcessingStateService` to verify that the orchestration logic is correct.

### 3.2. Integration Testing
- **Objective:** To verify the interaction between different components of the system.
- **Tools:** Quarkus Test Framework (`@QuarkusTest`), Testcontainers (for PostgreSQL and RabbitMQ).
- **Scope:**
    - **API Tests:** Testing REST endpoints by making real HTTP requests to the running application and asserting the responses.
    - **Persistence Tests:** Verifying that data is correctly written to and read from a real (containerized) PostgreSQL database.
    - **Messaging Tests:** Verifying that services can correctly publish and consume messages from a real (containerized) message queue.

### 3.3. End-to-End (E2E) Testing
- **Objective:** To test the entire application flow from the user's perspective.
- **Tools:** Cypress or Playwright.
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
    2. When `VideoSummary` attempts to generate a summary.
    3. Then the retry logic is triggered, and if it ultimately fails, the failure is logged, and the `processing_status` for the item in the `content_items` table is updated to `'FAILED'`. This prevents reprocessing loops while allowing for manual or automated retries.
- **RAG - Conversational Query:**
    1. Given the database contains several indexed summaries about "Quarkus".
    2. When a user asks "What are the benefits of Quarkus?".
    3. Then the API returns an answer synthesized from the relevant summaries and provides links to the original content.
- **Edge Case - No New Videos:**
    1. Given no new videos have been published since the last run.
    2. When the `summarizeFromAuthors` workflow runs.
    3. Then the system logs that no new videos were found and gracefully exits without creating empty files or reports.