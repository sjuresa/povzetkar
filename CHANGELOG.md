# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- (POV-193) **Transcript proxy service** (`transcript-proxy/`): a standalone, stateless FastAPI service that fetches a single YouTube transcript per request, deployable on a host whose IP is not blocked by YouTube (the OCI production range is). `GET /transcripts/{video_id}` with `X-API-Key` auth returns timestamped segments; 404 distinguishes `not_available_yet` (retry) from `video_unavailable` (permanent), 502 signals the proxy cannot reach YouTube. Quarkus integration is deferred to a follow-up task.

### Fixed

- (POV-192) Demo users (pending approval) no longer crash on Briefing Hub or Sources pages — added `SOURCE_VIEW` to `DEMO_PERMISSIONS` so the source filter API call succeeds.
- (POV-192) Topics page hides Edit, Delete, Run Now, and Add Topic controls for demo users to prevent confusing 403 failures on mutation actions.
- (POV-192) Demo topic "Science & Discovery" was incorrectly seeded as inactive — migration V35 corrects it to enabled.

## [0.17.0] - 2026-05-24

### Summary
Version 0.17.0 establishes **Administrative Control & User Onboarding** — the governance layer that makes Povzetkar production-ready for multi-user deployments. The centerpiece is a full Admin User Management surface backed by an explicit four-state Account Activation State Machine (`PENDING_APPROVAL → ENABLED / DISABLED / REJECTED`), with optimistic-locking guards and demo-content impersonation for new users. The Background Job Dashboard replaces a placeholder, providing restart capability and a full audit-trail modal for failed jobs. On the resilience side, transcript ingestion gains a multi-provider fallback chain (YouTube Legacy + Supadata pilot) with configurable jitter to defend against IP rate-limiting. Two long-standing UX friction points are resolved: external links now open in new tabs across all Markdown surfaces, and session expiry auto-redirects users back to their original page after re-authentication instead of landing them on an error screen.

### Added (POV-185)

- **Account activation state machine**: Four-state `AccountStatus` enum (`PENDING_APPROVAL`, `ENABLED`, `DISABLED`, `REJECTED`) replaces `users.active` boolean; Flyway migrations V30–V33.
- **Admin action endpoints**: `POST /api/v1/users/{id}/approve|reject|disable|enable` — each enforces state-transition guards via `UserAccountService`; self-disable (409) and last-admin (409) protections.
- **Optimistic locking on action endpoints**: `@OptimisticLocking` filter + `BaseApiResource.checkOptimisticLock()` (with weak ETag `W/` stripping) prevents lost updates when two admins act concurrently; `UserDTO.version` exposed so the frontend can forward the current entity version as `If-Match`.
- **`BusinessRuleException` for version conflicts**: `OPTIMISTIC_LOCK_ERROR` (409) returned via `BusinessRuleExceptionMapper` — consistent structured error shape with all other business rule failures.
- **`BusinessRuleException` + mapper**: Structured error codes (`INVALID_STATE_TRANSITION`, `CANNOT_SELF_DISABLE`, `LAST_ADMIN_REQUIRED`) returned in `ApiErrorDTO.errorCode`.
- **Demo content impersonation**: `UserContextService.getContentViewingUser()` — pending users with the `demo` role transparently see `demo@povzetkar.local` content; no frontend changes required for read paths.
- **`demo` role + permissions**: New `ROLE_DEMO` constant; `RolePermissionsMap` grants `TOPIC_VIEW`, `BRIEFING_VIEW`, `CONTENT_ITEM_VIEW`; new users created with `PENDING_APPROVAL` + `demo` role.
- **Demo seed (V32)**: `demo@povzetkar.local` + `Science & Discovery` topic (enabled=false) + Kurzgesagt (`UCsXVk37bltHxD1rDPwtNM8Q`) and Veritasium (`UCHnyfMqiRRG1u-2MsSQLbXA`) YouTube channels.
- **V33 migration**: Backfills `version = 0` for all users where the column is null, ensuring optimistic locking is safe for all pre-existing accounts.
- **`GET /me` open to all authenticated users**: Changed from `@RolesAllowed(ROLE_USER)` to `@Authenticated`; pending/disabled/rejected users can always fetch their account status.
- **`AccountStatusBanner` component**: Persistent sticky banner rendered in `AppShell` for non-ENABLED users; per-state i18n keys (`account.pending.*`, `account.rejected.*`, `account.disabled.*`).
- **i18n**: Added `account.pending.*`, `account.rejected.*`, `account.disabled.*` keys; added `admin.users.error.*` keys (`action_failed`, `cannot_self_disable`, `last_admin_required`) and `admin.users.success.*` keys to `en.json` and `sl.json`.
- **RUNBOOK section 15**: Documents approval requirement, admin action endpoints, initial admin seeding (Option A: SQL INSERT; Option B: approve after first sign-in), and demo content purpose.
- **eslint-plugin-sonarjs**: Added to `frontend/eslint.config.js`; all pre-existing SonarJS violations across the frontend resolved.

### Added (POV-184)

- **User Management admin surface** (`/admin/users`): full page with email search, status filter, sortable columns (Email, Status, Last Login, Created), and context-appropriate action buttons per account state.
- **`User.lastLoginAt` tracking**: nullable timestamp updated on every OIDC sign-in via `UserContextService.ensureUser()`, including PENDING users; Flyway migration V28.
- **`AccountStatus` enum stub**: `PENDING_APPROVAL`, `ENABLED`, `DISABLED`, `REJECTED` — prepared for POV-185 state machine wiring.
- **`GET /api/v1/users` filter + sort extensions**: `accountStatus` (enum, single-value) and `email` (case-insensitive substring) query params; sort by `email`, `accountStatus`, `lastLoginAt`, `createdAt`.
- **`UserDTO` expansion**: `accountStatus` and `lastLoginAt` fields exposed; `createdAt` added for UI display.
- **Sidebar nav entry**: "User Management" added to `AppShell.adminNavItems`; admin layout guard extended with `USER_MGMT_VIEW`.
- **i18n**: full `en.json` + `sl.json` coverage for all new admin user strings.

### Changed (POV-185)

- **`users.active` column removed**: Replaced by `users.account_status VARCHAR(50) NOT NULL`; existing rows backfilled (`active=true` → `ENABLED`, `active=false` → `DISABLED`).
- **`User.listAllEnabled()`**: Renamed from `listAllActive()`; filters on `accountStatus = ENABLED`; all `OrchestrationManager` call-sites updated.
- **`UserDTO.active` removed**: Dropped `boolean active` field; `accountStatus` and `version` auto-map via MapStruct.
- **`PATCH /{id}` and `PUT /{id}/roles` deleted**: Old user management endpoints removed; replaced by the four new action endpoints.
- **Read call-sites migrated**: `TopicApiResource`, `BriefingApiResource`, `DashboardApiResource`, and `TopicResource` (SSR) read paths now use `getContentViewingUser()` for demo impersonation; write paths unchanged.
- **`UserBuilder` refactored**: `boolean active` replaced by `AccountStatus accountStatus = ENABLED`; `.inactive()` removed; `.pending()` and `.withStatus(AccountStatus)` added.
- **`UserAccountService` layer separation**: Service methods (`approve`, `reject`, `disable`, `enable`) now accept a pre-loaded `User` entity; ETag parsing and version checking moved to `UserApiResource` via `BaseApiResource.checkOptimisticLock()`, keeping the service free of HTTP protocol concerns.
- **`UserContextService` demo user cache**: `getContentViewingUser()` caches the demo user ID in an `AtomicLong` with double-checked locking (`0` = uninitialised, `-1` = not found) — eliminates a DB query on every demo-role read request after the first.
- **`AccountStatusBanner` status map typed**: `statusKeyMap` uses `Partial<Record<AccountStatus, string>>` for compile-time exhaustiveness.
- **`UsersTable` sort state memoised**: `parseSortState(sort)` computed once per render into `sortState`; frontend error code extraction typed via generated `ApiErrorDTO`.

### Added (POV-186)

- **External links open in new tab**: All five in-scope `ReactMarkdown` call-sites in `BriefingDetailPage`, `PublicBriefingView`, and `PublicSummaryView` now use `[rehypeRaw, rehypeSanitize, rehypeExternalLinks]` — external links render with `target="_blank"` and `rel="noopener noreferrer"`.
- **`rehype-external-links ^3.0.0`** added to frontend `dependencies`.

### Fixed (POV-187)

- **Session expiry no longer triggers `ErrorPage`**: The Axios 401 interceptor now calls `clearAuth()` and immediately redirects to `/auth/login?redirect=<currentPath>` instead of re-throwing the error to the error boundary.
- **Seamless return after re-authentication**: `LoginResource.resolveSafeDestination()` validates and applies the `?redirect=` param after OAuth completes — the user lands directly on their original page with no manual interaction required.
- **Vite proxy OIDC redirect rewrite**: The Vite dev proxy was forwarding Quarkus OIDC 302 redirects to `accounts.google.com` back to the browser; Axios followed them cross-origin causing CORS failures. A `proxyRes` handler in `vite.config.ts` rewrites 302→Google responses to 401 for XHR requests (`X-Requested-With: XMLHttpRequest`), so the 401 interceptor fires cleanly.
- **ADR-0010 updated**: Decision revised from `sessionStorage` + landing page banner to direct auto-login via `?redirect=` param; `sessionStorage` not used.

### Added (POV-188)

- **Background Job Dashboard** (`/admin/jobs`): functional `AdminJobsPage` replacing the placeholder div; filters by `jobType` and `status` (apply-on-change dropdowns); table columns Started (sortable), Type, Duration, Status, Actions; 30s auto-refetch when RUNNING jobs are present on the current page.
- **Restart failed jobs**: `POST /api/v1/admin/jobs/{id}/restart` endpoint (gated by `ADMIN_JOB_UPDATE`); supports 5 restartable job types (system-wide + topic-scoped); item-level jobs return 400; audit entry with admin user ID written to `job_execution_audit` on the original job.
- **`jobType` filter on `GET /api/v1/admin/jobs`**: new `jobType` query param; `BackgroundJobService.getJobList()` refactored from `if/else` branching to `FilterBuilder` to handle all filter combinations cleanly.
- **Audit trail modal**: `[📋]` button per row opens a modal showing `JobExecutionAudit` entries for that job, reusing the existing `GET /api/v1/admin/jobs/{id}/audit` endpoint.
- **i18n**: `admin.jobs.*` keys added to `en.json` and `sl.json`; cancel-related keys omitted (feature deferred to v0.18.0).
- **`V_ADMIN_JOB_UPDATE` permission**: previously defined but unused — now wired to the restart endpoint.

### Added (POV-189)

- **Multi-provider transcript pipeline**: `TranscriptProvider` interface + `TranscriptOrchestrator` replace the single-provider `YouTubeTranscriptService`; ordered fallback chain driven by `transcript.providers.order` config; per-provider `enabled` flag; Micrometer metrics (`transcript.fetch.attempts|successes|failures|duration`) tagged by provider.
- **`YouTubeLegacyTranscriptProvider`**: wraps existing `thoroldvix` library; configurable random jitter (`jitter.min-ms` / `jitter.max-ms`) before each fetch to defend against OCI IP rate-limiting by YouTube.
- **`SupadataTranscriptProvider`**: pilot second provider via MicroProfile REST Client (`SupadataClient`); handles 200 (sync) and 202 (async poll, 10 s timeout); converts millisecond chunk offsets to seconds; requires `SUPADATA_API_KEY` env var.
- **`TranscriptSegment` record**: shared exchange type decoupling both providers from the `thoroldvix` library types.
- **`transcript_provider_quota_usage` table** (V34 migration): tracks per-provider monthly fetch counts via `ON CONFLICT DO UPDATE` upsert; populated by `TranscriptProviderQuotaService` on every successful fetch.
- **RUNBOOK §14.1a**: documents transcript provider config flags, enabling Supadata as primary provider, and emitted metrics.

### Changed (POV-189)

- **`quarkus-rest-client-jackson`** dependency scope promoted from `test` to compile (required for `SupadataClient` serialization at runtime).
- **ADR-0009** status updated to `Accepted`; Supadata API spike findings (requires full YouTube URL, not `videoId`) documented.

### Removed (POV-189)

- **`YouTubeTranscriptService`**: deleted; replaced by `YouTubeLegacyTranscriptProvider` + `TranscriptOrchestrator`.

## [0.16.0] - 2026-04-13

### Summary
Version 0.16.0 focuses on **Aesthetic Hardening, Gamified Engagement, and Technical Depth**. It introduces a professional **Monochromatic Zinc taxonomy**, purging legacy color literals to achieve WCAG AA compliance across all dashboard surfaces. The release also implements a **Gamified Onboarding** experience with a 5-step interactive checklist to guide users through the 'Golden Path', complemented by a new **Auditory Feedback Framework**. Technical foundations are strengthened with automated **N+1 query detection**, a modularized **Knowledge Base**, and critical **E2E coverage** for role-based security and global error handling.

### Added
- **Briefing UI Polish (POV-170):** Standardized source metadata with a high-contrast icon-based header (`Calendar`, `Clock`, `Timer`), restoring prominence to publication date and time.
- **Header Layout Fix (POV-170):** Optimized briefing titles to occupy the full container width, solving a layout regression where long titles wrapped prematurely across internal and public views.
- **Data Model (POV-170):** Promoted `authorName` to the base `ContentItem` entity and DTO, enabling universal attribution across all current and future content types.
- **Auditory Feedback Framework (POV-171):** Implemented a standardized audio notification system for system events. Features zero-asset overhead via dynamic **Web Audio API synthesis** (`OscillatorNode`).
- **Legal Compliance (POV-171):** Established a centralized **Third-Party Assets & Compliance Registry** (`LEGAL.md`) to track all media, code, and infrastructure dependencies.
- **Knowledge Base Modularization (POV-172):** Decomposed the monolithic `lessons-learned.md` into 6 domain-specific guides (`database.md`, `backend-api.md`, `frontend-ux.md`, `reactive.md`, `testing.md`, `infrastructure-ops.md`) and a consolidated legacy archive.
- **Knowledge Portal (POV-172):** Transformed the root `lessons-learned.md` into a high-visibility portal featuring curated v0.16.0 technical highlights and structured navigation.
- **Theme Readability (POV-174):** Implemented high-priority Light Mode contrast fixes by migrating to the **Zinc Palette** semantic taxonomy. Achieved WCAG AA (4.5:1) compliance across all core dashboard surfaces.
- **Accent Strategy (POV-174):** Introduced a high-contrast **Blue-700** primary accent for Light Mode to ensure visibility on secondary backgrounds (Neutral-100), satisfying the > 4.5:1 contrast requirement.
- **Admin Branding (POV-174):** Standardized Admin-specific actions with a high-contrast **Amber-800** semantic accent.
- **Shadow Standardization (POV-174):** Implemented theme-aware global shadow overrides in the Tailwind `@theme` block, ensuring shadows shift naturally with the active theme.
- **QueryCountAssert (POV-175):** New utility for automated N+1 query detection in integration tests.
- **Gamified Onboarding (POV-177):** Implemented a 5-step interactive "Golden Path" checklist (Topic, Source, Trigger, Read, Share) to drive user engagement. Features progressive disclosure (pill vs. list) and a celebration state at 100% completion.
- **Onboarding Persistence (POV-177):** Developed a hybrid Zustand store (`povzetkar_onboarding`) with LocalStorage persistence and automated state synchronization from `DashboardDTO` for existing users.
- **Visual How-To Guides (POV-177):** Created 5 minimalist AI-generated illustrations and numbered sub-steps for every onboarding goal, providing clear instructions within the application context.
- **Action Triggers (POV-177):** Integrated automated step completion triggers across `TopicForm`, `TriggerOrchestrationModal`, `BriefingDetailPage`, and `ExportMenu` for a seamless "learning by doing" experience.
- **Bilingual Onboarding (POV-177):** Full EN/SL localization for all checklist items and modal instructions.
- **Monochromatic UI Hardening (POV-178):** Enforced 100% theme consistency by purging remaining legacy blue/indigo and green color literals across the application. Migrated to a strict monochromatic Zinc-neutral taxonomy.
- **Form Component Standardization (POV-178):** Implemented a global `accent-color` override for native form controls (checkboxes, radio buttons), ensuring all browser-default interactive elements adhere to the Refined Zinc Bento design tokens.
- **Trigger Modal Refinement (POV-178):** Standardized input accents and interactive icons within the `TriggerOrchestrationModal`, matching orchestration triggers to the institutional-grade primary action palette.
- **E2E Stability (POV-178):** Hardened the Playwright test suite by implementing network-level mocking for authentication paths and injecting `data-testid` attributes into critical UI anchors (`brand-logo`, `hero-cta`), eliminating brittle dependencies on layout and localization.
- **E2E Coverage (POV-180):** Closed critical automation debt by implementing Playwright guards for Role-Protection (TC-SEC-002) and Global Error Catching (TC-GRX-002). Added verification for admin-route redirects and transient error toast notifications.

### Changed
- **Bundle Optimization (POV-176):** Reduced frontend JavaScript bundle size by replacing the `uuid` package with native `crypto.randomUUID()` and consolidating HTML export parsing onto the existing `react-markdown` engine, completely eliminating `marked` and `dompurify` dependencies from the payload.
- **Tooling (POV-176):** Added `vite-bundle-visualizer` to development scripts to proactively monitor bundle sizes against the < 1.1MB performance budget.
- **AI Prompt Cleanup (POV-170):** Stripped redundant UI-level headers (`## Daily Briefing`, `### 1. Executive Summary`, etc.) and metadata from the AI summarization prompts. The AI now generates "Pure Intelligence," while the System provides the structural "Frame."
- **Prompt Architecture (POV-170):** Migrated all summarization templates to a named `{{variable}}` syntax (e.g., `{{title}}`, `{{transcript}}`), replacing fragile positional `%s` formatting and improving template maintainability.
- **Backend Infrastructure (POV-170):** Updated `ContentCreationService` and `VideoSummaryService` to support the new template variable injection logic.
- **Cleanup (POV-170):** Simplified shared briefing and summary views by removing unimplemented export/sharing features to maintain a clean interface.
- **Event Store (POV-171):** Extended `eventStore.ts` with a transient `lastNotification` signal to decouple auditory triggers from business logic.
- **Job Events (POV-171):** Integrated auditory signaling into the `useJobEvents.ts` SSE listener, triggering sounds for `COMPLETED`, `FAILED`, and `ORCHESTRATION_COMPLETED` events.
- **Documentation Architecture (POV-172):** Migrated to a modular sub-directory structure (`docs/process/lessons/`) with stripped numeric prefixes and hardened relative link integrity for improved discoverability and AI context efficiency.
- **Unified Sharing & Export (POV-173):** Consolidated summary and briefing sharing into a single polymorphic `ExportMenu` component, granting individual summaries full Markdown and HTML export capabilities.
- **AppShell Refactor (POV-174):** Modernized the application shell by replacing hardcoded Slate/Blue classes with the new Zinc semantic variable taxonomy.
- **Dashboard Hardening (POV-174):** Refactored `StatCard`, `RecentBriefingList`, and `DashboardPage` to use semantic variables, resolving contrast regressions in active job indicators and icon squares.
- **Typography Polish (POV-174):** Standardized interactive label weights to `font-weight: 500` and primary headers to `font-weight: 900` to improve legibility in bright neutral environments.

### Fixed
- [POV-175] Resolved N+1 query bottleneck in `BriefingApiResource.getBriefings` by implementing HQL Join Fetch, reducing database round-trips from O(N) to O(1).
- [POV-175] Standardized explicit HQL aliasing and sorting prefixes for consistent query construction.

## [0.15.0] - 2026-03-30

### Summary
Version 0.15.0 focuses on **Operational Excellence and Advanced Analytics**, delivering "Analytical Depth" through a searchable **Briefing Archive**, fully **Exportable intelligence** in Markdown/HTML, and robust real-time input validation. It also introduces enterprise-grade observability via **Prometheus and Grafana** integrations, solidifies the architecture with **self-cleaning retention jobs**, and centralizes the platform's stability through the establishment of a **Master Test Specification** linked to verifiable user stories.

### Added
- **Quality Assurance (POV-166):** Established a permanent, living **Master Test Specification** (`test_specification.md`) with 3-digit sequential indexing (`TC-DOM-0XX`). Centralized all functional and non-functional regression scenarios from v0.12 through v0.15.
- **Requirements Traceability (POV-166):** Hardened the documentation stack by assigning unique IDs to all **User Stories (US-X)** and formalizing the mapping from User Stories ➔ Use Cases ➔ Test Cases.
- **Planning Integrity (POV-167):** Developed scripts/qa/validate-planning.py, an automated audit tool that enforces deep traceability between tasks, use cases, and test specifications. Features a **Version-Aware Automation Guardrail** that flags critical (P0) test cases missing automation. Includes a "Time-Bomb" mechanism (validate:skip until vX.Y.Z) to prevent indefinite technical debt accumulation.
- **Global Resilience (POV-166):** Introduced **Domain 0: GRX** specifically for baseline UI/UX stability (Skeleton loaders, Error boundaries, Theme persistence), ensuring core resilience is verified in every release.
- **Process Guardrails (POV-166):** Updated the **Development Process Checklist** with a mandatory "Master Specification Sync" step, enforcing documentation integrity as a Phase 2 completion gate.
- **Operational Maintenance (POV-165):** Implemented a 90-day retention policy for `JobExecutionAudit` records. The system now performs a "Safety-First" chunked deletion (1,000 records per transaction) daily at 03:00 UTC, preventing database performance degradation from historical log accumulation.
- **Observability (POV-165):** Integrated Prometheus metrics for the retention job, exposing `povzetkar_maintenance_audits_purged_total` to track long-term data hygiene.
- **Frontend Infrastructure (POV-165):** Upgraded the frontend build environment to **Node 24 LTS** and updated `@types/node` to `^24.0.0`, ensuring compatibility with the latest runtime and improved build-time type safety.
- **Briefing Library (POV-161):** Implemented a searchable, filterable **Briefing Library** page (`/briefings`), replacing the previous placeholder. Users can now search briefings by keyword, filter by Topic, Source, or Date Range, and paginate through their complete briefing archive.
- **Backend (POV-161):** Extended `GET /api/v1/briefings` with `search` (LIKE on title/metaSummary) and `sourceId` (EXISTS subquery) query parameters for advanced filtering.
- **Briefing Detail (POV-161):** Implemented the **Briefing Detail** page (`/briefings/{id}`) with full markdown rendering of AI-generated briefing text, a highlighted Executive Summary card, and a Source Content grid displaying analyzed content items with thumbnails and external links.
- **i18n (POV-161):** Full localization support for both English and Slovenian across the Briefing Library and Briefing Detail pages.
- **Export & Share (POV-162):** Implemented an **Opt-in Sharing Model**. Briefings are private by default; users must explicitly "Enable Share Link" to generate a secure UUID-based public URL.
- **Public UI (POV-162):** Developed a premium, standalone **Public Briefing Page** (`/s/$uuid`) for recipients. Features include a Sun/Moon theme toggle, an EN/SL language toggle for UI labels, and locale-aware date formatting.
- **Export Capabilities (POV-162):** Implemented a high-fidelity **Black & White print-ready HTML export** using `marked`. Exported files feature topic-aware filenames (e.g., `Topic_Title.html`) and serif-based typography optimized for physical printing.
- **SEO (POV-162):** Integrated `react-helmet-async` for dynamic SEO and OpenGraph metadata on shared briefings, ensuring rich previews (title/summary) when shared on social media or Slack.
- **Source Validation (POV-163):** Implemented a live, debounced existence check for YouTube channels in the `QuickSourceIntake` component. This prevents invalid sources from being added by performing real-time API validation against the YouTube Data API.
- **Quality Hardening (POV-163):** Achieved 100% compliance with strict quality audits, resolving issues in heading hierarchy, image accessibility (alt-tags), and unhandled asynchronous promises (`no-floating-promises`).
- **Observability (POV-164):** Integrated a full **Prometheus & Grafana** monitoring stack into the local development environment (`backend/docker-compose.yml`).
- **Backend Instrumentation (POV-164):** Instrumented core services (`OrchestrationManager`, `YouTubeTranscriptService`, `ContentSourceResource`) with Micrometer-based metrics (summarization rates, failure counters, active thread gauges).
- **Security (POV-164):** Implemented a secure Bearer Token authentication mechanism (`METRICS_SCRAPE_TOKEN`) for the `/q/metrics` endpoint, ensuring that sensitive performance data is not exposed to unauthorized pollers.
- **Visual Excellence (POV-164):** Developed the **"Povzetkar Baseline" Grafana Dashboard** with real-time throughput tracking, failure distribution, and active orchestration gauges, utilizing automated provisioning (Dashboards-as-Code).
- **Documentation (POV-164):** Fully documented the monitoring infrastructure, secret management, and verification procedures in `docs/RUNBOOK.md`.

## [0.14.0] - 2026-03-28

### Summary
Version 0.14.0 focuses on **Customization, Reliability, and Data Integrity**. Key highlights include **Custom Prompt Templates** at the topic level, a hardened **Distroless Java 25 Runtime**, and a premium distillery-inspired **Landing Page**. User control is significantly enhanced with a new **Orchestration Trigger Dialog** (allowing dry-runs and parameter refinement) and a global **Content Management Dashboard** for administrative oversight of the ingestion pipeline.

### Added
- **AI Customization (POV-153):** Implemented support for **Custom Prompt Templates** at the topic level. Users can now define specialized summarization instructions (e.g., specific language, tone, or format) that override the global default.
- **Backend (POV-153):** Integrated `promptTemplate` fallback logic in `VideoSummaryService`, ensuring a graceful return to the global prompt if a custom one is not provided.
- **Frontend (POV-153):** Enhanced the **Topic Edit/Create form** to support prompt template management with a live "Reset to Default" capability.
- **Content Ingestion (POV-153):** Implemented **Automated Source Enrichment**. Adding a YouTube channel now automatically fetches its name, description, and subscriber count via the YouTube Data API.
- **Content Ingestion (POV-153):** Implemented **Idempotent Source Creation**. Attempting to add an existing source now gracefully returns the current record with a `200 OK` instead of a conflict, facilitating smoother user flows.
- **Orchestration (POV-154):** Implemented a specialized **Trigger Dialog** (Run Now) allowing users to configure orchestration parameters before execution.
- **Orchestration (POV-154):** Added support for **Preview Mode** (Dry Run), enabling users to see pending items and potential status changes without persisting briefings.
- **Orchestration (POV-154):** Added a **Briefing Toggle** to skip briefing generation during manual runs.
- **Content Management (POV-155):** Implemented a global **Content Management** dashboard (`/admin/content`) for administrative oversight of all ingested content items.
- **Admin Tools (POV-155):** Added support for **Manual Status Overrides**, allowing administrators to force status transitions (e.g., PENDING → COMPLETED, FAILED → PENDING) via a secure administrative API.
- **Security (POV-156):** Transitioned the Docker runtime to an **official Distroless Java 25** image (`gcr.io/distroless/java25-debian13`). This definitively resolves multiple high-severity CVEs by eliminating the shell, `busybox`, `coreutils`, and `curl` from the production environment.
- **Security (POV-156):** Extended the **Vulnerability Scanner** (`security_scan.py`) with a dedicated **Docker Hardening Audit** module, enabling automated verification of image security and Distroless compliance.
- **Quality (POV-157):** Enabled **Jacoco Code Coverage** reporting in the backend build pipeline with auto-generation in `target/jacoco-report/`.
- **Quality (POV-157):** Achieved a 100% clean project-wide ESLint audit. Resolved over 150 TypeScript violations related to unsafe `any` usage, implicit member access, and async promise handling in the frontend.
- **Quality (POV-157):** Enabled **Frontend Code Coverage** using Vitest and `@vitest/coverage-v8`, with auto-generation in `frontend/coverage/`.
- **Landing Page (POV-158):** Implemented a high-converting, vision-driven **Landing Page** emphasizing the "Pyramid of Value" (Information ➔ Knowledge ➔ Wisdom). Features high-impact brutalist typography, responsive hero layouts, and unified insight-driven CTAs ("Get Key Insights").
- **Discovery Hub (POV-158):** Implemented the **System Discovery Hub**, a theme-aware metadata drawer providing grounded visibility into the project's **Roadmap**, **Changelog**, and **Wisdom/Reality Check** methodology.
- **i18n (POV-158):** Full localization support for both English and Slovenian across all visionary content, including differentiated "Preverjanje realnosti" (Reality Check) interactions.

### Changed
- **Database (POV-153):** Updated `ContentSource` entity to prioritize auto-fetched metadata while ensuring fallback to the source identifier for display.
- **Orchestration (POV-153):** Refactored `DefaultBriefingGenerator` to utilize shared transactions for historical summary lookups, improving database visibility and test reliability.
- **API (POV-155):** Enhanced the Content Item API with advanced filtering by **Status** and **Topic**, and server-side sorting by publication date.
- **Frontend (POV-155):** Updated the **Source Detail** page and **Admin Content Management** table to support individual content item status overrides with premium glassmorphic UI.
- **Documentation (POV-157):** Synchronized ERD (`04_database_design.md`) with V22 migration scripts, adding documented function-based indexes for `content_sources`.
- **Documentation (POV-157):** Updated `ADR-0004` (Frontend Architecture) to reflect the decommissioning of testing-only SSE pulse endpoints.

### Fixed
- **Error Handling (POV-153):** Hardened the Content Source API to provide `400 Bad Request` responses for invalid platforms/identifiers, eliminating 500 crashes.

## [v0.13.0] - 2026-03-24

### Summary
Version 0.13.0 focuses on **User Experience Foundations, Resilience, and Infrastructure Hardening**. It introduces a hardened network layer with automated retries, a global Error Boundary for catastrophic failure recovery, and a localized, themeable user environment. Key highlights include the new User Profile page, dual-language support (English/Slovenian), and a robust Light/Dark/System theme system with persistence. This release also solidifies the backend with a persistent **Job Execution Audit** system and a comprehensive **Secrets Management** strategy (fail-fast validation and masking), ensuring a professional and secure platform for content orchestration.

### Added
- **Infrastructure (POV-149):** Implemented `SecretConfigValidator` — a startup fail-fast validator that verifies all required secrets (API keys, DB password, OIDC credentials) are present before the application starts.
- **Infrastructure (POV-149):** Configured Quarkus secret-keys masking (`quarkus.config.secret-keys`) to prevent sensitive property values from appearing in diagnostic output.
- **Infrastructure (POV-149):** Suppressed config value logging at startup via `quarkus.config.log.values=false` to prevent accidental secret leakage.
- **Infrastructure (POV-149):** Added Section 14 "Secrets Management" to `RUNBOOK.md` with inventory, environment-specific injection methods, and security best practices.
- **Monitoring (POV-150):** Implemented a persistent **Job Execution Audit** system, enabling granular tracking of status transitions and item-level failures for background jobs.
- **Monitoring (POV-150):** Added `GET /api/v1/jobs/{id}/audit` endpoint to retrieve historical audit trails for specific jobs.
- **Infrastructure (POV-145):** Integrated `axios-retry` into the global API client with exponential backoff for network errors and transient 5xx status codes (502, 503, 504).
- **Frontend (POV-145):** Implemented a global **React Error Boundary** (`ErrorBoundary.tsx`) to catch unhandled rendering exceptions.
- **Frontend (POV-145):** Fabricated a professional-grade **Error Page** (`ErrorPage.tsx`) with a "Midnight Slate" aesthetic, providing graceful failure recovery and navigation.
- **Frontend (POV-145):** Created a reusable **Skeleton** component (`Skeleton.tsx`) with premium "pulse" animations and glassmorphic borders for consistent loading states.
- **Frontend (POV-145):** Developed a comprehensive **E2E Test** (`error-boundary.spec.ts`) verifying that catastrophic API failures correctly trigger the global Error Page.
- **Frontend (POV-146):** Implemented the **User Profile page** (`/me`) with a premium identity card displaying name, email, auth provider, and assigned permissions.
- **Infrastructure (POV-146):** Configured **Internationalization (i18n)** with `i18next`, featuring English and Slovenian translations with hybrid lazy-loading support.
- **Frontend (POV-146):** Developed a persistent **Theme System** (Light, Dark, System) using Zustand and Tailwind 4 CSS variables, featuring a professional "Polished Silver" light mode.
- **Frontend (POV-146):** Integrated **Header Shell Controls** including a dynamic Theme Toggle and Language Switcher next to the interactive user profile.
- **Backend (POV-148):** Enhanced `ContentSourceResource` with a case-insensitive **Search API** (`?search=`) for content sources.
- **Frontend (POV-148):** Implemented the **Sources Library** (`/sources`), a searchable and paginated hub for all ingested content providers.
- **Frontend (POV-148):** Developed the **Source Detail** page (`/sources/$id`), providing deep visibility into individual source items and their processing history.
- **Frontend (POV-148):** Extracted a reusable **Pagination** component, standardized across the Audit Log and Source Library.
- **Backend (POV-148):** Implemented a specialized **Summary API** (`/api/v1/content-items/{id}/summary`) to retrieve individual content piece summaries.
- **Frontend (POV-148):** Integrated a **Glassmorphic Summary Reader** with `react-markdown` support, a dedicated Executive Summary highlight, and an auditable **Reference Tag** (ITEM-ID).

### Changed
- **Infrastructure (POV-149):** Updated `backend/.env.example` to include `GOOGLE_OIDC_CLIENT_ID` and `GOOGLE_OIDC_CLIENT_SECRET` placeholders.
- **Infrastructure (POV-149):** Fixed test-profile property names for YouTube/Google API keys (corrected namespace from `quarkus.youtube.api.key` to `youtube.api.key`).
- **Database (POV-150):** Added `job_execution_audit` table (V23) with foreign key support to `background_jobs` and `content_items`.
- **Frontend (POV-145):** Migrated from the custom toast system to **Sonner**, enabling a more flexible and visually polished notification experience.
- **Frontend (POV-145):** Refactored `DashboardPage` and `AuditLogPage` to utilize the new `Skeleton` system and global error handling, simplifying component logic.
- **Frontend (POV-145):** Configured TanStack Query to use `throwOnError: true` by default, delegating error UX to the global boundary for a more unified architecture.
- **Frontend (POV-146):** Refactored global CSS to use semantic **Theme Tokens**, enabling seamless switching between "Midnight Slate" and "Polished Silver" aesthetics.
- **Frontend (POV-146):** Updated the **AppShell Header** to be fully interactive, linking the user profile to the new identity page.
- **Frontend (POV-148):** Refactored **AuditLogPage** to utilize the shared `Pagination` component, eliminating redundant UI logic.
- **Frontend (POV-148):** Regenerated the **API Client** using Orval to support the new searchable backend parameters.
- **Frontend (POV-148):** Updated the **Source Detail** page with a "View Summary" action for all processed content items and added a formatted **Duration** column (MM:SS) to the content list.

### Fixed
- **Logging (POV-150):** Resolved `IllegalArgumentException` in `StructuredLogger` by adding a `warn(Throwable, Object...)` overload to handle exceptions with context safely.
- **Testing (POV-150):** Hardened the integration test suite by implementing robust data setup for `User` and `ContentSource` entities, resolving intermittent foreign key constraint violations.
- **Backend (Orchestration):** Resolved `ContextNotActiveException` in background jobs by wrapping database operations in `QuarkusTransaction.requiringNew()`.
- **Backend (Orchestration):** Refactored `BackgroundJobService` to eliminate duplication in topic-related job creation via a unified helper.
- **Backend (Persistence):** Fixed `LazyInitializationException` by implementing eager loading of content sources via `Topic.findByIdWithSources()`.
- **Backend (Orchestration):** Restored inadvertently overwritten `findEnabledByUsers` method in `Topic.java` to maintain global processing stability.
- **Backend (API):** Fixed a timeout in `IdempotencyFilter.java` for requests with empty bodies.
- **Frontend (API):** Aligned `customInstance` to return the full response object, improving type safety and simplifying data access across the application.
- **Frontend (UX):** Improved toast notifications to support stacking and descriptive messages containing topic names.
- **Frontend (Build):** Resolved production build errors by removing unused imports in `TopicList.tsx`.
- **Frontend (i18n):** Added missing Slovenian translation keys for topic source counts.
- **Backend (Migration):** Added `topic_id` to `background_jobs` table (V21) for improved auditability.

### Removed
- **API (POV-157):** Decommissioned the obsolete `/api/v1/events/test-pulse` endpoint and its associated frontend "Test Pulse" button logic.
- **Infrastructure (POV-149):** Removed unused `github.username` and `github.pat` config properties from `application.properties` (dead code — not referenced in any Java class).
- **Frontend (POV-145):** Decommissioned legacy `Toaster.tsx` and `use-toast.ts` hooks in favor of the unified `sonner` implementation.

## [v0.12.0] - 2026-03-18

### Summary

Version 0.12.0 marks a significant milestone in Povzetkar's evolution, transitioning the project into a professional, production-ready Full-Stack application. This release finalizes the monorepo architecture, integrating a high-performance React/TypeScript frontend with a hardened Quarkus backend. Key highlights include a unified Docker deployment strategy with automated security scanning, a premium glassmorphic Dashboard Hub with real-time SSE job tracking, and a comprehensive Audit Log Admin UI for technical observability. With 100% end-to-end type safety, robust authentication guards, and a standardized API contract, version 0.12.0 establishes a rock-solid foundation for scalable content orchestration.

### Documentation
- **Frontend Architecture:** Finalized ADR-0004 specifying the frontend technology stack: React, Vite, TypeScript, Tailwind CSS, Shadcn/UI, Orval, TanStack Query, and TanStack Router. The architecture adopts a hybrid monorepo structure (co-located folders) without root-level workspaces, minimizing repository clutter while ensuring 100% type-safety and SEO compliance via pre-rendering.

### Added
- **Infrastructure (POV-141):** Implemented a root-level **Multi-stage Dockerfile** integrating the React frontend into the Quarkus backend for a unified production image.
- **Infrastructure (POV-141):** Integrated **Container Image Scanning (Trivy)** into the GitHub Actions CI pipeline, scanning the final build for vulnerabilities.
- **Frontend (POV-142):** Implemented the **Audit Log Admin UI** for technical observability of API requests and background system events.
- **Frontend (POV-142):** Fabricated a high-density **Audit Log Data Grid** with real-time filtering by User ID, HTTP Method, Status Code, and Correlation ID.
- **Frontend (POV-142):** Integrated permission-based **Admin Navigation and Routing** with automated guards for `ADMIN_AUDIT_LOG_VIEW`.
- **Backend (POV-142):** Hardened the **Audit Log API** with dynamic HQL filtering and 100% type-safe OpenAPI pagination models.
- **Frontend (POV-140):** Integrated real-time **SSE Job Tracking** (`GET /api/v1/events/stream`) into the UI with a dedicated Zustand `eventStore`.
- **Frontend (POV-140):** Implemented a lightweight **Toast Notification system** providing instant feedback on orchestration success, failures, and heartbeat (PULSE) status.
- **Frontend (POV-140):** Integrated **Real-time Status Badges** into the Dashboard Hub, reflecting live "Processing" states for background jobs without page refreshes.
- **Frontend (POV-139):** Implemented the interactive **Dashboard Hub** fetching real-time data from the `GET /api/v1/dashboard` BFF endpoint.
- **Frontend (POV-139):** Fabricated glassmorphic **Stat Widgets** for Active Topics, Weekly Output, and Latest Activity with `text-glow` effects.
- **Frontend (POV-139):** Integrated an **Empty State "Getting Started" guide** for new users to facilitate onboarding (Source → Topic → Briefing).
- **Frontend (POV-139):** Implemented a high-fidelity **Recent Briefings list** with interactive cards and navigation to a new detail view.
- **Frontend (POV-139):** Added a premium **Briefing Detail stub** at `/briefings/$id` to complete the user journey navigation.
- **Frontend (POV-139):** Implemented "Refetch on Focus" and manual refresh capability for dashboard data utilizing TanStack Query.
- **Frontend (POV-138):** Implemented authentication foundations using **Zustand** and the BFF pattern (Session Cookies).
- **Frontend (POV-138):** Integrated **TanStack Router** with type-safe context and `protectedRoute` auth guards.
- **Frontend (POV-138):** Fabricated a premium **AppShell** with a collapsible "Midnight Slate" sidebar, glassmorphic header, and smooth transitions.
- **Frontend (POV-138):** Implemented high-conversion **Landing Page** and protected **Dashboard** shell.
- **Frontend:** Implemented type-safe API foundations (POV-137) using Orval and TanStack Query, automated from the backend OpenAPI specification.
- **Frontend:** Implemented a global Axios interceptor for the standard API envelope (`{ data, meta, error }`), request tracing (`X-Correlation-ID`), and idempotency (`Idempotency-Key` for command endpoints).
- **Frontend:** Standardized global error handling in the API client to translate backend error codes into typed frontend exceptions.
- **Infrastructure:** Restructured the repository into a monorepo by migrating all backend code, configurations, and resources into a dedicated `/backend` module.
- **Infrastructure:** Introduced a root-level aggregator `pom.xml` to manage the project's parent-child relationship and shared dependency management.
- **Frontend:** Initialized the React/Vite/TypeScript frontend project in `/frontend` with Tailwind CSS 4, Shadcn/UI foundations, and `vite-react-ssg` for SEO pre-rendering.
- **Frontend:** Configured Vitest and Playwright testing frameworks with initial smoke tests.
- **Infrastructure:** Established root-level `package.json` for monorepo-wide script orchestration (dev, build, lint, test).
- **Documentation:** Updated `README.md` and `docs/RUNBOOK.md` to reflect the new monorepo structure and module-specific Maven commands (e.g., `-pl backend`).

### Changed
- **Infrastructure (POV-141):** Migrated from Jib-based containerization to a manual Docker build process, enabling unified static serving of the UI via Quarkus (`META-INF/resources`).
- **Frontend (Auth):** Optimized OIDC login flow to initiate authentication directly via `/auth/login`, eliminating an intermediate landing page step for the Single Page Application.
- **Infrastructure (Vite):** Refined `VITE_ALLOWED_HOSTS` parsing in `vite.config.ts` to robustly handle empty or malformed environment variables.
- **Backend (Auth):** Improved `OriginValidator` to use `java.net.URI` for canonical normalization, correctly handling default ports (80/443) and malformed origins.
- **Backend (Auth):** Refactored `LoginResource` to use constructor injection and constants for path and parameter management.
- **Infrastructure (POV-138):** Stabilized the frontend stack by pinning **Vite 6** and **Tailwind 4** (stable) to resolve peer dependency conflicts.
- **Backend:** Switched to `${project.root.basedir}` (using `maven.multiModuleProjectDirectory`) for stable cross-module resource loading, independent of directory depth.
- **Backend:** Updated `backend/pom.xml` to correctly locate global resources like `CHANGELOG.md` in the parent directory during the build process.
- **Backend:** Centralized all `<properties>`, `<dependencyManagement>`, and `<pluginManagement>` entries (including Quarkus and Maven plugins) in the root `pom.xml`.
- **Backend:** Switched `backend/pom.xml` to `<packaging>quarkus</packaging>` to leverage standard lifecycle bindings and eliminate redundant plugin executions.
- **Backend:** Unified plugin and dependency versions using new properties in the root POM.
- **Backend:** Simplified `backend/pom.xml` by removing redundant versions and execution blocks inherited from the root POM.

### Fixed
- **Backend (Auth):** Resolved an infinite redirect loop when accessing the backend root path (`/`) anonymously.
- **Backend (Auth):** Ensured the session cookie is explicitly cleared and cache-prevention headers are present during logout to prevent "sticky" sessions.
- **Backend (POV-141):** Corrected root access (`/`) behavior to serve the SPA instead of the legacy admin UI.
- **Backend (POV-141):** Implemented an SPA catch-all route (`SpaRoutingHandler`) to support client-side navigation (e.g., `/dashboard`) on page refresh in production.

## [v0.11.0] - 2026-03-12

### Summary

Version 0.11.0 introduces significant enhancements to real-time observability and security. This release features a new SSE-based event stream for live job updates, comprehensive API request auditing, and a centralized permission-based access control system. Operational improvements include JPQL projection optimizations, consolidated health metrics, and standardized error handling across the entire API layer. The integration of static analysis and vulnerability scanning further hardens the application's security posture.

### Added
- **API:** Implemented a Real-Time Server-Sent Events (SSE) stream endpoint (`GET /api/v1/events/stream`) providing live updates for `JOB_STATUS_CHANGED` and periodic system `PULSE` events.
- **API:** Introduced `EventBroadcaster`, a reactive service using Mutiny `BroadcastProcessor` to manage multi-subscriber SSE broadcasting with automatic pulse heartbeats.
- **API:** Integrated CDI event decoupling in `BackgroundJobService` to trigger real-time status broadcasts without direct coupling to the SSE infrastructure.
- **Security:** Refactored all `@RolesAllowed` annotations and programmatic security checks to use centralized constants in `Permission.java`, eliminating magic strings like `"user"` and `"admin"` across the entire API layer.
- **API:** Implemented `CorrelationIdFilter`, a global JAX-RS filter that reads or auto-generates an `X-Correlation-ID` UUID for every request, injects it into the MDC for structured log correlation, and echoes it back in the response header.
- **API:** Implemented `AuditLogFilter`, a global JAX-RS `ContainerResponseFilter` that persists one `AuditLog` record per `/api/v1/` request (HTTP method, path, response status, duration, authenticated user, correlation ID) in an isolated transaction. Audit failures are silently logged and never surface as API errors.
- **API:** Exposed `GET /api/v1/admin/audit-log` — a paginated, admin-only endpoint returning API request audit log entries sorted by most recent first, secured with the new `ADMIN_AUDIT_LOG_VIEW` permission.
- **API:** Exposed `GET /api/v1/admin/metrics` — an admin-only endpoint returning live aggregate system health counts (total/running/failed jobs, topics, content items, idempotency cache entries), secured with `ADMIN_DASHBOARD_VIEW`.
- **Database:** Added `V19__CreateAuditLogTable.sql` Flyway migration creating the `audit_log` table with a soft FK to `users (ON DELETE SET NULL)` and indexes on `created_at` and `user_id`.
- **Security:** Added `ADMIN_AUDIT_LOG_VIEW` granular permission to `Permission` enum (automatically granted to `admin` role via `EnumSet.allOf`).
- **Observability:** MDC `correlationId` is now explicitly propagated to background worker threads spawned by `Infrastructure.getDefaultExecutor()` in `BackgroundJobService`, ensuring all background job logs include the originating request's correlation ID.
- **UI:** Updated `admin.js` to correctly handle and display messages from the standardized `{ data, error }` response envelopes (standardized error handling and dashboard observability).
- **API:** Implemented JAX-RS `IdempotencyFilter` utilizing an `@Idempotent` name binding to safely cache and replay async `POST /commands/*` responses based on the `Idempotency-Key` header without redundant processing.
- **API:** Implemented global ExceptionMappers to translate all errors (`ConstraintViolationException`, `SecurityException`, `WebApplicationException`, and uncaught system errors) into the standard `ApiEnvelopeDTO` using the RFC 7807 problem details structure.
- **API:** Introduced `MessageDTO` for standardized simple message responses.
- **API:** Configured `quarkus-smallrye-openapi` to automatically generate API specifications and expose Swagger UI for local development.
- **API:** Added comprehensive OpenAPI annotations to all JAX-RS resource classes, including specific response envelope schemas (`ApiEnvelopeDTO`) and status codes.
- **API:** Implemented Backend-For-Frontend (BFF) Dashboard endpoint (`GET /api/v1/dashboard`) aggregating user-specific active topics and weekly briefing generation statistics, securely preventing frontend N+1 queries.
- **API:** Promoted `ContentItem` to a first-class resource with standard `GET` and explicit action command endpoints.
- **API:** Added user-facing job endpoints for monitoring async operations (`GET /api/v1/jobs/{id}`).
- **API:** Implemented Admin Command Center APIs (`/api/v1/admin/jobs`) for asynchronous background job management, including triggering, paginated history, and job detail retrieval.
- **Security:** Integrated SpotBugs into the Maven `verify` phase to automate static application security testing (SAST) and enforce code quality standards.
- **Security:** Integrated Trivy into the GitHub Actions CI pipeline for automated dependency vulnerability scanning, explicitly blocking builds on HIGH or CRITICAL issues.
- **Security:** Explicitly disabled Swagger UI in the production profile for enhanced security.
- **Security:** Added critical lockout protection for administrator roles, preventing self-removal of the admin role and accidental removal of the last system administrator.
- **Security:** Enhanced BackgroundJob error handling to sanitize outgoing messages, hiding raw stack traces from end users.
- **API:** Implemented Core REST APIs for Topics (`/api/v1/topics`) and Content Sources (`/api/v1/sources`) adhering to the Backend-For-Frontend (BFF) pattern using DTOs and MapStruct `@Mapper` interfaces. Includes pagination support for collection endpoints.
- **Security:** Implemented Permission-Based Access Control (PBAC) foundations. Mapped database roles (`admin`, `user`) to granular capabilities (e.g., `TOPIC_VIEW`, `ADMIN_SYSTEM_TRIGGER`) via `RolePermissionsMap` and dynamically injected them into the `QuarkusSecurityIdentity`.
- **API:** Implemented a global JAX-RS `ContainerResponseFilter` (`GlobalResponseEnvelopeFilter`) to automatically wrap all successful JSON responses in a standard `{ data, meta, error }` envelope, improving consistency and reducing boilerplate.
- **Security:** Implemented Optimistic Locking using the `@Version` field on all auditable entities. The API now requires valid quotes `If-Match` headers for all `PATCH`, `PUT`, and `DELETE` state mutations, returning `428 Precondition Required` or `409 Conflict` appropriately.
- **Background Jobs:** Introduced a persistent job execution framework with `BackgroundJob` entity, `BackgroundJobService`, and async orchestration integration using `Infrastructure.getDefaultExecutor()`.
- **API:** Standardized error response structure using `ApiEnvelopeDTO.error()` across all exception mappers, including `BadRequestExceptionMapper` and `OptimisticLockExceptionMapper`.
- **API:** Refactored `OrchestratorResource` to utilize standard JAX-RS exception mappers for error responses, improving internal error sanitization.
- **API:** Centralized `pageSize` clamping logic in `AdminApiResource` for consistency across paginated resources.
- **API:** Implemented systemic sorting and filtering across all list endpoints (e.g., `Topic`, `Briefing`, `User`) using generic `FilterBuilder` and `QueryParser` to safely parse `?sort=` fields without HQL injection vulnerabilities.

### Refactored
- **API:** Optimized `AdminApiResource.getAuditLog` using JPQL Constructor Projection (`SELECT NEW ...`) to eliminate full entity hydration and improve database-level projection efficiency.
- **API:** Consolidated `AdminApiResource.getMetrics` endpoint logic to use a single native query with sub-selects, reducing database roundtrips from 7 to 1.
- **API:** Cleaned up unused entity imports in `AdminApiResource` after transitioning to native and specialized JPQL queries.

### Changed
- **API:** Replaced ad-hoc `BadRequestExceptionMapper` and manual error responses with a unified, centralized exception handling architecture to ensure complete API response envelope coverage and to properly sanitize internal error messages (500 Internal Server Error) from reaching the client.
- **API:** Refactored Topics and ContentSources to use `PATCH` instead of `PUT` for updates, aligning with the core REST API contract.
- **API:** Refactored endpoint paths for actionable commands to follow CQRS patterns (e.g., `POST /api/v1/topics/{id}/commands/generate-briefing`, `POST /api/v1/content-items/{id}/commands/retry`).
- **API:** Enhanced `ContentSourceResource` with duplicate validation during `PATCH` operations and extracted repeated validation logic in `ContentItemApiResource` into shared helpers.
- **API:** Refactored all REST API resources (`Topic`, `ContentSource`, `User`, `Role`, `Job`, `Admin`, `Dashboard`, `Briefing`, `SourceItem`, `ContentItem`) to return raw DTOs or `PaginatedResponse` objects, delegating envelope wrapping to the centralized filter.
- **API:** Refactored `BaseApiResource` and all derived resource classes to use **Constructor Injection** for `UserContextService` and other dependencies, aligning with architectural best practices and improving testability.
- **API:** Optimized `DashboardApiResource` by inlining query orchestration logic and fixing type safety issues in the reactive pipeline recovery handlers.
- **Security:** Enforced instance-level ownership authorization across Job and ContentItem APIs to ensure users only access their own resources.
- **Testing:** Implemented the Test Data Builder pattern for core entities (`User`, `Topic`, `ContentSource`) to streamline test data generation and resolve testing brittleness. Refactored the entire test suite to use these builders.
- **API:** Refactored `ContentSourceResource` and Admin UI to utilize the new `/api/v1/sources` standard path and DTO payloads.
- **Database Schema:** Added `last_run_date` to the `topics` table via Flyway migration (`V14__add_last_run_date_to_topics.sql`).

### Documentation
- **Frontend Specification:** Finalized UI/UX requirements for the v0.11.0 API-first redesign (`docs/analysis/frontend-specification.md`).
- **API Contract:** Finalized the Backend-For-Frontend REST API contract, standardizing pagination, list responses, enum values, and strict HTTP code usage (`docs/analysis/api-design.md`).

## [v0.10.0] - 2026-02-20

### Summary

Version 0.10.0 transforms the application into a secure, multi-tenant platform. This major release integrates Google OIDC for seamless authentication, ensures strict data isolation so users only see their own topics and content sources, and provides a polished developer experience with OpenAPI documentation and standardized project structures. Additionally, structural enhancements like a minimum duration filter for videos and robust auditing capabilities have been introduced.

### Added
- **Authentication:** Integrated Google OIDC (`quarkus-oidc`) for secure user authentication and identity linking, including a programmatic local-only logout flow that preserves the global Google session.
- **Security (Multi-tenancy):** Implemented multi-tenancy for topics. Users can now only view and manage their own topics and associations with content sources. Endpoints enforce ownership and return 404 for unauthorized access.
- **UI Enhancements:** Implemented `/ui/system/changelog` and `/ui/system/roadmap` pages to display project documentation rendered as HTML, with navigation links added to the home page.
- **API Documentation:** Integrated `quarkus-smallrye-openapi` to expose OpenAPI documentation and Swagger UI at `/q/swagger-ui`.
- **Content Filtering:** Implemented a configurable minimum duration filter for YouTube videos. Videos shorter than the threshold (default: 60 seconds) are marked with a new `SKIPPED_TOO_SHORT` status.
- **Admin UI:** Added a "Modified At" column to the Admin Dashboard tables.
- **Auditing:** Added `updated_at` tracking to all major entities (`ContentItem`, `Briefing`, `Summary`, `ContentSource`, `Topic`).

### Documentation
- **Agent Protocols:** Refactored `AGENT.md` into modular protocol files in `docs/protocols/`, optimized for token efficiency, and added a protocol for using the `context7` MCP server.
- **Project Structure:** Created `docs/overview/project-structure.md` to document the project's directory layout and architecture.
- **Testing Guide:** Refactored `docs/guides/testing.md` to clarify the usage of `@QuarkusTest` vs `@QuarkusIntegrationTest` and deprecated `docs/guides/mastering-testing.md`.
- **ADR:** Initialized `docs/adr/` with the first Architectural Decision Record (ADR-001) and added ADR-002 documenting the decision to use OIDC for authentication.

## [v0.9.0] - 2026-02-05

### Summary

Version 0.9.0 focuses on **Operational Refinement & Admin Tools**. This release stabilizes the application's core logic by introducing a Circuit Breaker pattern to increase resilience against external API failures (like GenAI 503s) and enhances the administrator's toolkit with features like Orchestration Preview, GenAI usage tracking, and history pagination. A comprehensive Methodology guide has also been established.

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

### Summary

Version 0.8.0 introduces **Standalone Application Deployment**. It provides the necessary configuration to build and run the Quarkus application as an immutable, OCI-compliant container image (via Jib) without relying on development mode. This release also significantly improves observability by implementing a structured Logfmt logging pattern for key processing steps.

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
