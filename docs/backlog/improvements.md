# Improvement Backlog

**Version:** 1.1

This document serves as a backlog for non-critical improvements, ideas, and minor issues that are discovered during development.

Items listed here are candidates for inclusion in future development cycles. Each item should be reviewed and prioritized during the planning phase of a new version.

---

## Candidate Improvements

### UI/UX

*   **Admin Page UI Refinements:** Further refine the admin page UI, potentially by adding more detailed statistics, filtering/sorting options for the history tables, or a more sophisticated layout for actions.
*   **Changelog/Roadmap in UI:** Add a new section to the UI to display the `CHANGELOG.md` and `docs/overview/roadmap.md` files, providing greater transparency to users.
*   **Centralized Breadcrumb Management:** Explore a more automated or centralized way to manage breadcrumbs, potentially using annotations or a filter, to reduce boilerplate code in JAX-RS resources.
*   **Global Error Handling Page:** Create a global, user-friendly error page for unhandled exceptions (HTTP 500) to provide a better user experience than the default framework error page.
*   **Real-time Admin Page Updates:** Implement real-time updates for "Pending Content Items" and "Current Running Actions" on the admin page, possibly using WebSockets or polling.
*   **AJAX Form Submissions:** Refactor UI forms (e.g., Admin page actions) to use asynchronous JavaScript (AJAX) calls to `/api` endpoints instead of traditional form POSTs. This will prevent full-page reloads and improve user experience.
*   **User-Facing Content Source Detail Page:** Create a dedicated, user-facing page for each `ContentSource` that lists all associated `ContentItem`s. This provides a focused view of all content from a specific source.

### Backend & Architecture

*   **Centralized Date/Time Formatting Service:** Create a dedicated service for date/time formatting to avoid adding helper methods to every model class.
*   **Robust Subscriber Count Handling:** Re-evaluate the `subscriberCount` field in `ContentSource` and related classes to use `long` instead of `int` to prevent potential data truncation for very popular channels, as the YouTube API returns a `BigInteger`.
*   **Spike: Research Quarkus Transactional Testing:** Dedicate time to a formal spike to research and document the correct, idiomatic patterns for testing transactional code in Quarkus. This should cover `@TestTransaction`, `QuarkusTransaction.run()`, and the visibility rules between test transactions and application transactions. The goal is to produce a definitive guide to prevent future trial-and-error.
*   **[Spike] Establish a Canonical Pattern for Transactional Integration Tests:** The attempt to create a `BaseUITest` class (POV-78) failed due to a lack of clarity on the correct transactional pattern for tests that assert database changes made via an HTTP request. A dedicated spike is required to investigate and document the correct, idiomatic Quarkus approach. This should cover:
    - The difference between `@TestTransaction` and programmatic transaction control (`QuarkusTransaction.run()`).
    - The correct way to ensure assertions in the test thread can see data committed by the application thread.
    - A recommended pattern for setting up and tearing down test data to guarantee test isolation.
    - The findings should be documented in a new `docs/analysis/transactional_test_patterns.md` file.
    - Define when to use @TestTransaction vs. manual transaction management (QuarkusTransaction.requiringNew()) and establish a consistent pattern for setting up test data to avoid transactional visibility issues.
*   **Refactor Blocking Code in Reactive Chains [Partially Complete]:** A significant portion of this was completed in v0.6.2 with the introduction of the `JustInTimeSummarizationScheduler` and the refactoring of the briefing generation flow to move blocking AI calls out of JTA transactions. A full code review should still be conducted to identify any other remaining instances.
*   **Create a Testing Guide:** Document best practices for Quarkus integration testing, including transactional patterns, mocking strategies for Panache entities vs. external services, and how to handle reactive code. This will help prevent the kind of trial-and-error debugging experienced during the v0.6.2 development cycle.
*   **Improve Test Suite Resilience to Logging Changes:** The test suite proved brittle to changes in logging messages. Mocks that rely on specific string formats for `callDescription` should be updated to use `anyString()` to avoid test failures from minor logging improvements.

### Orchestration & Briefing Generation
