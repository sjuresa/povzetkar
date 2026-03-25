# Use Cases - Project Povzetkar

## 1. Introduction
This document defines the core use cases for the Povzetkar system, mapping user stories to specific interactions. It serves as the basis for test cases and implementation verification.

## 2. Actor Definitions
- **User/Consumer**: Views generated briefings and manages personal preferences.
- **Administrator**: Manages topics, content sources, users, and triggers orchestration.

## 3. Use Case List

### UC-1: Manage Topic Sources
**Actor**: Administrator
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
**Context**: [frontend-specification.md](frontend-specification.md)
**Preconditions**: Briefings have been generated for at least one topic.
**Main Flow**:
1. User navigates to Briefings Library.
2. User selects a Briefing from the list.
3. System renders the Markdown briefing text with source references.
4. User clicks a source reference to view the original content (e.g., YouTube URL).
**Postconditions**: User is informed of the gathered intelligence.

## 4. Revision History
| Version | Date       | Author       | Changes                                                                 |
|:--------|:-----------|:-------------|:------------------------------------------------------------------------|
| 1.0     | 2026-03-25 | Povzetkar AI | Initial draft based on FRD v1.3 and v0.14.0 Planning (POV-152/153/155). |
