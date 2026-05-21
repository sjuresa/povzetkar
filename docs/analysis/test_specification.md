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
| **TC-GRX-006** | **P1** | - | [v0.17] | Session Expiry Redirect | **Playwright** | 401 response redirects user to landing page (`/`) with `notice=session_expired` toast; `returnTo` path preserved in `sessionStorage`; no ErrorPage shown. |

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
| **TC-TS-002** | **P1** | UC-1 | [v0.13] | Source Attachment | Manual/PW | Sources are searchable and correctly linked to topics. |
| **TC-TS-003** | **P1** | UC-7 | [v0.15] | YouTube Real-time Validation | **Playwright** | Debounced input shows valid/invalid status before save. |
| **TC-TS-004** | **P2** | UC-1 | [v0.14] | Search-Based Source Addition | Manual | Adding sources is done via search popover, not a bulk list. |
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
| **TC-MON-001** | **P2** | - | [v0.15] | Prometheus Basic Metrics | Manual | `/q/metrics` reachable and shows JVM/Micrometer stats. |
| **TC-MON-002** | **P2** | - | [v0.15] | Business Metrics Pulse | Manual | Custom counters (Briefings generated) increment on Grafana. |
| **TC-MON-003** | **P2** | - | [v0.15] | Retention Purge Verification | Script | Simulation script proves that 90-day old logs are purged. |
| **TC-MON-004** | **P1** | - | [v0.12] | SSE Real-time Toast Sync | **Playwright** | Background job completion triggers a UI toast notification. |

## 5. Domain: Security & Identity (SEC)

| ID | Priority | Related UC | Version | Name | Automation | Expected Result |
|:---|:---|:---|:---|:---|:---|:---|
| **TC-SEC-001** | **P0** | - | [v0.13] | Profile Visibility | Manual/PW | `/me` data matches the currently authenticated user. |
| **TC-SEC-002** | **P0** | - | [v0.11] | Role Protection | **Playwright** | Admin sections (Sources) are blocked for generic users. |
| **TC-SEC-003** | **P0** | - | [v0.12] | Auth Guard Redirect | **Playwright** | Unauthenticated users visiting `/dashboard` are redirected to `/`. |
| **TC-SEC-004** | **P0** | UC-12 | [v0.17] | Pending Approval Gating | **Playwright** | New user with `PENDING_APPROVAL` is blocked from protected endpoints; sees generic status banner. |
| **TC-SEC-005** | **P0** | UC-12 | [v0.17] | Approval State Transition | Integration | Admin can transition `PENDING_APPROVAL → ENABLED`/`DISABLED`; audit row written. Last-admin guard prevents self-demotion. |

## 6. Domain: General UX & Brand (UI)

| ID | Priority | Related UC | Version | Name | Automation | Expected Result |
|:---|:---|:---|:---|:---|:---|:---|
| **TC-UI-001** | **P2** | - | [v0.14] | Brand Visibility | **Playwright** | Logo/Brand name "Povzetkar" prominently visible on landing. |
| **TC-UI-002** | **P2** | - | [v0.14] | Site Title | **Playwright** | Browser tab title contains the word "Povzetkar". |
| **TC-UI-003** | **P1** | - | [v0.12] | Responsive SideNav | Manual | Sidebar correctly collapses on smaller screens without breaking layout. |
| **TC-UI-004** | **P1** | UC-9 | [v0.16] | Dashboard Onboarding | **Playwright** | New users see the 5-step checklist; Progress updates on action. |

## 7. Domain: Admin & System (ADM)

| ID | Priority | Related UC | Version | Name | Automation | Expected Result |
|:---|:---|:---|:---|:---|:---|:---|
| **TC-ADM-001** | **P2** | - | [v0.12] | Audit Log Search & Filter | Manual | Paging and Keyword search works for tech audit logs. |
| **TC-ADM-002** | **P2** | UC-3 | [v0.14] | Status Override (Admin Only) | Manual | Admin can force retry of FAILED items via status dropdown. |
| **TC-ADM-003** | **P0** | UC-11 | [v0.17] | Admin User Management | **Playwright** | Admin can edit roles and disable/enable accounts; UI filter by status works. |
| **TC-ADM-004** | **P1** | UC-13 | [v0.17] | Background Job Restart | **Playwright** | Restart spawns new job for `FAILED` job; audit trail records acting admin. Cancel is deferred to v0.18.0. |

## 8. Domain: Core Ingestion (ING)

| ID | Priority | Related UC | Version | Name | Automation | Expected Result |
|:---|:---|:---|:---|:---|:---|:---|
| **TC-ING-001** | **P0** | UC-3 | [v0.6.2] | Autonomous Self-Healing | Integration | Items in `TRANSIENT_ERROR` are reset to `PENDING` automatically by the orchestrator. |
| **TC-ING-002** | **P0** | UC-3 | [v0.6.0] | Resilient Intelligence Fallback | Integration | System falls back to URL summarization if transcript fetch fails (prevents "black-hole" content). |
| **TC-ING-003** | **P0** | UC-14 | [v0.17] | Transcript Provider Fallback Chain | Integration | When primary transcript provider fails (timeout/quota/unavailable), orchestrator falls through to the next provider in the chain; metrics emitted per provider. |

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
| **PW-014** | Job Cancel/Restart | TC-ADM-004 | **Planned (v0.17.0)** |
| **PW-015** | Session Expiry Redirect | TC-GRX-006 | **Planned (v0.17.0)** |

---

## 10. Appendix: Decommissioned Scenarios

*No scenarios deprecated yet.*

---

## Revision History
| Version | Date       | Author       | Changes                                                                 |
|:--------|:-----------|:-------------|:------------------------------------------------------------------------|
| 2.7     | 2026-05-20 | Povzetkar AI | Added v0.17.0 test cases: TC-GRX-006, TC-LIB-006, TC-SEC-004/005, TC-ADM-003/004, TC-ING-003. Added PW-012…PW-015 to automation backlog. |
| 2.6     | April 20, 2026 | Antigravity AI | Integrated PW-010 and PW-011 for comprehensive backlog traceability. |
| 2.5     | April 20, 2026 | Antigravity AI | Synchronized Automation Status with backlog. Added PW-008 and PW-009. Corrected P0 coverage gaps. |
| 2.4     | April 13, 2026 | Antigravity AI | Concluded v0.16.0 Audit. Verified implementation of PW-005/PW-006. Deferred remaining v0.16.0 scenarios to v0.17.0. |
| 2.3     | April 01, 2026 | Povzetkar AI | Added TC-GRX-005, TC-EXP-004, TC-UI-004 for v0.16.0 UX Focus. Resolved legacy skips for TC-GRX-002 and TC-SEC-002 by promoting to **Playwright** automation. |
| 2.2     | March 30, 2026 | Povzetkar AI | Mandated Priority column (P0-P2) for automated auditing (POV-167). |
| 2.1     | March 30, 2026 | Povzetkar AI | Added Domain 8: Core Ingestion (ING) to bridge legacy core functionality gaps from v0.1-v0.11. |
| 2.0     | March 30, 2026 | Povzetkar AI | Centralized Master Spec. Introduced Domain 0 (GRX), 3-digit indexing, and requirements traceability (v0.12-v0.15). |
| 1.0     | 2026-03-28 | Povzetkar AI | Initialized Master Test Specification. Migrated legacy cases. |
