# 7. LocalStorage Persistence for Onboarding

Date: 2026-04-01

## Status

Accepted

## Context

New users need to be guided through the "Golden Path" (Topic -> Source -> Trigger -> Read -> Share). We want a persistent checklist on the Dashboard that tracks progress. However, fetching and persisting "Onboarding Step 3 Completed" via a backend API:
1.  Adds latency to the Dashboard load.
2.  Requires a new DB table/column just for a one-time onboarding state.
3.  Might be redundant for guest users or first-time registered users.

## Decision

We will use **LocalStorage** as the primary source of truth for the **Interactive Onboarding Checklist**.

Specifically:
-   A `povzetkar_onboarding` key in `LocalStorage` will store a JSON object with completion statuses (boolean) for each of the 5 steps.
-   The Dashboard logic will check this local state on mount.
-   Action handlers (e.g., clicking "View Briefing" or "Save Topic") will redundantly trigger a `setCompletion('stepName', true)` dispatch.
-   Checklist will only be displayed if `completionRate < 100%`.
-   **Reset Ability**: A simple "Clear data" or "Restart Guide" button will purge the `LocalStorage` key.

## Consequences

**Positive:**
-   **Performance**: Zero-latency, client-side state management for onboarding.
-   **No DB Bloat**: Avoids schema changes for a transient user state.
-   **Offline support**: Guideline progress is kept even if the API is unreachable (graceful failure).

**Negative:**
-   **Device Dependency**: Onboarding progress is NOT synced across browsers/devices.
-   **Data Fragility**: If a user clears their browser cache, the checklist might reappear. This is an acceptable trade-off for a one-time experience.
