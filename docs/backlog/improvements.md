# Improvement Backlog

This document serves as a backlog for non-critical improvements, ideas, and minor issues that are discovered during development.

Items listed here are candidates for inclusion in future development cycles. Each item should be reviewed and prioritized during the planning phase of a new version.

---

## Candidate Improvements

### UI/UX

*   **Changelog/Roadmap in UI:** Add a new section to the UI to display the `CHANGELOG.md` and `docs/overview/roadmap.md` files, providing greater transparency to users.
*   **Centralized Breadcrumb Management:** Explore a more automated or centralized way to manage breadcrumbs, potentially using annotations or a filter, to reduce boilerplate code in JAX-RS resources.
*   **Global Error Handling Page:** Create a global, user-friendly error page for unhandled exceptions (HTTP 500) to provide a better user experience than the default framework error page.
*   **Real-time Admin Page Updates:** Implement real-time updates for "Pending Content Items" and "Current Running Actions" on the admin page, possibly using WebSockets or polling.
*   **AJAX Form Submissions:** Refactor UI forms (e.g., Admin page actions) to use asynchronous JavaScript (AJAX) calls to `/api` endpoints instead of traditional form POSTs. This will prevent full-page reloads and improve user experience.
*   **User-Facing Content Source Detail Page:** Create a dedicated, user-facing page for each `ContentSource` that lists all associated `ContentItem`s. This provides a focused view of all content from a specific source.

### Backend & Architecture

*   **Centralized Date/Time Formatting Service:** Create a dedicated service for date/time formatting to avoid adding helper methods to every model class.
*   **Robust Subscriber Count Handling:** Re-evaluate the `subscriberCount` field in `ContentSource` and related classes to use `long` instead of `int` to prevent potential data truncation for very popular channels, as the YouTube API returns a `BigInteger`.
*   **Refactor Blocking Code in Reactive Chains [Partially Complete]:** A significant portion of this was completed in v0.6.2 with the introduction of the `JustInTimeSummarizationScheduler`. A full code review should still be conducted to identify any other remaining instances of blocking code in reactive pipelines.
*   **Create a Testing Guide:** Document best practices for Quarkus integration testing, including transactional patterns, mocking strategies for Panache entities vs. external services, and how to handle reactive code. This will help prevent the kind of trial-and-error debugging experienced during the v0.6.2 development cycle.
    *   **Addendum (v0.7.0):** The guide must include a section on dependency management for test libraries. It should emphasize letting the Quarkus BOM manage versions for `JUnit`, `Mockito`, etc., to prevent classpath conflicts (`NoSuchMethodError`) during framework upgrades.
*   **Improve Test Suite Resilience to Logging Changes:** The test suite proved brittle to changes in logging messages. Mocks that rely on specific string formats for `callDescription` should be updated to use `anyString()` to avoid test failures from minor logging improvements.
*   **Standardize Entity Processing Logs:** Instead of ad-hoc improvements, define a standard logging format for key processing steps (e.g., summarization, enrichment). This format should consistently include key identifiers like Entity Type, ID, URL, and Duration to ensure uniform observability across the system.

### Orchestration & Briefing Generation
