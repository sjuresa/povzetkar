# Non-Functional Requirements (NFR) - Project Povzetkar

**Version:** 1.0
**Date:** August 15, 2025

## 1. Introduction

This document defines the quality attributes and constraints of the Povzetkar system. It specifies *how well* the system should perform its functions.

## 2. Performance

*   **P-1 (API Response Time):** The p95 latency for all user-facing API endpoints (e.g., conversational queries) must be under **800ms**.
*   **P-2 (Ingestion Throughput):** The system must be capable of ingesting and processing at least **1,000** new content items per hour.
*   **P-3 (Report Generation):** Generation of a daily briefing report must complete within **5 minutes** of the scheduled time.
*   **P-4 (Startup Time):** In a containerized environment, the backend service should be ready to accept requests within **5 seconds** of container start (leveraging Quarkus native compilation).

## 3. Scalability

*   **SC-1 (Horizontal Scaling):** The Data Ingestion and Content Processing services must be stateless and designed to scale horizontally. Adding more container instances must linearly increase the processing throughput.
*   **SC-2 (Database Scaling):** The database schema must be optimized for read-heavy workloads typical of RAG applications. It should support read replicas to handle increased query load from the conversational API.
*   **SC-3 (Source Elasticity):** The system architecture must support the addition of new data sources (e.g., new social media platforms) without requiring a full redesign of the core processing pipeline.

## 4. Reliability & Availability

*   **R-1 (Uptime):** The user-facing Conversational API must have a minimum uptime of **99.9%**.
*   **R-2 (Fault Tolerance):** The failure to process a single content item must not halt the entire processing pipeline. The system should log the error, mark the item as failed, and continue processing other items.
*   **R-3 (Data Durability):** All processed data, summaries, and user interactions stored in the PostgreSQL database must be backed up regularly according to a defined schedule (daily snapshots).
*   **R-4 (Asynchronous Processing):** The use of a message queue ensures that ingestion spikes do not crash the system. Requests are queued and processed as resources become available.

## 5. Security

*   **SEC-1 (Data in Transit):** All communication between the frontend, backend services, and the database must be encrypted using TLS 1.2 or higher.
*   **SEC-2 (Data at Rest):** Sensitive information in the database, such as API keys for external services, must be encrypted.
*   **SEC-3 (Authentication & Authorization):** Access to the administrative functions of the application must be protected. User-facing APIs should be secured using a standard protocol like **OAuth2/OIDC**.
*   **SEC-4 (Dependency Management):** The project must include tools to scan for and report vulnerabilities in third-party libraries.