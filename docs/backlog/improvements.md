# Improvement Backlog

This document serves as a backlog for non-critical improvements, ideas, and minor issues that are discovered during development.

Items listed here are candidates for inclusion in future development cycles. Each item should be reviewed and prioritized during the planning phase of a new version.

---

## Candidate Improvements

### UI/UX

*   **OIDC Session Mismatch Handling:** Investigate and implement a graceful fallback when the local Quarkus session cookie exists but the upstream OIDC provider (Google) session has expired or been revoked, currently leading to potential endless redirect loops or `401 Unauthorized` errors on secured pages.
*   **Changelog/Roadmap in UI:** Add a new section to the UI to display the `CHANGELOG.md` and `docs/overview/roadmap.md` files, providing greater transparency to users.
*   **Global Error Handling Page:** Create a global, user-friendly error page for unhandled exceptions (HTTP 500) to provide a better user experience than the default framework error page.
*   **Real-time Admin Page Updates:** Implement real-time updates for "Pending Content Items" and "Current Running Actions" on the admin page, possibly using WebSockets or polling.

### Backend & Architecture

*   **Robust Subscriber Count Handling:** Re-evaluate the `subscriberCount` field in `ContentSource` and related classes to use `long` instead of `int` to prevent potential data truncation for very popular channels, as the YouTube API returns a `BigInteger`.
*   **Refactor Blocking Code in Reactive Chains:** A significant portion of this was completed in v0.6.2 with the introduction of the `JustInTimeSummarizationScheduler`. A full code review should still be conducted to identify any other remaining instances of blocking code in reactive pipelines.
*   **Standardize Exception Message Assertions in Tests:** Create a helper or wrapper for `ApiException` (and other external exceptions) to extract clean error messages, or standardize error message assertions in tests to handle variable message formats (e.g., including status codes) more robustly.
*   **Migrate Integration Tests to `@TestTransaction`:** The `OrchestrationIntegrationTest` currently uses manual database cleanup in `@BeforeEach` and `@AfterEach` methods. This should be migrated to use the `@TestTransaction` annotation on each test method to simplify the test setup and align with the best practices outlined in `docs/guides/testing.md`.
*   **Fix Qute Template Lints:** There are numerous lint warnings/errors in `admin.html`, `main.html`, etc. regarding unresolved objects (`activeTab`, `item`, etc.). These should be investigated and fixed to ensure a clean build and IDE experience.
