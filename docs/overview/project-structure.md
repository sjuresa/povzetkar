# Project Structure

This document outlines the high-level structure of the Povzetkar project and the organization of the source code.

## Root Directory

*   **`src/`**: Contains the source code and tests for the application.
*   **`docs/`**: Contains all project documentation, including architectural decisions, guides, and version planning.
*   **`agent_logs/`**: A dedicated workspace for the AI agent's task records — task definitions (with their Time Log and Deviations sections) and approved plans.
*   **`docker-compose.yml`**: Defines the local development environment (PostgreSQL, etc.).
*   **`pom.xml`**: The Maven project configuration file.

## Source Code Layout (`src/main/java/com/povzetkar/quarkus`)

The application logic is organized by layer/functionality within the `com.povzetkar.quarkus` package:

*   **`model`**: Contains JPA entities representing the database schema (e.g., `ContentItem`, `Topic`). These classes typically extend `PanacheEntity` or `PanacheEntityBase`.
*   **`service`**: Contains the core business logic and services that interact with external APIs (e.g., `YouTubeContentFetcherService`, `OrchestrationManager`).
*   **`resource`**: Contains JAX-RS resource classes that define the REST API endpoints (e.g., `TopicResource`).
*   **`ui`**: Contains classes related to the server-side rendered UI, including Qute templates and UI-specific resources (e.g., `DashboardResource`).
*   **`ai`**: Contains classes for interacting with Generative AI providers (e.g., `GenAiClient`).
*   **`util`**: Shared utility classes (e.g., `StructuredLogger`).

## Resources Layout (`src/main/resources`)

*   **`application.properties`**: The main configuration file for the application.
*   **`db/migration/`**: Flyway SQL migration scripts for database schema versioning.
*   **`templates/`**: Qute HTML templates for the server-side rendered UI.

## Test Layout (`src/test/java`)

Tests are located in `src/test/java` and mirror the package structure of the main code where appropriate.

*   **Unit/Component Tests**: Classes annotated with `@QuarkusTest` (or plain JUnit) that test individual components in isolation or with mocked dependencies.
*   **Integration Tests**: Classes annotated with `@QuarkusIntegrationTest` (or `@QuarkusTest` with real dependencies) that verify the interaction between components and the database.

## Documentation Layout (`docs/`)

*   **`adr/`**: Architectural Decision Records.
*   **`guides/`**: Developer guides (e.g., Testing, Setup).
*   **`overview/`**: High-level project information (Roadmap, Structure).
*   **`process/`**: Process definitions and checklists (e.g., Code Review Checklist).
*   **`versions/`**: Planning and task tracking for specific versions.

## Functional Architecture Map

To quickly locate functionality, refer to this mapping of business domains to code artifacts:

| Domain               | Key Services/Classes                                       | Key Resources/UI               |
|:---------------------|:-----------------------------------------------------------|:-------------------------------|
| **Orchestration**    | `OrchestrationManager`, `OrchestrationLockService`         | N/A (Background Process)       |
| **Content Fetching** | `YouTubeContentFetcherService`, `YouTubeTranscriptService` | N/A                            |
| **Summarization**    | `VideoSummaryService`, `BriefingService`                   | N/A                            |
| **Topic Management** | `TopicProcessor`                                           | `TopicResource`, `topics.html` |
| **User Management**  | `User`, `Role`                                             | `LoginResource`                |
| **Observability**    | `StructuredLogger`                                         | `SystemInfoResource`           |
