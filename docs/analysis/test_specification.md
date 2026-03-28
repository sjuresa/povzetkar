# Master Test Specification - Project Povzetkar

**Status:** Living Document
**Description:** This document is the Single Source of Truth for all functional and non-functional test cases. It acts as the permanent regression suite. Every new version must append its new test cases here.

---

## 1. Domain: Briefing & Intelligence (LIB)

| ID | Related Use Case | Name | Methodology | Expected Result |
|:---|:---|:---|:---|:---|
| **TC-LIB-01** | UC-4 | View Briefing Content | Manual/PW | Correct Markdown rendering with source links. |
| **TC-LIB-02** | UC-5 | Archive Search (Keyword) | Manual/PW | Search returns only items containing the keyword. |
| **TC-LIB-03** | UC-5 | Date Range Filter | Manual | Archive displays only briefings within the selected range. |
| **TC-LIB-04** | UC-5 | Pagination Check | Manual | Table correctly navigates between pages of historical briefings. |

## 2. Domain: Topic & Source Management (TS)

| ID | Related Use Case | Name | Methodology | Expected Result |
|:---|:---|:---|:---|:---|
| **TC-TS-01** | UC-1 | Topic Creation | Manual/PW | Form validation works and topic is persisted properly. |
| **TC-TS-02** | UC-1 | Source Attachment | Manual/PW | Sources are searchable and correctly linked to topics. |
| **TC-TS-03** | UC-7 | YouTube Real-time Validation | **Playwright** | Debounced input shows valid/invalid status before save. |
| **TC-TS-04** | UC-3 | Status Override | Manual | Admin can force retry of FAILED items. |

## 3. Domain: Export & Portability (EXP)

| ID | Related Use Case | Name | Methodology | Expected Result |
|:---|:---|:---|:---|:---|
| **TC-EXP-01** | UC-6 | Markdown Export | Manual | Downloaded file has correct headers and formatting. |
| **TC-EXP-02** | UC-6 | HTML Export | Manual | Downloaded file is a valid stand-alone HTML document. |
| **TC-EXP-03** | UC-6 | Shareable Link | Manual | Direct URL correctly resolves to the briefing view. |

## 4. Domain: Observability & Monitoring (MON)

| ID | Related Use Case | Name | Methodology | Expected Result |
|:---|:---|:---|:---|:---|
| **TC-MON-01** | - | Prometheus Basic Metrics | Manual | `/q/metrics` is reachable and shows JVM/Micrometer stats. |
| **TC-MON-02** | - | Business Metrics Pulse | Manual | Custom counters (Briefings generated) increment on Grafana. |
| **TC-MON-03** | - | Retention Purge Verification | Script/Manual | Simulation script proves that 90-day old logs are purged. |

## 5. Domain: Security & Identity (SEC)

| ID | Related Use Case | Name | Methodology | Expected Result |
|:---|:---|:---|:---|:---|
| **TC-SEC-01** | - | Profile Visibility | Manual/PW | `/me` data matches the currently authenticated user. |
| **TC-SEC-02** | - | Role Protection | Manual | Admin sections (Sources) are blocked for generic users. |
| **TC-SEC-03** | - | Auth Guard Redirect | **Playwright** | Unauthenticated users visiting `/dashboard` are redirected to `/`. |

## 6. Domain: General UX & Brand (UI)

| ID | Related Use Case | Name | Methodology | Expected Result |
|:---|:---|:---|:---|:---|
| **TC-UI-01** | - | Brand Visibility | **Playwright** | Logo/Brand name "Povzetkar" is prominently visible on landing. |
| **TC-UI-02** | - | Site Title | **Playwright** | Browser tab title contains the word "Povzetkar". |

---

## 7. Automation Status (Playwright Repository)

| Scenario ID | Name | TC Reference | Automation Status |
|:---|:---|:---|:---|
| **PW-01** | Auth Guard Redirect | TC-SEC-03 | **Implemented** (v0.14) |
| **PW-02** | Brand & Title Smoke | TC-UI-01, TC-UI-02 | **Implemented** (v0.14) |
| **PW-03** | **YT Source Validation** | TC-TS-03 | **Planned (v0.15.0)** |
| **PW-04** | Briefing Archive Search | TC-LIB-02 | **Planned (v0.15.0)** |

---

## Revision History
| Version | Date       | Author       | Changes                                                                 |
|:--------|:-----------|:-------------|:------------------------------------------------------------------------|
| 1.0     | 2026-03-28 | Povzetkar AI | Initialized Master Test Specification. Migrated legacy cases and added v0.15.0 scope. |
