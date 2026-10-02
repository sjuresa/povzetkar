# Master Test Specification - Project Povzetkar

**Status:** Living Document
**Description:** This document is the Single Source of Truth for all functional and non-functional test cases. It acts as the permanent regression suite. Every new version must append its new test cases here.

---

## 0. Domain: Global Resilience & UX (GRX)

| ID | Priority | Related UC | Version | Name | Automation | Expected Result |
|:---|:---|:---|:---|:---|:---|:---|
| **TC-GRX-001** | **P0** | - | [v0.13] | Skeleton Loading Pattern | **Playwright** | Slow APIs trigger shimmer skeletons instead of blank white screens. |
| **TC-GRX-002** | **P0** | - | [v0.13] | Global Error Catching | **Playwright** | Toasts appear for transient errors; Error boundary shows for fatal crashes. |
| **TC-GRX-003** | **P0** | - | [v0.13] | Theme Switching | **Playwright** | Light/Dark theme persists across page reloads and browser sessions. |
| **TC-GRX-004** | **P0** | - | [v0.12] | OIDC Handshake & Persistence | **Playwright** | Login redirect flow works and session remains active after refresh. |
| **TC-GRX-005** | **P1** | UC-10 | [v0.16] | Toast Actionability | Manual/PW | Toast contains a clickable button and plays sound on trigger. |
| **TC-GRX-006** | **P1** | - | [v0.17] | Session Expiry Redirect | **Playwright** | A 401 while signed in clears the session and redirects to `/auth/login?redirect=<path+search+hash>` (`api/client.ts`); after re-authentication `LoginResource` returns the user to that path (safe destinations only); a 401 for a visitor who was never signed in does not redirect (`e2e/auth.spec.ts` "unauthenticated /me 401 does not trigger auto-login redirect"); no ErrorPage shown. *(Corrected by POV-220: the landing page never had a `session_expired` toast or `sessionStorage` return path.)* |

## 1. Domain: Briefing & Intelligence (LIB)

| ID | Priority | Related UC | Version | Name | Automation | Expected Result |
|:---|:---|:---|:---|:---|:---|:---|
| **TC-LIB-001** | **P0** | UC-4 | [v0.15] | View Briefing Content | Manual/PW | Correct Markdown rendering with source links. |
| **TC-LIB-002** | **P1** | UC-5 | [v0.15] | Archive Search (Keyword) | Manual/PW | Search returns only items containing the keyword. |
| **TC-LIB-003** | **P1** | UC-5 | [v0.15] | Date Range Filter | Manual | Archive displays only briefings within the selected range. |
| **TC-LIB-004** | **P2** | UC-5 | [v0.15] | Pagination Check | Manual | Table correctly navigates between pages of historical briefings. |
| **TC-LIB-005** | **P1** | UC-2 | [v0.14] | Trigger Dialog Config | Manual/PW | Dialog allows setting Date Range, Briefing Toggle, and Preview Mode. |

## 2. Domain: Topic & Source Management (TS)

| ID | Priority | Related UC | Version | Name | Automation | Expected Result |
|:---|:---|:---|:---|:---|:---|:---|
| **TC-TS-001** | **P1** | UC-1 | [v0.13] | Topic Creation | Manual/PW | Form validation works and topic is persisted properly. |
| **TC-TS-002** | **P1** | UC-1 | [v0.13] | Source Attachment | Integration (`TopicApiResourceTest`) + Unit (`TopicForm.test.tsx`) | Sources are searchable and correctly linked to topics; only sources visible to the user can be attached by ID (foreign ID → 404, topic unchanged), Quick Add entries are found or created on save, once per channel (POV-219). |
| **TC-TS-003** | **P1** | UC-7 | [v0.15] | YouTube Real-time Validation | **Playwright** | Debounced input shows valid/invalid status before save. |
| **TC-TS-004** | **P2** | UC-1 | [v0.14] | Search-Based Source Addition | Unit (`SourceSearchModal.test.tsx`) | Adding sources is done via search popover, not a bulk list; the popover opens at the search list after every close (X, selection, Quick Add) (POV-219). |
| **TC-TS-005** | **P2** | UC-1 | [v0.14] | Advanced Prompt Settings | Manual | Prompt template selection is hidden under "Advanced" toggle. |

## 3. Domain: Export & Portability (EXP)

| ID | Priority | Related UC | Version | Name | Automation | Expected Result |
|:---|:---|:---|:---|:---|:---|:---|
| **TC-EXP-001** | **P1** | UC-6 | [v0.15] | Markdown Export | Manual | Downloaded file has correct headers and formatting. |
| **TC-EXP-002** | **P1** | UC-6 | [v0.15] | HTML Export | Manual | Downloaded file is a valid stand-alone HTML document. |
| **TC-EXP-003** | **P1** | UC-6 | [v0.15] | Shareable Link | Manual | Direct URL correctly resolves to the briefing view. |
| **TC-EXP-004** | **P1** | UC-8 | [v0.16] | Individual Summary Read | Manual | Public access to summary UUID is successful without login. |
| **TC-LIB-006** | **P2** | UC-15 | [v0.17] | External Links Open in New Tab | Manual/Unit | Markdown external links contain `target="_blank"` and `rel="noopener noreferrer"`. |

## 4. Domain: Observability & Monitoring (MON)

| ID | Priority | Related UC | Version | Name | Automation | Expected Result |
|:---|:---|:---|:---|:---|:---|:---|
| **TC-MON-001** | **P2** | - | [v0.15] | Prometheus Basic Metrics | Manual | `/q/metrics` reachable on the management port (9000, Docker network) and shows JVM/Micrometer stats; returns 404 on the public port 8080 (automated guard: `ManagementInterfaceTest`, POV-207). |
| **TC-MON-002** | **P2** | - | [v0.15] | Business Metrics Pulse | Manual | Custom counters (Briefings generated) increment on Grafana. |
| **TC-MON-003** | **P2** | - | [v0.15] | Retention Purge Verification | Script | Simulation script proves that 90-day old logs are purged. |
| **TC-MON-004** | **P1** | - | [v0.12] | SSE Real-time Toast Sync | **Playwright** | Background job completion triggers a UI toast notification. |
| **TC-MON-005** | **P1** | - | [v0.18] | GenAI RPD Counting Accuracy | Integration (`VideoSummaryServiceTest`) | Every GenAI request — including failed and retried ones — counts against the daily RPD limit; the counter never undercounts (POV-195). |
| **TC-MON-006** | **P2** | - | [v0.20] | Access-Log Content | Integration (`AccessLogTest`, `AccessLogFileTest`) + Unit (`AccessLogPrincipalRouteTest`) | One line per request with Cloudflare client IP, country and ray ID and the signed-in user (Google subject or API-key e-mail, never the display name; safe characters only) before the request line, client-controlled fields (Host, Referer, X-Forwarded-For, User-Agent) after the status; rerouted SPA probes logged once with the original path; the `X-API-Key` value never logged; with the `%prod` handler settings the lines go to their own file only, not to the main log (POV-210, POV-214). |
| **TC-MON-007** | **P2** | - | [v0.20] | Bounded `uri` Metric Tag | Integration (`HttpMetricsTagsTest`) | On the real `MeterRegistry`, no `http.server.*` meter carries a requested path: SPA routes → `SPA`, files → `STATIC`, 404 → `NOT_FOUND`, untemplated 4xx (unauthenticated requests to non-existent `/api` and `/ui` paths) → `UNKNOWN`; raw non-canonical variants of an SPA route (`/sources/1/%2e%2e/2`, `/%73ources/3`, served as SPA 200) never appear as a value; existing endpoints keep their template (`/api/v1/briefings/{id}`) (POV-221). |

## 5. Domain: Security & Identity (SEC)

| ID | Priority | Related UC | Version | Name | Automation | Expected Result |
|:---|:---|:---|:---|:---|:---|:---|
| **TC-SEC-001** | **P0** | - | [v0.13] | Profile Visibility | Manual/PW | `/me` data matches the currently authenticated user. |
| **TC-SEC-002** | **P0** | - | [v0.11] | Role Protection | **Playwright** + Integration (`LegacyUiAccessTest`) | Admin sections are blocked for generic users: SPA `/admin/*` routes (PW-006) and, since POV-219, the legacy server-rendered UI `/ui/*`. The Sources page is not an admin section — it is user-scoped (TC-SEC-008). |
| **TC-SEC-003** | **P0** | - | [v0.12] | Auth Guard Redirect | **Playwright** | Unauthenticated users visiting `/dashboard` are redirected to `/`. |
| **TC-SEC-004** | **P0** | UC-12 | [v0.17] | Pending Approval Gating | **Playwright** | New user with `PENDING_APPROVAL` is blocked from protected endpoints; sees an informational status banner (no buttons) with a demo-exploration hint; the app re-checks the status automatically and reloads once on approval. |
| **TC-SEC-005** | **P0** | UC-12 | [v0.17] | Approval State Transition | Integration | Admin can transition `PENDING_APPROVAL → ENABLED`/`DISABLED`; audit row written. Last-admin guard prevents self-demotion. |
| **TC-SEC-006** | **P1** | - | [v0.18] | Demo Role Access Regression | Unit (`RolePermissionsMapTest`) | `demo` role includes `SOURCE_VIEW` (no 403 on Briefing Hub source filter / Sources page); Topics page mutation controls hidden in readonly mode (UI part Manual) (POV-192). |
| **TC-SEC-008** | **P0** | UC-1, UC-7, UC-8, UC-17 | [v0.20] | Per-User Source and Content Scoping | Integration (`ContentVisibilityQueriesTest`, `ContentSourceResourceTest`, `SourceItemApiResourceTest`, `ContentItemApiResourceTest`, `TopicApiResourceTest`, `UserContextServiceContentScopeTest`) | A user sees only sources in their own topics (disabled included) and only items of those sources or with a summary in their briefings; admin sees all, an API key only the mapped user's. Foreign/unknown source or item → 404 on list filters, detail, items, summary, transcript, share, unshare and generate-summary, with no side effect; `POST /sources` is admin-only (POV-219). |
| **TC-SEC-007** | **P0** | UC-17 | [v0.19] | Read-Only API-Key Access | Integration (`ApiKeyAuthenticationTest`) | Valid `X-API-Key` reads the mapped user's data (briefings, content items, summary, transcript, topics, sources, dashboard); wrong key → 401; any non-GET and admin paths → 403; header-less requests follow the unchanged OIDC flow (POV-205). |
| **TC-SEC-009** | **P1** | - | [v0.20] | HTTP Security Headers & CSP | Integration (`SecurityHeadersTest`, `CspInlineScriptHashTest`) + **Playwright** (project `csp`) | Every response of the public port carries HSTS, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy`, `Permissions-Policy` and exactly one enforced CSP: legacy policy on `/ui/*`, `/login`, `/logout`, strict policy elsewhere (incl. `/q/*` 404s; `/q` is exempt only in the dev profile). The strict `script-src` holds exactly the sha256 of each inline script of `frontend/index.html`; the e2e specs run against the built SPA under the strict policy without a CSP violation (POV-214, NFR SEC-5). |
| **TC-SEC-010** | **P1** | - | [v0.20] | Forwarded-Header Trust | Integration (`TrustedProxyTest`, `TrustedProxyAcceptedTest`) | `X-Forwarded-Proto/Host/For` from an untrusted peer leave scheme, host and remote address unchanged; from a trusted peer they are applied; the default trust list is loopback only (production widens it to `cloudflared`'s fixed address) (POV-214, NFR SEC-6). |
| **TC-SEC-011** | **P1** | - | [v0.20] | SPA Fallback Allow-List (Probe 404) | Integration (`SpaRoutingHandlerTest`) + Unit (`SpaRouteAllowListTest`) | Frontend routes (`/dashboard`, `/me`, `/topics`, `/briefings[/<id>]`, `/sources[/<id>]`, `/admin` and `/admin/{audit-log,content,jobs,users}`, `/s/<uuid>`; matched segment by segment and case-insensitively, e.g. `/Dashboard`, one trailing slash allowed) get the SPA shell with 200; any other path reaching the fallback — dot-segments (`/.git/config`, `/sources/.git`), files under SPA prefixes (`/admin/config.php`), unknown paths under a route prefix (`/admin/does-not-exist`, `/dashboard/actuator`, `/sources/7/extra`), `/actuator/env`, `/wp-admin`, `/secret`, `/dashboardx`, `/quiz` (not `/q`) — gets a 404 without the shell; `/`, static assets and `/api` unchanged. The allow-list equals the URL patterns of `frontend/src/router.tsx` (both directions), and routes are defined only there (POV-221). |
| **TC-SEC-012** | **P1** | - | [v0.20] | Unauthenticated XHR Gets 401 | Integration (`UnauthenticatedXhrTest`) | A request with `X-Requested-With: XMLHttpRequest` or `JavaScript` and no session gets `401` + `WWW-Authenticate: OIDC` and no `Location`, with or without an `Origin` header; a browser navigation still gets the 302 to Google. Backend precondition of TC-GRX-006 (POV-221). |
| **TC-SEC-013** | **P1** | - | [v0.20] | No CORS, Relative Auth Redirects | Integration (`NoCorsTest`, `LoginResourceRedirectTest`) | A preflight or cross-origin request (incl. the former dev origin `http://localhost:5173`) gets no `Access-Control-Allow-*` header; `/logout` and `/auth/login` redirect to relative paths regardless of `Origin`/`Referer`, and `/auth/login` keeps the open-redirect guard (`//evil.example`, absolute URLs → `/dashboard`) (POV-221). |

## 6. Domain: General UX & Brand (UI)

| ID | Priority | Related UC | Version | Name | Automation | Expected Result |
|:---|:---|:---|:---|:---|:---|:---|
| **TC-UI-001** | **P2** | - | [v0.14] | Brand Visibility | **Playwright** | Logo/Brand name "Povzetkar" prominently visible on landing. |
| **TC-UI-002** | **P2** | - | [v0.14] | Site Title | **Playwright** | Browser tab title contains the word "Povzetkar". |
| **TC-UI-003** | **P1** | - | [v0.12] | Responsive SideNav | Manual | Sidebar correctly collapses on smaller screens without breaking layout. |
| **TC-UI-004** | **P1** | UC-9 | [v0.16] | Dashboard Onboarding | **Playwright** | Approved users see the 5-step checklist; progress updates on action and is stored per user (not shared between accounts in one browser); pending users see no checklist. |
| **TC-UI-005** | **P2** | UC-18 | [v0.20] | Landing Page Content & Sign-in | Vitest (`LandingPage.test.tsx`) + **Playwright** (`smoke.spec.ts`) | Anonymous visitor can sign in from header, hero and closing call to action; signed-in user sees *Dashboard* links instead; the sample briefing is labelled as an example; three benefits; every *Coming soon* item is labelled *planned*; FAQ uses native disclosure; one `h1`; no status claims for unimplemented features; no links to the engineering changelog/roadmap; a failing system-metadata call leaves the page usable (no error page). SL copy uses the glossary term for a briefing (`i18n.test.ts`). |
| **TC-UI-006** | **P2** | UC-18 | [v0.20] | First-visit Language & Theme | Vitest + **Playwright** (`language.test.ts`, `themeStore.test.ts`, `smoke.spec.ts`) | Without a stored choice the app opens in the browser language (SL for `sl*`, else EN) and the system theme; a stored choice wins; the pre-paint script sets `<html class>`/`lang` before React loads. |
| **TC-UI-007** | **P2** | - | [v0.20] | Browser Caching Policy | Integration (`CachingHeadersTest`) | Content-hashed `/assets/*` are `public, immutable, max-age=31536000` when served (a 404 there gets no caching header); every other static file (SPA shell, locales, public files, legacy `/static`) is `no-cache` and revalidates with 304; `/api` and the legacy pages keep their own caching headers (POV-214). |

## 7. Domain: Admin & System (ADM)

| ID | Priority | Related UC | Version | Name | Automation | Expected Result |
|:---|:---|:---|:---|:---|:---|:---|
| **TC-ADM-001** | **P2** | - | [v0.12] | Audit Log Search & Filter | Manual | Paging and Keyword search works for tech audit logs. |
| **TC-ADM-002** | **P2** | UC-3 | [v0.14] | Status Override (Admin Only) | Manual | Admin can force retry of FAILED items via status dropdown. |
| **TC-ADM-003** | **P0** | UC-11 | [v0.17] | Admin User Management | **Playwright** | Admin can edit roles and disable/enable accounts; UI filter by status works. |
| **TC-ADM-004** | **P1** | UC-13 | [v0.17] | Background Job Restart | Manual/PW | Restart spawns new job for `FAILED` job; audit trail records acting admin. Cancel is a backlog item — see "Admin Jobs Cancel Action" in `docs/backlog/improvements.md`. |

## 8. Domain: Core Ingestion (ING)

| ID | Priority | Related UC | Version | Name | Automation | Expected Result |
|:---|:---|:---|:---|:---|:---|:---|
| **TC-ING-001** | **P0** | UC-3 | [v0.6.2] | Autonomous Self-Healing | Integration | Items in `TRANSIENT_ERROR` are reset to `PENDING` automatically by the orchestrator. |
| **TC-ING-002** | **P0** | UC-3 | [v0.6.0] | Resilient Intelligence Fallback | Integration | System falls back to URL summarization if transcript fetch fails (prevents "black-hole" content). |
| **TC-ING-003** | **P0** | UC-14 | [v0.17] | Transcript Provider Fallback Chain | Integration | When primary transcript provider fails (timeout/quota/unavailable), orchestrator falls through to the next provider in the chain; metrics emitted per provider. |
| **TC-ING-004** | **P1** | UC-14 | [v0.18] | Proxy Primary Transcript Path | Unit (`ProxyTranscriptProviderTest`) | `ProxyTranscriptProvider` is first in the chain; a 200 proxy response with segments is formatted via `TimeTextFormatter` and returned (POV-193/194). |
| **TC-ING-005** | **P1** | UC-14 | [v0.18] | Proxy Failure Falls Back to Legacy Chain | Unit (`ProxyTranscriptProviderTest`) | Non-200 proxy responses (404/502/401) and transport failures raise `ProviderUnavailableException`, triggering orchestrator fall-through to `youtube-legacy`/`supadata` (POV-194/196). |
| **TC-ING-006** | **P0** | UC-16 | [v0.18] | Scheduled Orchestration Trigger | Manual <!-- validate:skip until v0.21.0 — the scheduler test is POV-213, planned as the next v0.20 task; POV-213 removes this skip --> | Cron invocation runs on a worker thread without `BlockingOperationNotAllowedException`; fetches+summarizes yesterday and today, generates yesterday's briefings idempotently (POV-197). |
| **TC-ING-007** | **P1** | UC-14, UC-16 | [v0.18] | Transcript Persisted Before Summarization | Integration (`TranscriptPersistenceTest`) | A fetched transcript is persisted to the `ContentItem` before the Gemini call, so a summarization failure cannot lose the transcript (POV-198). |
| **TC-ING-008** | **P1** | UC-14 | [v0.20] | Serial Human-Like YouTube Pacing | Unit (`YouTubeRequestPacerTest`, `YoutubeHttpClientTest`, `YouTubeLegacyTranscriptProviderTest`) | youtube-legacy fetches never overlap app-wide; each waits a random pause within the configured range measured from the end of the previous fetch (none for the first fetch or after idle); the calls inside a fetch are spaced by the in-fetch range; a failing fetch still releases the slot; the manual "Fetch transcript" bypasses the pacer (POV-222). |
| **TC-ING-009** | **P1** | UC-14, UC-16 | [v0.20] | Bounded Transcript Retries per Video | Unit (`TranscriptRetryPolicyTest`, `YouTubeSummarizationStrategyTest`, `ContentItemServiceTest`) + Integration (`TranscriptRetryStatePersistenceTest`, `OrchestrationManagerTest`) | A failed automatic transcript fetch sets `RETRY_LATER` with the next due time per `transcript.retry.intervals`; a video is not selected before it is due; the 7th failed attempt ends it `FAILED`; the scheduled run carries due retries of earlier days over (yesterday's step); a late failure does not change a video an overlapping run completed; failed manual attempts (per-item retry, fetch transcript) change nothing; Gemini-caused `RETRY_LATER` items are unaffected (POV-222). |
| **TC-ING-010** | **P1** | UC-14 | [v0.20] | Any-Language Transcript Selection | Unit (`YouTubeLegacyTranscriptProviderTest`) | youtube-legacy prefers English manual, then English generated, then a manual and finally a generated transcript in any language; no transcript at all → provider unavailable (POV-222). |
| **TC-ING-011** | **P1** | UC-16 | [v0.20] | Time Budget per Orchestration Run | Unit (`JustInTimeSummarizationSchedulerTest`, `YouTubeSummarizationStrategyTest`, `TranscriptOrchestratorTest`, `ContentCreationServiceTest`) + `YouTubeRequestPacerTest` | Once the run's time budget is used up, items not started — or waiting for a YouTube fetch slot — are left unchanged (no retry counted) and counted as skipped; an item in progress finishes; a skipped fetch does not fall through to the next provider; per-item manual jobs have no budget (POV-222). |

---

## 9. Automation Status (Playwright Repository)

| Scenario ID | Name | TC Reference | Automation Status |
|:---|:---|:---|:---|
| **PW-001** | Auth Guard Redirect | TC-SEC-003, TC-GRX-004 | **Implemented** (v0.14) |
| **PW-002** | Brand & Title Smoke | TC-UI-001, TC-UI-002 | **Implemented** (v0.14) |
| **PW-003** | YT Source Validation | TC-TS-003 | **Planned (v0.17.0)** |
| **PW-004** | Briefing Archive Search | TC-LIB-002 | **Planned (v0.17.0)** |
| **PW-005** | Global Resilience Smoke | TC-GRX-001, TC-GRX-003, TC-GRX-002 | **Implemented** (v0.16) |
| **PW-006** | Role Protection | TC-SEC-002 | **Implemented** (v0.16) |
| **PW-007** | Dashboard Onboarding | TC-UI-004 | **Planned (v0.17.0)** |
| **PW-008** | Share Link Lifecycle | TC-EXP-003 | **Planned (v0.17.0)** |
| **PW-009** | Briefing Content View | TC-LIB-001 | **Planned (v0.17.0)** |
| **PW-010** | Summary Persistence Smoke | TC-EXP-004 | **Planned (v0.17.0)** |
| **PW-011** | Toast Interaction | TC-GRX-005 | **Planned (v0.17.0)** |
| **PW-012** | Admin User Management | TC-ADM-003 | **Planned (v0.17.0)** |
| **PW-013** | Pending Approval Gating | TC-SEC-004 | **Planned (v0.17.0)** |
| **PW-014** | Job Restart | TC-ADM-004 | **Planned** (carried forward; no E2E exists as of v0.19 — cancel scope moved to backlog) |
| **PW-015** | Session Expiry Redirect | TC-GRX-006 | **Planned (v0.17.0)** |

---

## 10. Appendix: Decommissioned Scenarios

*No scenarios deprecated yet.*

---

## Revision History
| Version | Date       | Author       | Changes                                                                 |
|:--------|:-----------|:-------------|:------------------------------------------------------------------------|
| 2.15    | 2026-10-02 | Povzetkar AI | POV-222: added TC-ING-008 (Serial Human-Like YouTube Pacing), TC-ING-009 (Bounded Transcript Retries per Video), TC-ING-010 (Any-Language Transcript Selection), TC-ING-011 (Time Budget per Orchestration Run). |
| 2.14    | 2026-10-01 | Povzetkar AI | POV-221: added TC-SEC-011 (SPA Fallback Allow-List), TC-SEC-012 (Unauthenticated XHR Gets 401), TC-SEC-013 (No CORS, Relative Auth Redirects), TC-MON-007 (Bounded `uri` Metric Tag). |
| 2.13    | 2026-09-29 | Povzetkar AI | POV-214: added TC-SEC-009 (HTTP Security Headers & CSP), TC-SEC-010 (Forwarded-Header Trust), TC-MON-006 (Access-Log Content, POV-210 backfill), TC-UI-007 (Browser Caching Policy). |
| 2.12    | 2026-09-28 | Povzetkar AI | POV-220: added TC-UI-005 (Landing Page Content & Sign-in) and TC-UI-006 (First-visit Language & Theme) for UC-18; TC-GRX-006 expected result corrected to the implemented 401 flow. |
| 2.11    | 2026-09-28 | Povzetkar AI | POV-219: added TC-SEC-008 (Per-User Source and Content Scoping, P0, automated); TC-SEC-002 restated (admin sections incl. legacy `/ui/*`, Sources page is user-scoped) with `LegacyUiAccessTest`; TC-TS-002/004 automated and extended (visible sources only, Quick Add on save, popover reset). |
| 2.10    | 2026-09-27 | Povzetkar AI | POV-212 (v0.20.0 initialisation): TC-ING-006 gets a time-bound `validate:skip until v0.21.0` — the P0 automation guardrail turns it CRITICAL at v0.20 and would block CI from the version bump on; the automation is POV-213, which removes the skip (PO decision). |
| 2.9     | 2026-07-08 | Povzetkar AI | POV-205: added TC-SEC-007 (Read-Only API-Key Access, P0, automated via `ApiKeyAuthenticationTest`). |
| 2.8     | 2026-07-06 | Povzetkar AI | POV-203 v0.18 drift backfill: added TC-ING-004/005/006/007, TC-MON-005, TC-SEC-006 (TC-ING-006 is P0 Manual without skip — enforces the scheduler-test carry-forward at v0.20). Honesty fixes: TC-ADM-004 automation → Manual/PW, stale cancel note → backlog reference; PW-014 restated. |
| 2.7     | 2026-05-20 | Povzetkar AI | Added v0.17.0 test cases: TC-GRX-006, TC-LIB-006, TC-SEC-004/005, TC-ADM-003/004, TC-ING-003. Added PW-012…PW-015 to automation backlog. |
| 2.6     | April 20, 2026 | Antigravity AI | Integrated PW-010 and PW-011 for comprehensive backlog traceability. |
| 2.5     | April 20, 2026 | Antigravity AI | Synchronized Automation Status with backlog. Added PW-008 and PW-009. Corrected P0 coverage gaps. |
| 2.4     | April 13, 2026 | Antigravity AI | Concluded v0.16.0 Audit. Verified implementation of PW-005/PW-006. Deferred remaining v0.16.0 scenarios to v0.17.0. |
| 2.3     | April 01, 2026 | Povzetkar AI | Added TC-GRX-005, TC-EXP-004, TC-UI-004 for v0.16.0 UX Focus. Resolved legacy skips for TC-GRX-002 and TC-SEC-002 by promoting to **Playwright** automation. |
| 2.2     | March 30, 2026 | Povzetkar AI | Mandated Priority column (P0-P2) for automated auditing (POV-167). |
| 2.1     | March 30, 2026 | Povzetkar AI | Added Domain 8: Core Ingestion (ING) to bridge legacy core functionality gaps from v0.1-v0.11. |
| 2.0     | March 30, 2026 | Povzetkar AI | Centralized Master Spec. Introduced Domain 0 (GRX), 3-digit indexing, and requirements traceability (v0.12-v0.15). |
| 1.0     | 2026-03-28 | Povzetkar AI | Initialized Master Test Specification. Migrated legacy cases. |
