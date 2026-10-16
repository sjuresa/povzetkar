# Povzetkar Runbook

## Table of Contents
- [1. Introduction](#1-introduction)
- [2. Prerequisites](#2-prerequisites)
- [3. Local Environment Setup](#3-local-environment-setup)
- [4. Resetting the Local Database](#4-resetting-the-local-database)
- [5. Verifying Database State](#5-verifying-database-state)
- [6. Running the Application](#6-running-the-application)
- [7. Running Tests](#7-running-tests)
- [8. Security & Code Quality](#8-security--code-quality)
- [9. Building the Full Stack](#9-building-the-full-stack)
- [10. Production Stack](#10-production-stack)
- [11. Containerized Execution (Docker/Podman)](#11-containerized-execution-dockerpodman)
- [12. Application Usage](#12-application-usage)
- [13. Compliance & Monitoring](#13-compliance--monitoring)
- [14. Real-Time Event Stream (SSE)](#14-real-time-event-stream-sse)
- [15. Monitoring & Observability](#15-monitoring--observability)
- [16. API Response Structure (Envelopes)](#16-api-response-structure-envelopes)
- [17. Secrets Management](#17-secrets-management)
- [18. Account Activation & User Onboarding](#18-account-activation--user-onboarding)

## 1. Introduction

This document provides all the necessary instructions for setting up the local development environment, running the application, and executing tests.
It is the primary technical guide for developers working on the project.
## 2. Prerequisites
- **Java:** JDK 25 or later.
- **Node.js:** v24 (LTS) — the version of the Docker build image (`node:24-alpine`) and CI.
- **NPM:** v11.x (ships with Node 24). Install dependencies with `npm ci` (root and `frontend/`), as the Dockerfile and CI do; `npm ci` fails when `package-lock.json` does not match `package.json`.
- **Maven:** Version 3.8.x or later.
- **Docker & Docker Compose:** For running the local development environment.
- **IDE:** IntelliJ IDEA or VS Code are recommended.
- **Git:** For version control.

## 3. Local Environment Setup
The local database environment is managed by Docker Compose and is located in the `backend/` directory.

1.  **Create Environment Files:**
    The application requires `.env` files for both the backend and frontend to handle configuration across different dev environments.

    **Backend (`backend/.env`):**
    Copy `backend/.env.example` to `backend/.env` and update values.
    ```properties
    # For PostgreSQL (dev & tests)
    DB_KIND=postgresql
    DB_HOST=localhost
    DB_PORT=5434   # dev DB container (docker-compose.yml); the test DB is on 5433
    DB_USER=your_db_user
    DB_PASS=your_db_password
    DB_NAME=povzetkar
    # For the PostgreSQL container (testing)
    TEST_DB_USER=your_test_db_user
    TEST_DB_PASS=your_test_db_password
    TEST_DB_NAME=povzetkar_test
    
    # API Keys & Auth
    YOUTUBE_API_KEY=your_youtube_api_key_here
    GOOGLE_API_KEYS=your_google_api_keys_here
    ```

    **Frontend (`frontend/.env`):**
    Copy `frontend/.env.example` to `frontend/.env` and update values.
    ```properties
    # Comma-separated list of extra allowed hosts (e.g. for a VPN hostname); applies once Vite listens
    # beyond loopback (`npm run dev -- --host`, §6)
    VITE_ALLOWED_HOSTS=www.example.com,192.168.1.100
    ```

2.  **Start the Database:**
    The database volumes are declared `external` (they survive `docker compose down -v`), so create them once;
    `backend/.env` must be filled first — compose reads the database credentials from it.
    ```bash
    cd backend
    docker volume create povzetkar_postgres_dev_data
    docker volume create povzetkar_postgres_test_data
    docker compose up -d
    ```

## 4. Resetting the Local Database

PostgreSQL initialization scripts (like the one that enables the `pgvector` extension) only run when the database volume is first created. If you need to re-run initialization scripts or start with a fresh database:

1.  **Destroy the existing containers and the database volumes** (`down -v` does not remove external volumes):
    ```bash
    cd backend
    docker compose down
    docker volume rm povzetkar_postgres_dev_data povzetkar_postgres_test_data
    ```
2.  **Recreate the volumes and the environment:**
    ```bash
    docker volume create povzetkar_postgres_dev_data
    docker volume create povzetkar_postgres_test_data
    docker compose up -d
    ```

## 5. Verifying Database State

To ensure the environment is set up correctly, you can perform the following checks.

### 5.1 Verifying the `pgvector` Extension

The application uses the `pgvector` extension for PostgreSQL. To verify it's enabled:

1.  **Connect to the development database container:**
    This command executes a shell inside the container to correctly access the environment variables set by Docker Compose (`POSTGRES_USER` and `POSTGRES_DB`).
    ```bash
    cd backend
    docker compose exec postgres-dev sh -c 'psql -U $POSTGRES_USER -d $POSTGRES_DB'
    ```

2.  **List installed extensions:**
    Once inside the `psql` shell, run the following command:
    ```sql
    \dx
    ```
    You should see an entry for `vector` in the list, in addition to `plpgsql`.

## 6. Running the Application

With the database running, you can start the full stack (Backend + Frontend) using the root-level orchestration:

```bash
npm run dev
```

This command uses `npm-run-all` to simultaneously boot:
- **Backend (Quarkus):** `http://localhost:8080` (API & Dev UI)
- **Frontend (Vite 6):** `http://localhost:5173` (React SPA)

**Network exposure:** the dev environment listens on loopback only — Quarkus dev and the tests by
`%dev.`/`%test.quarkus.http.host=localhost` (stated explicitly: under WSL the Quarkus default would be `0.0.0.0`;
production sets `%prod.quarkus.http.host=0.0.0.0`), Vite by its default, and the dev databases publish their
ports on `127.0.0.1` (POV-229). To open the app from another device, opt in at start — both, because a
loopback-bound Quarkus 3.40 validates the `Host` header and rejects requests Vite forwards for another host:
`../mvnw quarkus:dev -DskipTests=true -Dquarkus.http.host=0.0.0.0` (or `QUARKUS_HTTP_HOST=0.0.0.0`; in PowerShell
`..\mvnw.cmd quarkus:dev "-DskipTests=true" "-Dquarkus.http.host=0.0.0.0"` or `$env:QUARKUS_HTTP_HOST='0.0.0.0'`) and
`npm run dev -- --host` in `frontend/`; a host name (not an IP address) must also be in `VITE_ALLOWED_HOSTS` (§3).
Use it only on a trusted network: it also exposes Swagger UI, `/q/openapi`, `/q/health` and `/q/metrics` (on 8080
in dev).

### 6.1 Individual Service Execution

If you need to run services separately:

**Backend only:**
```bash
cd backend
../mvnw quarkus:dev -DskipTests=true
```

`-DskipTests=true` deactivates the `config-guard` Maven profile, whose `quarkus:dependency-tree` execution makes
dev mode fail otherwise (backlog: "`quarkus:dev` fails while the `config-guard` profile is active").

**Frontend only:**
```bash
cd frontend
npm run dev
```

### 6.2 Application Entry Points

- **Frontend SPA:** `http://localhost:5173`
- **Backend Health:** `http://localhost:8080/q/health`
- **Backend API Docs:** `http://localhost:8080/q/swagger-ui`
- **Legacy Admin UI (Qute):** `http://localhost:8080/ui/admin`

### 6.1 API Documentation (Swagger UI)

In development mode, the application generates an OpenAPI specification and provides a Swagger UI:
*   **Swagger UI:** `http://localhost:8080/q/swagger-ui`
*   **OpenAPI Specification:** `http://localhost:8080/q/openapi`

In **dev mode** the management interface is disabled (`%dev.quarkus.management.enabled=false`), so
these stay on 8080 and Swagger UI "Try it out" works same-origin. In **tests and production** they
live only on the management interface (port 9000; 9001 in tests) — see §13.2.

**Security Note:** Access to Swagger UI is explicitly disabled in the production profile (`%prod.quarkus.swagger-ui.always-include=false`) to minimize the attack surface. This is verified during the build process and manual security reviews.

## 7. Running Tests
To run the backend and frontend unit test suites (Backend JUnit + Frontend Vitest; `npm run test:full` adds
Playwright):

```bash
npm run test
```

### 7.1 Individual Testing Commands

**Backend (Maven):**
```bash
./mvnw test -pl backend
```

**Frontend (Vitest):**
```bash
cd frontend
npm run test -- --run   # without --run, Vitest stays in watch mode
```

**Frontend (Playwright E2E):**
```bash
cd frontend
npx playwright test
```

### 7.2 Code Coverage (Jacoco & Vitest)

Both the backend and frontend have automated code coverage reporting.

#### Backend (Jacoco)

Coverage is automatically calculated during the test phase.

1.  **Run tests and generate report:**
    ```bash
    ./mvnw test -pl backend
    ```
2.  **View the report:**
    Open `backend/target/jacoco-report/index.html` in your browser.
3.  **CI/CD Baseline:**
    The project targets a minimum instruction coverage of **70%**.

#### Frontend (Vitest)

Coverage is calculated using `@vitest/coverage-v8`.

1.  **Run tests and generate report:**
    ```bash
    cd frontend
    npm run test -- --coverage --run
    ```
2.  **View the report:**
    Open `frontend/coverage/index.html` in your browser.
3.  **CI/CD Baseline:**
    The project targets a minimum line coverage of **70%** for new components.

## 8. Security & Code Quality
The project integrates automated security and quality gates into the Maven lifecycle.

### 8.1 Static Analysis (SpotBugs)
Static Application Security Testing (SAST) is performed via the `spotbugs-maven-plugin` during the `verify` phase. It is configured to fail the build if **High** severity issues are detected.
```bash
./mvnw verify
```

### 8.2 Dependency Scanning (Trivy)
Third-party dependencies are scanned for known vulnerabilities (CVEs) in CI (filesystem scan and image scan, `CRITICAL,HIGH`, `.trivyignore`). CI is the pre-deploy scan: a production deploy needs a green CI run on the exact deployed commit (see `docs/protocols/verification.md`, *Pre-deploy Verification*). CI pins `aquasecurity/trivy-action` by commit SHA and the Trivy binary by version (`.github/workflows/ci.yml`). Run the scans locally only by exception — a commit without a CI run, or the per-version `.trivyignore` audit (checklist 3.1a, then without `--ignorefile`) — without installing Trivy (from Git Bash, `MSYS_NO_PATHCONV=1`; never in parallel with the test suite), using the same pinned Trivy version as CI (image referenced by digest — a tag can be repointed):

```bash
docker run --rm -v "<repo>:/src:ro" --tmpfs /src/.git -v trivy-cache:/root/.cache aquasec/trivy:0.75.0@sha256:af6acf9a6b85dfe389a1941505c0ce9efef52a4719635e1a962f022a3d855daa fs \
  --include-dev-deps --skip-dirs transcript-proxy/.venv --skip-dirs backend/target --skip-dirs frontend/coverage --skip-dirs frontend/dist \
  --severity CRITICAL,HIGH --ignorefile /src/.trivyignore --exit-code 1 --timeout 30m /src
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v trivy-cache:/root/.cache \
  -v "<repo>/.trivyignore:/cfg/.trivyignore:ro" aquasec/trivy:0.75.0@sha256:af6acf9a6b85dfe389a1941505c0ce9efef52a4719635e1a962f022a3d855daa image \
  --severity CRITICAL,HIGH --ignorefile /cfg/.trivyignore --exit-code 1 --timeout 30m <image>
```

`--include-dev-deps` matches CI (see below). The rest only keeps the local FS scan short, with the same findings
(measured in POV-224). `--tmpfs /src/.git` hides the git metadata (run it on a normal clone — in a git worktree `.git` is a file and the
mount fails): Trivy's check of the git repository took about
6 min of a 6.3-min scan of the npm lockfiles alone (the cause is likely the Docker Desktop file sharing; not
measured). The `--skip-dirs` paths are untracked local build output and a virtual environment, not source; Trivy's
default secret scanner otherwise walks their ~35 000 files — with `.git` hidden, the full scan (offline variant,
below) took 13 min without them and about 2 min with them.

The FS scan resolves Maven parent POMs from Maven Central and can be blocked there (`429 Too Many Requests`, `Retry-After: 1800` — the whole IP for 30 min). Locally, add `-v "<home>/.m2:/root/.m2:ro"` and `--offline-scan` to the FS command so the POMs come from the local Maven cache. The cache must hold the exact parent POMs and BOMs of the commit being scanned — run a local build of that commit first (e.g. `mvnw verify`); in offline mode a missing POM only produces a warning and its managed dependencies are skipped, so check the scan output for unresolved-POM warnings before accepting a clean result. This is how the v0.19.0 audit completed (POV-211).

Every `.trivyignore` entry carries an `exp:YYYY-MM-DD` date and a rationale; after expiry CI fails again and forces a re-check (remove the entry if the fix has shipped, otherwise extend it with a fresh rationale). The FS scan includes npm devDependencies, which Trivy skips by default: in CI through the `TRIVY_INCLUDE_DEV_DEPS`
environment variable on the FS step (`trivy-action` has no input for it), locally through `--include-dev-deps`.

### 8.3 Frontend API Client (orval)
The typed client in `frontend/src/api/generated` is generated from the backend OpenAPI spec and committed. After any API change, regenerate and commit it:

```bash
./mvnw package -DskipTests -pl backend -am   # writes backend/target/openapi.json (no test classes)
npm run gen:api                                # orval, output.clean: true removes stale files
```

- Generate from a `package -DskipTests` build: a spec written while test classes are on the classpath contains test-only endpoints.
- orval is pinned exactly (`8.41.0`, no `^`) and raised only deliberately, together with a regeneration; `prettier: false` keeps the output independent of a globally installed prettier. orval 8.41.0 no longer writes its version into the file header (8.5.3 did), so a bump changes only what the generator actually emits differently.
- CI step **"API client drift check"** runs the two commands above before the tests and fails if `git status --porcelain -- frontend/src/api/generated` is not empty.

### 8.4 Infra Config Validation
`bash scripts/qa/validate-infra-configs.sh` validates `docker-compose.prod.yml` (`docker compose config`, own compose project name) and `backend/infra/monitoring/prometheus/prometheus.yml` (`promtool check config`, via `docker run --network none` with the image pinned in the prod compose file). It creates no containers or networks of the prod project and no network with the prod file's fixed subnet, so it never touches the running `backend` stack. CI runs it before the image build, so a broken config or an incompatible image bump fails there instead of crash-looping production (`restart: always`).

## 9. Building the Full Stack

To build both the frontend and backend in one command:

```bash
npm run build
```

This will:
1.  Build the React frontend and place artifacts in `frontend/dist`.
2.  Build the Quarkus backend and run standard JAR packaging.

## 10. Production Stack

The production stack uses `docker-compose.prod.yml` with `.env.docker` (compose project `backend`) and runs the app, PostgreSQL, Prometheus, Grafana and the Cloudflare Tunnel (`cloudflared`). The tunnel is the only public entry: it routes `povzetkar.<domain>` to `app:8080`, TLS ends at Cloudflare, and no port is published for public traffic. There is no reverse proxy — the app sends its own security headers, CSP and caching headers and trusts `X-Forwarded-*` only from `cloudflared` ([ADR-0012](adr/0012-cloudflare-tunnel-only-edge.md), §13.5).

The dev stack (`docker-compose.yml`, same directory) is its own compose project `povzetkar-dev` with its own container names and host ports (dev DB 5434, test DB 5433, both on `127.0.0.1`), so both run side by side. It has no Prometheus/Grafana: they never had a scrape target in dev and were removed in POV-229 — metrics run only in the production stack (backlog: "Dev monitoring removed"). Never remove the `name:` from the dev file: the two files would again form one project, a dev `up` would recreate the prod Prometheus/Grafana, and `--remove-orphans` from either file would remove the other stack's containers (POV-214).

Production operations for the author's deployment are documented in a private operations runbook (not part of this repository).

## 11. Containerized Execution (Docker/Podman)

The application uses a multi-stage `Dockerfile` at the root of the project to build both the React frontend and the Quarkus backend into a unified production-ready image.

### 9.1 Building the Container Image

To build the container image, run the following command from the root directory:

```bash
docker build -t si.jumis/povzetkar:latest .
```

This process:
1.  **Builds the Frontend**: Compiles the React SPA with Vite.
2.  **Integrates Static Resources**: Embeds the built frontend into the Quarkus `META-INF/resources` directory.
3.  **Packages Backend**: Builds the Quarkus "runner" with all dependencies included.

### 9.2 Running the Container

The application requires several environment variables to function correctly. These must be passed to the container at runtime.

**Required Environment Variables:** (.env.docker)

| Variable          | Description                             | Example                                                                                                                    |
|:------------------|:----------------------------------------|:---------------------------------------------------------------------------------------------------------------------------|
| `DB_KIND`         | Database type                           | `postgresql`                                                                                                               |
| `DB_HOST`         | Database host                           | `povzetkar-db-dev` on the Docker network, or `host.docker.internal` (Docker Desktop: reaches the dev DB's loopback-only host port; not on Linux Docker Engine, where `host-gateway` is the bridge address — use the network) |
| `DB_PORT`         | Database port                           | `5432` with `povzetkar-db-dev` (the container's own port), `5434` with `host.docker.internal`; prod uses `postgres-prod:5432` inside its network |
| `DB_NAME`         | Database name                           | `povzetkar`                                                                                                                |
| `DB_USER`         | Database username                       | `your_db_user`                                                                                                             |
| `DB_PASS`         | Database password                       | `your_db_password`                                                                                                         |
| `YOUTUBE_API_KEY` | YouTube Data API Key                    | `AIzaSy...`                                                                                                                |
| `GOOGLE_API_KEYS` | Google GenAI API Keys (comma-separated) | `AIzaSy...`                                                                                                                |
| `GOOGLE_OIDC_CLIENT_ID`     | Google OIDC Client ID (Production)      | `your-client-id.apps.googleusercontent.com`                                                                                |
| `GOOGLE_OIDC_CLIENT_SECRET` | Google OIDC Client Secret (Production)  | `GOCSPX-your-secret`                                                                                                       |

**Note:** `FRONTEND_URL`, `CORS_METHODS` and `CORS_HEADERS` are no longer used (POV-221 removed CORS: every
browser client is same-origin, login/logout redirects are relative). They can be removed from `.env.docker` and
`backend/.env`; `VITE_API_BASE_URL` likewise from `frontend/.env`.

**Docker Run Example:**

Assuming the database is running in a container named `povzetkar-db-dev` on the same network (e.g., `povzetkar_default` created by docker-compose):

> **Note:** The network name `povzetkar_default` is fixed in `backend/docker-compose.yml` (`networks.default.name`).
> **Note:** Since the frontend is now served directly by Quarkus, navigate to `http://localhost:8080` to access the application. The API is hosted at the same origin under `/api/*`. OIDC credentials should be obtained from the Google Cloud Console for the production origin.

```bash
docker run -it --rm \
  --name povzetkar-app \
  --network povzetkar_default \
  --env-file .env.docker \
  -p 8080:8080 \
  si.jumis/povzetkar:latest
```

**Podman Run Example:**

> **Note:** The dev database publishes its port on `127.0.0.1` only (POV-229), so neither the host's LAN IP nor, very likely, `host.containers.internal` (a gateway or host-interface address, not loopback — not verified) reaches it. Run the container in the host's network namespace (`--network host` on Linux, then `DB_HOST=localhost`, `DB_PORT=5434`) or start the database with Podman too, in a shared pod or network. Podman does not share networks with Docker Compose by default.

```bash
podman run -it --rm \
  --env-file .env.docker \
  -p 8080:8080 \
  si.jumis/povzetkar:latest
```

#### 9.x Running on a Different Port

If you need to run the Docker container on a different port (e.g., to avoid conflicts with a local `quarkus dev` session on 8080), you can use Docker's port mapping:

```bash
# Map host port 8081 to container port 8080
docker run -it --rm -p 8081:8080 si.jumis/povzetkar:latest
```

#### 9.y Docker Runtime Configuration

The application is configured using several environment variables in the `Dockerfile`:

-   **`QUARKUS_HTTP_HOST`**: Set to `0.0.0.0` by default. This is critical for Docker, as it allows the application to listen on all available network interfaces, making it accessible from outside the container.
-   **`QUARKUS_HTTP_PORT`**: Set to `8080` by default. You can override this using `-e QUARKUS_HTTP_PORT=8082` at runtime.
-   **`JAVA_OPTS`**: Used to pass additional JVM arguments. Currently, it is essential for the Quarkus `LogManager` to function correctly in the runtime environment:
    `-Djava.util.logging.manager=org.jboss.logmanager.LogManager`.

Example overriding both host and port:

```bash
docker run -it --rm \
  -e QUARKUS_HTTP_HOST=127.0.0.1 \
  -e QUARKUS_HTTP_PORT=8082 \
  -p 8082:8082 \
  si.jumis/povzetkar:latest
```

## 12. Application Usage

### 10.1 Content Source Management

The `ContentSourceDetailService` is responsible for enriching and refreshing details (like name, description, and subscriber count) for content sources in the database. This service can be triggered on-demand or runs on a schedule.

#### 9.1.1 On-Demand Enrichment

To manually trigger the enrichment process for all content sources that are missing details:

*   **Endpoint:** `POST /api/v1/sources/enrich`
*   **Method:** `POST`
*   **Authentication:** an administrator's browser session (`ADMIN_SYSTEM_TRIGGER`); API keys are rejected
    (read-only, §14.6).

#### 9.1.2 Scheduled Refresh

The service is configured to periodically refresh content source details automatically. By default, it runs weekly on Monday at 10:00 UTC.

*   **Configuration Property:** `povzetkar.enrichment.cron` (was `quarkus.scheduler.enrich-content-sources.cron` before POV-208)
*   **Location:** `backend/src/main/resources/application.properties`
*   **Default Value:** `0 0 10 ? * MON` (weekly on Monday at 10:00 UTC)

    You can modify this cron expression in `application.properties` to adjust the schedule according to your needs.

### 9.2 Orchestrator and Briefing Service

The `OrchestrationManager` is responsible for the end-to-end process of fetching new content, summarizing it, and generating daily briefings for active users. This service runs on a schedule.

#### 9.2.1 Scheduled Orchestration

The orchestration process is configured to run periodically. By default, it runs every hour at :27.

*   **Configuration Property:** `povzetkar.orchestrator.cron`
*   **Location:** `backend/src/main/resources/application.properties`
*   **Default Value:** `0 27 * * * ?` (every hour at :27)

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

1.  **Check Application Logs:** Look for log entries from `OrchestrationManager` indicating the start and end of orchestration runs, and any errors during content fetching or summarization.
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
*   **Location:** `backend/src/main/resources/application.properties`
*   **Default Value:** `20`

You can modify this value to control how many items appear on each page of the history tables.

### 9.4 Circuit Breaker Configuration

The circuit breaker for GenAI API calls prevents repeated attempts to a failing service. It tracks consecutive 503 (Service Unavailable) errors per API key.

*   **Configuration Property:** `povzetkar.orchestrator.circuit-breaker.threshold`
*   **Location:** `backend/src/main/resources/application.properties`
*   **Default Value:** `3`

If a specific API key encounters this many consecutive 503 errors, the orchestration process will stop for that key, preventing further calls until the issue is resolved. The error count for a key is reset upon a successful API call using that key.

## 13. Compliance & Monitoring

### 11.1 Correlation IDs & Request Tracing

Every request to the `/api/v1/*` endpoints is assigned a **Correlation ID** to allow end-to-end tracing across logs and the database.

*   **Request Header:** `X-Correlation-ID`. Clients can provide their own UUID; otherwise, the system generates one.
*   **Response Header:** `X-Correlation-ID`. The same ID is echoed back to the client.
*   **Logs:** The ID is automatically added to the **MDC** (Mapped Diagnostic Context) under the key `correlationId`. Every log line for a given request will contain this ID.
*   **Background Threads:** The ID is automatically propagated to background tasks triggered via `BackgroundJobService`.

### 10.2 Audit Logging

The `AuditLogFilter` records every API access in the `audit_log` database table.

*   **Captured Data:** HTTP Method, Request Path, Response Status, Duration (ms), User Agent, Correlation ID, and User ID (if authenticated).
*   **Accessing logs via API:** `GET /api/v1/admin/audit-log?page=0&size=20` with an admin's browser session (the
    admin UI's Audit Log page uses it). There is no bearer token; API keys are rejected on admin paths (§14.6).
*   **Direct Database Query:**
    ```sql
    SELECT * FROM audit_log ORDER BY created_at DESC LIMIT 10;
    ```

### 10.2a Job Execution Audit Retention

`RetentionService` deletes `job_execution_audit` rows (the execution history of background jobs — not the `audit_log` table above) older than the retention period, in chunks, each chunk in its own transaction.

*   **Configuration Property:** `povzetkar.retention.cron` — required, there is no default in the code
*   **Location:** `backend/src/main/resources/application.properties`
*   **Default Value:** `0 0 10 * * ?` (daily at 10:00 UTC)
*   **Retention period / chunk size:** `povzetkar.retention.audits.days` (default 90), `povzetkar.retention.audits.chunk-size` (default 1000)
*   **Check:** metric `povzetkar_maintenance_audits_purged_total`; log lines `Starting scheduled audit log retention` and `Finished audit log retention. Total records purged: …`
*   A run missed while the app is down is not caught up; the next run purges the backlog.

### 10.3 Performance Metrics

A consolidated metrics endpoint provides a snapshot of the system state with a single database roundtrip.

*   **Endpoint:** `GET /api/v1/admin/metrics`
*   **Captured Metrics:** Total/Running/Failed Background Jobs, Total/Enabled Topics, Total Content Items, and Idempotent Request count.
*   **Access:** an admin's browser session, as for the audit log above.

### 10.4 Command Idempotency

Mutating "command" endpoints (`POST /api/v1/*/commands/*`) support strict **Idempotency** to prevent accidental double-processing of asynchronous requests.

*   **Request Header:** `Idempotency-Key` (e.g., a unique UUID generated by the client).
*   **Behavior:** If the system receives a second request with the same `Idempotency-Key` within 24 hours (default), it will return the cached response (typically a `202 Accepted` with the same `jobId`) without re-executing the operation.
*   **Response:** Successful idempotent submissions return a job DTO:
    ```json
    {
      "data": {
        "jobId": "...",
        "status": "QUEUED"
      }
    }
    ```

### 10.5 Optimistic Locking

To prevent concurrent update conflicts, all `PATCH`, `PUT`, and `DELETE` operations on core resources (Topics, Sources, Users) enforce **Optimistic Locking**.

*   **Prerequisite:** Clients must first `GET` the resource to retrieve its current `version` integer field.
*   **Request Header:** `If-Match: <version_number>`
*   **Error Handling:**
    *   Missing header returns `428 Precondition Required`.
    *   Version mismatch returns `409 Conflict`.
*   **Example:**
    ```bash
    curl -X PATCH -H "If-Match: 1" -H "Content-Type: application/json" \
      -d '{"name":"New Name"}' http://localhost:8080/api/v1/topics/123
    ```

### 10.6 Logging Standards

The project has standardized on **SLF4J** for logging and uses a `StructuredLogger` helper for consistent key-value pair output.

*   **Standard Logger:** `private static final Logger logger = LoggerFactory.getLogger(YourClass.class);`
*   **Structured Logger:** `private static final StructuredLogger logger = StructuredLogger.getLogger(YourClass.class);`
*   **Usage Examples:**
    *   **Standard Logger (for formatted messages):**
        ```java
        // In a class with: private static final Logger logger = LoggerFactory.getLogger(YourClass.class);
        logger.info("Processing item with ID: {}", itemId);
        ```
    *   **Structured Logger (for key-value pairs):**
        ```java
        // In a class with: private static final StructuredLogger logger = StructuredLogger.getLogger(YourClass.class);
        logger.info("event", "job_started", "job_id", jobId);
        ```

## 14. Real-Time Event Stream (SSE)

The application provides a centralized **Server-Sent Events (SSE)** endpoint for real-time system monitoring. This is the preferred method for frontends to track background job completion.

*   **Endpoint:** `GET /api/v1/events/stream`
*   **Event Types:**
    *   `JOB_STATUS_CHANGED`: Pushed whenever a background job changes state (e.g., QUEUED -> RUNNING -> COMPLETED).
    *   `PULSE`: Periodic heartbeat sent every 30 seconds to keep the connection alive.
*   **Consuming via CLI:**
    ```bash
    curl -N -H "Accept: text/event-stream" http://localhost:8080/api/v1/events/stream
    ```

## 15. Monitoring & Observability

Povzetkar v0.15.0+ includes a built-in monitoring stack based on **Micrometer**, **Prometheus**, and **Grafana**.

### 13.1 Infrastructure Overview

The monitoring infrastructure is defined in `backend/docker-compose.prod.yml` (production only; the dev stack has none since POV-229, §10):
- **Prometheus**: Scrapes metrics from the Quarkus application.
- **Grafana**: Visualizes metrics via pre-provisioned dashboards.

### 13.2 Technical Endpoints & the Management Interface (POV-207)

The Quarkus technical endpoints — `/q/metrics`, `/q/health` (incl. `/live`, `/ready`) and
`/q/openapi` — are served **only on the management interface, port 9000**
(`quarkus.management.enabled=true`, build-time; port 9001 in tests). On the application
port 8080 they return 404. Exception: **dev mode** disables the management interface
(`%dev.quarkus.management.enabled=false`) so Swagger UI can call the API same-origin.

**Protection boundary = the network, not a token.** The Cloudflare tunnel forwards only
`app:8080`. Port 9000 is listed under `expose` in `docker-compose.prod.yml`, but that is
documentation only — `expose` restricts nothing, and every container on the Docker network can
reach `app:9000`. It must **never** be published via `ports:` or configured as a Cloudflare tunnel
public hostname. Consumers inside the network:

- **Prometheus** scrapes `http://app:9000/q/metrics` (no auth).
- **Docker `HEALTHCHECK`** probes `http://localhost:9000/q/health/ready` from inside the container.

> [!IMPORTANT]
> Anything in the Docker network with a public UI would be a bridge to port 9000 — notably
> **Grafana**: a Grafana admin can add a datasource targeting `app:9000`. Grafana therefore has
> **no public hostname** — it is published on `127.0.0.1:3000` only (POV-214, ADR-0012). If remote
> access is ever needed, add a tunnel hostname behind Cloudflare Access, never a plain route. Keep
> `GF_ADMIN_PASSWORD` strong. When checking the Cloudflare dashboard, confirm no tunnel route
> targets `app:9000` or `grafana:3000`.

**Note:** `METRICS_SCRAPE_TOKEN` is no longer used (the former `…prometheus.bearer-token`
properties were never real Quarkus config and were silently ignored). It can be removed from
`.env.docker`.

**Verifying (production stack):**
```bash
# Public: must NOT be 200
curl -s -o /dev/null -w "%{http_code}\n" "https://<your-domain>/q/metrics"   # 404

# Inside the Docker network: metrics served on the management port
docker exec povzetkar-prometheus wget -qO- http://app:9000/q/metrics | head -3

# Prometheus target health
docker exec povzetkar-prometheus wget -qO- http://localhost:9090/api/v1/targets | grep -o '"health":"[a-z]*"'

# uri tag cardinality (POV-221): stays small and does not grow with traffic; past 100 distinct
# values Micrometer would stop recording new ones
docker exec povzetkar-prometheus wget -qO- http://app:9000/q/metrics \
  | grep '^http_server_requests_seconds_count' | grep -o 'uri="[^"]*"' | sort -u
```

In Grafana (Explore): `count(count by (uri) (http_server_requests_seconds_count))`.

### 13.3 Dashboards-as-Code

Grafana is pre-configured with the **"Povzetkar Baseline"** dashboard (datasource UID `prometheus`, fixed in the
provisioning file — POV-229), which tracks:
- **Briefing Production Throughput**: briefing generation rate, by status (success / failed).
- **Currently Active Orchestrations**: active orchestration threads.
- **Total Orchestration Failures**: failed orchestrations (0 until the first failure creates the metric).

On a Grafana that already holds the datasource with a generated UID (provisioned before POV-229), delete the
"Prometheus" datasource once (Connections → Data sources) and restart Grafana; provisioning then creates it with the
fixed UID. A fresh Grafana volume needs nothing.

**Accessing Grafana:**
- **Production**: `http://127.0.0.1:3000` on the host (on a remote host: `ssh -L 3000:localhost:3000 <host>`);
  admin password = `GF_ADMIN_PASSWORD` from `.env.docker`.
- **Dev stack**: none (removed in POV-229, §10).

### 13.4 Custom Business Metrics

All custom Povzetkar metrics use the `povzetkar_` prefix:
- `povzetkar_briefing_generated_total`: Counter for success/failed briefings (with `status="success|failed"`, `topic_id`, `user_id` tags).
- `povzetkar_active_orchestrations_current`: Gauge for active threads.
- `povzetkar_source_added_total`: Counter for content sources added (with `platform` tag).
- `povzetkar_youtube_validation_failed_total`: Counter for source intake errors (with `error_type` tagging `unresolved_identifier`, `channel_not_found`, or `io_exception`).
- `povzetkar_transcript_retrieval_duration_seconds`: Timer for transcript fetching.
- `povzetkar_transcript_service_failed_total`: Counter for transcript failures (with `error_type` tagging `invalid_format` or `retrieval_exception`).
- `povzetkar_orchestration_failed_total`: Counter for high-level manual orchestration failures.

### 13.5 Access Log & Request History (POV-210)

**Traffic path:** the Cloudflare tunnel routes `povzetkar.<domain>` straight to
`http://povzetkar-app:8080` (`docker logs povzetkar-cloudflared` prints the ingress on every start as
`Updated to new configuration … "service":"http://povzetkar-app:8080"`). There is no reverse proxy and no host port
for public traffic ([ADR-0012](adr/0012-cloudflare-tunnel-only-edge.md)), so every request reaches the app through
the tunnel, and the app's access log is the forensic record.

**Where the logs live:**

| Log | Location | After a container recreate | Size bound |
| --- | --- | --- | --- |
| App log (without access lines) | `docker logs povzetkar-app` (Docker `json-file`) | **lost** — tied to the container ID | 5 × 20 MB (`x-logging` in `docker-compose.prod.yml`, all services) |
| Same, as a file | `/var/log/app/quarkus.log`, `quarkus.log.1…10` on volume `backend_app_logs` | kept | 11 × 10 MB, size rotation only, no rotation on start |
| **Access log** (since POV-214) | `/var/log/app/access.log`, `access.log.1…19` on the same volume | kept | 20 × 10 MB, size rotation only, no rotation on start |

The app file log is switched on only in the prod container (`QUARKUS_LOG_FILE_ENABLED`,
`QUARKUS_LOG_FILE_PATH` in `docker-compose.prod.yml`); the access-log file handler and the routing of the
access-log category into it (and out of the app log and `docker logs`) are switched on together in the `%prod`
profile of `application.properties` — `/var/log/app` exists in the image. There is no age-based deletion: at
today's volume the files hold years of history. Access lines logged before POV-214 are in `quarkus.log*` in the
old format (without `ray`/`user`/`host`/`ref`).

**Access log** (`%prod`, logger category `io.quarkus.http.access-log`). One line per request, pattern:

```text
%t cf-ip=<CF-Connecting-IP> country=<CF-IPCountry> ray=<CF-Ray> user=<AccessLogPrincipalRoute> "%<r" %s %b %D host="<Host>" ref="<Referer>" xff="<X-Forwarded-For>" "<User-Agent>"
```

- `cf-ip=` is the **client IP** (set by Cloudflare). Since POV-214 only the tunnel reaches the app, so it is
  trustworthy for every request.
- `ray=` is Cloudflare's request ID: search it in the Cloudflare dashboard (Security → Events) to see what
  Cloudflare did with the request.
- `user=` is the signed-in user: the **Google subject** (`sub`, the numeric account ID) for browser
  sessions — map it to a user via `user_identities` (provider `google`, external ID) — or the mapped **e-mail**
  for API-key requests; `-` when anonymous. Set by `AccessLogPrincipalRoute` (`%{d,povzetkar.log-user}`), limited to
  `[A-Za-z0-9@._+-]`. Not `%u`: with Quarkus' Google provider that is the display name, which the user controls.
- `"%<r"` is the **original** request line incl. the query string (attack payloads live there;
  `SpaRoutingHandler`'s reroute to `/index.html` is consolidated into one entry). It includes the
  OIDC callback `code`/`state`: single-use, redeemed at once, useless without the client secret.
- `%s` final status, `%b` bytes (`-` = none), `%D` duration in ms.
- `host=`, `ref=`, `xff=` are **client-controllable**. `xff=` is the whole `X-Forwarded-For` chain: Cloudflare
  keeps a value the client sent and appends the real address, so a first hop that differs from `cf-ip` means the
  client sent its own `X-Forwarded-For`.
- Field order: only the timestamp and values set by Cloudflare or the server precede the request line; the
  client-controlled fields come after the status, so a forged `cf-ip=` or log prefix in a URL or header cannot
  shift them. (`%h` — the client's own first `X-Forwarded-For` value — is deliberately not logged.)
- The `X-API-Key` header is never logged. The logs therefore hold **client IPs, countries, Google subjects,
  query strings and public share UUIDs** (`/api/v1/public/resolve/{uuid}` and the SPA share routes carry
  the share's capability token in the path — a leaked log grants access to a share until it is
  unshared; privacy notice: backlog).
- Not logged: the management port 9000 (health checks, Prometheus scrapes).
- Separate, older record: `AuditLogFilter` stores every **matched** `/api/v1` request (method,
  path, status, duration, User-Agent, user) in the `audit_log` table; unmatched probes are not in it.

Reading the production logs of the author's deployment (recipes for the access log, the pre-POV-214 lines and the YouTube transcript pacing) is documented in a private operations runbook (not part of this repository).

### 13.6 Checking for Suspicious Traffic

Production operations for the author's deployment are documented in a private operations runbook (not part of this repository).

## 16. API Response Structure (Envelopes)

All REST API responses (except for special streams like SSE) are wrapped in a standard **JSON Envelope** to ensure consistency across the platform.

### Success Response (2xx)
```json
{
  "data": { ... payload ... },
  "meta": {
    "timestamp": "2023-10-25T14:30:00Z",
    "correlationId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 100,
      "totalPages": 5,
      "hasNext": true
    }
  },
  "error": null
}
```

### Error Response (4xx/5xx)
Errors follow the **RFC 7807** (Problem Details) structure:
```json
{
  "error": {
    "type": "/errors/validation",
    "title": "Validation Error",
    "status": 400,
    "detail": "One or more constraints have been violated.",
    "errorCode": "VALIDATION_ERROR",
    "fieldErrors": [
      {
        "field": "name",
        "message": "must not be empty"
      }
    ]
  }
}
```

## 17. Secrets Management

The application requires several sensitive configuration values (API keys, database credentials, OIDC secrets) to function. This section documents how secrets are managed across environments.

### 14.1 Required Secrets Inventory

| Environment Variable | Config Property | Description | Required |
|:---|:---|:---|:---:|
| `DB_PASS` | `quarkus.datasource.password` | PostgreSQL database password | ✅ |
| `YOUTUBE_API_KEY` | `youtube.api.key` | YouTube Data API v3 key | ✅ |
| `GOOGLE_API_KEYS` | `google.api.keys` | Google GenAI API keys (comma-separated for pool rotation) | ✅ |
| `GOOGLE_OIDC_CLIENT_ID` | `quarkus.oidc.client-id` | Google OIDC OAuth 2.0 Client ID | ✅ |
| `GOOGLE_OIDC_CLIENT_SECRET` | `quarkus.oidc.credentials.secret` | Google OIDC OAuth 2.0 Client Secret | ✅ |
| `SUPADATA_API_KEY` | `quarkus.rest-client.supadata.headers.x-api-key` | Supadata transcript API key | ⬜ (required if supadata provider enabled) |
| `TRANSCRIPT_PROXY_API_KEY` | `transcript.providers.proxy.api-key` | Key the backend sends to the transcript proxy (`X-API-Key`, 16.1b) | ⬜ (required if the proxy provider is enabled) |

Not secrets: `TRANSCRIPT_PROXY_ENABLED` (`transcript.providers.proxy.enabled`, default `false`) and
`TRANSCRIPT_PROXY_URL` (`quarkus.rest-client.transcript-proxy.url`) — the switch and base URL of the transcript
proxy (14.1a).

### 14.1a Transcript Provider Configuration

The transcript fetching pipeline supports multiple providers with runtime-configurable ordering and fallback.

| Config Property | Default | Description |
|:---|:---|:---|
| `transcript.providers.order` | `youtube-legacy,proxy,supadata` | Comma-separated list of providers tried in order. |
| `transcript.providers.youtube-legacy.enabled` | `true` | Enable/disable the YouTube legacy (direct scraping) provider. |
| `transcript.providers.proxy.enabled` | `false` (`TRANSCRIPT_PROXY_ENABLED`) | Enable/disable the transcript proxy provider — only with a reachable proxy host (below). |
| `transcript.providers.youtube-legacy.pacing.fetch.min` / `.fetch.max` | `15s` / `45s` | Random pause before a youtube-legacy fetch, measured from the **end of the previous fetch**; no pause for the first fetch or after idle (POV-222). |
| `transcript.providers.youtube-legacy.pacing.in-fetch.min` / `.in-fetch.max` | `1s` / `5s` | Random pause before each further call inside a fetch (video page → innertube → captions). |
| `transcript.retry.intervals` | `30m,30m,90m,90m,210m,210m` | After the n-th failed **automatic** transcript fetch a video is retried no earlier than the n-th interval later; the attempt after the last interval ends it `FAILED` (6 intervals = 7 attempts). |
| `povzetkar.orchestration.time-budget` | `15m` | Time budget of one orchestration run (scheduled, manual, per topic): items not started — or still waiting for a YouTube fetch slot — when it is used up stay unchanged for the next run. |
| `transcript.providers.supadata.enabled` | `false` | Enable/disable the Supadata provider. Set to `true` and provide `SUPADATA_API_KEY` to activate. |
| `transcript.providers.supadata.lang` | `en` | ISO 639-1 language code for Supadata transcript requests. Falls back to first available language if the video has no transcript in the requested language. |

**YouTube pacing and retries (POV-222).** Automatic youtube-legacy fetches run strictly one after another,
app-wide (all topics, items and threads share one fair queue). The pacing ranges are validated at startup
(min ≤ max, not negative — an invalid value stops the start). Behaviour per trigger:

| Trigger | Paced | Retry counter | Time budget | On a failed fetch |
| --- | --- | --- | --- | --- |
| Scheduled / manual orchestration, per-topic job | yes | counted | yes | `RETRY_LATER` with due time, `FAILED` after the 7th attempt |
| Admin per-item *Retry* / *Generate summary* | yes | not counted | no | nothing changes (status, counter, due time) |
| Admin *Fetch transcript* | **no** (exception) | not counted | no | nothing changes |

- youtube-legacy picks English manual → English generated → any manual → any generated captions (English includes
  `en-GB`, `en-US`, …; no YouTube translation); ties go to exact `en`, then to the spoken language (that of the
  speech-recognised track), then to the language code. The summary prompt asks Gemini for English.
- An unexpected YouTube response the library cannot parse counts as a failed attempt (next provider, retry policy).
  Each YouTube call is bounded by 30 s for the whole exchange (a stalled response body cannot hold the queue).
- A failure inside a window already counted by an overlapping run (e.g. a per-topic job during the hourly run) is
  not counted again. Any stored transcript (manual capture or a per-item job whose Gemini call then failed) clears
  `next_transcript_attempt_at`, so the next run only needs Gemini.
- `budgetExhausted=true` in the run summary means items were skipped **and** the budget had passed; skips before
  the deadline come from an interruption (shutdown).
- The time budget is checked when an item starts, when the YouTube fetch slot is obtained and again after its pause,
  and before falling through to the next provider — no youtube-legacy fetch starts after the budget is used up (the
  proxy fallback's own 15–45 s jitter may still end its single call shortly after it).
- A successful *Fetch transcript* clears `next_transcript_attempt_at` (the stored transcript needs no fetch), so the
  next run summarizes a `RETRY_LATER` video; the attempt counter stays. A video that already ended `FAILED` keeps
  its status — summarize it with the per-item *Generate summary* (uses the stored transcript) or a status override.
- `quarkus.vertx.max-worker-execute-time=1200s` is above the time budget plus the items still in progress and the
  briefing step, and `povzetkar.orchestration.lock-timeout=PT30M` is above both (a lock older than the timeout is
  taken over as crashed); change the three together.
- A 429 is a per-video transient failure like any other — there is no global cooldown.
- The retry state is in `content_items.transcript_attempts` / `next_transcript_attempt_at` (V38); not shown in the
  admin UI. An admin status override (e.g. `FAILED` → `PENDING`) keeps the counter: the next automatic failure
  ends the video `FAILED` again. To give a video a new budget, reset both columns in the database.
- An item still unprocessed after the last run of the day leaves the today+yesterday window of the hourly job
  and stays as it is (unchanged behaviour) — except a video with a scheduled transcript retry
  (`RETRY_LATER` and `next_transcript_attempt_at` set): the hourly job carries it over in yesterday's step until it
  succeeds or its 7th attempt ends it `FAILED`; its summary is dated yesterday.
- When two runs process the same video (a per-topic job during the hourly run), a late transcript failure does not
  change a video the other run already completed or stored the transcript for.
- The legacy keys `transcript.providers.youtube-legacy.jitter.*` are gone; a leftover
  `TRANSCRIPT_PROVIDERS_YOUTUBE_LEGACY_JITTER_*` in `.env.docker` is ignored.

**Transcript proxy (`proxy` provider):** the backend calls the service in `transcript-proxy/` (16.1b) at
`TRANSCRIPT_PROXY_URL`, sending `TRANSCRIPT_PROXY_API_KEY` as `X-API-Key`. It is off by default
(`TRANSCRIPT_PROXY_ENABLED=false`): an enabled proxy without a reachable host would add its 15–45 s jitter and a
failed call to every youtube-legacy failure. To use it, set `TRANSCRIPT_PROXY_ENABLED=true`, the URL and the key
(the production compose file passes all three; without a URL it falls back to `https://proxy.invalid`, which never
reaches a real host). Whether youtube-legacy works directly depends on the host's egress IP — YouTube blocks many
cloud IP ranges; then the proxy, run on a host YouTube does not block, or Supadata (below) takes over.

**Enabling Supadata as primary provider** (if YouTube blocks the production host's IP):

```properties
transcript.providers.order=supadata,youtube-legacy
transcript.providers.supadata.enabled=true
# SUPADATA_API_KEY env var must be set
```

**Metrics emitted** (all tagged with `provider` label):

- `transcript.fetch.attempts` — counter per provider (requests made; a fetch skipped by the time budget is not
  counted)
- `transcript.fetch.successes` — counter per provider
- `transcript.fetch.failures` — counter tagged with `provider` and `reason`
- `transcript.fetch.duration` — timer per provider

### 16.1b Transcript Proxy Host

The `proxy` provider calls the standalone FastAPI service in `transcript-proxy/` (see its README), which fetches a single YouTube transcript per request from a host whose IP YouTube does not block. The backend reaches it at `TRANSCRIPT_PROXY_URL` with `TRANSCRIPT_PROXY_API_KEY` (14.1a). The author's proxy hosts are documented in a private operations runbook (not part of this repository).

### 14.2 How Secrets Are Provided

Secrets are injected via **environment variables** and resolved by Quarkus Config at startup. The approach varies by environment:

| Environment | Method | Details |
|:---|:---|:---|
| **Local Dev** | `.env` file | Copy `backend/.env.example` to `backend/.env` and fill in real values. Loaded by Quarkus Dev mode. |
| **Docker** | `--env-file` | Create a `.env.docker` file (never committed) and pass via `docker run --env-file .env.docker`. |
| **CI/CD** | GitHub Secrets | Injected as environment variables in the `ci.yml` workflow via `${{ secrets.* }}`. |
| **Kubernetes** | K8s Secrets | Mount as environment variables from a `Secret` resource (option; this project deploys with Docker Compose). |

### 14.3 Startup Secret Validation (Fail-Fast)

The `SecretConfigValidator` bean automatically runs at application startup and validates that **all required secrets** from the inventory above are present and non-blank.

*   **If any secret is missing:** The application logs a `FATAL` error listing the missing keys and exits immediately with code `1`.
*   **If all secrets are present:** A confirmation `INFO` log is emitted.

This prevents partial system failures where the app starts successfully but crashes at runtime when a service first tries to use an unconfigured API key.

### 14.4 Secret Handling & Log Suppression

Secrets stay out of logs through three rules:

1.  **Environment only.** Secrets are supplied as environment variables (`backend/.env`, `.env.docker`); `application.properties` holds only `${ENV_VAR}` references.
2.  **`quarkus.config.log.values=false`**: Suppresses the config value dump that normally appears in startup logs.
3.  **Never log a secret value.** Code logs at most *which* property is missing (e.g. `SecretConfigValidator`, the half-configured API-key warning in §14.6).

> There is **no** `quarkus.config.secret-keys` property (removed in POV-208 — Quarkus ignored it). SmallRye's secret-keys feature is a programmatic access *lock* that makes plain `@ConfigProperty` reads throw; it was never in effect here.

**Browser session (OIDC):** the session cookie is `q_session` (Quarkus default name, path `/`, `SameSite=Lax`, `Secure` when the request arrives over HTTPS — in production via the `X-Forwarded-Proto: https` that `cloudflared` forwards and the app accepts only from `cloudflared`'s fixed address — [ADR-0012](adr/0012-cloudflare-tunnel-only-edge.md), `QUARKUS_HTTP_PROXY_TRUSTED_PROXIES` and `ipv4_address` in `backend/docker-compose.prod.yml`; checked after every change to the proxy trust). It lives as long as the Google ID token (≈1 h) plus `session-age-extension` (5 min); `token.refresh-expired` is off, so an expired session re-authenticates via Google (silently if the Google session is still active). Logout (`/logout`) expires it through `OidcSession.logout()`.

### 14.5 Security Best Practices

1.  **Never commit `.env` files.** Both `backend/.env` and `.env.docker` are listed in `.gitignore`.
2.  **Rotate API keys periodically.** Google API keys and OIDC secrets should be rotated at least annually.
3.  **Use separate keys per environment.** Never share production API keys with development or test environments.
4.  **Principle of Least Privilege.** API keys should be scoped to the minimum required permissions (e.g., YouTube Data API read-only).
5.  **Audit key usage.** Monitor API key usage in the Google Cloud Console to detect unauthorized access or quota anomalies.

### 14.6 Programmatic API-Key Access (POV-205)

An optional read-only API key lets automation agents (e.g., Claude Code) read briefings, summaries, content items (including transcripts via `GET /api/v1/content-items/{id}/transcript`), topics, sources and the dashboard without a browser OIDC session.

**Behavior:** requests carrying `X-API-Key: <secret>` authenticate as the mapped user with the synthetic `api-readonly` role (the four VIEW permissions). Non-GET requests and admin paths return 403. A wrong key returns 401. Without the header, authentication is unchanged. The feature is **disabled unless both properties are set**.

**Setup (local or prod):**

1.  Generate a key: `openssl rand -hex 32`
2.  Add **both** to the environment (`backend/.env` / `.env.docker` locally, the prod env file on the server) — the key is a secret, the email just maps it to an existing `ENABLED` user:

    ```env
    POVZETKAR_API_KEY=<generated secret>
    POVZETKAR_API_KEY_USER_EMAIL=<email of the existing user the key acts as>
    ```

3.  Restart the application, then verify:

    ```bash
    curl -H "X-API-Key: <generated secret>" http://localhost:8080/api/v1/briefings          # 200
    curl -X POST -H "X-API-Key: <generated secret>" http://localhost:8080/api/v1/briefings/1/share  # 403 (read-only)
    ```

The key must never appear in logs (§14.4). Rotation = replace the env value and restart.

**Half-configured?** If only one of the two values is set, the feature stays disabled and startup logs a `WARN` naming the missing property (e.g. `povzetkar.api.key-user-email is not set - API-key access stays disabled …`); `X-API-Key` requests then get the normal OIDC login redirect. The key itself is never logged.

**Getting the API schema (since POV-207):** `/q/openapi` is no longer served publicly — it lives on the internal management port (§13.2). An agent running on the production host fetches the live contract (including the `apiKey` security scheme) through a container on the Docker network:

```bash
docker exec povzetkar-prometheus wget -qO- "http://app:9000/q/openapi?format=json" > openapi.json
```

Alternatively build it from the repo: `./mvnw package -pl backend -DskipTests` writes `backend/target/openapi.json` (build output, not committed — `target/` is gitignored).

## 18. Account Activation & User Onboarding

### 15.1 Account Approval Model

Admin approval is **always required** for new user accounts. There is no configuration flag to bypass this. New OIDC sign-ins produce an account in `PENDING_APPROVAL` state with the `demo` role. The user can browse demo content but cannot create topics or trigger briefings until an administrator approves them.

Account lifecycle states: `PENDING_APPROVAL` → `ENABLED` | `REJECTED`; `ENABLED` → `DISABLED`; `DISABLED` | `REJECTED` → `ENABLED`.

### 15.2 Admin Action Endpoints

| Endpoint | Transition |
|:---|:---|
| `POST /api/v1/users/{id}/approve` | `PENDING_APPROVAL` → `ENABLED` |
| `POST /api/v1/users/{id}/reject` | `PENDING_APPROVAL` → `REJECTED` |
| `POST /api/v1/users/{id}/disable` | `ENABLED` → `DISABLED` |
| `POST /api/v1/users/{id}/enable` | `DISABLED` or `REJECTED` → `ENABLED` |

All four endpoints require the `USER_MGMT_UPDATE` permission.

### 15.3 Seeding the First Admin User

On a fresh deployment there are no users in the database. An initial admin must be seeded directly before the first OIDC sign-in (otherwise the first user arrives as `PENDING_APPROVAL` with no one to approve them).

**Option A — SQL INSERT (recommended for production):**

```sql
-- Run after Flyway migrations have applied (V30+ schema)
INSERT INTO users (username, email, name, account_status, created_at, updated_at, version)
VALUES ('admin@yourdomain.com', 'admin@yourdomain.com', 'Initial Admin', 'ENABLED', NOW(), NOW(), 0)
ON CONFLICT (email) DO NOTHING;

-- Assign the admin role (role ID 1 = 'admin' from V11 seed, verify with: SELECT id FROM roles WHERE name = 'admin')
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'admin@yourdomain.com' AND r.name = 'admin'
ON CONFLICT DO NOTHING;
```

After inserting, the admin logs in via OIDC. The `OidcSecurityIdentityAugmentor` will find the existing user by email, link the OIDC identity, and return roles from the database — the admin will have full access immediately.

**Option B — Approve via direct SQL after first sign-in:**

If the intended admin has already signed in (and is now `PENDING_APPROVAL`), run:

```sql
UPDATE users SET account_status = 'ENABLED' WHERE email = 'admin@yourdomain.com';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'admin@yourdomain.com' AND r.name IN ('admin', 'user')
ON CONFLICT DO NOTHING;

-- Remove the demo role
DELETE FROM user_roles
WHERE user_id = (SELECT id FROM users WHERE email = 'admin@yourdomain.com')
  AND role_id = (SELECT id FROM roles WHERE name = 'demo');
```

### 15.4 Demo Content

The `demo@povzetkar.local` user and `Science & Discovery` topic (with Kurzgesagt and Veritasium channels) are seeded by migration V32. This account is `ENABLED` with no roles — it exists solely as a data source for the impersonation pattern. Do not delete or modify this user.
