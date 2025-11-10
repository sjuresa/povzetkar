# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Breadcrumb Navigation:** Implemented dynamic breadcrumb navigation in the master layout (`main.html`) to enhance user orientation.
    - Centralized "Povzetkar" home breadcrumb in `ViewUtils` for DRY principle adherence.
    - Breadcrumbs dynamically adjust based on the current page and its context (e.g., topic, briefing, summary).
    - Added a visual separator ('>') between breadcrumb elements using Pico.css's native `::before` pseudo-element.
- **Flash Messages:** Integrated a session-based flash message system for user feedback on actions (success/error).
    - Consolidated flash message handling in `ViewUtils` for consistent usage across resources.
    - Flash messages are rendered in `main.html` and automatically cleared after display.
- **Dedicated Home Page:** Created a new home page (`home.html` and `HomeResource.java`) at `/ui` to serve as the application's landing page.
- **Footer Version Display:** Added dynamic display of the application version (from `pom.xml`) to the footer in `main.html`.
- **Admin UI Page:** Created a new administrative UI page (`/ui/admin`) with enhanced features for triggering system actions and monitoring.
    - Semantic HTML5 elements and improved form structures for better accessibility.
    - Backend endpoints (`/ui/admin/enrich-sources`, `/ui/admin/run-orchestration`) in `AdminResource.java` to trigger system-level actions.
    - Success and error flash messages for user feedback.
    - Configurable date/time format (`povzetkar.ui.datetime.format`) applied consistently across all displayed timestamps.
    - Tables to display: Pending Content Items, Recent Content Items with Summaries, Recent Briefings, and Failed Content Items.
    - Row numbers to all tables.
    - Executive summary as a tooltip on desktop, and a Pico.css modal on mobile, for summary titles.
    - Links from summary titles to a new summary detail page.
- **Summary Detail Page:** Created a new `/ui/summaries/{id}` page (`SummaryResource.java` and `summary-detail.html`) to display individual summaries, converting Markdown content to HTML using `flexmark`.

### Changed

- Refactored `getExecutiveSummary` logic in `Briefing.java` and `Summary.java` into `TextUtils.extractSection` to reduce code duplication and improve maintainability.
- Replaced the content source dropdown with a link to a searchable page.
- Made the content source search case-insensitive.
- Updated `ContentSource` entity's `subscriberCount` from `long` to `int`.
- Modified `ContentSourceDetailService.ContentSourceDetails` record to use `int` for `subscriberCount`.
- Modified `YouTubeDetailEnrichmentStrategy` to use `intValue()` for `subscriberCount`.
- **UI Navigation:** Moved the main topics listing to `/ui/topics` from `/ui` to provide a dedicated home page.

### Fixed

- Corrected a bug where the main page would not refresh after deleting a topic.
- Resolved a 404 error when adding a content source to a topic.
- Fixed a bug where deleting a content source or topic would not show a confirmation dialog.
- Fixed Qute rendering errors related to date formatting by implementing helper methods in model classes (`ContentItem`, `Briefing`, `Summary`) and using correct Qute syntax.
- Fixed `BlockingOperationNotAllowedException` by annotating blocking UI resource methods (`getAdminPage`, `enrichSources`, `runOrchestration`) with `@Blocking`.
- Corrected mobile executive summary display from a transparent overlay to a Pico.css modal, with improved positioning and readability.
- Added logging to catch blocks in `AdminResource.java` to prevent exception swallowing and improve debuggability.
- **Breadcrumb Logic:** Corrected breadcrumb generation for summary pages to handle cases where a briefing might not yet exist, and removed redundant date information from briefing breadcrumbs.
- **Form Repopulation:** Ensured submitted form values are re-populated on validation failure for topic creation/update forms.
- **Page Refresh:** Implemented cache-control headers on redirects to force page refreshes after POST operations.

### Refactored

- Centralized `createdAt` field and `formattedCreatedAt` method into `BaseAuditableEntity` to adhere to DRY principle. `Briefing`, `ContentItem`, and `Summary` now extend this base class.
- Extracted duplicate redirect logic in `AdminResource.java` into a private helper method `redirectBack` to improve maintainability and adhere to the DRY principle.
- **UI Utilities:** Refactored `ViewUtils` to be an `@ApplicationScoped` bean, allowing dependency injection and centralizing common UI helper methods.
- **Form Validation:** Extracted common form parameter validation logic into reusable `WebUtils` methods.
- **Code Duplication:** Consolidated UI-related utility classes (`WebUtils`, `Breadcrumb`, `ViewUtils`) into `com.povzetkar.quarkus.ui.util` package.
- **Readability:** Extracted breadcrumb building logic in `SummaryResource` into a private helper method.
- **Efficiency:** Extracted form rendering logic in `TopicResource` and `ContentSourceResource` into private helper methods to avoid redundant database queries and ensure correct parameter passing on re-render.
- **Constructor Injection:** Refactored all UI resource classes (`AdminResource`, `BriefingWebResource`, `ContentSourceResource`, `HomeResource`, `SummaryResource`, `TopicResource`) and `ViewUtils` to use constructor injection for dependencies, addressing SonarQube/IDE warnings.

### Documentation

- Updated `docs/RUNBOOK.md` with a new section under "Application Usage" explaining how to use the new Admin UI page.

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
- Expanded unit/integration tests covering transcript formatting, transcript service, GenAI pool, summarization, and YouTube integration.

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

### Documentation
- Added prompt templates and resource files for authors, videos, and summaries.
