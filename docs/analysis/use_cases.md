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

## 4. Revision History
| Version | Date       | Author       | Changes                                                                 |
|:--------|:-----------|:-------------|:------------------------------------------------------------------------|
| 1.2     | March 30, 2026 | Povzetkar AI | Mapped User Story IDs to all Use Cases for Traceability Matrix hardening (POV-166). |
| 1.1     | 2026-03-28 | Povzetkar AI | Added UC-5, UC-6, UC-7 for v0.15.0 planning. |
| 1.0     | 2026-03-25 | Povzetkar AI | Initial draft based on FRD v1.3 and v0.14.0 Planning (POV-152/153/155). |
