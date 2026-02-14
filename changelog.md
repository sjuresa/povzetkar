# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Documentation
- **Agent Protocol:** Refactored `AGENT.md` into modular protocol files located in `docs/protocols/` to improve maintainability and reduce context usage.
- **Agent Protocol:** Updated `AGENT.md` with a new protocol for using the `context7` MCP server to retrieve external documentation.
- **Agent Protocol:** Optimized `AGENT.md` by removing deduplication and condensing protocols for better token efficiency.
- **Project Structure:** Created `docs/overview/project-structure.md` to document the project's directory layout and architecture.
- **Testing Guide:** Refactored `docs/guides/testing.md` to clarify the usage of `@QuarkusTest` vs `@QuarkusIntegrationTest` and deprecated `docs/guides/mastering-testing.md`.
- **ADR:** Initialized `docs/adr/` with the first Architectural Decision Record (ADR-001) to track architectural decisions.

## [v0.9.0] - 2026-02-05

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
