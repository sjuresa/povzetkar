# 8. Account Activation State Machine

Date: 2026-05-20

## Status

Accepted

## Context

Since v0.10.0 (Google OIDC integration), new users are auto-activated on first sign-in: a `User` record is created with `active=true` (see [User.java:39](../../backend/src/main/java/com/povzetkar/quarkus/model/User.java#L39) and [UserContextService.java:116](../../backend/src/main/java/com/povzetkar/quarkus/service/UserContextService.java#L116)) and the `user` role. This was acceptable while the platform was effectively single-tenant but exposes the public deployment to **uncontrolled sign-up**: any Google account holder can land on the deployed instance, sign in, and consume orchestration cycles before an administrator is aware.

We need an explicit gating mechanism that prevents resource consumption by unapproved users while remaining lightweight enough to not require email infrastructure.

## Decision

Replace the existing `active: boolean` field on `User` with an explicit `accountStatus: AccountStatus` enum. Lifecycle state and PBAC roles remain **orthogonal** concerns — status answers *"where in the lifecycle is this account"*, roles answer *"what is this account permitted to do"*. Business logic coordinates the two transactionally.

### State machine

```text
[OIDC first sign-in]
        │
        ▼
   PENDING_APPROVAL ──[admin approve]──▶ ENABLED ──[admin disable]──▶ DISABLED
        │                                  ▲                              │
        │                                  └──[admin enable]──────────────┘
        │
        └──[admin reject]──▶ REJECTED ──[admin approve]──▶ ENABLED
```

States: `PENDING_APPROVAL`, `ENABLED`, `DISABLED`, `REJECTED`. All transitions to/from `ENABLED` are reversible (including `REJECTED → ENABLED` — rejection is **not terminal**, admin may change their mind). The enum is **extensible** for future states (e.g., `SUSPENDED_PAYMENT`, `LOCKED_SECURITY`, `EXPIRED_INACTIVE`) without schema changes.

### Implementation specifics

1. **User entity**: drop `active: boolean`. Add `accountStatus: AccountStatus` (enum, NOT NULL).
2. **New `demo` role**: read-only view permissions only. Specifically: `TOPIC_VIEW`, `BRIEFING_VIEW`, `CONTENT_ITEM_VIEW`. No `*_CREATE/UPDATE/DELETE`, no `TOPIC_TRIGGER_RUN`, no `*_GENERATE`, no `ADMIN_*`, no `SOURCE_*`.
3. **OIDC creation flow** ([UserContextService.createNewUser](../../backend/src/main/java/com/povzetkar/quarkus/service/UserContextService.java#L111)): every new user is created with `accountStatus = PENDING_APPROVAL` and `demo` role. **No config flag** — approval is always required. Existing users are bulk-migrated to `accountStatus = ENABLED` via idempotent SQL backfill.
4. **Status-role coordination**: transitions are atomic and handled in a service method that owns both fields:
   - `approve` (PENDING_APPROVAL → ENABLED): remove `demo`, add `user`.
   - `reject` (PENDING_APPROVAL → REJECTED): remove `demo` (no role assigned).
   - `disable` (ENABLED → DISABLED): remove `user`.
   - `enable` (DISABLED|REJECTED → ENABLED): add `user`.
5. **Action endpoints** (POV-185): replace the existing (unused) `PUT /users/{id}` and `PUT /users/{id}/roles` with action-specific endpoints. The old endpoints are **deleted** rather than refactored, since neither has any frontend consumer:
   - `POST /api/v1/users/{id}/approve`
   - `POST /api/v1/users/{id}/reject`
   - `POST /api/v1/users/{id}/disable` — with self-action guard + last-admin guard.
   - `POST /api/v1/users/{id}/enable`
   - All under `@RolesAllowed(Permission.V_USER_MGMT_UPDATE)`.
6. **`/me` endpoint** ([UserApiResource.getCurrentUser](../../backend/src/main/java/com/povzetkar/quarkus/api/UserApiResource.java#L68)): change `@RolesAllowed(Permission.ROLE_USER)` to `@Authenticated`. The `UserDTO` response gains an `accountStatus` field. This is the **only** way the frontend learns the account state — no separate `/me/account-status` endpoint.
7. **Frontend UX**: SPA reads `accountStatus` from `/me`. For any non-`ENABLED` value, shows a generic banner with the status name and a "contact administrator" hint. No dedicated `PendingApprovalPage` / `DisabledPage` / `RejectedPage` in v0.17.0 — when contextual UX becomes necessary, add then.
8. **Demo content (visibility for pending users)**: a single seed system user (`demo@povzetkar.local`) owns one demo topic with 1-2 content sources. The demo user has **no OIDC identity** and never holds a session. Pending users view demo content via an **impersonation pattern**: a service helper `getContentViewingUser()` returns the demo user when the caller holds the `demo` role, otherwise the authenticated user. Existing topic/briefing/content-item queries (e.g., [TopicApiResource.java:104](../../backend/src/main/java/com/povzetkar/quarkus/api/TopicApiResource.java#L104)) keep their existing `WHERE t.user = ?1` filter — only the helper changes which user is passed in. The SPA must function correctly even when the demo user has no content (empty briefing list, etc.).
9. **No audit history table**: status transitions rely on `BaseAuditableEntity`'s `updatedAt`/`updatedBy` for minimal trace. A dedicated `account_status_history` table is **deferred** until forensic visibility becomes necessary.
10. **Session invalidation**: out of scope for this ADR — Quarkus OIDC session behavior on role/status change is verified at implementation time.

### What is NOT in this ADR

- **Anonymous demo trial** (landing-page "Preizkusi demo" without OIDC). Logged in [improvements.md](../backlog/improvements.md). Pending users already receive the equivalent experience after OIDC sign-in.
- **Admin role management** (promote-to-admin / revoke-admin). No frontend need in v0.17.0. The existing `PUT /users/{id}/roles` is **deleted** as dead code; equivalent action endpoints can be added later if required.
- **Email notifications** (admin notification of new pending user, user notification of approval). Requires SMTP infrastructure; logged in [improvements.md](../backlog/improvements.md). Approval discovery in v0.17.0 happens via the admin UI badge counter only.
- **Status enforcement filter / default-deny middleware**. Not needed — PBAC `@RolesAllowed` already enforces correctly when role assignments are kept consistent with status (which they are, by transactional coordination).
- **`account.approval.required` config flag**. Deliberately omitted — approval is always required. Test fixtures explicitly create users with `accountStatus = ENABLED` via `UserBuilder` rather than relying on a config override.

## Consequences

### Positive

- **Resource protection**: unapproved users cannot trigger orchestration or consume GenAI credits.
- **Single source of truth on lifecycle**: one enum, no redundant fields drifting out of sync.
- **PBAC reuse**: access enforcement uses existing `@RolesAllowed` mechanism, not a parallel system.
- **Extensible**: future states (payment, security lock, inactivity expiry) are new enum values, no schema change.
- **Production-test parity**: no config flag means tests exercise the same code path as production. Tests adapt by explicitly creating users in the desired state via `UserBuilder`.
- **Clean impersonation pattern**: demo visibility for pending users requires only one new helper, not query refactoring across 5-8 endpoints.
- **Minimal frontend surface**: a single `/me` extension and a generic banner cover all four states.

### Negative

- **Test fixture churn**: `UserBuilder` and any OIDC-mock flow that assumes auto-active users must be updated to either build `ENABLED` users explicitly or exercise the approval flow.
- **No history of transitions**: `BaseAuditableEntity` shows only the latest change. Multi-step lifecycles (approve → disable → re-enable) are not visible. Acceptable for single-admin operation; revisited if forensic need emerges.
- **Generic banner is unpolished UX**: pending users see only a status name, not contextual instructions. Acceptable when admin approval latency is short (minutes/hours under current single-admin operation). Replaced with dedicated pages when justified.
- **Demo user is a special-case data entity**: requires migration to seed it, and the impersonation helper introduces a "viewing identity ≠ authenticated identity" distinction in the service layer. Documented in code comments at the helper site.

## Alternatives Considered

1. **Keep `active` boolean, add a separate `lifecyclePhase` enum** — two orthogonal fields to coordinate. Rejected: invariant maintenance is a bug magnet; single enum carries the same information with no consistency burden.
2. **PBAC-only (lifecycle expressed via roles, no status field)** — e.g., a `pending_user` role represents "pending status". Rejected: conflates two distinct concerns (lifecycle vs capabilities); admin needs to query "users awaiting approval" as a first-class lifecycle question.
3. **Default-deny JAX-RS filter for non-ENABLED users** — parallel access-control system alongside PBAC. Rejected: existing PBAC sufficient when role assignments are coordinated with status; a filter would duplicate enforcement.
4. **`/api/v1/me/account-status` as a separate endpoint** with `@Authenticated`. Rejected: `/me` already returns user info and only needs to be opened to all authenticated users plus gain one new field. Avoids a new endpoint contract.
5. **Generic `PUT /users/{id}` with `accountStatus` field** (single endpoint for all transitions). Rejected after learning the existing endpoints have no consumers: action-specific endpoints (`/approve`, `/reject`, `/disable`, `/enable`) are more REST-correct, easier to validate per-action, and align with the codebase's preference for granular operations.
6. **`account.approval.required` config flag with `false` default in test profile** — preserve existing tests as-is. Rejected: shaping production code to avoid touching tests is the wrong direction; test/production divergence creates worse defects than the one-time refactor cost.
7. **Dedicated `account_status_history` audit table** for v0.17.0. Rejected for v0.17.0 scope; can be added when forensic visibility becomes a real (not hypothetical) requirement.
8. **Email notification for pending users** (admin + user notifications). Rejected for v0.17.0 because no SMTP infrastructure exists. Candidate work in [improvements.md](../backlog/improvements.md).
9. **Anonymous demo trial** (landing-page "Preizkusi demo" without OIDC sign-in). Rejected because OIDC sign-in already grants the equivalent demo-viewing experience (new users land in PENDING_APPROVAL with `demo` role). Logged as a backlog candidate; revisited only if a real friction signal emerges.
