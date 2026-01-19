# Improvement Backlog

This document serves as a backlog for non-critical improvements, ideas, and minor issues that are discovered during development.

Items listed here are candidates for inclusion in future development cycles. Each item should be reviewed and prioritized during the planning phase of a new version.

---

## Candidate Improvements

### UI/UX

*   **Changelog/Roadmap in UI:** Add a new section to the UI to display the `CHANGELOG.md` and `docs/overview/roadmap.md` files, providing greater transparency to users.
*   **Global Error Handling Page:** Create a global, user-friendly error page for unhandled exceptions (HTTP 500) to provide a better user experience than the default framework error page.
*   **Real-time Admin Page Updates:** Implement real-time updates for "Pending Content Items" and "Current Running Actions" on the admin page, possibly using WebSockets or polling.
*   **Standardize AJAX Form Submissions:** The custom JavaScript for the orchestration form is brittle. Refactor it into a more generic, reusable helper function or library to handle all AJAX-based form submissions consistently. This will make the client-side logic more robust and easier to maintain.

### Backend & Architecture

*   **Centralized Date/Time Formatting Service:** Create a dedicated service for date/time formatting to avoid adding helper methods to every model class.
*   **Robust Subscriber Count Handling:** Re-evaluate the `subscriberCount` field in `ContentSource` and related classes to use `long` instead of `int` to prevent potential data truncation for very popular channels, as the YouTube API returns a `BigInteger`.
*   **Refactor Blocking Code in Reactive Chains:** A significant portion of this was completed in v0.6.2 with the introduction of the `JustInTimeSummarizationScheduler`. A full code review should still be conducted to identify any other remaining instances of blocking code in reactive pipelines.
*   **Standardize Exception Message Assertions in Tests:** Create a helper or wrapper for `ApiException` (and other external exceptions) to extract clean error messages, or standardize error message assertions in tests to handle variable message formats (e.g., including status codes) more robustly.
*   **Migrate Integration Tests to `@TestTransaction`:** The `OrchestrationIntegrationTest` currently uses manual database cleanup in `@BeforeEach` and `@AfterEach` methods. This should be migrated to use the `@TestTransaction` annotation on each test method to simplify the test setup and align with the best practices outlined in `docs/guides/testing.md`.

### Orchestration & Briefing Generation

*   **Use a Request Object for Orchestration:** The `OrchestrationManager.runOrchestrationManually` method signature is growing unwieldy (currently 5+ parameters, with more added in v0.7.0). Refactor this to accept a dedicated `OrchestrationRequest` data object. This will improve readability, make the API more extensible, and prevent future breaking changes to the method signature as more granular options are added.
