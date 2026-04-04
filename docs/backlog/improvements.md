# Improvement Backlog

This document serves as a backlog for non-critical improvements, ideas, and minor issues that are discovered during development.

Items listed here are candidates for inclusion in future development cycles. Each item should be reviewed and prioritized during the planning phase of a new version.

---

## Candidate Improvements

### UI/UX

*   **Real-time Admin Page Updates:** Implement real-time updates for "Pending Content Items" and "Current Running Actions" on the admin page, possibly using WebSockets or polling.
*   **Admin Jobs UI Implementation:** (Moved to v0.16.0) Implement the `AdminJobsPage.tsx` placeholder with a paginated table, status-based filtering, and manual retry/cancel actions for background jobs.
*   **Advanced Audit Log Filtering:** (POV-142 Success-Enhancement) Extend the Audit Log UI with date-range pickers and a "Request Metadata" search capability to improve troubleshooting speed.
*   **Full Component Internationalization:** (Moved to v0.16.0) Systematic migration of all remaining hardcoded strings in Dashboard, Audit Log, and TopicManager views to the internationalization system.
*   **Theme Transition Animations:** (Moved to v0.16.0) Add smooth CSS transitions to all background and text colors to make theme switching feel more premium (fluid motion).
*   **Profile Image Upload:** (POV-146 Extension) Implement a profile image upload/management feature for the `/me` page, integrating with the backend user storage.
*   **Restore Content Duration Display (POV-170 Follow-up):** Investigate and resolve the backend gap where `formattedDuration` is missing for some content items in the briefing response.
*   **Unified API Error Handling for Sharing Hooks (POV-173 Follow-up):** Refactor the `onSuccess`/`onError` handlers in `ExportMenu` to use a shared functional pattern for toast notifications, reducing duplication between briefing and summary mutations.
*   **Automated Contrast Audit Script (POV-174 Follow-up):** Implement a Playwright-based audit script to verify that theme-aware accent variables (Blue-700, Amber-600) maintain > 4.5:1 contrast across all surface combinations after future CSS changes.
*   **Shadow Token Standardization (POV-174 Follow-up):** Migrate remaining component-specific shadow classes (e.g., `shadow-xl` in tooltips) to use the new global `--shadow-color` override to ensure 100% theme consistency.

### Backend & Architecture

*   **Job Retention Policy for `background_jobs` table:** (POV-165 follow-up) Implement a similar retention policy (e.g., 30 days) for the `background_jobs` table itself to prevent historical bloat.
*   **Dynamic Cron Scheduling:** (Backlog) Allow administrators to configure maintenance cron expressions via UI/API without requiring application restarts.
*   **Robust Subscriber Count Handling:** Re-evaluate the `subscriberCount` field in `ContentSource` and related classes to use `long` instead of `int` to prevent potential data truncation for very popular channels, as the YouTube API returns a `BigInteger`.
*   **Content Deduplication Engine:** Basic hash-based or semantic deduplication of sources (Deferred from v0.12).
*   **GenAI Rate Limiting (TPM/RPM):** Advanced token-per-minute and request-per-minute management for GenAI providers (Deferred from v0.12).
*   **UI Performance Monitoring:** Integrated performance metrics for the SPA (Deferred from v0.13).
*   **Refactor Blocking Code in Reactive Chains:** A significant portion of this was completed in v0.6.2 with the introduction of the `JustInTimeSummarizationScheduler`. A full code review should still be conducted to identify any other remaining instances of blocking code in reactive pipelines.
*   **Standardize Exception Message Assertions in Tests:** Create a helper or wrapper for `ApiException` (and other external exceptions) to extract clean error messages, or standardize error message assertions in tests to handle variable message formats (e.g., including status codes) more robustly.
*   **Advanced Idempotency (Full HTTP Response Replay):** Upgrade the current Idempotency-Key validation to fully cache and replay the exact HTTP response (including 400/500 `ProblemDetail` errors) for duplicate POST command requests, preventing any backend re-execution upon retry.
*   **Recursive Briefings (Map-Reduce):** Implementing hierarchical summarization for extremely long content sets or timeframes (Deferred from v0.12).
*   **Fix Qute Template Lints:** There are numerous lint warnings/errors in `admin.html`, `main.html`, etc. regarding unresolved objects (`activeTab`, `item`, etc.). These should be investigated and fixed to ensure a clean build and IDE experience.
*   **Primary Identity Resolution:** Refactor `UserMapper` to deterministically resolve a "primary" identity/provider when multiple OIDC accounts are linked to a single user.
*   **Background Jobs Cleanup Strategy:** (Moved to v0.16.0) Implement a background job to purge the `background_jobs` table of old `COMPLETED` or `FAILED` items to prevent historical database bloat.
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
*   **Centralized Lint Suppression Policy:** Create a shared configuration or a custom ESLint plugin to handle recurring framework "exceptions" (like TanStack redirects) without broad `eslint-disable` comments.
*   **Docker Build Optimization:** Optimize the root-level Dockerfile by using a more granular `COPY` strategy for dependencies (e.g., `package-lock.json` and `pom.xml` first) to leverage Docker's layer caching more effectively.
*   **Enhanced Docker Healthchecks:** Implement a robust Docker `HEALTHCHECK` in the final image, possibly using a dedicated `/q/health` endpoint or a simple `curl` check against the SPA landing page.
*   **Automatic Summary Generation Validation:** Add a backend check to prevent re-triggering "Generate Summaries" if a job for the same topic is already `RUNNING` or `QUEUED` to prevent resource waste.
*   **Improved Toast Actionability:** Enhance toast notifications with "View Job" or "Refresh Page" buttons for `COMPLETED` or `FAILED` states.
*   **Mapper Audit Field Standardization:** (Completed in v0.14.0 - POV-157) Centralized exclusions for auditable entities using @MappingConfig.
*   **Topic Template Preview:** (Backlog) Add a "Preview with Template" action in the `TopicForm`.
*   **Context-Aware UX Audit:** (POV-163 Backlog) Refine `ux_audit.py` to distinguish between global navigation menus (subject to Hick's Law) and content grids/data tables.
*   **Cross-platform Script Wrapper:** (POV-163 Backlog) Implement a lightweight Node.js or Python wrapper for `package.json` scripts to handle `./mvnw` vs `mvnw.cmd` transitions transparently across all shells.
*   **Frontend Lint Refactoring:** (Completed in v0.14.0 - POV-159) Resolved persistent unused variable warnings and standardized useState destructuring.
*   **Grafana Alerting implementation:** Define alerting thresholds for `Total Orchestration Failures > 10` to send notifications to Slack or Email.
*   **Business Intelligence Dashboard:** Extend Grafana with "User Engagement" metrics, tracking the most popular content sources and briefing generation patterns per user.
*   **Test Case Re-indexing Script:** (POV-166 follow-up) Develop a Python utility to automatically re-index `test_specification.md` and update all mapping references in Section 9 to prevent manual errors during large-scale refactors.
*   **Traceability Heatmap:** (POV-166 follow-up) Implement a visual representation (or Markdown table generator) showing the percentage of Test Cases covered by automated Playwright scenarios (PW-X) vs. manual ones.

+### Compliance & Documentation Debt
+
+*   **v0.16.0 Mandatory P0 Automation Debt**:
+    *   [ ] **TC-GRX-002 (Global Error Handling)**: Implement Playwright scenario for Error Boundary triggering. (Legacy Gap from v0.13).
+    *   [ ] **TC-SEC-002 (Role Protection)**: Implement Playwright scenario for Admin-only route access (`/admin/*`) for non-admin users. (Legacy Gap from v0.11).
+    *   [ ] **TC-LIB-001 (Briefing Search Flow)**: Add Playwright E2E test (PW-04) for the library search flow.
+    *   [ ] **TC-LIB-002 (Share Link Lifecycle)**: Add Playwright E2E test (PW-05) for the Share Link generation and validation lifecycle.
+
+### UX & Observability Debt
+
+*   **Toggle-Based State Unification:** Implement standard pattern for optimistic UI on toggle-based actions (e.g. "Share Link" creation) to avoid visual blinks on refetching.
+*   **Playwright Maintenance Strategy:** Define robust locators and state isolation to stabilize E2E tests and prevent regression flakiness.
+*   **Full-Text Search Migration:** Consider migrating the `BriefingLibrary` search to `pg_trgm` or Lucene when briefing volume justifies it (1000+).

### Process & Tooling (PPP Extensions)

*   **Semantic AI Planning Validator:** Upgrade `validate-planning.py` to use an LLM-based "Semantic Checker" to verify that ADRs and UI Manifestos actually address the problems defined in User Stories/Use Cases.
*   **Living Documentation (Two-way Sync):** Implement a development-time gate that forces a return-to-planning phase if implementation deviates from the UI Manifesto or ADRs. Ensures the "Map is the Territory".
*   **Automated Performance Auditing in Tasks:** Integrate the Performance Budgeting (NFRs) directly into the `validate-planning.py` to ensure every P0/P1 task has defined operational constraints.
*   **Discovery Phase Standardization:** Formalize the Socratic Discovery and Brainstorming phase in the Project Planning Protocol (PPP) as a mandatory pre-req for Task ID generation (POV-X).
*   **Full Supply Chain & Asset Audit:** Conduct a high-precision audit across the **Four Pillars** (Media, Code/Dependencies, Services/SaaS, and Infrastructure). Ensure proper attribution, license compatibility, performance optimization, and usage hygiene for all external dependencies.
*   **Knowledge Base Integrity Auditor (POV-172 Follow-up):** Implement a Python script to verify that all modular domain guides contain the required `POV-XXX` traceability IDs and that all relative links in the Knowledge Portal resolve correctly to prevent documentation rot.
