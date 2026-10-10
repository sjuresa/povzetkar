# POV-216 Retrospective — Pending-approval Banner and Per-user Onboarding Guide

## 1. Planned vs. actual

Delivered as planned: an informational pending banner with automatic status detection and a demo hint, the onboarding guide held per user on the server, the US-16.x catalogue migration and the SSE finding as its own bug (POV-217). The plan interview added one requirement — the general test-scope rule (QA rule 3) — and two small copy/scope adjustments (R3, R4). Execution found four things the plan had missed: API-key identities writing through a lazy `GET`, the `PanacheMock` static-factory trap, a dashboard sync race, and ADR-0007.

## 2. Deviations & why

- **"Check status does nothing" was a feedback defect, not a broken call.** The button re-read `/users/me`; the status just was not re-fetched anywhere else and nothing showed while pending. Removing it only works together with automatic detection — which in turn needed a reload on approval: the query cache held demo content, and the SSE stream had failed with 403 for the pending user and does not reconnect.
- **The test strategy changed mid-plan.** The agent proposed DB-backed integration tests per the old QA rule 3; the PO challenged the rule, the agent traced it to the v0.6.2 retrospective (brittle `PanacheMock`/`mockStatic` use amid a Quarkus-transaction knowledge gap) and overstated it at first ("the rule forbids mocking Panache" — it only covered `@QuarkusTest`, and `PanacheMock` was already in use). The PO chose the documented Quarkus approach as a deliberate trial and then made it the general rule, stated once.
- **The plan missed ADR-0007.** The `localStorage` decision was an accepted ADR whose stated trade-off (device dependency) did not mention the real defect (no user dimension). Superseded by ADR-0011.

## 3. Technical findings

- **`PanacheMock.mock(Entity.class)` mocks every static method, custom ones included.** A static factory on the entity silently returns `null` in service tests; use a constructor. With that, `PanacheMock` + `@InjectMock Session` worked on the first run — the v0.6.2 difficulties were a knowledge gap, not a limitation.
- **`schema-management.strategy=none` means no test catches an entity/table mismatch unless one persists the entity** — hence the dedicated persistence test per entity in the new rule.
- **A lazily-creating `GET` is a write.** Under API-key auth (read-only by design, POV-205) the global policy blocks only non-GET methods; the service has to exclude API-key identities itself.
- **Effects that depend on asynchronously loaded state must list it as a dependency** — `syncFromDashboard` ran before the guide loaded and never repeated.
- **Anonymous requests to an OIDC `web-app` endpoint get `302`, not `401`** — RestAssured follows the redirect and reports `200` unless `redirects().follow(false)`.

**Process-weight evidence (v0.20 carry-forward theme 4):** three pre-commit review rounds — 4, 2 and 6 verified code findings (+5 normative); every medium finding was a real defect in concurrency or load ordering that the TDD tests had not covered (lost update, early marks, retry, failed PATCH), i.e. the reviews paid for themselves. Review rounds took ~20 min of agent time (plus 11 min rate-limit pause); the Time Log and deviation entries a few minutes per phase; the YouTrack record is booked after the PR loop.

## 4. Feed-forward

- **New and changed tests follow the rewritten QA rule 3** — per entity one DB persistence test; service logic with `PanacheMock` + `@InjectMock Session`; see `testing.md` §2.5.
- **POV-217 (SSE per user)** would let an `ACCOUNT_STATUS_CHANGED` event replace the pending-status polling.
- **POV-213 (scheduler integration test)** is a transactional-behaviour case — under the new rule it stays DB-backed.
- **Give review skills their target explicitly** (`git diff <base>` incl. uncommitted files) and never offer the subagent a "review by hand if the skill cannot" escape — in round 1 that turned into two manual reviews and an unverified claim about the skill.
- **Queries go into named entity/repository methods** (QA rule 3); the eight classes with inline HQL are [POV-218](https://jumis.myjetbrains.com/youtrack/issue/POV-218) (PO: must be fixed).
