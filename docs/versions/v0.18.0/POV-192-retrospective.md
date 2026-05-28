# POV-192 Retrospective — Fix Demo User Access

## Planned vs. actual

Scoped and delivered: two fixes, four files. No scope creep, no scope reduction.

## Deviations & why

Migration V35 added during execution — the demo topic was discovered to be seeded as `enabled=FALSE` (an oversight in V32). Addressed within the task per Step 2.14 (critical usability issue, minimal fix).

## Technical findings

**`DEMO_PERMISSIONS` omission pattern:** The demo role's permission set was defined at the time the Sources feature was not yet gated by a dedicated permission — when `SOURCE_VIEW` was later introduced as a distinct permission, `DEMO_PERMISSIONS` was not updated. This is a latent risk: any future permission added to `USER_PERMISSIONS` that covers read-only data access should be evaluated for inclusion in `DEMO_PERMISSIONS` at the same time.

**Frontend readonly gate via single discriminator permission:** Using `TOPIC_CREATE` as the sole discriminator for the readonly flag is correct because the demo role's write permissions form a contiguous group — if you can't create, you can't update/delete/trigger either. This pattern holds as long as role definitions remain internally consistent. If a future role is introduced that can update but not create, this assumption breaks and per-permission checks would be needed.

**Test coverage gap closed:** `RolePermissionsMapTest` previously had no test for the `demo` role despite it being a first-class role with production significance. Added `testDemoRoleHasScopedPermissions` as a regression guard.

## Feed-forward

- When adding a new permission to `USER_PERMISSIONS`, explicitly evaluate whether `DEMO_PERMISSIONS` should also include it (especially read-only permissions).
- The `readonly` prop pattern in `TopicList` is available as a template if other list components need similar gating in future.
