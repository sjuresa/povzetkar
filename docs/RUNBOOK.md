# Application Runbook

**Version:** 1.0
**Date:** August 16, 2025

## Revision History
| Version | Date            | Author       | Changes                                                             |
|---------|-----------------|--------------|---------------------------------------------------------------------|
| 1.0     | August 16, 2025 | Povzetkar AI | Initial version based on the upcoming Quarkus multi-module project. |

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
    cd povzetkar
    ```
2.  **Start the environment:**
    This command will start the PostgreSQL database and any other required services.
    ```bash
    docker-compose up -d
    ```
3.  **Run the application in development mode:**
    Quarkus's dev mode provides live reloading, making development fast and efficient.
    ```bash
    ./mvnw quarkus:dev
    ```
    The application will be available, and you can start making changes.

## 4. Running Tests
To run the full test suite:
```bash
./mvnw verify
```
