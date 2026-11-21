# POV-235 Walkthrough — Production Log Review: Status Override 500, Log Noise, Videos without a Duration

**Task:** POV-235 (Bug, Normal) · **Branch:** `POV-235` → `v0.21.0` · **Date:** 2026-11-21

## 1. What was done

A review of the production log (27 Sep – 21 Nov, 477 ERROR lines) found one real defect behind a lot of noise. The
Brainstorm re-derived the issue from the code, the log and the database; the task fixes three things.

- **A — status override 500.** `POST /api/v1/content-items/{id}/commands/status` failed for every real status change
  although the change was saved. `ContentItemAdminService.updateStatus` returns `em.merge(item)`; for the detached
  item the resource passed in, that is a new instance whose `contentSource.topics` is not loaded, and the resource
  mapped it to the DTO after the commit (`LazyInitializationException`). The issue's hypothesis — the query does not
  fetch the topics — was wrong: it does. Fix: `@Transactional` on `ContentItemApiResource.updateItemStatus`, so load,
  change and mapping share one persistence context (the pattern of `shareSummary` in the same class); the service is
  unchanged. `EntityManager.merge` is used nowhere else in the backend.
- **C — videos without a duration.** YouTube reports no duration, or `P0D`, while a video is live or upcoming.
  `YouTubeDataApiClient.fetchVideoDurations` threw `NullPointerException: text` on a missing one, which aborted the
  whole channel's fetch for that run (one channel blocked for ~4 h on 18 Nov); `YouTubeContentFetcherService` gave
  every video without a duration `Duration.ZERO`, so it was stored as `SKIPPED_TOO_SHORT` — final. The production DB
  held 177 such videos, mostly live streams and premieres (the item of Part A among them). Now the client returns only
  known, positive durations and the fetcher leaves a video without one out of that run (one INFO line); a later run
  within the fetch window (yesterday, today) stores it once it has a duration.
- **B — log levels.**
  - A video without a transcript produced a provider WARN with a ~35-line stack trace, an orchestrator WARN over two
    lines and an ERROR "All transcript providers failed" — 266 ERROR lines in the period. Now one WARN per video:
    `No transcript for video ID 'X': [youtube-legacy] Transcripts are disabled for this video.` The reason is the
    library's own `Reason:` line (`TranscriptFailureReason`), carried by `ProviderUnavailableException.reason()`;
    the per-provider line is DEBUG; the legacy provider logs an expected retrieval failure only at DEBUG (unexpected
    responses and invalid IDs keep their WARN with stack trace); the quota WARN and the metrics are unchanged. (PR
    loop:) the callers' follow-up lines no longer repeat it — `ContentCreationService` logs the empty result at DEBUG,
    `YouTubeSummarizationStrategy` logs a scheduled retry as INFO and only the final `FAILED` as WARN.
  - Parallel topics fetch a channel that is in two topics twice; the second insert loses on
    `content_items_source_url_key` (6 ERROR lines, all one channel). `TopicProcessor` logs that case (and the same
    race on `uc_video_id`) as INFO — the losing batch rolls back, the first fetch stored its videos, and anything
    only the losing batch had is fetched by the next run — and every other fetch failure as ERROR, as before. The
    legacy provider's retrieval failure keeps its stack trace at DEBUG (review round 1).
- **D — two pre-existing defects the review found (round 1), included by the PO.** (D1) `fetchVideoDurations` sent
  all of a date's video IDs in one `videos.list` call; the API accepts at most 50, so a channel with more than 50
  uploads on one date failed its fetch — now batches of 50, merged. (D2) The status override with `{}` reached
  `Objects.requireNonNull` in the service → 500; now `@NotNull` on `StatusUpdateDTO.status` and `@Valid @NotNull` on
  the parameter → 400 (the pattern of `ContentSourceResource`). The OpenAPI schema already marked `status` required;
  the generated client is unchanged.
- **Not changed, by decision:** the Gemini 503 cascade (87 + 31 ERROR) and the network failures (72 ERROR, 64 in one
  outage) — backlog; an intermittent admin-history failure ("Session/EntityManager is closed") — POV-236.
- **Documentation:** CHANGELOG; backlog — fetch each channel once per run (PO question on many users and API quota),
  the Gemini 503 cascade, the fetch window for late durations; UC-16 step 6; TC-ADM-002 automated, TC-ING-012 new;
  two lessons learned (`merge()` instance; "unknown" vs. zero duration). New test helper `test/util/LogCapture`.

**Verification:**

- RED before each fix: A — 500 with the production `LazyInitializationException`
  (`ContentItemStatusOverrideIntegrationTest`, DB-backed, request outside a test transaction); D1 — one `setId` call
  for 120 IDs, D2 — 500 with `New Status cannot be null` (`YouTubeDataApiClientTest`, `ContentItemApiResourceTest`); C —
  `NullPointerException: text` (`YouTubeDataApiClientTest`) and a zero-duration video returned
  (`YouTubeContentFetcherServiceTest`); B — the ERROR line (`TranscriptOrchestratorTest`), the provider WARN
  (`YouTubeLegacyTranscriptProviderTest`), the ERROR for the race (`TopicProcessorTest`). All green after the fixes.
- Backend `mvnw clean verify`: exit 0, 661 tests, 0 failures or errors, 3 skipped; SpotBugs 0. Frontend untouched
  (the endpoint's response contract is unchanged).
- Planning audit (`validate-planning.py --current-version 0.21.0 --fail-on all`): passed.

## 2. Planned vs. actual scope

| Planned | Actual |
| --- | --- |
| Issue: Part A cause = topics not fetched | Cause = `merge()` returns an instance without loaded topics (Brainstorm) |
| Issue: Part B = 425 transcript-path ERRORs in three classes | 266 transcript ERRORs; the other 159 were Gemini and network failures — backlog (Brainstorm #1) |
| Not in the issue | Admin-history failure split off as POV-236 (Brainstorm #2) |
| Issue: `NullPointerException: text` out of scope | Part C (Brainstorm #3) |
| Part C: `null` duration | Also `P0D` and videos not returned — 177 stored as too short (planning, PO) |
| Not in the issue | Repair of the stored zero-duration videos after the deploy: real duration; ≥ 150 s → `PENDING` (planning, PO) |
| Issue: Done-when "a later log review shows ERROR only for real defects" | Tests pin the levels; one manual log check after the deploy as evidence (Brainstorm #4) |
| Not in the plan | D1 (> 50 IDs per `videos.list`) and D2 (status override without a status → 500), found by review round 1, included (PO) |
| Orchestrator strips the message prefix | `ProviderUnavailableException.reason()` (§4) |

## 3. Time delta

Estimate 4 h 30 min (comparable: POV-216 3 h 45 min, POV-221 5 h 11 min, POV-219 5 h 8 min — booked).

| Phase (2026-11-21, CET) | Actual |
| --- | --- |
| Brainstorm 09:05–09:27 | 22 min |
| Planning 09:27–10:08 | 41 min |
| Implementation 10:08–10:19 | 11 min |
| Close-out documents 10:19–10:26 | 7 min |
| Pre-commit review round 1 10:26–10:34 | 8 min |
| Review fixes 10:34–10:38 | 4 min |
| PO wait and decision on D1/D2 10:38–~10:45 (start not read) | ~7 min |
| D1/D2 implementation 10:4x–10:54 | ~9 min |
| Pre-commit review round 2 10:54–11:03 | 9 min |
| Review fixes 11:03–11:06 | 3 min |
| **Up to the commit proposal** (09:05–11:07) | **2 h 2 min** — the estimate (4 h 30 min) covers the whole task incl. PR loop, deploy, data repair and record; the comparison is in the booked time |

## 4. Deviations

- **Production log not read at first:** the agent said it had no log extract and could not verify the issue's figures;
  the production log is readable per the private operations runbook (PO). Reading it changed the scope
  (§2).
- **Reason on the exception, not parsed from its message** (plan B1): `ProviderUnavailableException` carries a
  one-line `reason()`; its `Throwable` constructor derives it with `TranscriptFailureReason.of`. No parsing of our own
  message, and the exception message is one line for every provider. Log format
  `No transcript for video ID 'X': [provider] reason …` instead of the planned `; `-joined form, which read
  "video.; supadata".
- **Part A test JSON paths:** the first green run failed on `id` instead of `data.id` (the API's `data` envelope); the
  RED run had failed on the status (500) before any body assertion.
- **Stale incremental build:** one test compile failed on unrelated classes ("class file … not found"); a `clean`
  build fixed it.
- **Self-review of the close-out:** the CHANGELOG and the retrospective first stated the data repair as done (it runs
  after the deploy); a stray CHANGELOG heading; `ProviderUnavailableException`'s one-line reason held for one
  constructor only. Fixed.
- **Pre-commit review, round 1** (subagent, `/code-review high --max-findings all` on `git diff v0.21.0`): 6 verified
  findings — 0 class A, 2 B, 4 C; all fixed. B: the legacy provider's retrieval failure lost its stack trace (now at
  DEBUG; the network cause of "Request to YouTube failed." stays reachable); the "later run" for a video without a
  duration holds only within the fetch window — Javadoc, CHANGELOG and the backlog entry say so, and that such a video
  now leaves no row. C: "nothing lost" for the race was too strong (the losing batch rolls back; the next run fetches
  the rest); "real failures stay errors" did not cover transcript outages (now warnings); the race check also accepts
  `uc_video_id` (with a test); three wording/public-content fixes in this walkthrough and the retrospective. Two
  pre-existing defects found by the review went to the backlog (more than 50 IDs per `videos.list`; status override
  without a status → 500) — the PO included both (D1, D2); two findings rejected after verification (a speculative in-batch duplicate; the PO's README
  change). Suite re-run green; planning audit passed.
- **Pre-commit review, round 2** (PO confirmed; whole task diff): 7 verified findings, all class C; 7 rejected.
  Fixed: a duration java.time cannot parse (e.g. `P1W`) is treated as unknown instead of aborting the channel's
  fetch (pre-existing; test added with the fix, RED not demonstrated); wording of the legacy provider's DEBUG line here
  and in its test (which now asserts nothing at INFO or above); the `uc_video_id` test asserts the INFO line; TC-ADM-002
  says the dropdown stays a manual check. Deliberately not changed: matching the constraint name relies on PostgreSQL's
  English error text (the production log shows it extracted; the post-deploy log check is the evidence); a provider
  failure followed by another provider's success is visible only at DEBUG and in the failures counter (consequence of
  the approved per-provider DEBUG); no `@APIResponse(400)` on the status override (it would change the generated
  client's response union for a documentation-only gain). Suite re-run green (661 tests); planning audit passed.

## 5. Traceability

- **US:** N/A — bug fixes and logging, no new User Story (UC-3 relates to US-UI-006, UC-16 to US-ING-004).
- **UC:** UC-3 (Review and Override Content Status) — restored, unchanged; UC-16 (Scheduled Orchestration & Periodic
  Content Fetch) — step 6 added (video without a known duration deferred).
- **TC:** TC-ADM-002 (Status Override) — automated (`ContentItemStatusOverrideIntegrationTest`); TC-ING-012 (Video
  Without a Duration Is Deferred, new, P1, automated). Logging levels (Part B): N/A — no functional behaviour.
