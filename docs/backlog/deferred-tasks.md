# Deferred Tasks

Task drafts deferred from v0.16.0. Each one also has an entry in [`improvements.md`](improvements.md), which is
the backlog of record.

## Actionable Toasts
- **Priority:** **P1**
- **Title:** `feat(ux): integrate primary actions into background job notifications`
- **Description:**
  Enhance toast notifications with interactive buttons corresponding to the job status (e.g., "View Result" for completed compilations).
- **Resilience & Error States**:
  - **Missing Action**: If action metadata is corrupt, fallback to a standard static notification.
- **Context / Documentation:**
  - [ADR-0006](../adr/0006-auditory-feedback-framework.md)
- **Required Skills**: `@[skills/clean-code]`, `@[skills/frontend-design]`
- **Performance Budget**:
  - **Toast Latency**: < 50ms for action-enabled renders.
- **Acceptance Criteria:**
  - [ ] Toast for "Briefing Compiled" shows a [View Result] button.

## Background Jobs Retention
- **Priority:** **P2**
- **Title:** `chore(ops): Implement automated job history cleanup job`
- **Description:**
  Develop a scheduled maintenance service to purge old job execution records, preventing long-term database bloat.
  *(Partly done: `RetentionService` purges `job_execution_audit` in chunks since POV-165; `background_jobs` itself
  has no retention yet — "Job Retention Policy" in `improvements.md`.)*
- **Resilience & Error States**:
  - **Transaction Timeout**: Ensure purge runs in small batches if volume > 10k.
- **Context / Documentation:**
  - [../sdlc/11_development_checklist.md](../sdlc/11_development_checklist.md)
- **Required Skills**: `@[skills/server-management]`
- **Performance Budget**:
  - **CPU Impact**: Job execution must consume < 5% CPU during nightly window.
- **Acceptance Criteria:**
  - [ ] Old records (>30 days) are purged nightly.

## Toggle State Unification
- **Priority:** **P2**
- **Title:** `refactor(ux): standardize optimistic state pattern for all interactive switches`
- **Description:**
  Refactor all Dashboard switches to use an optimistic UI pattern with instant UI local state updates and async backend synchronization.
- **Resilience & Error States**:
  - **Sync Conflict**: In cases of server-side failure after an optimistic update, the UI must perform a rollback-with-toast (POV-171).
- **Context / Documentation:**
  - [ui-ux-manifesto.md](../versions/v0.16.0/v0.16.0-ui-ux-manifesto.md)
- **Required Skills**: `@[skills/nextjs-react-expert]`
- **Performance Budget**:
  - **Effective Latency**: < 10ms for UI state toggle (Optimistic).
- **Acceptance Criteria:**
  - [ ] Toggles feel "lag-free" regardless of API latency.

## Full i18n Audit
- **Priority:** **P3**
- **Title:** `chore(i18n): eliminate hardcoded strings from Dashboard and Item managers`
- **Description:**
  A comprehensive sweep of the frontend codebase to extract all remaining hardcoded strings into Slovenian and English translation files.
- **Resilience & Error States**:
  - **Missing Key**: Fallback to English if Slovenian translation is missing in `sl.json`.
- **Context / Documentation:**
  - [i18n-audit.md](../versions/v0.16.0/v0.16.0-i18n-audit.md)
- **Required Skills**: `@[skills/i18n-localization]`
- **Performance Budget**:
  - **Translation Payload**: < 10KB total increase after mapping all strings.
- **Acceptance Criteria:**
  - [ ] 100% keys found in `en.json` and `sl.json`.
