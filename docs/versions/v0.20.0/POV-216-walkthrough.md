# POV-216 Walkthrough — Pending-approval Banner and Per-user Onboarding Guide

**Task:** [POV-216](https://jumis.myjetbrains.com/youtrack/issue/POV-216) (Bug, Normal) · **Branch:** `POV-216` → `v0.20.0` · **Date:** 2026-09-27

## 1. What was done

PO report: on a new account's dashboard the pending-approval banner's *Check status* did nothing, *Sign out* duplicated the header's *Logout*, and "Your Assistant's Guide" did not work correctly — its state was suspected to live in `localStorage`.

**Findings.** *Check status* worked (it re-read `/users/me`), but the status was otherwise fetched only once per page load and the button gave no feedback while the account was still pending. The guide was persisted in `localStorage` under the fixed key `povzetkar_onboarding` — shared by every account in the browser (the defect the PO saw). Pending accounts hold the view-only `demo` role and read the demo account's content, so the guide ticked topic/source/trigger from data that was not theirs.

**Backend**
- Flyway **V36** `user_onboarding` — one row per user (`user_id` PK/FK `ON DELETE CASCADE`, five step flags, `dismissed`, `collapsed`, `updated_at`); a separate table so guide clicks never bump `User.@Version`.
- `UserOnboarding` entity with the domain rule `apply(patch)`: omitted fields unchanged, steps only ever set (a reset → `400 ONBOARDING_STEP_IRREVERSIBLE`, rejected before any change), `dismissed`/`collapsed` both ways. `@DynamicUpdate` (an UPDATE writes only changed columns, so concurrent patches cannot reset each other's steps) and `findOrCreate` (lazy `INSERT … ON CONFLICT DO NOTHING`, safe for concurrent first reads).
- `OnboardingService` — via `findOrCreate`, lazily creates the empty row on first access for an `ENABLED` account (covers approval, re-enabling a rejected/disabled account, seeded users); non-enabled accounts and read-only API-key identities → `403 ONBOARDING_NOT_AVAILABLE`.
- `OnboardingApiResource` — `GET`/`PATCH /api/v1/users/me/onboarding` (`@Authenticated`, enveloped); generated client regenerated (orval).
- [ADR-0011](../../adr/0011-onboarding-state-per-user.md) supersedes ADR-0007.

**Frontend**
- `onboardingStore` — same public API, no `persist`: an in-memory cache loaded from the server; actions update the store immediately and send a `PATCH` — one after another, in order; a failed `PATCH` unloads the store, so the loader reloads the saved state and resends the unsent steps. Steps marked before the state arrives are remembered and applied silently on load; other actions are no-ops until then. The old `localStorage` key is ignored.
- `useOnboardingLoader` loads the guide for `ENABLED` accounts and retries a failed load (5 s, 15 s, then every minute); overlapping loads share one request; the dashboard shows it only when loaded, and `syncFromDashboard` now waits for the loaded state (it previously could run first and never repeat).
- `usePendingApprovalPolling` — while `PENDING_APPROVAL`: `GET /users/me` every 60 s (visible tab only) and on returning to the tab, via the generated client (not `checkAuth`, which logs the UI out on any error). Approval → one reload (fresh query cache, SSE stream and guide) with a `sessionStorage` flag; `useApprovalNotice` then shows the "account approved" toast. Rejected/disabled → also one reload (no toast), so no cached demo content stays visible. Inert for every other status.
- `AccountStatusBanner` — no buttons in any variant; the pending banner adds a two-step demo hint (Topics, Briefing Hub) in EN and SL; unused i18n keys removed.

**Specification and process**
- US-16.2/.3/.4/.8 — orphaned references of UC-8/9/10 — migrated into the US catalogue (§3.8, story text reconstructed); UC-9, TC-UI-004, TC-SEC-004 updated; backlog entry closed.
- **QA rule 3 rewritten** as the general test-scope rule (PO): persistence and transactional behaviour against the real DB once, business logic with Panache mocked per the Quarkus guide, pure logic as unit tests, security/HTTP contract with the service mocked. Stated only in `quality-mandates.md`; the test plan, checklist, methodology and `testing.md` point to it; `testing.md` §2.5 documents the pattern with this task's tests.
- Backlog: SSE events are broadcast to all users — opened as bug [POV-217](https://jumis.myjetbrains.com/youtrack/issue/POV-217).

**Verification**
- TDD, red first per unit: backend 17 of 21 new tests failing against the skeleton (the 4 green ones exercise only the final thin resource layer), then 21/21; frontend store 9/13, dashboard 2 new, hooks 12/12, banner 4/6 failing, then green.
- The `PanacheMock` + `@InjectMock Session` trial worked on the first run once the static-factory trap was avoided (see Deviations).
- Final state: backend `mvn verify` 386 tests, 0 failures/errors, 3 skipped, SpotBugs 0 (backend unchanged after that run) · frontend `npx vitest run` 19 files / 96 tests green · `tsc -b` clean · lint on touched files: only the pre-existing `AppShell.tsx` `theme as …` error · orval regeneration: only the new endpoint functions and models · planning audit `--current-version v0.20.0 --fail-on all`: passed.

## 2. Planned vs. actual scope

| Planned | Actual |
| --- | --- |
| R1–R3 pending banner, status polling, demo hint | Done; hint has 2 steps instead of 3 (plan interview P6), rendered without `<Trans>` |
| R4–R7 per-user guide state | Done; plus API-key identities excluded, dashboard sync race fixed |
| R8–R9 specification sync | Done; plus ADR-0011 / ADR-0007 superseded |
| R10 SSE backlog | Done — own bug POV-217 (PO) |
| R11 QA rule 3 (added in plan interview P8) | Done |

## 3. Time delta

Estimate 6.5 h for the whole task (incl. PR loop and YouTrack record); up to the commit ~3 h 20 min.

| Phase (2026-09-27, CET) | Actual |
| --- | --- |
| Brainstorm 19:07–20:05 (11 decisions) | 58 min |
| Planning 20:05–21:09 (P1–P8) | 64 min |
| Implementation 21:09–21:28 (TDD backend + frontend, specs, close-out drafts; `mvn verify` 21:26–21:28) | 19 min |
| Review round 1 21:28–21:43 (paused 21:29–21:40 — session rate limit, not booked) | 4 min |
| Fixes 21:43–21:49 (incl. `mvn verify`) | 6 min |
| Review round 2 21:49–21:56 | 7 min |
| Fixes 21:56–21:59 | 3 min |
| Review round 3 21:59–22:08 | 9 min |
| Fixes + PO decisions on B1/B2 22:08–22:18 | 10 min |
| Close-out 22:18–22:25 | ~7 min |
| **Up to the commit** | **~3 h 07 min** (vs ~4.5 h of the 6.5 h estimate before PR loop/YouTrack) |

## 4. Deviations

- **Scope added after the task definition:** R11 (QA rule 3), R3 two steps instead of three, R4 old key not cleared — all PO decisions in the plan interview.
- **API-key identities get 403** (not planned): the lazy-creating `GET` would have written through a read-only API-key identity; `UserContextService.isApiKeyIdentity()` made public for the check.
- **Constructor instead of a static factory:** `PanacheMock.mock(Entity.class)` mocks every static method of the entity, custom ones included, so `emptyFor()` returned `null` in the service test — recorded in `testing.md` §2.5.
- **Anonymous → 302, not 401:** OIDC `web-app` redirects to login; the test was corrected (no production change).
- **Dashboard sync race** found and fixed (red first).
- **AppShell logic extracted into three hooks** with their own tests instead of an AppShell render test; **demo hint without `<Trans>`** (the global i18n test mock has none).
- **ADR-0011** written — the plan missed that the `localStorage` decision was ADR-0007.
- `OnboardingChecklist.test` / `ExportMenu.test` needed no change (presentational / store mocked).

- **Pre-commit review** (`/code-review high` over `git diff v0.20.0` incl. uncommitted files; `/security-review` — new authenticated endpoint):
  - *Round 1* — neither skill actually ran (`/code-review` hit the session rate limit; the `/security-review` subagent wrongly claimed the skill sees only commits — a claim the agent's prompt had invited and the agent relayed unverified; PO: the skill reviews what it is told). Manual reviews: security 0 findings; code 4 — lost update between concurrent PATCHes (→ `@DynamicUpdate` + DB test), "read" lost when the briefing loads before the guide (→ `earlySteps` applied on load), PK violation on concurrent first reads (→ `findOrCreate` with `ON CONFLICT DO NOTHING`), QA rule 3 ambiguity for changed entities (→ one sentence). All fixed, red first.
  - *Round 2* — both skills ran with the explicit target. Security 0. Code 2 of 10 raw verified: a failed first load never retried (→ retry 5 s/15 s/60 s) and overlapping loads racing (→ shared in-flight promise). Fixed, red first. An agent scripted edit duplicated part of the store file meanwhile — caught by `tsc`, rewritten.
  - *Round 3* (last, PO) — code 6 of 9 raw verified: a failed step PATCH lost for good (→ roll the step back; superseded in the PR loop by a resync on failure); 5 low rejected with reasons (cosmetic reordering of `collapsed` retries, 403 retry noise after mid-session disablement, TanStack rewrite of the polling, entity importing a DTO — existing pattern, an unreachable `null`). Normative documents (reviewed separately): pure pointers in `testing.md`/`methodology.md`, the §2.3 note on committing `requiringNew` tests and a compiling §2.5 example fixed; B1 "a missing `@Transactional` passes every test" **rejected by the PO** — code review catches that, the whole chain is the job of integration/E2E tests (PW-007 planned); B2 **applied** — queries (HQL included) live in named entity/repository methods, not inline in services (existing inline HQL in 8 classes → [POV-218](https://jumis.myjetbrains.com/youtrack/issue/POV-218)).

## 5. Traceability

- **US:** US-17.3 (pending-approval status — banner and automatic status check), US-16.4 (onboarding checklist — per-user state; migrated into the catalogue by this task).
- **UC:** UC-9 (Complete Onboarding Checklist) — precondition and flow updated.
- **TC:** TC-UI-004 (Dashboard Onboarding), TC-SEC-004 (Pending Approval Gating) — expected results updated; both remain Playwright-planned (PW-007, PW-013); this task adds unit/integration coverage.
