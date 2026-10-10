# POV-219 Walkthrough — Sources and Content Scoped to the User's Topics

**Task:** [POV-219](https://jumis.myjetbrains.com/youtrack/issue/POV-219) (Bug) · **Branch:** `POV-219` → `v0.20.0` · **Date:** 2026-09-28

## 1. What was done

PO report: content sources are not bound to a user, so every user sees every source. Decision: a user sees only the sources in their own topics; the same restriction applies to content items.

**Findings.** Sources were global and every read path was open: `GET /sources` (Sources page, Add Source search, Briefings source filter), `GET /sources/{id}` and `/items`, and all content-item endpoints by ID — detail, summary, transcript, share, unshare and generate-summary (an LLM call). Three further doors: a topic accepted any existing source ID (`resolveSources`), so a guessed ID attached — and then revealed — any source; the legacy Qute UI (`/ui/*`, `authenticated` only, not linked from the SPA) listed every source, attached any by ID and showed any summary by ID. Quick Add stored the typed identifier (`@handle`, URL) while ingestion reads it as a channel ID — such sources never received content, and prod held one orphan (`@veritasium`, id 72, 0 items) next to the real channel.

**Visibility rule** (Brainstorm #1–#2): a source is visible iff it is in one of the user's topics (disabled ones included); an item iff its source is visible **or** its summary is in one of the user's briefings — so old briefings stay fully usable (incl. per-item share) after a source is removed. Admin sees everything; DEMO sees the demo account's content; an API key only the mapped user's (also an admin's). Anything else → 404, no side effect.

**Backend**
- `ContentScope` (`unrestricted()` / `of(User)`, private constructor — no implicit "everything"); `UserContextService.getContentScope()` resolves it (admin not via API key → unrestricted).
- Named queries with the visibility condition defined once per entity: `ContentSource.findVisible/findVisibleById/listVisibleByIds/findByIdentifier`, `ContentItem.findVisibleIds` (with `Filter`), `findIdsBySource`, `findVisibleByIdWithAssociations`, `Summary.findVisibleByContentIdWithAssociations`, `findContentItemIdsWithSummary`, `Topic.findByUserWithSources/findByIdAndUserWithSources/findByUserAndName` — the inline HQL of `ContentSourceResource`, `SourceItemApiResource`, `ContentItemApiResource` and `TopicApiResource` moved there (overlaps POV-218).
- Scoped endpoints: sources list/detail/items; content-items list (a foreign `sourceId`/`topicId` → 404), detail, summary, transcript, share, unshare, generate-summary. OpenAPI descriptions state the scope.
- `ContentSourceService.findOrCreate` — the one place a source is created: resolves the input on the platform, finds or stores it under the **channel ID** (`ContentSourceDetails.channelId`), 400 for an unknown channel, an unknown platform or `BLOG`. No `requiringNew`/race recovery (plan P2/P4a). The dead single-source enrichment path was removed.
- Topics: `TopicCreate`/`TopicPatch` accept `sourceIds` only for visible sources and `newSources` (`ContentSourceCreate`) found or created on save; every source is validated before the topic is touched (duplicate name → sources → only then fields and sources are written). `SOURCE_CREATE` removed from the user role — `POST /sources` is admin-only.
- `/ui/*` admin-only (`application.properties`). Flyway **V37**: indexes `content_source_topics(topic_id, content_source_id)`, `briefing_summaries(summary_id)`.

**Frontend**
- API client regenerated (orval) — additive: `newSources`, `channelId`, descriptions.
- `TopicForm`: one selected-sources list keyed `id:<n>` / `new:<platform>:<identifier>` (`selectedSource.ts`), payload `sourceIds` + `newSources`, onboarding step "source" also for Quick Add-only topics, PATCH via the mutation hook (Save disabled while pending), sources query invalidated after save.
- `QuickSourceIntake`: no `POST /sources`; *Add* only when the channel check found the channel; Blog visible but disabled (no hint, PO); guidance texts corrected (handle/URL/ID accepted).
- `SourceSearchModal`: every close (X, selection, Quick Add) resets Quick Add, search and page — the PO's finding.
- `SourcesLibraryPage`: all texts in i18n (SL/EN), subtitle "sources from your topics", neutral counter, two empty states ("no sources yet — add them in a topic" + link / "No sources found" only). `SourceDetailPage`: a 404 shows "Source not found" instead of the error boundary (`throwOnError`/`retry` off for 404 only).

**Process**
- QA rule 3 — "Design first" for transactional behaviour (plan P5); `testing.md` §2.5 pointer and scope-mocking notes; three lessons (`lessons-learned.md`).
- Backlog: "Ownership scoping on detail endpoints (POV-205)" closed; new: global per-item summary sharing, removing the legacy Qute UI.

**Verification**
- Backend: new `ContentVisibilityQueriesTest` (16, real DB: own/disabled/foreign/no-topic sources, a source in two topics listed and counted once, search/`excludeIds` never foreign, briefing-only items, filters, unrestricted; plus the three moved Hibernate 7.4 paging guards), `ContentSourceServiceTest` (6), `UserContextServiceContentScopeTest` (5), `LegacyUiAccessTest` (7); the four resource test classes rewritten as HTTP-contract tests with mocks (53 tests: 404 on every scoped endpoint, no side effect on foreign content — share sets no `uuid`, unshare keeps a foreign link, generate-summary starts no job —, a PATCH with a foreign source leaves the topic untouched, Quick Add de-duplication, BLOG 400, admin unrestricted, `POST /sources` 403 for users).
- Final state: backend `mvn clean verify` **438 tests, 0 failures/errors, 3 skipped** (after the review fixes), SpotBugs clean · frontend `npx vitest run` **24 files / 120 tests** green (5 new files, 25 tests) · `tsc -b` clean · lint: the touched production files carry exactly their `v0.20.0` violations (backlog), new files clean · orval: additive only · planning audit `--current-version v0.20.0 --fail-on all`: passed.
- Manual check in the running app: handed to the PO (§4).

## 2. Planned vs. actual scope

| Planned | Actual |
| --- | --- |
| A–C visibility rule, scoped source and item endpoints | Done as planned |
| D topics accept visible `sourceIds` + `newSources`; `POST /sources` admin-only | Done; plus channel-ID normalization (plan P2) |
| E modal reset, Blog disabled | Done; plus corrected Quick Add guidance texts |
| F Sources page texts, empty states, i18n | Done; plus `SourceDetailPage` 404 handling (plan self-review) |
| G V37 indexes | Done (both indexes) |
| H backlog, spec sync | Done |
| Plan P4: 4 resource test classes aligned with QA rule 3 | Done; `Topic` named queries added for it |
| Plan P5: QA rule 3 "Design first" | Done |
| Plan P6: legacy `/ui/*` admin-only | Done |
| Phase 8 manual check in the running app | Done by the PO; plus the theme-aware *Enabled* toggle (PO finding) |

## 3. Time delta

Estimate 11.5 h for the whole task (incl. PR loop and YouTrack record); ~9.5 h up to the commit.

| Phase (2026-09-28, CET) | Actual |
| --- | --- |
| Brainstorm 09:16–10:00 (6 decisions + definition self-review) | 44 min |
| Planning 10:00–12:03 (P1–P6, two plan self-reviews) | 123 min |
| Implementation 12:03–12:34 (backend, tests, frontend, docs; suites < 10 min) | 31 min |
| Close-out documentation 12:34–12:36 | 2 min |
| Review round 1 12:36–12:44 (security + code, subagents) | 8 min |
| Review fixes 12:44–12:49 (incl. suites) | 5 min |
| **Up to the commit** | **~3 h 33 min** (vs ~9.5 h of the 11.5 h estimate before PR loop/YouTrack) |

## 4. Deviations

- **`ContentScope` is a final class**, not a record — a component `unrestricted` clashes with the factory `unrestricted()`, and a public canonical constructor would allow the fail-open `new ContentScope(null)`.
- **`Topic` named queries** added so `TopicApiResourceTest` can mock `Topic` (QA rule 3); a fourth resource overlaps POV-218.
- **Dead enrichment path removed** (`enrichContentSource`, `enrich(ContentSource)` in the strategy interface and both implementations).
- **One persistence test class** `ContentVisibilityQueriesTest` with a shared fixture instead of extending four entity tests; `getContentScope` tested with `@InjectSpy` in its own class (the existing `UserContextServiceTest` is DB-backed).
- **Legacy-UI tests**: `admin` role added in `@TestSecurity` only (planned).
- **Quick Add texts** corrected; `common.close` added.
- **Manual check done by the PO** (the agent cannot log in with Google; `postgres-dev` collides with the prod DB on port 5432 — backlog): an activated user OK; a pending user sees the demo topic (read-only) and two sources but no briefings — the dev DB has no demo briefings (none are seeded), not a defect. The PO found the topic form's *Enabled* toggle ignoring the colour theme (white knob on a near-white track in the dark theme) — pre-existing, fixed here (PO) with theme tokens and a test.
- **Pre-commit review** (`/code-review high` on `git diff v0.20.0` incl. uncommitted files; `/security-review` — authorization changed), one round (PO: no further round after the fixes):
  - *Security* — 0 findings (the skill's built-in diff section showed another diff; the subagent applied its method to `git diff v0.20.0`). Residual notes: global per-item summary sharing (backlog), SSE to all users (POV-217).
  - *Code* — 8 verified; 6 fixed: an item's `topicName` could show **another user's topic name** (mapper now takes the `ContentScope`; briefings use the owner's scope); Quick Add could add an edited identifier with the previous check result; a YouTube outage was cached for an hour as "not found" (now 503 `SOURCE_CHECK_UNAVAILABLE`, not cached); a legacy row stored as typed was duplicated (the entered identifier is looked up first); `newSources: [null]` → 500 (now 400); dead `ContentSourceMapper.toEntity` removed. 2 rejected: 500 on a concurrent insert of the same new channel (PO decision P2/P4a, javadoc clarified) and YouTube calls inside the topic transaction (accepted in P2).
- **Test infra**: the local test DB container was stopped (`docker start povzetkar-db-test`); the IDE's Java compiler writes into `backend/target` — after an IDE build with errors run Maven with `clean`.

## 5. Traceability

- **UC:** UC-1 (Manage Topic Sources), UC-7 (Real-time Source Validation), UC-8 (Share Individual Summary), UC-17 (Programmatic Read-Only API Access) — updated.
- **TC:** TC-SEC-008 (Per-User Source and Content Scoping, new, P0, automated), TC-SEC-002 (restated: admin sections incl. legacy `/ui/*`), TC-TS-002 and TC-TS-004 (automated and extended); TC-SEC-006 and TC-SEC-007 unchanged and still hold (DEMO keeps `SOURCE_VIEW`; the API key reads the mapped user's — now scoped — data).
- **US:** N/A — security fix task, no new User Story.
