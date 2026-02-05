# Povzetkar Runbook

**Version:** 1.7

## Revision History
| Version | Date               | Author       | Changes                                                                 |
|---------|--------------------|--------------|-------------------------------------------------------------------------|
| 1.7     | January 22, 2026   | Povzetkar AI | Added documentation for admin history pagination configuration.         |
| 1.6     | January 18, 2026   | Povzetkar AI | Added instructions for containerized execution (Docker/Podman).         |
| 1.5     | January 13, 2026   | Povzetkar AI | Updated manual orchestration instructions for v0.7.0 granular controls. |
| 1.4     | November 1, 2025   | Povzetkar AI | Updated for v0.6: removed legacy app, simplified run instructions.      |
| 1.2     | September 17, 2025 | Povzetkar AI | Added instructions for Content Source Management.                       |
| 1.1     | September 17, 2025 | Povzetkar AI | Added instructions for verifying the pgvector extension.                |
| 1.0     | August 16, 2025    | Povzetkar AI | Initial version based on the upcoming Quarkus multi-module project.     |

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
The application requires a `.env` file in the project root for database credentials and API keys. The
`docker-compose.yml` uses these to configure the PostgreSQL container, and the Quarkus application is configured to use
them for its database connection.

    ```properties
    # For the PostgreSQL container (development)
    DB_KIND=postgresql
    DB_HOST=localhost
    DB_PORT=5432
    DB_USER=your_db_user
    DB_PASS=your_db_password
    DB_NAME=povzetkar
    # For the PostgreSQL container (testing)
    TEST_DB_USER=your_test_db_user
    TEST_DB_PASS=your_test_db_password
    TEST_DB_NAME=povzetkar_test
    # For the Quarkus application
    YOUTUBE_API_KEY=your_youtube_api_key_here
    GOOGLE_API_KEYS=your_google_api_keys_here
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

## 8. Containerized Execution (Docker/Podman)

The application can be packaged as a container image using Jib. This image is OCI-compliant and can be run with Docker or Podman.

### 8.1 Building the Container Image

To build the container image, run the following Maven command:

```bash
./mvnw clean package -Dquarkus.container-image.build=true
```

This will create an image named `si.jumis/povzetkar:latest` (or `latest` tag by default).

### 8.2 Running the Container

The application requires several environment variables to function correctly. These must be passed to the container at runtime.

**Required Environment Variables:** (.env.docker)

| Variable          | Description                             | Example                                                                                                                    |
|:------------------|:----------------------------------------|:---------------------------------------------------------------------------------------------------------------------------|
| `DB_KIND`         | Database type                           | `postgresql`                                                                                                               |
| `DB_HOST`         | Database host                           | `povzetkar-db-dev` (if using Docker network) or `host.docker.internal` to connect to a service running on the host machine |
| `DB_PORT`         | Database port                           | `5432`                                                                                                                     |
| `DB_NAME`         | Database name                           | `povzetkar`                                                                                                                |
| `DB_USER`         | Database username                       | `your_db_user`                                                                                                             |
| `DB_PASS`         | Database password                       | `your_db_password`                                                                                                         |
| `YOUTUBE_API_KEY` | YouTube Data API Key                    | `AIzaSy...`                                                                                                                |
| `GOOGLE_API_KEYS` | Google GenAI API Keys (comma-separated) | `AIzaSy...`                                                                                                                |

**Docker Run Example:**

Assuming the database is running in a container named `povzetkar-db-dev` on the same network (e.g., `povzetkar_default` created by docker-compose):

> **Note:** The network name `povzetkar_default` is derived from the directory name. If your directory is named differently, check the network name using `docker network ls`.

```bash
docker run -it --rm \
  --network povzetkar_default \
  --env-file .env.docker \
  -p 8080:8080 \
  si.jumis/povzetkar:latest
```

**Podman Run Example:**

> **Note:** Ensure `DB_HOST` in `.env.docker` is set to your host's IP address accessible from the container. On many systems, you can use the special DNS name `host.containers.internal`. Otherwise, you may need to use a specific IP (e.g., `10.0.2.2` on some VM setups or your LAN IP). Podman does not share networks with Docker Compose by default.

```bash
podman run -it --rm \
  --env-file .env.docker \
  -p 8080:8080 \
  si.jumis/povzetkar:latest
```

## 9. Application Usage

### 9.1 Content Source Management

The `ContentSourceDetailService` is responsible for enriching and refreshing details (like name, description, and subscriber count) for content sources in the database. This service can be triggered on-demand or runs on a schedule.

#### 9.1.1 On-Demand Enrichment

To manually trigger the enrichment process for all content sources that are missing details:

*   **Endpoint:** `POST /api/v1/content-sources/enrich`
*   **Method:** `POST`
*   **Example using curl:**
    ```bash
    curl -X POST http://localhost:8080/api/v1/content-sources/enrich
    ```

#### 9.1.2 Scheduled Refresh

The service is configured to periodically refresh content source details automatically. By default, it runs weekly on Sunday at midnight.

*   **Configuration Property:** `quarkus.scheduler.enrich-content-sources.cron`
*   **Location:** `src/main/resources/application.properties`
*   **Default Value:** `0 0 0 ? * SUN` (weekly on Sunday at midnight)

    You can modify this cron expression in `application.properties` to adjust the schedule according to your needs.

### 9.2 Orchestrator and Briefing Service

The `OrchestratorService` is responsible for the end-to-end process of fetching new content, summarizing it, and generating daily briefings for active users. This service runs on a schedule.

#### 9.2.1 Scheduled Orchestration

The orchestration process is configured to run periodically. By default, it runs daily at midnight.

*   **Configuration Property:** `povzetkar.orchestrator.cron`
*   **Location:** `src/main/resources/application.properties`
*   **Default Value:** `0 0 0 * * ?` (daily at midnight)

    You can modify this cron expression in `application.properties` to adjust the schedule according to your needs.

#### 9.2.2 Manual Orchestration

To manually trigger the orchestration process:

*   **Endpoint:** `POST /api/v1/orchestrator/run`
*   **Method:** `POST`
*   **Request Body:**
    The endpoint accepts a JSON object with the following fields. All fields are optional.
    ```json
    {
      "startDate": "YYYY-MM-DD",
      "endDate": "YYYY-MM-DD",
      "processPending": true,
      "summarize": true,
      "createBriefing": false
    }
    ```
    - `startDate` (Optional): The start date for the `summarize` step. Defaults to the current date if omitted.
    - `endDate` (Optional): The end date for the `summarize` step. Defaults to `startDate` if omitted.
    - `processPending` (boolean, default `false`): Re-process items in `PENDING` or `RETRY_LATER` state.
    - `summarize` (boolean, default `false`): Fetch and summarize new content.
    - `createBriefing` (boolean, default `false`): Generate briefings from existing summaries.

*   **Examples using curl:**
    *   **Run all steps for current date:**
        ```bash
        curl -X POST -H "Content-Type: application/json" \
          --data '{"processPending":true,"summarize":true,"createBriefing":true}' \
          http://localhost:8080/api/v1/orchestrator/run
        ```
    *   **Only re-process failed/pending items:**
        ```bash
        curl -X POST -H "Content-Type: application/json" \
          --data '{"processPending":true}' \
          http://localhost:8080/api/v1/orchestrator/run
        ```
    *   **Only summarize new content for a range of past dates:**
        ```bash
        curl -X POST -H "Content-Type: application/json" \
          --data '{"summarize":true,"startDate":"2025-09-10","endDate":"2025-09-15"}' \
          http://localhost:8080/api/v1/orchestrator/run
        ```
    *   **Only generate new briefings:**
        ```bash
        curl -X POST -H "Content-Type: application/json" \
          --data '{"createBriefing":true}' \
          http://localhost:8080/api/v1/orchestrator/run
        ```

#### 9.2.3 Verification

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

### 9.3 Admin UI Page

The Admin UI page (`/ui/admin`) provides a user-friendly interface to trigger system-level actions and monitor the status of content processing and briefing generation.

#### 9.3.1 Accessing the Admin UI

*   **URL:** `http://localhost:8080/ui/admin`

#### 9.3.2 Triggering Content Source Enrichment

This section allows you to manually initiate the process of enriching content sources.

*   **Action:** Click the "Run Content Source Enrichment" button.
*   **Confirmation:** A `window.confirm()` dialog will appear. Confirm to proceed.
*   **Feedback:** You will be redirected back to the Admin UI page with a success or error message.

#### 9.3.3 Triggering Orchestration

This section allows you to manually trigger the main orchestration process, optionally for a specific date range.

*   **Date Parameters:**
    *   **Start Date (optional):** Select a start date using the date picker. If left empty, it defaults to the current date.
    *   **End Date (optional):** Select an end date. If left empty, it defaults to the Start Date.
*   **Action:** Click the "Run Orchestration" button.
*   **Orchestration Options:**
    *   **Process Retry/Pending Items:** Check to re-process items in `PENDING` or `RETRY_LATER` state.
    *   **Generate Summaries:** Check to fetch and summarize new content for the selected date range.
    *   **Generate Briefings:** Check to generate new briefings from existing summaries.
*   **Confirmation:** A `window.confirm()` dialog will appear. Confirm to proceed.
*   **Validation:** The system validates that:
    *   End Date is not before Start Date.
    *   Neither Start Date nor End Date are in the future.
*   **Feedback:** You will be redirected back to the Admin UI page with a success or error message.

#### 9.3.4 Monitoring History

The Admin UI page displays several tables for monitoring the application's activity:

*   **Pending Content Items:** Shows content items that are awaiting processing.
*   **Recent Content Items with Summaries:** Displays the last 25 content items that have been summarized, including their processing status, creation date, and a link to the original source. On desktop, hovering over the title shows an executive summary tooltip. On mobile, tapping the title opens a modal with the executive summary.
*   **Recent Briefings:** Shows the last 25 generated briefings, including their creation date, briefing date, and associated topic.
*   **Failed Content Items:** Lists content items whose processing status is `FAILED` or `RETRY_LATER`, indicating issues that may require attention.

All tables include a '#' column for row numbering and display dates in the configurable format `dd.MM.yyyy HH:mm:ss`.

#### 9.3.5 History Pagination

The history tables on the Admin UI page are paginated. The number of items displayed per page can be configured in `application.properties`.

*   **Configuration Property:** `povzetkar.ui.admin.page-size`
*   **Location:** `src/main/resources/application.properties`
*   **Default Value:** `20`

You can modify this value to control how many items appear on each page of the history tables.

### 9.4 Circuit Breaker Configuration

The circuit breaker for GenAI API calls prevents repeated attempts to a failing service. It tracks consecutive 503 (Service Unavailable) errors per API key.

*   **Configuration Property:** `povzetkar.orchestrator.circuit-breaker.threshold`
*   **Location:** `src/main/resources/application.properties`
*   **Default Value:** `3`

If a specific API key encounters this many consecutive 503 errors, the orchestration process will stop for that key, preventing further calls until the issue is resolved. The error count for a key is reset upon a successful API call using that key.
