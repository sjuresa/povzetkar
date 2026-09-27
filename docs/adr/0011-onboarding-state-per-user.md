# 11. Server-side, Per-User Onboarding Guide State

Date: 2026-09-27

## Status

Accepted — supersedes [ADR-0007](0007-onboarding-logic-persistence.md) (POV-216)

## Context

[ADR-0007](0007-onboarding-logic-persistence.md) kept the dashboard onboarding guide ("Your Assistant's
Guide") in `localStorage` under the fixed key `povzetkar_onboarding`, accepting device dependency and
cache loss as trade-offs. In practice the key has no user dimension: **every account that signs in
from the same browser inherits the previous account's progress and dismissal** — a new user may never
see the guide. Since v0.17 there are also accounts awaiting approval: they hold only the view-only
`demo` role and browse the demo account's content, so the dashboard-derived steps would be ticked
from data that is not theirs.

## Decision

The guide state is held **per user on the server**:

- Table `user_onboarding` (Flyway V36): one row per user — `user_id` (PK, FK → `users`,
  `ON DELETE CASCADE`), five explicit step flags, `dismissed`, `collapsed`, `updated_at`. A separate
  table, not columns on `users`, because `User` carries `@Version` for admin edits and guide clicks
  must not cause optimistic-lock conflicts.
- `GET` / `PATCH /api/v1/users/me/onboarding` (`OnboardingApiResource` → `OnboardingService`). The
  row is created **lazily** on the first read for an `ENABLED` account (covers approval, re-enabling a
  rejected/disabled account and seeded users alike). Non-enabled accounts and read-only API-key
  identities get `403` — they have no guide.
- Steps are **monotonic** (`false` → `400`); `dismissed`/`collapsed` can be set either way. The entity
  uses `@DynamicUpdate`, so an UPDATE writes only the changed columns: concurrent or late `PATCH`es
  (e.g. two tabs) cannot erase progress, and no optimistic locking is needed. The lazy insert uses
  `ON CONFLICT DO NOTHING`, so concurrent first reads do not fail on the primary key.
- The frontend keeps the zustand `onboardingStore` API as an in-memory cache: loaded for enabled
  accounts, actions update it immediately and send a `PATCH`. Steps marked before the state is loaded
  are remembered and applied once it arrives (accounts without a guide never load, so nothing is sent
  for them); other actions are no-ops until then. Overlapping loads share one request, and a failed
  load is retried with a growing delay (5 s, 15 s, then every minute). The old `localStorage` key is
  no longer read or written (and not cleared).
- Pending accounts see no guide; the pending-approval banner points them to the demo content instead.

## Consequences

**Positive:**
- The guide follows the user across browsers and devices, and never leaks between accounts.
- Progress is only ever recorded for the user's own actions on an enabled account.

**Negative:**
- One extra request per session (`GET` after sign-in) and a `PATCH` per guide change; both are small
  and off the critical path (the guide is non-blocking and hidden until loaded).
- Adding, removing or renaming a step needs a small migration (`ADD`/`DROP`/`RENAME COLUMN`) next to the
  unavoidable code changes; whether a new step re-opens a dismissed guide is decided with that change.
