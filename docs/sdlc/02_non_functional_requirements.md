# Non-Functional Requirements (NFR) - Project Povzetkar

**Version:** 1.0
**Date:** August 15, 2025

## 1. Introduction

This document defines the quality attributes and constraints of the Povzetkar system. It specifies *how well* the system should perform its functions.

> [!NOTE]
> Most targets were set in August 2025 for the full vision (conversational API, horizontal scaling, message queue)
> and are **targets, not measured guarantees** — the deployed system is a single application instance. Requirements
> restated or added since (P-5, SEC-1, SEC-5, SEC-6) describe the current state; items marked *Status* below note
> where the system differs. A full review of this catalogue is in the backlog.

## 2. Performance

*   **P-1 (API Response Time):** The p95 latency for all user-facing API endpoints (e.g., conversational queries) must be under **800ms**.
*   **P-2 (Ingestion Throughput):** The system must be capable of ingesting and processing at least **1,000** new content items per hour.
*   **P-3 (Report Generation):** Generation of a daily briefing report must complete within **5 minutes** of the scheduled time.
*   **P-4 (Startup Time):** In a containerized environment, the backend service should be ready to accept requests within **5 seconds** of container start (leveraging Quarkus native compilation). *Status: the image runs on the JVM (distroless Java 25), not as a native binary; not measured.*
*   **P-5 (Frontend Initial Load):** The landing-page **initial-load JavaScript payload** should stay at or below **~250 KB gzip** (aspirational ceiling), and the landing-page Lighthouse **performance score** should stay at or above **90** (measured on a local production build via `vite preview`). *Baseline (POV-202, 2026-07-05):* initial-load JS **167 KB gzip**, Lighthouse performance **93** (FCP 1.8 s, LCP 2.8 s) — measured after route-level code-splitting reduced the bundle from a 370 KB / score-85 pre-optimization state. This NFR is **aspirational / not yet CI-enforced**; it is the explicit anchor for a future bundle-size budget and Lighthouse-CI gate (see `docs/backlog/improvements.md`).

## 3. Scalability

*   **SC-1 (Horizontal Scaling):** The Data Ingestion and Content Processing services must be stateless and designed to scale horizontally. Adding more container instances must linearly increase the processing throughput.
*   **SC-2 (Database Scaling):** The database schema must be optimized for read-heavy workloads typical of RAG applications. It should support read replicas to handle increased query load from the conversational API.
*   **SC-3 (Source Elasticity):** The system architecture must support the addition of new data sources (e.g., new social media platforms) without requiring a full redesign of the core processing pipeline.

## 4. Reliability & Availability

*   **R-1 (Uptime):** The user-facing Conversational API must have a minimum uptime of **99.9%**.
*   **R-2 (Fault Tolerance):** The failure to process a single content item must not halt the entire processing pipeline. The system should log the error, mark the item as failed, and continue processing other items.
*   **R-3 (Data Durability):** All processed data, summaries, and user interactions stored in the PostgreSQL database must be backed up regularly according to a defined schedule (daily snapshots).
*   **R-4 (Asynchronous Processing):** The use of a message queue ensures that ingestion spikes do not crash the system. Requests are queued and processed as resources become available. *Status: there is no message queue; orchestration runs in-process as background jobs with a per-date lock, a per-run time budget and paced, app-wide serialized YouTube fetches.*

## 5. Security

*   **SEC-1 (Data in Transit):** All traffic between clients and the system must be encrypted using TLS 1.2 or higher: TLS ends at Cloudflare, and the Cloudflare Tunnel to `cloudflared` is encrypted. Traffic inside the isolated production Docker network (`cloudflared` → app → database, Prometheus, Grafana) is not TLS-encrypted — accepted, since no port of that network is reachable from outside the host ([ADR-0012](../adr/0012-cloudflare-tunnel-only-edge.md)). *(Restated in POV-214; the original wording required TLS between all internal services.)*
*   **SEC-2 (Data at Rest):** Sensitive information in the database, such as API keys for external services, must be encrypted.
*   **SEC-3 (Authentication & Authorization):** Access to the administrative functions of the application must be protected. User-facing APIs should be secured using a standard protocol like **OAuth2/OIDC**.
*   **SEC-4 (Dependency Management):** The project must include tools to scan for and report vulnerabilities in third-party libraries.
*   **SEC-5 (HTTP Security Headers):** Every response of the public HTTP port must carry HSTS, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, a `Referrer-Policy`, a `Permissions-Policy` and exactly one enforced `Content-Security-Policy` — the strict policy (no inline script except by hash, no `eval`) for the SPA and the API, a legacy policy only for the server-rendered UI. The application sends them itself; delivery is verified in the test suite (POV-214, ADR-0012; TC-SEC-009).
*   **SEC-6 (Proxy Trust):** `Forwarded`/`X-Forwarded-*` headers may change the scheme, host or client address only when they come from the edge connector (`cloudflared`); from any other peer they are ignored (POV-214, ADR-0012; TC-SEC-010). Even from `cloudflared` the client address derived from `X-Forwarded-For` is its first element, which the client can set; any decision based on the client IP must use Cloudflare's `CF-Connecting-IP`.
