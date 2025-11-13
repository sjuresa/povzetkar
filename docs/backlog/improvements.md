# Improvement Backlog

**Version:** 1.0

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
*   **Standardize UI Test Setup:** Prioritize the creation of a `BaseUITest` class to encapsulate the database cleanup and setup logic. This will make the test suite more robust and prevent future state pollution issues.

### Orchestration & Briefing Generation
