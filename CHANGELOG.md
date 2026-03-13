# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
## [Unreleased]

## [v0.11.0] - 2026-03-12

### Summary

Version 0.11.0 introduces significant enhancements to real-time observability and security. This release features a new SSE-based event stream for live job updates, comprehensive API request auditing, and a centralized permission-based access control system. Operational improvements include JPQL projection optimizations, consolidated health metrics, and standardized error handling across the entire API layer. The integration of static analysis and vulnerability scanning further hardens the application's security posture.

### Added
- **API:** Implemented a Real-Time Server-Sent Events (SSE) stream endpoint (`GET /api/v1/events/stream`) providing live updates for `JOB_STATUS_CHANGED` and periodic system `PULSE` events.
- **API:** Introduced `EventBroadcaster`, a reactive service using Mutiny `BroadcastProcessor` to manage multi-subscriber SSE broadcasting with automatic pulse heartbeats.
- **API:** Integrated CDI event decoupling in `BackgroundJobService` to trigger real-time status broadcasts without direct coupling to the SSE infrastructure.
- **Security:** Refactored all `@RolesAllowed` annotations and programmatic security checks to use centralized constants in `Permission.java`, eliminating magic strings like `"user"` and `"admin"` across the entire API layer.
- **API:** Implemented `CorrelationIdFilter`, a global JAX-RS filter that reads or auto-generates an `X-Correlation-ID` UUID for every request, injects it into the MDC for structured log correlation, and echoes it back in the response header.
- **API:** Implemented `AuditLogFilter`, a global JAX-RS `ContainerResponseFilter` that persists one `AuditLog` record per `/api/v1/` request (HTTP method, path, response status, duration, authenticated user, correlation ID) in an isolated transaction. Audit failures are silently logged and never surface as API errors.
- **API:** Exposed `GET /api/v1/admin/audit-log` — a paginated, admin-only endpoint returning API request audit log entries sorted by most recent first, secured with the new `ADMIN_AUDIT_LOG_VIEW` permission.
- **API:** Exposed `GET /api/v1/admin/metrics` — an admin-only endpoint returning live aggregate system health counts (total/running/failed jobs, topics, content items, idempotency cache entries), secured with `ADMIN_DASHBOARD_VIEW`.
- **Database:** Added `V19__CreateAuditLogTable.sql` Flyway migration creating the `audit_log` table with a soft FK to `users (ON DELETE SET NULL)` and indexes on `created_at` and `user_id`.
- **Security:** Added `ADMIN_AUDIT_LOG_VIEW` granular permission to `Permission` enum (automatically granted to `admin` role via `EnumSet.allOf`).
- **Observability:** MDC `correlationId` is now explicitly propagated to background worker threads spawned by `Infrastructure.getDefaultExecutor()` in `BackgroundJobService`, ensuring all background job logs include the originating request's correlation ID.
- **UI:** Updated `admin.js` to correctly handle and display messages from the standardized `{ data, error }` response envelopes (standardized error handling and dashboard observability).
- **API:** Implemented JAX-RS `IdempotencyFilter` utilizing an `@Idempotent` name binding to safely cache and replay async `POST /commands/*` responses based on the `Idempotency-Key` header without redundant processing.
- **API:** Implemented global ExceptionMappers to translate all errors (`ConstraintViolationException`, `SecurityException`, `WebApplicationException`, and uncaught system errors) into the standard `ApiEnvelopeDTO` using the RFC 7807 problem details structure.
- **API:** Introduced `MessageDTO` for standardized simple message responses.
- **API:** Configured `quarkus-smallrye-openapi` to automatically generate API specifications and expose Swagger UI for local development.
- **API:** Added comprehensive OpenAPI annotations to all JAX-RS resource classes, including specific response envelope schemas (`ApiEnvelopeDTO`) and status codes.
- **API:** Implemented Backend-For-Frontend (BFF) Dashboard endpoint (`GET /api/v1/dashboard`) aggregating user-specific active topics and weekly briefing generation statistics, securely preventing frontend N+1 queries.
- **API:** Promoted `ContentItem` to a first-class resource with standard `GET` and explicit action command endpoints.
- **API:** Added user-facing job endpoints for monitoring async operations (`GET /api/v1/jobs/{id}`).
- **API:** Implemented Admin Command Center APIs (`/api/v1/admin/jobs`) for asynchronous background job management, including triggering, paginated history, and job detail retrieval.
- **Security:** Integrated SpotBugs into the Maven `verify` phase to automate static application security testing (SAST) and enforce code quality standards.
- **Security:** Integrated Trivy into the GitHub Actions CI pipeline for automated dependency vulnerability scanning, explicitly blocking builds on HIGH or CRITICAL issues.
- **Security:** Explicitly disabled Swagger UI in the production profile for enhanced security.
- **Security:** Added critical lockout protection for administrator roles, preventing self-removal of the admin role and accidental removal of the last system administrator.
- **Security:** Enhanced BackgroundJob error handling to sanitize outgoing messages, hiding raw stack traces from end users.
- **API:** Implemented Core REST APIs for Topics (`/api/v1/topics`) and Content Sources (`/api/v1/sources`) adhering to the Backend-For-Frontend (BFF) pattern using DTOs and MapStruct `@Mapper` interfaces. Includes pagination support for collection endpoints.
- **Security:** Implemented Permission-Based Access Control (PBAC) foundations. Mapped database roles (`admin`, `user`) to granular capabilities (e.g., `TOPIC_VIEW`, `ADMIN_SYSTEM_TRIGGER`) via `RolePermissionsMap` and dynamically injected them into the `QuarkusSecurityIdentity`.
- **API:** Implemented a global JAX-RS `ContainerResponseFilter` (`GlobalResponseEnvelopeFilter`) to automatically wrap all successful JSON responses in a standard `{ data, meta, error }` envelope, improving consistency and reducing boilerplate.
- **Security:** Implemented Optimistic Locking using the `@Version` field on all auditable entities. The API now requires valid quotes `If-Match` headers for all `PATCH`, `PUT`, and `DELETE` state mutations, returning `428 Precondition Required` or `409 Conflict` appropriately.
- **Background Jobs:** Introduced a persistent job execution framework with `BackgroundJob` entity, `BackgroundJobService`, and async orchestration integration using `Infrastructure.getDefaultExecutor()`.
- **API:** Standardized error response structure using `ApiEnvelopeDTO.error()` across all exception mappers, including `BadRequestExceptionMapper` and `OptimisticLockExceptionMapper`.
- **API:** Refactored `OrchestratorResource` to utilize standard JAX-RS exception mappers for error responses, improving internal error sanitization.
- **API:** Centralized `pageSize` clamping logic in `AdminApiResource` for consistency across paginated resources.
- **API:** Implemented systemic sorting and filtering across all list endpoints (e.g., `Topic`, `Briefing`, `User`) using generic `FilterBuilder` and `QueryParser` to safely parse `?sort=` fields without HQL injection vulnerabilities.

### Refactored
- **API:** Optimized `AdminApiResource.getAuditLog` using JPQL Constructor Projection (`SELECT NEW ...`) to eliminate full entity hydration and improve database-level projection efficiency.
- **API:** Consolidated `AdminApiResource.getMetrics` endpoint logic to use a single native query with sub-selects, reducing database roundtrips from 7 to 1.
- **API:** Cleaned up unused entity imports in `AdminApiResource` after transitioning to native and specialized JPQL queries.

### Changed
- **API:** Replaced ad-hoc `BadRequestExceptionMapper` and manual error responses with a unified, centralized exception handling architecture to ensure complete API response envelope coverage and to properly sanitize internal error messages (500 Internal Server Error) from reaching the client.
- **API:** Refactored Topics and ContentSources to use `PATCH` instead of `PUT` for updates, aligning with the core REST API contract.
- **API:** Refactored endpoint paths for actionable commands to follow CQRS patterns (e.g., `POST /api/v1/topics/{id}/commands/generate-briefing`, `POST /api/v1/content-items/{id}/commands/retry`).
- **API:** Enhanced `ContentSourceResource` with duplicate validation during `PATCH` operations and extracted repeated validation logic in `ContentItemApiResource` into shared helpers.
- **API:** Refactored all REST API resources (`Topic`, `ContentSource`, `User`, `Role`, `Job`, `Admin`, `Dashboard`, `Briefing`, `SourceItem`, `ContentItem`) to return raw DTOs or `PaginatedResponse` objects, delegating envelope wrapping to the centralized filter.
- **API:** Refactored `BaseApiResource` and all derived resource classes to use **Constructor Injection** for `UserContextService` and other dependencies, aligning with architectural best practices and improving testability.
- **API:** Optimized `DashboardApiResource` by inlining query orchestration logic and fixing type safety issues in the reactive pipeline recovery handlers.
- **Security:** Enforced instance-level ownership authorization across Job and ContentItem APIs to ensure users only access their own resources.
- **Testing:** Implemented the Test Data Builder pattern for core entities (`User`, `Topic`, `ContentSource`) to streamline test data generation and resolve testing brittleness. Refactored the entire test suite to use these builders.
- **API:** Refactored `ContentSourceResource` and Admin UI to utilize the new `/api/v1/sources` standard path and DTO payloads.
- **Database Schema:** Added `last_run_date` to the `topics` table via Flyway migration (`V14__add_last_run_date_to_topics.sql`).

### Documentation
- **Frontend Specification:** Finalized UI/UX requirements for the v0.11.0 API-first redesign (`docs/analysis/frontend-specification.md`).
- **API Contract:** Finalized the Backend-For-Frontend REST API contract, standardizing pagination, list responses, enum values, and strict HTTP code usage (`docs/analysis/api-design.md`).

## [v0.10.0] - 2026-02-20

### Summary

Version 0.10.0 transforms the application into a secure, multi-tenant platform. This major release integrates Google OIDC for seamless authentication, ensures strict data isolation so users only see their own topics and content sources, and provides a polished developer experience with OpenAPI documentation and standardized project structures. Additionally, structural enhancements like a minimum duration filter for videos and robust auditing capabilities have been introduced.

### Added
- **Authentication:** Integrated Google OIDC (`quarkus-oidc`) for secure user authentication and identity linking, including a programmatic local-only logout flow that preserves the global Google session.
- **Security (Multi-tenancy):** Implemented multi-tenancy for topics. Users can now only view and manage their own topics and associations with content sources. Endpoints enforce ownership and return 404 for unauthorized access.
- **UI Enhancements:** Implemented `/ui/system/changelog` and `/ui/system/roadmap` pages to display project documentation rendered as HTML, with navigation links added to the home page.
- **API Documentation:** Integrated `quarkus-smallrye-openapi` to expose OpenAPI documentation and Swagger UI at `/q/swagger-ui`.
- **Content Filtering:** Implemented a configurable minimum duration filter for YouTube videos. Videos shorter than the threshold (default: 60 seconds) are marked with a new `SKIPPED_TOO_SHORT` status.
- **Admin UI:** Added a "Modified At" column to the Admin Dashboard tables.
- **Auditing:** Added `updated_at` tracking to all major entities (`ContentItem`, `Briefing`, `Summary`, `ContentSource`, `Topic`).

### Documentation
- **Agent Protocols:** Refactored `AGENT.md` into modular protocol files in `docs/protocols/`, optimized for token efficiency, and added a protocol for using the `context7` MCP server.
- **Project Structure:** Created `docs/overview/project-structure.md` to document the project's directory layout and architecture.
- **Testing Guide:** Refactored `docs/guides/testing.md` to clarify the usage of `@QuarkusTest` vs `@QuarkusIntegrationTest` and deprecated `docs/guides/mastering-testing.md`.
- **ADR:** Initialized `docs/adr/` with the first Architectural Decision Record (ADR-001) and added ADR-002 documenting the decision to use OIDC for authentication.

## [v0.9.0] - 2026-02-05

### Summary

Version 0.9.0 focuses on **Operational Refinement & Admin Tools**. This release stabilizes the application's core logic by introducing a Circuit Breaker pattern to increase resilience against external API failures (like GenAI 503s) and enhances the administrator's toolkit with features like Orchestration Preview, GenAI usage tracking, and history pagination. A comprehensive Methodology guide has also been established.

### Added
- **Resilience:** Implemented a Circuit Breaker pattern for AI API calls. The system now pauses orchestration for a specific client after a configurable number of consecutive 503 errors (`povzetkar.orchestrator.circuit-breaker.threshold`).
- **Observability:** Added `error_count` to the `api_key_usage` table to track API failures separately from successful usage.
- **Observability:** Added debug logging for AI response headers to facilitate future quota tracking research.
- **Admin:** Implemented persistent tracking of GenAI API calls per API key. The daily usage count is now stored in the database and displayed on the Admin Dashboard.
- **UI:** Implemented centralized date/time formatting via `DateTimeService`.
- **UI:** Added timezone configuration (`povzetkar.ui.timezone`) to `application.properties` to display times in the user's local timezone (default: `Europe/Ljubljana`).
- **UI:** Implemented pagination for all history tables on the Admin page. The page size is configurable via `povzetkar.ui.admin.page-size`.
- **Orchestration:** Implemented a "Preview" mode to fetch new content without summarization, returning a count of pending items.
- **Orchestration:** Implemented manual status management in the Admin UI, allowing users to change the status of pending and failed items.
- **Database:** Added `IGNORED` to the `ProcessingStatus` enum and created a Flyway migration to update the database schema.

### Changed
- **Dependency:** Upgraded `google-genai` library from `1.9.0` to `1.34.0`.
- **Usage Tracking:** Refined API usage tracking to only increment the usage count on successful calls. 503 errors now increment the new `error_count` instead.
- **UI:** The "Failed Content Items" table in the Admin UI has been renamed to "Failed and Ignored Content Items" and now displays items with `FAILED`, `RETRY_LATER`, and `IGNORED` statuses.
- **UI:** Replaced individual status change buttons with a dropdown menu for more flexible status management.

### Documentation
- **Methodology:** Created `docs/process/methodology.md`, a definitive guide consolidating the project's development processes, roles, and lifecycle. Updated `07_development_methodology_comparison.md` to reference it.
- **Runbook:** Added documentation for the new `povzetkar.ui.admin.page-size` configuration property.
- **Research:** Added `docs/spikes/mcp-research.md` detailing the Model Context Protocol (MCP) and a strategy for integrating it with the agent workflow.

## [0.8.0] - 2026-01-20

### Summary

Version 0.8.0 introduces **Standalone Application Deployment**. It provides the necessary configuration to build and run the Quarkus application as an immutable, OCI-compliant container image (via Jib) without relying on development mode. This release also significantly improves observability by implementing a structured Logfmt logging pattern for key processing steps.

### Added
- **Containerization:** Added Jib support for building OCI-compliant container images without a Docker daemon (`mvn clean package -Dquarkus.container-image.build=true`).
- **Documentation:** Updated `RUNBOOK.md` with instructions for running the containerized application using Docker and Podman.
- **Testing Guide:** Added `docs/guides/testing.md` documenting patterns for transactional tests, mocking, and reactive testing.
- **Observability:** Implemented a consistent structured logging format (Logfmt) for key processing steps to improve observability and simplify log analysis.

### Changed
- **API:** Refactored the manual orchestration endpoint (`POST /api/v1/orchestrator/run`) to accept a JSON request body (`OrchestrationRequest`) instead of multiple query parameters. This improves API clarity and extensibility.
- **Frontend:** The AJAX form submission logic in `admin.js` has been generalized and made more robust, correctly serializing form state (including all checkboxes) into a JSON payload.
- **Dependency Management:** Removed explicit versions for `slf4j`, `logback`, and `mockito` in `pom.xml`, delegating version management to the `quarkus-bom` to ensure compatibility and reduce maintenance.
- **Logging:** Refactored logging in `OrchestrationManager`, `ContentCreationService`, `VideoSummaryService`, and `YouTubeSummarizationStrategy` to use a new thread-safe `StructuredLogger` utility. Standardized on using the public YouTube video ID and title in logs for easier correlation.

## [0.7.0] - 2026-01-13

### Summary

Version 0.7.0 focuses on **Operational Excellence**, starting with a major framework upgrade to Quarkus 3.27.1 LTS. This ensures the platform is built on a secure, stable, and modern foundation. This release also introduces significant improvements to administrative control, allowing for granular orchestration and fine-grained topic management.

### Added
- **Admin UI Enhancements:** The Admin UI has been updated to provide greater visibility and control. Tables for pending, failed, and recent items now display the associated Topic, transcript availability, creation and publication dates, and video duration. Failed items now show detailed error codes and messages.
- **Manual Transcript Fetching:** Added a "Fetch Transcript" button to the Admin UI for pending and failed items, allowing administrators to manually trigger transcript retrieval via a new `POST /api/v1/admin/content-items/{id}/fetch-transcript` endpoint.
- **Backend Persistence:** Added `error_code` and `error_message` columns to the `content_items` table to support better error tracking and observability.
- **Topic Management:** Added the ability to enable or disable individual topics. Disabled topics are now skipped by the orchestration process.
- **Granular Orchestration:** The manual orchestration trigger now supports independent execution of "Process Retry/Pending Items," "Summarization," and "Briefing Generation" via API parameters and UI checkboxes.
- **Orchestration Resilience:** The orchestrator now automatically recovers `PENDING` items from crashed runs by treating them as `RETRY_LATER` items when a new lock is acquired.
- **Enhanced Logging:** Content processing logs now include Video ID, Duration, and URL for better observability and debugging.
- **Smart Retry:** Implemented logic to check for existing transcripts in the database before attempting to fetch them again, optimizing resource usage and reducing API calls.
- **Error Handling:** Introduced specific error codes (`TRANSCRIPT_UNAVAILABLE`, `RATE_LIMIT_EXCEEDED`, etc.) for better categorization of failures.

### Changed
- **Framework Upgrade:** Updated Quarkus from 3.15.6 to 3.27.1 LTS.
- **Dependencies:** Migrated `quarkus-resteasy-reactive-jackson` to `quarkus-rest-jackson` and aligned JUnit/Mockito versions with the Quarkus BOM.
- **Database Schema:** Added an `enabled` column to the `topics` table via a `V4__AddEnabledToTopics.sql` Flyway migration script.
- **Refactored Error Handling:** Consolidated API error handling logic in `YouTubeSummarizationStrategy` to ensure consistent status updates and logging.
- **Refactored ContentProcessingError:** Added `retriable` field to `ContentProcessingError` enum to centralize retry logic.

### Fixed
- **Transcript Persistence:** Fixed a critical bug where transcripts were not being saved to the database during summarization. The fix ensures that the transcript fetched during processing is explicitly copied to the managed entity before persistence.

## [0.6.2] - 2026-01-04

### Summary

Version 0.6.2 focuses on **hardening the core orchestration engine**. It introduces a robust, idempotent briefing generation process, a just-in-time scheduling mechanism to prevent thread exhaustion, and improved handling of transient errors and rate limits. This release also addresses several concurrency race conditions and improved logging visibility, significantly increasing the system's reliability under load.

### Added
- **Orchestration Robustness:** Implemented retry logic for `ContentItem`s with `RETRY_LATER` status and improved lock handling to automatically recover from crashed runs.
- **Idempotent Briefing Generation:** Decoupled briefing generation from content fetching, allowing the system to automatically self-heal and regenerate briefings without manual intervention.
- **Just-in-Time Scheduling:** Implemented a pipelined execution model for content summarization. This ensures that a new summarization task is only scheduled after the previous one has successfully acquired a `GenAiClient`, preventing thread exhaustion.
- **Enhanced Observability:** Added detailed logging for AI API calls (duration, failure reasons) and briefing generation statistics.

### Fixed
- **Concurrency & Stability:** Resolved transactional race conditions during briefing generation using pessimistic locking and fixed transaction boundaries to prevent timeouts.
- **Performance:** Eliminated blocking calls in the reactive pipeline and optimized the `GenAiPool` to prevent thread starvation.
- **Rate Limit Handling:** Improved handling of HTTP 429 errors to respect `Retry-After` headers dynamically.
- **Test Stability:** Fixed transaction boundary issues in integration tests to ensure consistent execution.

### Documentation
- **Concurrency Analysis:** Added findings from the GenAiPool spike test regarding concurrency behavior and blocking calls.

## [0.6.1] - 2025-11-13

### Summary

Version 0.6.1 introduces a comprehensive UI/UX refinement, transforming the basic HTML interface into a clean, responsive, and navigable application. This release establishes a foundational user experience with a consistent master layout, modern styling via Pico.css, and improved user feedback mechanisms. A key highlight is the new Admin UI, which provides a powerful interface for system monitoring and manual triggering of core application processes like content enrichment and orchestration.

### Fixed

- **UI Responsiveness & Stability:** Corrected mobile display issues, fixed page refresh bugs after form submissions, and resolved various Qute rendering errors.
- **Error Handling:** Prevented `BlockingOperationNotAllowedException` in UI resources and added logging to catch blocks to improve debuggability.
- **Navigation & Links:** Fixed 404 errors when adding content sources and corrected breadcrumb generation logic for edge cases.
- **User Actions:** Ensured confirmation dialogs appear correctly for all destructive actions.
- **API Data Handling:** Added a null check in `YouTubeDetailEnrichmentStrategy` to prevent `NullPointerException` for malformed API responses.

### Added

- **Foundational UI/UX:** Implemented a master layout (`main.html`), integrated the "class-less" Pico.css framework, and refactored all pages to use semantic HTML5.
- **Improved Navigation:** Added dynamic breadcrumb navigation and a dedicated home page (`/ui`).
- **User Feedback:** Integrated a session-based flash message system for success/error feedback and ensured form values are repopulated on validation failure.
- **Admin UI Page (`/ui/admin`):**
    - Created a powerful dashboard with a tabbed interface for "Actions" and "History".
    - Implemented AJAX-driven forms to trigger backend orchestration and enrichment services.
    - Added monitoring tables for pending, recent, and failed content items and briefings.
    - Included user-friendly features like tooltips/modals for executive summaries and links to a new summary detail page.
    - **Summary Detail Page:** Created a new page to display full summary details, with Markdown rendering.
    - **Scalable Content Source Selection:** Replaced the unscalable `select` dropdown with a new searchable page for adding existing content sources to a topic.

### Refactored

- **Code Consolidation (DRY):**
    - Centralized `createdAt` logic into a `BaseAuditableEntity`.
    - Extracted `getExecutiveSummary` logic into a reusable `TextUtils.extractSection` method.
    - Consolidated UI helper methods into an injectable `ViewUtils` bean and a `WebUtils` class.
    - Grouped all UI utility classes into a new `com.povzetkar.quarkus.ui.util` package.
- **Dependency Injection:** Refactored all UI resource classes to consistently use constructor injection.
- **Data Type Correction:** Changed `subscriberCount` from `long` to `int` across relevant entities and services to align with API data types.

### Documentation

- Updated `docs/RUNBOOK.md` with a new section under "Application Usage" explaining how to use the new Admin UI page.

### Tests

- **UI Backend Integration Tests:** Added new integration tests for the Admin UI page and its backing API endpoints.

## [0.6.0] - 2025-10-31

### Summary

Version 0.6 represents a pivotal transformation, completing the migration of the entire core workflow into the new Quarkus application and rendering it fully autonomous. The legacy application has been decommissioned. Key achievements include the implementation of a multi-tenant data model with a new `User` entity, the migration of all summarization and briefing logic, and the introduction of a scheduled orchestrator to automate the end-to-end process. Additionally, a simple web UI for reviewing briefings has been added, and the overall architecture has been refactored for improved robustness and testability.

### Added

-   **Full Workflow Autonomy:** Migrated the entire workflow (content fetching, summarization, briefing generation) to the Quarkus application.
-   **Multi-Tenancy:** Introduced a `User` entity, making the application multi-tenant aware. Topics are now unique per user.
-   **Summarization & Briefing Logic:** Implemented `SummarizationService` and `BriefingService` in Quarkus, including all related entities (`Summary`, `Briefing`).
-   **Scheduled Orchestration:** Created a scheduled `OrchestratorService` to automate the entire process, from fetching new content to generating final briefings.
-   **Simple Briefing UI:** Added a basic server-side rendered HTML interface using Qute for reviewing briefings by user and topic.
-   **Database Migrations:** Integrated Flyway for systematic and automated database schema management.

### Refactored

-   **Decoupled Orchestration:** Separated high-level orchestration from per-topic processing and decoupled briefing generation from content summarization to improve robustness.
-   **Core Entities:** Refactored `Channel` to a more generic `ContentSource` entity for future flexibility.
-   **Project Structure:** Simplified the project into a single-module Maven application, removing the legacy app module and all related code.

### Chore

-   **Database Setup:** Configured PostgreSQL to enable the `pgvector` extension in preparation for future embedding features.
-   **Documentation:** Updated architecture and database design documents to reflect the new, fully autonomous application structure.

## [0.5.0] - 2025-09-08

### Summary

This version marks a major architectural milestone by establishing the foundational "architectural skeleton" for the new Quarkus-based application. Following the "Strangler Fig" pattern, this release introduces the new application, sets up its database, defines core entities, and implements a vertical slice API for fetching channel data. The first concrete "strangling" step was completed by refactoring the legacy `AuthorService` to delegate author fetching to this new API, moving away from local file-based lookups.

### Added

-   **New Quarkus Application:** Created a new multi-module Maven project containing the `quarkus-app` and the `legacy-app`.
-   **Database Setup:** Introduced a `docker-compose.yml` for a PostgreSQL database.
-   **Core JPA Entities:** Implemented `Channel`, `Topic`, and `ContentItem` entities as the basis for the new data model.
-   **Health Check & Integration Tests:** Added a `/q/health` endpoint and a `@QuarkusTest` to validate database connectivity.
-   **Data Migration Script:** Created a one-time `DatabaseMigrator` script to populate the new database from existing `authors_to_query.txt` files.
-   **Vertical Slice API:** Implemented a `GET /api/v1/topics/{name}/channels` endpoint in the Quarkus app to serve channel data.

### Refactored

-   **Legacy AuthorService:** Refactored the service to fetch author channel IDs from the new Quarkus API instead of reading from a local file. The method signature was preserved to ensure no breaking changes.

### Chore

-   Added `jackson-databind` dependency to the legacy application for JSON parsing.

## [0.4.4] 2025-09-01

### Features

- Load author list from an external file path.
- Configurable processing state storage path for greater flexibility.

### Fixes

-   Improved test isolation in `ProcessingStateServiceTest`.

### Documentation

-   Refined the Commit Message Mandate for clarity.
-   Enhanced the consistency and clarity of the `AGENT.md` document.
-   Resolved ambiguity and improved phrasing in the documentation.
-   Made examples in the documentation clearer and more consistent with protocols.
-   Clarified logging and expanded the execution example.
-   Formalized task conclusion with a handover question.
-   Clarified two-stage verification in the pre-execution checklist.

### Chores

-   Refined and hardened the agent execution workflow.

## [0.4.3] - 2025-08-31

### New Features
- Centralized app configuration via properties file.

### Refactor
- Major architectural decoupling into focused services and config-driven paths.
- Streamlined YouTube API usage, reducing calls and improving reliability.

### Documentation
- Overhauled process guide and added planning/task docs for v0.4.3.

### Tests
- Comprehensive unit tests for new services and configuration.

## [0.4.2] - 2025-08-27

### New Features
- Prevents duplicate video processing across authors so each video is summarized only once.

### Reliability
- Improved API error handling and retry behavior with clearer non-retriable failure reporting.

### Tests
- Expanded and refactored tests: added deduplication scenario, per-author test setup, and removed sleep-based controls.

## [0.4.1] - 2025-08-19

### Bug Fixes
- Cleaner AI summaries (trims separators/whitespace).
- Video fetching now respects date boundaries when resuming.

### Documentation
- Added collaboration guide, professional practices, workflow/tooling, and expanded governance/process docs.
- Roadmap restructured into versioned milestones; specification headings updated.

### Tests
- New parameterized tests for summary cleaning and date-bound fetching; tests updated for API behavior.

### Chores
- Enabled JUnit parameterized tests via added dependency.

## [0.4] - 2025-08-16

### New Features
- **Enhanced YouTube Video Processing**: The video summarization workflow has been significantly refactored to dynamically fetch transcripts for longer videos using the new youtube-transcript-api library, while continuing to use video URLs for shorter content. This improves the accuracy and depth of summaries for diverse video lengths.
- **AI API Rate Limit Management**: A GenAiPool has been introduced to manage multiple Google Gemini API keys and enforce a cooldown period between API calls. This mechanism aims to prevent rate-limiting issues and improve the reliability of AI-powered summarization.
- **Additional Capabilities**: Added a dry-run mode for "summarize and brief", transcript-driven summarization for long videos, and API-key pooling for GenAI with stricter startup validation.
- **Streamlined Summarization Workflows**: The previous explicit "summarize from transcript" (TR) use case has been removed, integrating transcript-based summarization directly into the main video processing flow. This simplifies the application's command-line interface and internal logic.

### Documentation
- **Formalized SDLC Documentation**: A comprehensive suite of documentation has been added, including functional and non-functional requirements, API specifications, database design, a test plan, a development roadmap, and a high-level system specification. This establishes a clear architectural vision and development strategy for Project Povzetkar.

### Tests
- Expanded unit/integration tests covering transcript formatting, transcript service, GenAiPool, summarization, and YouTube integration.

## [0.3] - 2025-08-10

### New Features
- Introduced a detailed author model, enriching author information in summaries and documentation.
- Added formatted display of video duration and publication date.
- Enhanced README with an authors section, including channel links and subscriber counts.
- Added new prompt template for summarizing videos by URL.

### Improvements
- Standardized date and time formatting throughout the app.
- Improved error handling and retry logic for API calls, including support for additional retriable errors.
- Enhanced logging, commit metadata, and API call duration reporting.
- Simplified and unified author data handling by fetching full author details.

### Bug Fixes
- Corrected time zone handling for video date comparisons.

### Documentation
- Updated prompt templates for clearer and more consistent summaries.
- README subtitle updated.

### Chores
- Removed obsolete static author mapping file.

### Tests
- Expanded and refactored tests to support the new author model and improved summary prompts.
- Added tests for new author details retrieval and briefing creation workflows.

## [0.2] - 2025-07-19

### New Features
- Automated documentation updates and version control integration, including automatic updates to README and updates files with the latest summaries.
- Summaries and briefings are now organized into structured directories for easier access.
- Added support for a new author in the author query list.

### Improvements
- Enhanced file handling and error logging for more robust summarization workflows.
- Improved resource management and modularization for the main application runner.
- Refined output file management by using direct path handling and directory preparation.

### Bug Fixes
- Improved error handling and logging during file operations and Git integration.

### Chores
- Updated project dependencies and removed unused plugins.
- Simplified configuration files and cleaned up `.gitignore` entries.

### Tests
- Expanded and improved test coverage for summarization workflows, file handling, and documentation updates.
- Updated tests to use direct path handling for file operations.

## [0.1] - 2025-07-18

### New Features
- Initial release of a Java application for summarizing YouTube videos using AI, with support for multiple workflows including author-based, URL-based, and transcript-based summarization.
- Integration with YouTube Data API for video retrieval and Gemini API for AI-generated summaries.
- Structured Markdown output for video summaries and daily briefings.
- Project configuration with Maven, Java 21, and logging setup.
- Added retry logic for AI API calls to handle rate limiting.
- State management for tracking processed videos per channel and date.
- Utility classes for file operations, resource reading, and sleep abstraction.
- Detailed logging configuration for runtime diagnostics.
