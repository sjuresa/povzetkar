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
- [10. Production Stack (Local)](#10-production-stack-local)
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
    FRONTEND_URL=http://localhost:5173
    ```

    **Frontend (`frontend/.env`):**
    Copy `frontend/.env.example` to `frontend/.env` and update values.
    ```properties
    # Empty for relative proxying (recommended)
    VITE_API_BASE_URL=
    # Comma-separated list of extra allowed hosts (e.g. for Tailscale)
    VITE_ALLOWED_HOSTS=www.example.com,192.168.1.100
    ```

2.  **Start the Database:**
    Navigate to the backend directory and start the PostgreSQL database.
    ```bash
    cd backend
    docker compose up -d
    ```

## 4. Resetting the Local Database

PostgreSQL initialization scripts (like the one that enables the `pgvector` extension) only run when the database volume is first created. If you need to re-run initialization scripts or start with a fresh database:

1.  **Destroy the existing containers and volumes:**
    ```bash
    cd backend
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
- **Frontend (Vite 8):** `http://localhost:5173` (React SPA)

### 6.1 Individual Service Execution

If you need to run services separately:

**Backend only:**
```bash
./mvnw quarkus:dev -pl backend
```

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
To run the full-stack test suite (Backend JUnit + Frontend Vitest + Playwright):

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
npm run test
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
docker run --rm -v "<repo>:/src:ro" -v trivy-cache:/root/.cache aquasec/trivy:0.74.0@sha256:62b1e65e8869bc4b4c6aa4fa2b21595256c7c2f6018a9d9ad61caf87187c1969 fs \
  --severity CRITICAL,HIGH --ignorefile /src/.trivyignore --exit-code 1 --timeout 30m /src
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v trivy-cache:/root/.cache \
  -v "<repo>/.trivyignore:/cfg/.trivyignore:ro" aquasec/trivy:0.74.0@sha256:62b1e65e8869bc4b4c6aa4fa2b21595256c7c2f6018a9d9ad61caf87187c1969 image \
  --severity CRITICAL,HIGH --ignorefile /cfg/.trivyignore --exit-code 1 --timeout 30m <image>
```

The FS scan resolves Maven parent POMs from Maven Central and can be blocked there (`429 Too Many Requests`, `Retry-After: 1800` — the whole IP for 30 min). Locally, add `-v "<home>/.m2:/root/.m2:ro"` and `--offline-scan` to the FS command so the POMs come from the local Maven cache. The cache must hold the exact parent POMs and BOMs of the commit being scanned — run a local build of that commit first (e.g. `mvnw verify`); in offline mode a missing POM only produces a warning and its managed dependencies are skipped, so check the scan output for unresolved-POM warnings before accepting a clean result. This is how the v0.19.0 audit completed (POV-211).

Every `.trivyignore` entry carries an `exp:YYYY-MM-DD` date and a rationale; after expiry CI fails again and forces a re-check (remove the entry if the fix has shipped, otherwise extend it with a fresh rationale). The FS scan skips npm devDependencies — `npm audit` in `frontend/` covers those.

### 8.3 Frontend API Client (orval)
The typed client in `frontend/src/api/generated` is generated from the backend OpenAPI spec and committed. After any API change, regenerate and commit it:

```bash
./mvnw package -DskipTests -pl backend -am   # writes backend/target/openapi.json (no test classes)
npm run gen:api                                # orval, output.clean: true removes stale files
```

- Generate from a `package -DskipTests` build: a spec written while test classes are on the classpath contains test-only endpoints.
- orval is pinned exactly (`8.5.3`, no `^`) and raised only deliberately, together with a regeneration; `prettier: false` keeps the output independent of a globally installed prettier.
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

## 10. Production Stack (Local)

The production stack uses `docker-compose.prod.yml` with `.env.docker` (compose project `backend`) and runs the app, PostgreSQL, Prometheus, Grafana and the Cloudflare Tunnel (`cloudflared`). The tunnel is the only public entry: it routes `povzetkar.<domain>` to `app:8080`, TLS ends at Cloudflare, and no port is published for public traffic. There is no reverse proxy — the app sends its own security headers, CSP and caching headers and trusts `X-Forwarded-*` only from `cloudflared` ([ADR-0012](adr/0012-cloudflare-tunnel-only-edge.md), §13.5).

The dev stack (`docker-compose.yml`, same directory) is its own compose project `povzetkar-dev` with its own container names and host ports (dev DB 5434, test DB 5433, dev Grafana 3001, dev Prometheus 9090), so both run side by side. Never remove the `name:` from the dev file: the two files would again form one project, a dev `up` would recreate the prod Prometheus/Grafana, and `--remove-orphans` from either file would remove the other stack's containers (POV-214).

### 10.1 Build

Run from the `backend/` directory:

```bash
docker compose -f docker-compose.prod.yml --env-file ../.env.docker build
```

The `APP_VERSION` variable in `.env.docker` is used as the image tag (e.g. `povzetkar-app:0.18`).

### 10.2 Run

```bash
docker compose -f docker-compose.prod.yml --env-file ../.env.docker up -d
```

### 10.3 Update (rebuild + restart app only)

```bash
docker compose -f docker-compose.prod.yml --env-file ../.env.docker build --pull app
docker compose -f docker-compose.prod.yml --env-file ../.env.docker up -d --no-deps app
```

- `--pull` fetches the current base images (distroless, temurin, node, golang). Without it Docker
  reuses cached bases — during POV-207 the local base was months behind the one CI scans, so the
  deployed image carried Debian CVEs CI never saw.
- `--no-deps` keeps compose from also recreating dependencies (e.g. `postgres-prod`) when it detects
  a config difference in them (observed during POV-207).
- Before rebuilding, tag the running image for rollback:
  `docker tag povzetkar-app:<APP_VERSION> povzetkar-app:<APP_VERSION>-pre-<task>`.
- Before `up`: the deployed commit needs a green CI run (Trivy FS + image) not older than 7 days — see
  `docs/protocols/verification.md`, *Pre-deploy Verification*. Local Trivy (§8.2) only for a commit without a CI run.

### 10.4 Stop

```bash
docker compose -f docker-compose.prod.yml --env-file ../.env.docker down
```

Never add `-v` (it deletes the volumes, the production database included).

### 10.4a Network change (full restart)

The network `povzetkar-net` has a fixed subnet (`10.203.0.0/24`, dynamic addresses from `10.203.0.128/25`) so
that `cloudflared` keeps its fixed address `10.203.0.10`, the only peer the app trusts for `X-Forwarded-*`
(`QUARKUS_HTTP_PROXY_TRUSTED_PROXIES` in the same file — change both together). Docker cannot change an existing
network's IPAM settings, so a change to them (first applied in POV-214) needs the whole stack down and up — a
short outage including the DB. The subnet lies outside Docker's default address pools (172.17–31.0.0/16,
192.168.0.0/16); before choosing another one, check that no network uses it:
`docker network inspect $(docker network ls -q) --format '{{.Name}} {{range .IPAM.Config}}{{.Subnet}}{{end}}'`.

```bash
# 1. Containers attached to the network that compose does not manage keep it in use — list and remove them
#    (in POV-214: povzetkar-proxy, the removed Caddy service, and gifted_davinci, a stopped manual
#    cloudflared:latest run from 2026-06 — started with the same token it would become a second tunnel connector)
docker network inspect backend_povzetkar-net --format '{{range .Containers}}{{.Name}} {{end}}'
docker ps -a --filter network=backend_povzetkar-net --format '{{.Names}} {{.Status}} {{.Label "com.docker.compose.service"}}'
docker rm -f povzetkar-proxy gifted_davinci
# 2. Recreate
docker compose -f docker-compose.prod.yml --env-file ../.env.docker down   # no -v, no --remove-orphans
docker compose -f docker-compose.prod.yml --env-file ../.env.docker up -d
```

### 10.5 Notes

- `.env.docker` is in the project root (not `backend/`), hence `--env-file ../.env.docker`.
- The app is exposed via **Cloudflare Tunnel** at `https://povzetkar.juresa.si` — no ports need to be open on the host; the prod stack publishes only `127.0.0.1:5432` (DB) and `127.0.0.1:3000` (Grafana).
- Grafana: `http://127.0.0.1:3000` on the host (on a remote host: `ssh -L 3000:localhost:3000 <host>`). It has no public hostname on purpose — see §13.2.

## 11. Containerized Execution (Docker/Podman)

The application uses a multi-stage `Dockerfile` at the root of the project to build both the React frontend and the Quarkus backend into a unified production-ready image.

### 9.1 Building the Container Image

To build the container image, run the following command from the root directory:

```bash
docker build -t si.jumis/povzetkar:latest .
```

This process:
1.  **Builds the Frontend**: Compiles React and Vite-React-SSG artifacts.
2.  **Integrates Static Resources**: Embeds the built frontend into the Quarkus `META-INF/resources` directory.
3.  **Packages Backend**: Builds the Quarkus "runner" with all dependencies included.

### 9.2 Running the Container

The application requires several environment variables to function correctly. These must be passed to the container at runtime.

**Required Environment Variables:** (.env.docker)

| Variable          | Description                             | Example                                                                                                                    |
|:------------------|:----------------------------------------|:---------------------------------------------------------------------------------------------------------------------------|
| `DB_KIND`         | Database type                           | `postgresql`                                                                                                               |
| `DB_HOST`         | Database host                           | `povzetkar-db-dev` (if using Docker network) or `host.docker.internal` to connect to a service running on the host machine |
| `DB_PORT`         | Database port                           | `5434` (dev container; prod uses `postgres-prod:5432` inside the Docker network)                                           |
| `DB_NAME`         | Database name                           | `povzetkar`                                                                                                                |
| `DB_USER`         | Database username                       | `your_db_user`                                                                                                             |
| `DB_PASS`         | Database password                       | `your_db_password`                                                                                                         |
| `YOUTUBE_API_KEY` | YouTube Data API Key                    | `AIzaSy...`                                                                                                                |
| `GOOGLE_API_KEYS` | Google GenAI API Keys (comma-separated) | `AIzaSy...`                                                                                                                |
| `FRONTEND_URL`    | Frontend origin(s) and redirect base URL(s). Supports comma-separated list. | `http://localhost:5173`                                                                                                    |
| `CORS_METHODS`    | Allowed HTTP methods for CORS           | `GET, POST, ...`                                                                                                           |
| `CORS_HEADERS`    | Allowed HTTP headers for CORS           | `Content-Type, ...`                                                                                                        |
| `GOOGLE_OIDC_CLIENT_ID`     | Google OIDC Client ID (Production)      | `your-client-id.apps.googleusercontent.com`                                                                                |
| `GOOGLE_OIDC_CLIENT_SECRET` | Google OIDC Client Secret (Production)  | `GOCSPX-your-secret`                                                                                                       |

**Docker Run Example:**

Assuming the database is running in a container named `povzetkar-db-dev` on the same network (e.g., `povzetkar_default` created by docker-compose):

> **Note:** The network name `povzetkar_default` is derived from the directory name. If your directory is named differently, check the network name using `docker network ls`.
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

> **Note:** Ensure `DB_HOST` in `.env.docker` is set to your host's IP address accessible from the container. On many systems, you can use the special DNS name `host.containers.internal`. Otherwise, you may need to use a specific IP (e.g., `10.0.2.2` on some VM setups or your LAN IP). Podman does not share networks with Docker Compose by default.

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

*   **Endpoint:** `POST /api/v1/content-sources/enrich`
*   **Method:** `POST`
*   **Example using curl:**
    ```bash
    curl -X POST http://localhost:8080/api/v1/content-sources/enrich
    ```

#### 9.1.2 Scheduled Refresh

The service is configured to periodically refresh content source details automatically. By default, it runs weekly on Sunday at midnight.

*   **Configuration Property:** `povzetkar.enrichment.cron` (was `quarkus.scheduler.enrich-content-sources.cron` before POV-208)
*   **Location:** `backend/src/main/resources/application.properties`
*   **Default Value:** `0 0 0 ? * SUN` (weekly on Sunday at midnight)

    You can modify this cron expression in `application.properties` to adjust the schedule according to your needs.

### 9.2 Orchestrator and Briefing Service

The `OrchestratorService` is responsible for the end-to-end process of fetching new content, summarizing it, and generating daily briefings for active users. This service runs on a schedule.

#### 9.2.1 Scheduled Orchestration

The orchestration process is configured to run periodically. By default, it runs daily at midnight.

*   **Configuration Property:** `povzetkar.orchestrator.cron`
*   **Location:** `backend/src/main/resources/application.properties`
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
*   **Accessing logs via API:**
    ```bash
    curl -H "Authorization: Bearer <admin_token>" \
      "http://localhost:8080/api/v1/admin/audit-log?page=0&size=20"
    ```
*   **Direct Database Query:**
    ```sql
    SELECT * FROM audit_log ORDER BY created_at DESC LIMIT 10;
    ```

### 10.3 Performance Metrics

A consolidated metrics endpoint provides a snapshot of the system state with a single database roundtrip.

*   **Endpoint:** `GET /api/v1/admin/metrics`
*   **Captured Metrics:** Total/Running/Failed Background Jobs, Total/Enabled Topics, Total Content Items, and Idempotent Request count.
*   **Example using curl:**
    ```bash
    curl -H "Authorization: Bearer <admin_token>" http://localhost:8080/api/v1/admin/metrics
    ```

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

The monitoring infrastructure is defined in `backend/docker-compose.yml`:
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

**Verifying (production, local stack):**
```bash
# Public: must NOT be 200
curl -s -o /dev/null -w "%{http_code}\n" https://povzetkar.juresa.si/q/metrics   # 404

# Inside the Docker network: metrics served on the management port
docker exec povzetkar-prometheus wget -qO- http://app:9000/q/metrics | head -3

# Prometheus target health
docker exec povzetkar-prometheus wget -qO- http://localhost:9090/api/v1/targets | grep -o '"health":"[a-z]*"'
```

### 13.3 Dashboards-as-Code

Grafana is pre-configured with the **"Povzetkar Baseline"** dashboard, which tracks:
- **Briefing Health**: Success vs. Failure rates.
- **System Load**: Active orchestration threads.
- **Ingestion Success**: Content source creation and YouTube API errors.

**Accessing Grafana:**
- **Production**: `http://127.0.0.1:3000` on the host (on a remote host: `ssh -L 3000:localhost:3000 <host>`);
  admin password = `GF_ADMIN_PASSWORD` from `.env.docker`.
- **Dev stack**: `http://localhost:3001` (`docker-compose.yml`), default credentials `admin` / `admin`.

### 13.4 Custom Business Metrics

All custom Povzetkar metrics use the `povzetkar_` prefix:
- `povzetkar_briefing_generated_total`: Counter for success/failed briefings (with `status="success|failed"`, `topic_id`, `user_id` tags).
- `povzetkar_active_orchestrations_current`: Gauge for active threads.
- `povzetkar_source_added_total`: Counter for content sources added (with `platform` tag).
- `povzetkar_youtube_validation_failed_total`: Counter for source intake errors (with `error_type` tagging `unresolved_identifier`, `channel_not_found`, or `io_exception`).
- `povzetkar_transcript_retrieval_duration_seconds`: Timer for transcript fetching.
- `povzetkar_transcript_service_failed_total`: Counter for transcript failures (with `error_type` tagging `invalid_format` or `retrieval_exception`).
- `povzetkar_orchestration_failed_total`: Counter for high-level manual orchestration failures.

### 13.5 Production Logs & Request History (POV-210)

**Traffic path:** the Cloudflare tunnel routes `povzetkar.<domain>` straight to
`http://povzetkar-app:8080` (tunnel config in the Cloudflare dashboard; `docker logs
povzetkar-cloudflared` shows it as `Updated to new configuration … "service":"http://povzetkar-app:8080"`).
There is no reverse proxy and no host port for public traffic ([ADR-0012](adr/0012-cloudflare-tunnel-only-edge.md)),
so every request reaches the app through the tunnel. Cloudflare keeps no request logs on this plan — the app's
access log is the forensic record.

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
old format (below the POV-210 recipe).

**Access log** (`%prod`, logger category `io.quarkus.http.access-log`). One line per request, pattern:

```text
%t cf-ip=<CF-Connecting-IP> country=<CF-IPCountry> ray=<CF-Ray> user=%u "%<r" %s %b %D host="<Host>" ref="<Referer>" xff="<X-Forwarded-For>" "<User-Agent>"
```

- `cf-ip=` is the **client IP** (set by Cloudflare). Since POV-214 only the tunnel reaches the app, so it is
  trustworthy for every request.
- `ray=` is Cloudflare's request ID: search it in the Cloudflare dashboard (Security → Events) to see what
  Cloudflare did with the request.
- `user=` is the authenticated principal: the **Google subject** (`sub`, the numeric account ID) for browser
  sessions — map it to a user via `user_identities` (provider `google`, external ID) — or the mapped **e-mail**
  for API-key requests; `-` when anonymous.
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

**Reading the access log** — the distroless app image has no shell, so read the volume from a
throw-away container that borrows the app container's volumes (`--volumes-from`, read-only; works
while `povzetkar-app` exists, even stopped, and needs no volume name). In Git Bash prefix
`MSYS_NO_PATHCONV=1`, otherwise the container paths are rewritten. The file holds only access lines
(format `%s%n`, no logger prefix). The `sed` is anchored on the fixed line start and the server-set
fields and prints only parsed lines, so a crafted URL or header cannot replace the fields or pose as an entry;
a line whose `Host` or `Referer` contains a `"` is **dropped** — compare `wc -l` of the raw files with
`access.txt` when completeness matters. A value containing a **newline** could still start a forged line (log
messages are not escaped).

```bash
# All access-log entries, oldest file first, as:
# cf-ip country status method path time (2 fields) xff-first-hop user ray
MSYS_NO_PATHCONV=1 docker run --rm --volumes-from povzetkar-app:ro alpine:3.24.2 \
  sh -c 'cd /var/log/app && for i in $(seq 19 -1 1); do [ -f access.log.$i ] && cat access.log.$i; done; cat access.log' \
  | sed -nE 's/^\[([^]]+)\] cf-ip=(\S*) country=(\S*) ray=(\S*) user=(\S*) "(\S+) (\S+) [^"]*" ([0-9]{3}) \S+ \S+ host="[^"]*" ref="[^"]*" xff="([^", ]*).*/\2 \3 \8 \6 \7 \1 \9 \5 \4/p' \
  > access.txt
```

Access lines from before POV-214 (in `quarkus.log*`, format without `ray`/`user`/`host`/`ref`) — POV-210 recipe:

```bash
MSYS_NO_PATHCONV=1 docker run --rm --volumes-from povzetkar-app:ro alpine:3.24.2 \
  sh -c 'cd /var/log/app && for i in 10 9 8 7 6 5 4 3 2 1; do [ -f quarkus.log.$i ] && cat quarkus.log.$i; done; cat quarkus.log' \
  | grep 'access-log\]' \
  | sed -nE 's/^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9:,]+ (\S+ \S+ )?INFO +\[[^] ]*access-log\] \([^)]*\) \[([^]]+)\] cf-ip=(\S*) country=(\S*) "(\S+) (\S+) [^"]*" ([0-9]{3}) \S+ \S+ xff="([^", ]*).*/\3 \4 \7 \5 \6 \2 \8/p' \
  > access-pre-pov214.txt
```

### 13.6 Checking for Suspicious Traffic

1. **Cloudflare dashboard** — Security → Analytics / Events: request volume, blocked requests
   (the non-domestic traffic rule), top paths and countries. First line, since every request
   passes Cloudflare, including blocked ones.
2. **Access log** (§13.5, `access.txt` — fields: cf-ip, country, status, method, path, time (two
   fields), first `X-Forwarded-For` hop, user, ray):

   ```bash
   awk '$3 ~ /^[45]/ {print $3, $4, $5}' access.txt | sort | uniq -c | sort -rn | head -50   # 4xx/5xx by path
   awk '{print $1, $2}' access.txt | sort | uniq -c | sort -rn | head -20                      # top client IPs
   awk '{split($5, p, "?")} p[1] ~ /\/\./ {print $1, $3, $5}' access.txt | sort | uniq -c | sort -rn   # dot-segment probes (/.git, /.env, ...), query ignored
   awk '$1 != $8 {print $1, $8, $5}' access.txt                                                # cf-ip ≠ first X-Forwarded-For hop
   awk '$9 != "-" {print $9}' access.txt | sort | uniq -c | sort -rn                          # requests per signed-in principal
   ```

   The `cf-ip ≠ first hop` recipe lists requests where the client sent its own `X-Forwarded-For`.
   Since POV-214 there is no path around the tunnel (no host ports, no reverse proxy), so `cf-ip`
   is Cloudflare's value for every line. A `ray` value leads to the request in the Cloudflare
   dashboard (Security → Events).

   Probes the SPA fallback reroutes (no extension in the last segment, outside `/api`, `/q`,
   `/static`, `/assets`, … — see `SpaRoutingHandler`) get `200` with the SPA shell; others end in
   `404`/`400` (e.g. `/.env`, `*.php`, `/wp-json/batch/v1`) — see the backlog entry "SPA fallback
   answers 200 to probe paths".
3. **`audit_log` table** — matched `/api/v1` requests only (not unmatched probes), with the user:

   ```bash
   docker exec povzetkar-db-prod sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "
     SELECT response_status, http_method, request_path, count(*) FROM audit_log
     WHERE created_at > now() - interval '"'"'14 days'"'"' AND response_status >= 400
     GROUP BY 1, 2, 3 ORDER BY 4 DESC LIMIT 50"'
   ```

4. **Prometheus** — per-path counts even without the access log, from inside the Docker network:

   ```bash
   docker exec povzetkar-prometheus wget -qO- \
     'http://localhost:9090/api/v1/query?query=max_over_time(http_server_requests_seconds_count%5B14d%5D)'
   ```

   Limits: counters reset on every app restart (lower bounds only), Micrometer stops creating
   `uri` tags after 100 distinct paths per run, default 15-day retention, no IPs and no query
   strings.

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
    "type": "https://povzetkar.jumis.si/errors/validation-failed",
    "title": "Validation Failed",
    "status": 400,
    "detail": "The 'name' field cannot be empty.",
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

### 14.1a Transcript Provider Configuration

The transcript fetching pipeline supports multiple providers with runtime-configurable ordering and fallback.

| Config Property | Default | Description |
|:---|:---|:---|
| `transcript.providers.order` | `youtube-legacy,proxy,supadata` | Comma-separated list of providers tried in order. |
| `transcript.providers.youtube-legacy.enabled` | `true` | Enable/disable the YouTube legacy (direct scraping) provider. |
| `transcript.providers.youtube-legacy.jitter.min-ms` | `500` | Minimum random delay before each YouTube fetch (IP rate-limit protection). |
| `transcript.providers.youtube-legacy.jitter.max-ms` | `2500` | Maximum random delay before each YouTube fetch. |
| `transcript.providers.supadata.enabled` | `false` | Enable/disable the Supadata provider. Set to `true` and provide `SUPADATA_API_KEY` to activate. |
| `transcript.providers.supadata.lang` | `en` | ISO 639-1 language code for Supadata transcript requests. Falls back to first available language if the video has no transcript in the requested language. |

**Production today (verified in POV-214):** `youtube-legacy` serves the transcripts — it fetches directly from the
production host (the local Docker stack, §10, residential egress IP). `proxy` is the fallback, with
`TRANSCRIPT_PROXY_URL` pointing at Railway; `supadata` is disabled. The earlier OCI deployment, whose datacenter IPs
YouTube blocked, is gone.

**Enabling Supadata as primary provider** (if YouTube blocks the production host's IP):

```properties
transcript.providers.order=supadata,youtube-legacy
transcript.providers.supadata.enabled=true
# SUPADATA_API_KEY env var must be set
```

**Metrics emitted** (all tagged with `provider` label):

- `transcript.fetch.attempts` — counter per provider
- `transcript.fetch.successes` — counter per provider
- `transcript.fetch.failures` — counter tagged with `provider` and `reason`
- `transcript.fetch.duration` — timer per provider

### 16.1b Transcript Proxy Host (Synology DS220j + Tailscale)

The `proxy` provider calls a standalone FastAPI service in `transcript-proxy/` that fetches a
single YouTube transcript per request from a host whose IP YouTube does not block.

> [!NOTE]
> **Status (POV-214): this Synology setup is an option, not in use.** Production runs on the
> local Docker stack (§10) and fetches with `youtube-legacy` directly; `proxy` is the fallback and
> points at **Railway** (§14.1a). The Synology/Tailscale setup below (POV-196, built for the former
> OCI host) stays documented, and its code stays in `transcript-proxy/`, in case the production
> host's IP gets blocked. To use it, the production host must join the tailnet.

> [!NOTE]
> **Why self-hosted:** Railway's shared datacenter egress IP was soft-throttled by YouTube
> (intermittent `502 proxy_blocked` interleaved with `200`s). The DS220j serves from a
> residential egress IP, which YouTube throttles far less. See ADR-0009 and the POV-196
> retrospective.

**Connectivity model:** Both the production host and the Synology join the same tailnet. The
backend reaches the proxy at the Synology's **raw tailnet IP** (`http://100.x.y.z:8000`) — *not*
a MagicDNS name, because the backend runs in a Docker bridge network that does not resolve
MagicDNS. The MagicDNS name (e.g. `ds220j.<tailnet>.ts.net`) is a human-readable alias only.
Traffic is plain HTTP; Tailscale (WireGuard) already encrypts the path, so no TLS termination is
needed on the Synology.

**Backend configuration** (`TRANSCRIPT_PROXY_URL`, `TRANSCRIPT_PROXY_API_KEY`):

| Variable | Active (Synology) | Fallback (Railway) |
|:---|:---|:---|
| `TRANSCRIPT_PROXY_URL` | `http://<synology-tailnet-ip>:8000` | `https://povzetkar-production.up.railway.app` (the default if unset) |
| `TRANSCRIPT_PROXY_API_KEY` | the shared key | the **same** shared key |

> [!IMPORTANT]
> **Both hosts must accept the same `X-API-Key`.** The backend sends one
> `TRANSCRIPT_PROXY_API_KEY`; whichever host is active must be configured with that exact value,
> or a fallback flip will return `401`. On the tailnet the key is defense-in-depth (the tailnet
> is the primary boundary); on Railway it is the sole boundary and is mandatory.

**Flip-back to Railway (manual fallback):** Only one host is active at a time. If the Synology
proxy (once in use) is unreachable or misbehaving, on the production host:

```bash
# In .env.docker (project root), set TRANSCRIPT_PROXY_URL back to Railway
# (or remove the override entirely — the compose default is the Railway URL):
#   TRANSCRIPT_PROXY_URL=https://povzetkar-production.up.railway.app
cd backend
docker compose -f docker-compose.prod.yml --env-file ../.env.docker up -d --no-deps app   # recreate to pick up the env
```

The provider fallback chain (`transcript.providers.order`) also degrades automatically if the
active proxy is down — flip-back is for restoring the proxy path.

**Synology deployment notes (DS220j gotchas):**

- **Runtime is the box's built-in Python 3.8** (`/bin/python3`) — SynoCommunity `python312` is
  not required. `youtube-transcript-api 1.2.4` supports 3.8; `requirements.txt` is pinned to the
  3.8-compatible set (`fastapi==0.124.4`, `uvicorn[standard]==0.33.0`).
- **`/tmp` is mounted `noexec`.** A venv or pip cache under `/tmp` will fail to load compiled
  extensions (`pydantic_core ... failed to map segment from shared object`). The proxy MUST be
  installed on an exec-capable `/volume1` path (e.g. `/volume1/transcript-proxy`).
- **Process supervision:** a DSM Task Scheduler **Boot-up** task (user `root`) runs
  `run-proxy.sh`, which sources `.env` (the API key, `chmod 600`, gitignored) and launches
  `uvicorn ... --host 0.0.0.0 --port 8000`, appending to `proxy.log`. The proxy is started once
  at boot; there is **no crash-restart supervision yet** (backlog) — if uvicorn dies mid-run it
  stays down until reboot, so monitor and flip to Railway if needed.
- **Updating the code on the box:** copy the repo file over with
  `scp -O transcript-proxy/main.py minimax@<host>:/volume1/transcript-proxy/main.py`, then
  restart (stop the running uvicorn and re-run `run-proxy.sh`, or run the boot task). The `-O`
  flag is required — DSM's SSH has no SFTP subsystem, so a plain `scp` fails with
  `subsystem request failed on channel 0`.

**Health check & per-request diagnostics:** `GET /health` (no auth) returns `{"status":"ok"}`.
Every transcript request logs its outcome to `proxy.log` with the exact cause, e.g.
`video_id=... blocked by YouTube: RequestBlocked -> 502 proxy_blocked` — distinguishing
`IpBlocked` from `RequestBlocked` (both `502 proxy_blocked`) and `upstream_error` without
guessing.

### 14.2 How Secrets Are Provided

Secrets are injected via **environment variables** and resolved by Quarkus Config at startup. The approach varies by environment:

| Environment | Method | Details |
|:---|:---|:---|
| **Local Dev** | `.env` file | Copy `backend/.env.example` to `backend/.env` and fill in real values. Loaded by Quarkus Dev mode. |
| **Docker** | `--env-file` | Create a `.env.docker` file (never committed) and pass via `docker run --env-file .env.docker`. |
| **CI/CD** | GitHub Secrets | Injected as environment variables in the `ci.yml` workflow via `${{ secrets.* }}`. |
| **Kubernetes** | K8s Secrets | Mount as environment variables from a `Secret` resource. See deployment docs. |

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

**Browser session (OIDC):** the session cookie is `q_session` (Quarkus default name, path `/`, `SameSite=Lax`, `Secure` when the request arrives over HTTPS — in production via the `X-Forwarded-Proto: https` that `cloudflared` forwards and the app accepts only from `cloudflared`'s fixed address, §10.4a; checked in the deploy smoke test after every change to the proxy trust). It lives as long as the Google ID token (≈1 h) plus `session-age-extension` (5 min); `token.refresh-expired` is off, so an expired session re-authenticates via Google (silently if the Google session is still active). Logout (`/logout`) expires it through `OidcSession.logout()`.

### 14.5 Security Best Practices

1.  **Never commit `.env` files.** Both `backend/.env` and `.env.docker` are listed in `.gitignore`.
2.  **Rotate API keys periodically.** Google API keys and OIDC secrets should be rotated at least annually.
3.  **Use separate keys per environment.** Never share production API keys with development or test environments.
4.  **Principle of Least Privilege.** API keys should be scoped to the minimum required permissions (e.g., YouTube Data API read-only).

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
5.  **Audit key usage.** Monitor API key usage in the Google Cloud Console to detect unauthorized access or quota anomalies.
