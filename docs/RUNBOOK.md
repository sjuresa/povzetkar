# Povzetkar Runbook

**Version:** 1.4

## Revision History
| Version | Date               | Author       | Changes                                                             |
|---------|--------------------|--------------|---------------------------------------------------------------------|
| 1.4     | November 1, 2025   | Povzetkar AI | Updated for v0.6: removed legacy app, simplified run instructions.  |
| 1.2     | September 17, 2025 | Povzetkar AI | Added instructions for Content Source Management.                   |
| 1.1     | September 17, 2025 | Povzetkar AI | Added instructions for verifying the pgvector extension.            |
| 1.0     | August 16, 2025    | Povzetkar AI | Initial version based on the upcoming Quarkus multi-module project. |

## 1. Introduction

This document provides all the necessary instructions for setting up the local development environment, running the application, and executing tests.
It is the primary technical guide for developers working on the project.
## 2. Prerequisites
- **Java:** JDK 21 or later.
- **Maven:** Version 3.8.x or later.
- **Docker & Docker Compose:** For running the local development environment.
- **IDE:** IntelliJ IDEA or VS Code are recommended.
- **Git:** For version control.

## 3. Local Environment Setup
The entire local environment is managed by Docker Compose.

1.  **Create the Environment File:**
    The application requires a `.env` file in the project root for database credentials and API keys.
    ```
    QUARKUS_DATASOURCE_USERNAME=povzetkar
    QUARKUS_DATASOURCE_PASSWORD=povzetkar
    YOUTUBE_API_KEY=your_youtube_api_key_here
    ```
2.  **Start the Database:**
    This command starts the PostgreSQL database in a Docker container.
    ```bash
    docker compose up -d
    ```

## 4. Resetting the Local Database

PostgreSQL initialization scripts (like the one that enables the `pgvector` extension) only run when the database volume is first created. If you need to re-run initialization scripts or start with a fresh database:

1.  **Destroy the existing containers and volumes:**
    ```bash
    docker compose down -v
    ```
2.  **Recreate the environment:**
    ```bash
    docker compose up -d
    ```

## 5. Verifying Database State

To ensure the environment is set up correctly, you can perform the following checks.

### 5.1 Verifying the `pgvector` Extension

The application uses the `pgvector` extension for PostgreSQL. To verify it's enabled:

1.  **Connect to the development database container:**
    This command executes a shell inside the container to correctly access the environment variables set by Docker Compose (`POSTGRES_USER` and `POSTGRES_DB`).
    ```bash
    docker compose exec postgres-dev sh -c 'psql -U $POSTGRES_USER -d $POSTGRES_DB'
    ```

2.  **List installed extensions:**
    Once inside the `psql` shell, run the following command:
    ```sql
    \dx
    ```
    You should see an entry for `vector` in the list, in addition to `plpgsql`.

## 6. Running the Application

With the database running, you can start the Quarkus application in development mode:
```bash
./mvnw quarkus:dev
```

**Access the Endpoint:**
`http://localhost:8080/q/health/live`
`http://localhost:8080/q/health`

## 7. Running Tests
To run the test suite:
```bash
./mvnw test
```

## 8. Application Usage

### 8.1 Content Source Management

The `ContentSourceDetailService` is responsible for enriching and refreshing details (like name, description, and subscriber count) for content sources in the database. This service can be triggered on-demand or runs on a schedule.

#### 8.1.1 On-Demand Enrichment

To manually trigger the enrichment process for all content sources that are missing details:

*   **Endpoint:** `POST /api/v1/content-sources/enrich`
*   **Method:** `POST`
*   **Example using curl:**
    ```bash
    curl -X POST http://localhost:8080/api/v1/content-sources/enrich
    ```

#### 8.1.2 Scheduled Refresh

The service is configured to periodically refresh content source details automatically. By default, it runs weekly on Sunday at midnight.

*   **Configuration Property:** `quarkus.scheduler.enrich-content-sources.cron`
*   **Location:** `src/main/resources/application.properties`
*   **Default Value:** `0 0 0 ? * SUN` (weekly on Sunday at midnight)

    You can modify this cron expression in `application.properties` to adjust the schedule according to your needs.

### 8.2 Orchestrator and Briefing Service

The `OrchestratorService` is responsible for the end-to-end process of fetching new content, summarizing it, and generating daily briefings for active users. This service runs on a schedule.

#### 8.2.1 Scheduled Orchestration

The orchestration process is configured to run periodically. By default, it runs daily at midnight.

*   **Configuration Property:** `povzetkar.orchestrator.cron`
*   **Location:** `src/main/resources/application.properties`
*   **Default Value:** `0 0 0 * * ?` (daily at midnight)

    You can modify this cron expression in `application.properties` to adjust the schedule according to your needs.

#### 8.2.2 Manual Orchestration

To manually trigger the orchestration process for a specific date or a range of dates:

*   **Endpoint:** `POST /api/v1/orchestrator/run`
*   **Method:** `POST`
*   **Query Parameters:**
    *   `startDate` (Optional): The start date for orchestration in `YYYY-MM-DD` format. If omitted, defaults to the current date.
    *   `endDate` (Optional): The end date for orchestration in `YYYY-MM-DD` format. If omitted, defaults to `startDate`.
*   **Examples using curl:**
    *   **Run for current date (default):**
        ```bash
        curl -X POST http://localhost:8080/api/v1/orchestrator/run
        ```
    *   **Run for a specific past date:**
        ```bash
        curl -X POST "http://localhost:8080/api/v1/orchestrator/run?startDate=2025-09-15"
        ```
    *   **Run for a range of past dates:**
        ```bash
        curl -X POST "http://localhost:8080/api/v1/orchestrator/run?startDate=2025-09-10&endDate=2025-09-15"
        ```
    *   **Error: Future date:**
        ```bash
        curl -X POST "http://localhost:8080/api/v1/orchestrator/run?startDate=2025-10-01"
        ```
        (Will return HTTP 400 Bad Request)
    *   **Error: Invalid date format:**
        ```bash
        curl -X POST "http://localhost:8080/api/v1/orchestrator/run?startDate=01-01-2025"
        ```
        (Will return HTTP 400 Bad Request)

#### 8.2.3 Verification

To verify that the orchestrator is running and generating summaries and briefings:

1.  **Check Application Logs:** Look for log entries from `OrchestratorService` indicating the start and end of orchestration runs, and any errors during content fetching or summarization.
2.  **Inspect Database for Summaries:**
    Connect to the database and query the `summary` table. You should see new entries corresponding to summarized content.
    ```sql
    SELECT * FROM summaries;
    ```
3.  **Inspect Database for Briefings:**
    Connect to the database and query the `briefing` table. You should see new entries for generated briefings.
    ```sql
    SELECT * FROM briefings;
    ```
