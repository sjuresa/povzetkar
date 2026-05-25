# Improvement Backlog

This document serves as a backlog for non-critical improvements, ideas, and minor issues that are discovered during development.

Items listed here are candidates for inclusion in future development cycles. Each item should be reviewed and prioritized during the planning phase of a new version.

---

## Candidate Improvements

### UI/UX

*   **Real-time Admin Page Updates:** Implement real-time updates for "Pending Content Items" and "Current Running Actions" on the admin page, possibly using WebSockets or polling.
*   **Admin Jobs Cancel Action:** Add cancel button to the Background Job Dashboard (`/admin/jobs`). Frontend implementation is straightforward once the backend signal mechanism is in place — see "Background Job Cancel (Signal Mechanism)" in the Backend section. Deferred from POV-188 (v0.17.0); restart + audit trail already delivered.
*   **Advanced Audit Log Filtering:** (POV-142 Success-Enhancement) Extend the Audit Log UI with date-range pickers and a "Request Metadata" search capability to improve troubleshooting speed.
*   **Full Component Internationalization:** (Moved to deferred) Systematic migration of all remaining hardcoded strings in Dashboard, Audit Log, and TopicManager views to the internationalization system.
*   **Profile Image Upload:** (POV-146 Extension) Implement a profile image upload/management feature for the `/me` page, integrating with the backend user storage.
*   **Anonymous Demo Trial (no OIDC):** Allow visitors to click "Preizkusi demo" on the landing page and view demo content without Google sign-in. v0.17.0 already covers the equivalent experience via OIDC sign-in (new users land in `PENDING_APPROVAL` with `demo` role and see demo content via impersonation), so this is only justified if there's a real friction signal from users who refuse to sign in with Google. Two viable implementations when needed: (B1) dedicated `@PermitAll` public endpoints under `/api/v1/public/demo/*`, or (B2) synthetic session issued for the demo user without OIDC. Decide at implementation time.
*   **Background Job Cancel (Signal Mechanism):** Implement cancel for `RUNNING` background jobs. Requires: (1) `cancelRequested` boolean flag on `BackgroundJob` entity + migration, (2) check-points in `OrchestrationManager` — between pages in `processPendingAndRetryItemsByPage` and between topics in `executeTopicProcessingForDate`, (3) `POST /api/v1/admin/jobs/{id}/cancel` endpoint with guard that prevents overwrite of CANCELLED status by the lifecycle's final `updateJobStatus` call. Without the signal mechanism, any status update is meaningless — `executeJobLifecycle` will overwrite it with `COMPLETED` when the chain finishes. Deferred from POV-188 (v0.17.0). Estimated ~4-6h backend. (See also UI/UX entry above.)
*   **Markdown Rendering Inconsistency (DRY):** POV-186 (v0.17.0) added `rehype-external-links` to the 5 call-sites that already had `[rehypeRaw, rehypeSanitize]`. Three call-sites remain without any plugins (`SummaryModal.tsx` line 151, `SystemMetadata.tsx` lines 189+227) — currently insecure (no sanitize) and external links don't open in new tab. Fix: extract a shared `<MarkdownContent>` wrapper with the full `[rehypeRaw, rehypeSanitize, rehypeExternalLinks]` stack and replace all 8 call-sites. Eliminates duplication and closes the security gap.
*   **E2E Visual Regression Testing (POV-178 Follow-up):** Integrate visual snapshot testing (Playwright `toHaveScreenshot`) for critical UI states (Trigger Modal, Briefing Viewer) to catch design regressions that don't trigger functional failures.

### Backend & Architecture

*   **OrchestrationManager → OrchestrationService rename:** Evaluated during v0.17.0 planning (2026-05-20) and **rejected**. The class is a true coordinator over six leaf services (`OrchestrationLockService`, `TopicProcessor`, `BriefingService`, `JustInTimeSummarizationScheduler`, `EventBroadcaster`, `MeterRegistry`); the "Manager" suffix carries useful semantic distinction from leaf `*Service` siblings. If reconsidered later, `OrchestrationCoordinator` would be a more accurate target than `OrchestrationService`. Note: historical docs reference the current name — rename would falsify those records.
*   **`update-version.cjs` sandbox compatibility:** Script invokes `mvnw.cmd` without `.\` prefix. Works in interactive shells but fails in restricted environments where `NoDefaultCurrentDirectoryInExePath=1` is set (e.g., Claude Code sandbox). Hardening fix: prefix the invocation with `.\` (Windows) or run via `path.join(rootDir, mvnw)`. Low priority — only affects automated/sandboxed runs.
*   **`Clock` injection in `TranscriptProviderQuotaService`:** Inject `java.time.Clock` instead of calling `YearMonth.now(ZoneOffset.UTC)` directly to allow deterministic testing of period-based quota logic. Raised in Gemini review of PR #252.
*   **Transcript Cache Layer:** A `transcript_cache` table keyed on video URL would eliminate redundant fetches across orchestration runs. Recognized in [ADR-0009](../adr/0009-multi-provider-transcript-strategy.md) as orthogonal to the multi-provider work. Candidate follow-up after v0.17.0.
*   **SMTP Infrastructure Foundation:** Dev SMTP server (Mailpit/MailHog), production SMTP provider, template engine, retry/error handling. Unblocks user approval notifications ([ADR-0008](../adr/0008-account-activation-state-machine.md) deferred this) and other transactional emails. Candidate for v0.18.0.
*   **Job Retention Policy for `background_jobs` table:** (POV-165 follow-up / v0.17.0) Implement a retention policy (e.g., 30 days) and automated cleanup job for the `background_jobs` table to prevent historical database bloat from `COMPLETED` or `FAILED` items.
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
*   **Grafana Alerting implementation:** Define alerting thresholds for `Total Orchestration Failures > 10` to send notifications to Slack or Email.
*   **Business Intelligence Dashboard:** Extend Grafana with "User Engagement" metrics, tracking the most popular content sources and briefing generation patterns per user.
*   **Test Case Re-indexing Script:** (POV-166 follow-up) Develop a Python utility to automatically re-index `test_specification.md` and update all mapping references in Section 9 to prevent manual errors during large-scale refactors.
*   **Traceability Heatmap:** (POV-166 follow-up) Implement a visual representation (or Markdown table generator) showing the percentage of Test Cases covered by automated Playwright scenarios (PW-X) vs. manual ones.
*   **System-Wide Performance Hardening (POV-175 Follow-up):** Retroactively apply the `QueryCountAssert` differential testing pattern to all 50+ existing integration tests to establish a baseline and prevent N+1 regressions across the entire API surface.

### Compliance & Documentation Debt

*   **P0 Automation Debt**:
    *   [ ] **TC-LIB-001 (View Briefing Content)**: Implement Playwright E2E test (PW-009) to verify Markdown rendering and navigation integrity.

*   **Automation Debt (Unscheduled/Deferred)**:
    *   [ ] **TC-TS-003 (YT Source Validation)**: Implement Playwright E2E test (PW-003) for real-time channel verification.
    *   [ ] **TC-LIB-002 (Archive Search)**: Add Playwright E2E test (PW-004) for the keyword-based library search flow.
    *   [ ] **TC-UI-004 (Dashboard Onboarding)**: Implement Playwright E2E test (PW-007) for the interactive checklist progress.
    *   [ ] **TC-EXP-003 (Shareable Link)**: Add Playwright E2E test (PW-008) for the shared briefing public URL resolution.
    *   [ ] **TC-EXP-004 (Individual Summary Read)**: Ensure Playwright E2E coverage (PW-010) for the CSPRNG-UUID public access logic.
    *   [ ] **TC-GRX-005 (Toast Actionability)**: Verify Playwright coverage (PW-011) for audio synthesis and interactive buttons in toast notifications.

### Deferred & Legacy Debt

*   **Restore Content Duration Display (v0.16.0 Finding):** Address the parity gap identified where backend metadata gaps led to empty duration fields in the UI. (Ref: Retrospective line 39).
*   **Responsive Header Layout Refinement (v0.16.0 Finding):** Refactor editorial headers to use a `flex-1 min-w-0` pattern for titles with fixed-width action siblings to prevent parentheses-based premature wrapping on medium-sized screens.
*   **Explicit Interaction Transition Scoping (v0.16.0 Finding):** Avoid universal transition selectors (`* { transition: ... }`) which cause mounting jank. Implement scoped transition classes targeted only at layout boundaries.
*   **Automated Contrast Audit Script:** Formalize the `accessibility_checker.py` audit into a dedicated CI gate or localized `npm run audit` workflow.
*   **Toggle-Based State Unification:** Implement standard pattern for optimistic UI on toggle-based actions (e.g. "Share Link" creation) to avoid visual blinks on refetching.
*   **Playwright Maintenance Strategy:** Define robust locators and state isolation to stabilize E2E tests and prevent regression flakiness.
*   **Full-Text Search Migration:** Consider migrating the `BriefingLibrary` search to `pg_trgm` or Lucene when briefing volume justifies it (1000+).

### Process & Tooling (PPP Extensions)

*   **Semantic AI Planning Validator:** Upgrade `validate-planning.py` to use an LLM-based "Semantic Checker" to verify that ADRs and UI Manifestos actually address the problems defined in User Stories/Use Cases.
*   **Living Documentation (Two-way Sync):** Implement a development-time gate that forces a return-to-planning phase if implementation deviates from the UI Manifesto or ADRs. Ensures the "Map is the Territory".
*   **Automated Performance Auditing in Tasks:** Integrate the Performance Budgeting (NFRs) directly into the `validate-planning.py` to ensure every P0/P1 task has defined operational constraints.
*   **Discovery Phase Standardization:** Formalize the Socratic Discovery and Brainstorming phase in the Project Planning Protocol (PPP) as a mandatory pre-req for Task ID generation (POV-X).
*   **Full Supply Chain & Asset Audit:** Conduct a high-precision audit across the **Four Pillars** (Media, Code/Dependencies, Services/SaaS, and Infrastructure). Ensure proper attribution, license compatibility, performance optimization, and usage hygiene for all external dependencies.
*   **Knowledge Base Integrity Auditor (POV-172 Follow-up):** Implement a Python script to verify that all modular domain guides contain the required `POV-XXX` traceability IDs and that all relative links in the Knowledge Portal resolve correctly to prevent documentation rot.
