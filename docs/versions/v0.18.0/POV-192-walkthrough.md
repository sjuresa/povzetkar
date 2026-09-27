# POV-192 Walkthrough — Fix Demo User Access

## What was done

Added `SOURCE_VIEW` to the `demo` role's permission set so that not-yet-approved users can load the source filter on Briefing Hub and navigate the Sources page without a 403 crash. Added a `readonly` mode to the Topics page that hides Edit, Delete, Run Now, and Add Topic controls for demo users, eliminating confusing backend rejections on mutation actions.

**Files changed:**
- `backend/src/main/java/com/povzetkar/quarkus/security/RolePermissionsMap.java` — 1 line added to `DEMO_PERMISSIONS`
- `backend/src/test/java/com/povzetkar/quarkus/security/RolePermissionsMapTest.java` — new `testDemoRoleHasScopedPermissions` test
- `backend/src/main/resources/db/migration/V35__Enable_demo_topic.sql` — corrects demo topic seeded as inactive
- `frontend/src/pages/topics/TopicsPage.tsx` — `readonly` flag derived from permissions, "Add Topic" button gated
- `frontend/src/pages/topics/TopicList.tsx` — `readonly` prop added, action button group conditionally rendered

## Planned vs. actual scope

Delivered exactly as scoped. No additions or removals.

## Time delta

- Estimated: 1–2 hours
- Actual: ~1 hour (brainstorm + plan + execution)

## Deviations

None. The `TOPIC_CREATE` discriminator worked cleanly as the single permission check for the readonly gate, as predicted.

## Traceability

- **TC:** TC-SEC-006 (demo role access regression)
