# Improvement Backlog

This document serves as a backlog for non-critical improvements, ideas, and minor issues that are discovered during development.

Items listed here are candidates for inclusion in future development cycles. Each item should be reviewed and prioritized during the planning phase of a new version.

---

## Candidate Improvements

### UI/UX

*   **OIDC Session Mismatch Handling:** Investigate and implement a graceful fallback when the local Quarkus session cookie exists but the upstream OIDC provider (Google) session has expired or been revoked, currently leading to potential endless redirect loops or `401 Unauthorized` errors on secured pages.
*   **Global Error Handling Page:** Create a global, user-friendly error page for unhandled exceptions (HTTP 500) to provide a better user experience than the default framework error page.
*   **Real-time Admin Page Updates:** Implement real-time updates for "Pending Content Items" and "Current Running Actions" on the admin page, possibly using WebSockets or polling.
*   **Automatic Optimistic Locking in Orval mutator:** Enhance `customInstance` in `client.ts` to automatically extract a `version` or `meta.version` field from request data and inject the `If-Match` header for mutation methods (PATCH, PUT, DELETE), reducing manual boilerplate in TanStack Query hooks.
*   **Axios Retry Strategy:** Implement a standard retry mechanism for transient network errors (e.g., using `axios-retry`) to improve frontend resilience during intermittent connectivity.

### Backend & Architecture

*   **Robust Subscriber Count Handling:** Re-evaluate the `subscriberCount` field in `ContentSource` and related classes to use `long` instead of `int` to prevent potential data truncation for very popular channels, as the YouTube API returns a `BigInteger`.
*   **Content Deduplication Engine:** Basic hash-based or semantic deduplication of sources (Deferred from v0.12).
*   **GenAI Rate Limiting (TPM/RPM):** Advanced token-per-minute and request-per-minute management for GenAI providers (Deferred from v0.12).
*   **UI Performance Monitoring:** Integrated performance metrics for the SPA (Deferred from v0.13).
*   **Refactor Blocking Code in Reactive Chains:** A significant portion of this was completed in v0.6.2 with the introduction of the `JustInTimeSummarizationScheduler`. A full code review should still be conducted to identify any other remaining instances of blocking code in reactive pipelines.
*   **Standardize Exception Message Assertions in Tests:** Create a helper or wrapper for `ApiException` (and other external exceptions) to extract clean error messages, or standardize error message assertions in tests to handle variable message formats (e.g., including status codes) more robustly.
*   **Advanced Idempotency (Full HTTP Response Replay):** Upgrade the current Idempotency-Key validation to fully cache and replay the exact HTTP response (including 400/500 `ProblemDetail` errors) for duplicate POST command requests, preventing any backend re-execution upon retry.
*   **Recursive Briefings (Map-Reduce):** Implementing hierarchical summarization for extremely long content sets or timeframes (Deferred from v0.12).
*   **Migrate Integration Tests to `@TestTransaction`:** The `OrchestrationIntegrationTest` currently uses manual database cleanup in `@BeforeEach` and `@AfterEach` methods. This should be migrated to use the `@TestTransaction` annotation on each test method to simplify the test setup and align with the best practices outlined in `docs/guides/testing.md`.
*   **Fix Qute Template Lints:** There are numerous lint warnings/errors in `admin.html`, `main.html`, etc. regarding unresolved objects (`activeTab`, `item`, etc.). These should be investigated and fixed to ensure a clean build and IDE experience.
*   **Primary Identity Resolution:** Refactor `UserMapper` to deterministically resolve a "primary" identity/provider when multiple OIDC accounts are linked to a single user.
*   **Job Cleanup Strategy:** Implement a background job to purge `COMPLETED` or `FAILED` job history older than X days to prevent indefinite table growth.
*   **API Abuse Mitigation (LLM Endpoints):** Implement rate limiting/quotas per user and strict state validation (e.g., preventing regeneration of already `COMPLETED` summaries) for global mutation endpoints like `POST /api/v1/content-items/{id}/commands/generate-summary` to prevent financial risk and queue starvation.
*   **Advanced Caching Strategies:** Investigate `ETag`, `Cache-Control`, and `Stale-while-revalidate` patterns for Dashboard and Briefings endpoints to reduce database load.
*   **Fully Reactive Identity Resolution:** Refactor `UserContextService.getCurrentUser()` to return a `Uni<User>`, allowing its removal from the initial JAX-RS method body and enabling the complete removal of the `@Blocking` annotation from reactive endpoints.
*   **Standardize Reactive Transaction Helpers:** Extract the `executeInTransactionIf` pattern from `DashboardApiResource` into a shared utility or base class for all reactive resources requiring parallel database queries.
*   **Replace Deprecated Panache and Parameters Methods:** Refactor `BriefingApiResource` (and other resource classes) to replace deprecated `Parameters.with()` and `PanacheEntityBase.find(..., Parameters)` calls with modern, type-safe alternatives or the `Map` based approach to eliminate build-time deprecation warnings and future incompatibilities. 
*   **Address Medium-Severity SpotBugs Warnings:** Investigate and resolve the ~40 pre-existing `Medium` severity warnings flagged by SpotBugs during the POV-125 integration. These relate primarily to internal representation exposure (`EI_EXPOSE_REP`/`EI_EXPOSE_REP2`), partially initialized objects returning from constructors (`CT_CONSTRUCTOR_THROW`), and public primitive variables (`PA_PUBLIC_PRIMITIVE_ATTRIBUTE`).
*   **Decouple Filter Logic from URL Paths:** Migrate remaining URL-prefix-based exclusions in `GlobalResponseEnvelopeFilter` and `OptimisticLockingFilter` to JAX-RS Name Binding (`@Enveloped`, `@OptimisticLocking`) across the entire codebase to eliminate brittle path-scanning and improve security robustness.
*   **Internationalization (i18n) for Exception Mappers:** Extract hardcoded error messages in `GlobalExceptionMapper` and `SecurityExceptionMapper` into a dedicated `messages.properties` file or ResourceBundle to support multi-language API error responses.
*   **Standardized Background Job Pattern:** Standardize the `BackgroundJob` pattern (including MDC propagation and reactive orchestration) for other long-running tasks like OCR, embedding generation, or trend analysis.
*   **Vite 8 Multi-File Patching:** Re-evaluate upgrading to Vite 8 once `@tailwindcss/vite` and `vitest` have released stable versions supporting it, to leverage improved build performance.
