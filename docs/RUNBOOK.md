# Application Runbook

**Version:** 1.1

## Revision History
| Version | Date               | Author       | Changes                                                             |
|---------|--------------------|--------------|---------------------------------------------------------------------|
| 1.1     | September 17, 2025 | Povzetkar AI | Added instructions for verifying the pgvector extension.            |
| 1.0     | August 16, 2025    | Povzetkar AI | Initial version based on the upcoming Quarkus multi-module project. |

## 1. Introduction

This document provides all the necessary instructions for setting up the local development environment, running the application, and executing tests.

## 2. Prerequisites
- **Java:** JDK 21 or later.
- **Maven:** Version 3.8.x or later.
- **Docker & Docker Compose:** For running the local development environment.
- **IDE:** IntelliJ IDEA or VS Code are recommended.
- **Git:** For version control.

## 3. Local Environment Setup
The entire local environment is managed by Docker Compose.

1.  **Clone the repository:**
    ```bash
    git clone <repository-url>
    cd <repository-directory-name>
    ```

2.  **Create the Environment File:**
    The Docker Compose setup relies on a `.env` file for database credentials. Create this file by copying the provided template.

    *   **On Windows (Command Prompt or PowerShell):**
        ```shell
        copy .env.example .env
        ```
    *   **On Linux or macOS:**
        ```shell
        cp .env.example .env
        ```

3.  **Start the environment for the first time:**
    This command will create the Docker volumes and run the initial database scripts.
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

## 7. Running Tests
To run the full test suite:
```bash
./mvnw verify
```
