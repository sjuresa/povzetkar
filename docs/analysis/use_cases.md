# Use Cases - Project Povzetkar

## 1. Introduction
This document defines the core use cases for the Povzetkar system, mapping user stories to specific interactions. It serves as the basis for test cases and implementation verification.

## 2. Actor Definitions
- **User/Consumer**: Views generated briefings and manages personal preferences.
- **Administrator**: Manages topics, content sources, users, and triggers orchestration.

## 3. Use Case List

### UC-1: Manage Topic Sources
**Actor**: Administrator
**Related Stories**: US-ING-001, US-UI-002
**Context**: [topic-management.md](../features/topic-management.md)
**Preconditions**: Administrator is logged in.
**Main Flow**:
1. Administrator navigates to Topic Management and selects a Topic for Editing.
2. System displays *only* the sources currently associated with this topic.
3. Administrator removes a source from the topic.
4. Administrator clicks "Add Source".
5. System displays a search-enabled popover of the global Source Library.
6. Administrator filters/searches and selects one or more new sources.
7. Administrator saves the Topic.
**Postconditions**: Topic is updated with the new source configuration.

### UC-2: Trigger Manual Orchestration (Configurable)
**Actor**: Administrator
**Related Stories**: US-UI-004, US-UI-005
**Context**: [frontend-specification.md](frontend-specification.md)
**Preconditions**: Topic has active sources.
**Main Flow**:
1. Administrator navigates to Dashboard and clicks the "Trigger Run" (Play) icon on a Topic.
2. System displays a Configuration Dialog.
3. Administrator selects a Date Range (e.g., last 3 days).
4. Administrator toggles "Generate Briefing" and "Preview Mode".
5. Administrator confirms the trigger.
6. System starts a background job with the selected parameters.
**Postconditions**: Background job is queued and visible in the SSE/Job status.

### UC-3: Review and Override Content Status (Admin Only)
**Actor**: Administrator
**Related Stories**: US-UI-006, US-ING-006, US-ING-007
**Context**: [frontend-specification.md](frontend-specification.md), [ProcessingStatus.java](../../backend/src/main/java/com/povzetkar/quarkus/model/ProcessingStatus.java)
**Preconditions**: Content items have been fetched.
**Main Flow**:
1. Administrator navigates to Source Detail view or a global "Processing History".
2. Administrator identifies a `FAILED` or `SKIPPED_TOO_SHORT` item.
3. Administrator selects "Override Status".
4. Administrator chooses a new status (e.g., `RETRY_LATER` or `PENDING`).
5. System validates the transition and updates the item.
**Postconditions**: The item is requeued for processing in the next run.

### UC-4: View Briefings
**Actor**: User
**Related Stories**: US-REP-003, US-UI-001
**Context**: [frontend-specification.md](frontend-specification.md)
**Preconditions**: Briefings have been generated for at least one topic.
**Main Flow**:
1. User navigates to Briefings Library.
2. User selects a Briefing from the list.
3. System renders the Markdown briefing text with source references.
4. User clicks a source reference to view the original content (e.g., YouTube URL).
**Postconditions**: User is informed of the gathered intelligence.

### UC-5: Browse Briefing Archive
**Actor**: User
**Related Stories**: US-REP-003, US-UI-001
**Context**: [frontend-specification.md](frontend-specification.md)
**Preconditions**: Multiple briefings exist in the system.
**Main Flow**:
1. User navigates to Briefings Library.
2. System displays a table/list with search and filter controls.
3. User enters a keyword or selects a date-range filter.
4. System updates the list to show matching briefings.
5. User clicks a Briefing to view it.
**Postconditions**: User successfully retrieves historical intelligence.

### UC-6: Export Briefing
**Actor**: User
**Related Stories**: US-REP-003
**Context**: [frontend-specification.md](frontend-specification.md)
**Preconditions**: User is viewing a generated briefing.
**Main Flow**:
1. User clicks the "Export" button on the Briefing view.
2. System displays options: "Download Markdown", "Download HTML".
3. User selects a format.
4. System generates and triggers a file download.
**Postconditions**: User has a local copy of the briefing.

### UC-7: Real-time Source Validation
**Actor**: Administrator
**Related Stories**: US-ING-001, US-UI-002
**Context**: [frontend-specification.md](frontend-specification.md)
**Preconditions**: Administrator is on the "Add Source" dialog.
**Main Flow**:
1. Administrator enters a YouTube URL/ID.
2. System (debounced) triggers a backend existence check.
3. System displays a visual indicator (Green/Red check) and the channel name if found.
4. Administrator saves the source.
**Postconditions**: Source is added with high confidence in its validity.

### UC-8: Share Individual Summary
**Actor**: User/Administrator
**Related Stories**: US-16.3
**Context**: [v0.16.0-planning-process.md](../versions/v0.16.0/v0.16.0-planning-process.md)
**Preconditions**: A summary exists for a content item.
**Main Flow**:
1. User views a Briefing or Administrator views Content Management.
2. User/Administrator clicks "Share" on a specific summary item.
3. System generates a unique public UUID for the summary.
4. User/Administrator copies the public link to the clipboard.
5. External viewer accesses the link without authentication.
**Postconditions**: Individual summary is visible to the public until unshared.

### UC-9: Complete Onboarding Checklist
**Actor**: New User
**Related Stories**: US-16.4
**Context**: [v0.16.0-planning-process.md](../versions/v0.16.0/v0.16.0-planning-process.md)
**Preconditions**: User has just registered or has no topics/briefings.
**Main Flow**:
1. User logs in and lands on the Dashboard.
2. System displays a 5-step checklist (Topics, Sources, Trigger, Read, Share).
3. User performs the required action (e.g., creates a topic).
4. System automatically checks off the corresponding item in the list.
5. User completes all steps and reaches 100% progress.
**Postconditions**: User is successfully onboarded and understands the system flow.

### UC-10: React to Job Completion Toasts
**Actor**: User
**Related Stories**: US-16.2, US-16.8
**Context**: [v0.16.0-planning-process.md](../versions/v0.16.0/v0.16.0-planning-process.md)
**Preconditions**: A background job (orchestration) is running.
**Main Flow**:
1. System completes the background job and sends an SSE event.
2. System displays a toast notification with a sound (Success or Failure).
3. System includes an "Action" button in the toast (e.g., [View Briefing]).
4. User clicks the button in the toast.
5. System navigates the user directly to the relevant view.
**Postconditions**: User experiences a seamless transition from job completion to result consumption.

### UC-11: Manage User Accounts (Roles & Statuses)
**Actor**: Administrator
**Related Stories**: US-17.1
**Context**: [v0.17.0-planning-process.md](../versions/v0.17.0/v0.17.0-planning-process.md), [ADR-0003](../adr/0003-authorization-strategy.md)
**Preconditions**: Administrator is logged in with `USER_MANAGE_PERMISSION`.
**Main Flow**:
1. Administrator navigates to `/admin/users`.
2. System displays paginated user list with email, status, role, last login.
3. Administrator filters by status (e.g., `ACTIVE`, `DISABLED`).
4. Administrator clicks "Edit roles" on a user, selects/deselects role bindings, and saves.
5. Administrator disables or enables a user account via the action menu.
**Postconditions**: User role/status changes persisted; affected user's session invalidated if disabled.

### UC-12: Approve or Reject New User Account
**Actor**: Administrator
**Related Stories**: US-17.2, US-17.3
**Context**: [v0.17.0-planning-process.md](../versions/v0.17.0/v0.17.0-planning-process.md), [ADR-0008](../adr/0008-account-activation-state-machine.md)
**Preconditions**: A new user has signed in via OIDC; user is in `PENDING_APPROVAL` state.
**Main Flow**:
1. New user signs in via Google OIDC. System creates `User` with `accountStatus = PENDING_APPROVAL`.
2. New user sees a generic status banner with "contact administrator" hint and sign-out option.
3. Administrator opens `/admin/users` and sees a "Pending (N)" badge on the sidebar.
4. Administrator clicks the badge and reviews the pending list.
5. Administrator clicks "Approve" (or "Reject") on a pending user.
6. System transitions `PENDING_APPROVAL → ACTIVE` (or → `DISABLED`); `updatedAt`/`updatedBy` audit fields record the change.
7. Next time the user reloads, `accountStatus` on `/me` reflects the new state; banner updates accordingly.
**Postconditions**: User account state transitioned; audit trail recorded.

### UC-13: Operate on Background Jobs (Restart/Audit)

**Actor**: Administrator
**Related Stories**: US-17.4
**Context**: [v0.17.0-planning-process.md](../versions/v0.17.0/v0.17.0-planning-process.md)
**Preconditions**: Administrator is logged in; one or more `JobExecutionAudit` records exist.
**Main Flow**:
1. Administrator navigates to `/admin/jobs`.
2. System displays paginated job list with start time, type, duration, status.
3. Administrator filters by type and/or status (e.g., `RUNNING`, `FAILED`).
4. Administrator clicks "Restart" on a `FAILED` job. System spawns a new job with the original parameters.
5. Administrator opens the audit trail for a job; sees a chronological log of state transitions and the acting admin per change.
**Postconditions**: New job spawned; audit row written with acting admin user ID. *(Cancel is deferred to v0.18.0 — requires stop-signal mechanism in `OrchestrationManager`.)*

### UC-14: Resilient Transcript Fetching with Provider Fallback
**Actor**: System (orchestrator)
**Related Stories**: US-17.5
**Context**: [v0.17.0-planning-process.md](../versions/v0.17.0/v0.17.0-planning-process.md), [ADR-0009](../adr/0009-multi-provider-transcript-strategy.md)
**Preconditions**: A `ContentItem` requires transcript fetching; `TranscriptOrchestrator` is configured with an ordered provider chain.
**Main Flow**:
1. Orchestrator invokes the first provider in the chain (default: `youtube-legacy`).
2. If `youtube-legacy` returns `ProviderUnavailable`, `QuotaExhausted`, or times out, orchestrator falls through to the next provider (e.g., `supadata`).
3. Each attempt is recorded in `transcript_provider_quota_usage` for monitoring.
4. On success, transcript is persisted to the `ContentItem` and orchestration continues.
5. If all providers fail, `ContentItem` is marked `FAILED` with `failure_reason=ALL_PROVIDERS_EXHAUSTED`.
**Postconditions**: Transcript fetched (or item correctly marked failed); Micrometer counters incremented per provider.

### UC-15: External Links Open in New Tab
**Actor**: User
**Related Stories**: US-17.6
**Context**: [v0.17.0-planning-process.md](../versions/v0.17.0/v0.17.0-planning-process.md)
**Preconditions**: User is viewing a briefing or summary with embedded external links.
**Main Flow**:
1. User scrolls a briefing and sees an external link (e.g., to a source article).
2. User clicks the link.
3. Browser opens the link in a new tab; current briefing context is preserved.
4. User finishes reading the linked content and closes the new tab.
5. User returns to the original briefing without navigation interruption.
**Postconditions**: User retains briefing context; external link safety attributes (`rel="noopener noreferrer"`) prevent reverse-tabnabbing.

## 4. Revision History
| Version | Date       | Author       | Changes                                                                 |
|:--------|:-----------|:-------------|:------------------------------------------------------------------------|
| 1.4     | 2026-05-20 | Povzetkar AI | Added UC-11 (Admin User Mgmt), UC-12 (User Approval), UC-13 (Job Ops), UC-14 (Transcript Fallback), UC-15 (External Links) for v0.17.0. |
| 1.3     | April 01, 2026 | Povzetkar AI | Added UC-8, UC-9, UC-10 for v0.16.0 (User Experience Focus). |
| 1.2     | March 30, 2026 | Povzetkar AI | Mapped User Story IDs to all Use Cases for Traceability Matrix hardening (POV-166). |
| 1.1     | 2026-03-28 | Povzetkar AI | Added UC-5, UC-6, UC-7 for v0.15.0 planning. |
| 1.0     | 2026-03-25 | Povzetkar AI | Initial draft based on FRD v1.3 and v0.14.0 Planning (POV-152/153/155). |
