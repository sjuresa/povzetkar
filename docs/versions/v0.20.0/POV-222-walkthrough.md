# POV-222 Walkthrough — Human-Like Serial Pacing of YouTube Calls, Bounded Per-Video Retries, Any-Language Transcripts

**Task:** [POV-222](https://jumis.myjetbrains.com/youtrack/issue/POV-222) (Task) · **Branch:** `POV-222` → `v0.20.0` ·
**Date:** 2026-10-02 · **Decision record:** [ADR-0009](../../adr/0009-multi-provider-transcript-strategy.md) (Update POV-222)

## 1. What was done

YouTube blocked the production IP for transcript requests (HTTP 429). The prod log showed up to 84 youtube-legacy
attempts a day, parallel fetches, only a 0.5–2.5 s jitter, and 19 videos without (English) captions retried every
hour for up to two days (136 failed attempts). The task changes how — and how often — the app asks YouTube.

**Serial, human-like pacing** (`service/transcript`):

- `YouTubeRequestPacer` (`@Startup @ApplicationScoped`): a fair `ReentrantLock` held for the **whole fetch**, so
  youtube-legacy fetches run strictly one after another across all topics, items and threads, and the calls of two
  fetches never interleave. Before a fetch: a random pause in `[fetch.min, fetch.max]` (15–45 s) measured from the
  **end** of the previous fetch — none for the first fetch or after idle; a failing fetch still records its end and
  releases the slot. `runInFetchSlot(deadline, fetch)` gives up with `TranscriptSkippedException` when the run's
  deadline has passed once the slot is obtained, or when the wait is interrupted. One INFO line per actual wait.
- `YoutubeHttpClient implements YoutubeClient` replaces the library's `DefaultYoutubeClient` (final,
  package-private, no timeouts) via `TranscriptApiFactory.createWithClient`: same requests and error messages, plus
  connect 10 s / request 30 s timeouts. A paced instance belongs to one fetch and waits 1–5 s (`in-fetch` range)
  before each call but the first (video page → innertube → captions).
- `PauseRange` (record: validation + random draw), `YouTubeLegacyPacingConfig` (`@ConfigMapping`, 4 keys; both
  ranges validated at startup by the pacer). The old `jitter.*` keys and `applyJitter` are gone.
- The admin's **Fetch transcript** is a complete exception: `TranscriptProvider.fetchManual` (default = `fetch`),
  overridden by youtube-legacy with an unpaced client; `TranscriptOrchestrator.fetchManual`;
  `ContentItemService.fetchTranscript` uses it and no longer changes the video's status on failure.

**Any-language captions:** youtube-legacy selects English manual → English generated → any manual → any generated
(no `translate()`, an extra call) and logs language + generated flag; `video_summary_prompt.txt` asks Gemini to
write the summary in English.

**Bounded retries per video:**

- Flyway V38: `content_items.transcript_attempts`, `next_transcript_attempt_at` (nullable), fields on `YouTubeVideo`.
- `TranscriptRetryPolicy` (`transcript.retry.intervals=30m,30m,90m,90m,210m,210m`): after the n-th failed automatic
  attempt `RETRY_LATER` at failure + interval n; the 7th failure → `FAILED`.
- `ProcessingContext` (mode AUTOMATIC/MANUAL, run start, deadline, fetch and skipped counters) is created at every
  orchestration entry point (scheduled run — one for both dates; manual run; per-topic job) and passed down through
  `TopicProcessor` / `JustInTimeSummarizationScheduler.schedule(items, date, context)` / `SummarizationWorker` /
  `SummarizationStrategy` / `ContentCreationService` / `TranscriptOrchestrator.fetch(videoId, context)` to the
  provider (`TranscriptProvider.fetch(videoId, context)`, default = `fetch(videoId)`). Per-item admin jobs use
  `ProcessingContext.manualItem()`. The old overloads without a context are removed.
- `YouTubeSummarizationStrategy`: automatic transcript failure → counter, policy, status/error/next time and audit
  in one `requiringNew` transaction; manual failure → audit and log only, nothing changes.
- The pending/retry selections moved into named queries on `ContentItem` (`findDueInEnabledTopics`,
  `listDueInTopic`) with the due condition `nextTranscriptAttemptAt is null or <= now`.

**Time budget per run** (`povzetkar.orchestration.time-budget=15m`): the scheduler skips items that have not
started once the deadline has passed; the pacer skips fetches still waiting for the slot. A skipped item is left
unchanged (no retry counted), the skip passes through orchestrator (no failure metric, no fall-through) and
`ContentCreationService` to the strategy. Each run logs one `action="orchestrationRunSummary"` line (trigger,
`duration_s`, `youtubeFetches`, `skippedItems`, `budgetExhausted`) after its summarization.

**Documentation:** RUNBOOK §14.1a (keys, behaviour per trigger, retry state, status-override note) and §13.5 (log
check for the pacing); ADR-0009 "Update (POV-222)"; `application.properties` comments; UC-14 updated (stale
`FAILED`/`ALL_PROVIDERS_EXHAUSTED` step); TC-ING-008…011; backlog (obsolete `JitterDelay` entry removed, two
entries added); CHANGELOG.

**Verification**

- Red first, then green: `YouTubeRequestPacerTest` 13/13 red (stub) → green; `YoutubeHttpClientTest` 7/7 red → green
  (local `HttpServer`, no YouTube); `YouTubeLegacyTranscriptProviderTest` language cases 8/13 red → green;
  `TranscriptRetryPolicyTest` 10/10 red → green; `YouTubeSummarizationStrategyTest` 3 red (counter, `FAILED`,
  manual unchanged) → green; `TranscriptRetryStatePersistenceTest` (V38 round trip, due selection incl. a plain
  `ContentItem` `RETRY_LATER` row) red at compile → green; `OrchestrationManagerTest` not-due video scheduled → red →
  green; `ContentItemServiceTest` (new class; manual path, failure leaves the video unchanged) red → green. Phase 4
  red was at compile level (new signatures); the scheduler's deadline gate was demonstrated afterwards (condition
  disabled → 2 of 4 tests fail, restored → green).
- Full suite 2026-10-02: backend `clean test -pl backend -am` 619 tests, 0 failures, 3 skipped (frontend untouched);
  planning audit (`validate-planning.py --current-version 0.20.0 --fail-on all`) passed. After the review round 1
  fixes: 626 tests, 0 failures, 3 skipped; after round 2: 631 tests, 0 failures, 3 skipped; after round 3 (final): 637 tests,
  0 failures, 3 skipped; planning audit passed again.
- Prod check after the deploy (Phase 7): V38 applied; pacing lines and serial youtube-legacy timestamps in the log;
  the run summary line; a failing video gets `next_transcript_attempt_at` and is skipped while not due; a
  non-English video summarised in English.

## 2. Planned vs. actual scope

- Delivered as planned: Phases 0–5 (branch, pacing, any-language selection, bounded retries, time budget and run
  log, documentation); Phase 6 close-out; Phase 7 follows the merge.
- Added: `ContentItemServiceTest` (the plan assumed an existing test); a wiring test in `OrchestrationManagerTest`
  and the pending path in `TopicProcessorTest`; UC-14 correction; the run summary also for manual and per-topic runs.
- Design details settled in execution (task.md `## Deviations`): unchecked `TranscriptSkippedException`; context via
  default methods on `TranscriptProvider` (proxy/Supadata untouched); `ProcessingContext` built once in its final
  shape in Phase 3; scheduler parameters grouped in a `Batch` record.

## 3. Time delta

Estimate ~9 h for the whole task (incl. PR loop, deploy and YouTrack record).

| Phase (2026-10-02, CET) | Actual |
| --- | --- |
| Brainstorm 09:50–10:25 (Q1–Q8, task definition approved) | 35 min |
| Planning 10:25–11:27 (P1–P6, two review rounds, plan approved) | 62 min |
| Phase 0 and session hand-over 11:27–11:33 | 6 min |
| Implementation 11:33–12:08 (Phases 1–5) | 35 min |
| Full suite, close-out documents, pre-commit review 12:08– | see the Time Log |

## 4. Deviations

See `agent_logs/POV-222 … task.md` `## Deviations` for the full list; the main ones:

- Plan changes accepted before execution: retry intervals 0.5/0.5/1.5/1.5/3.5/3.5 h (7 attempts, no tolerance);
  whole-fetch slot instead of a per-call lock; 15–45 s; manual capture fully exempt; time budget 15 min per run;
  one `ProcessingContext`.
- Implicit subtype attribute in HQL (`ci.nextTranscriptAttemptAt` on a `ContentItem` path) — Hibernate 7.4 resolves
  it on the single table; the DB test covers a plain `ContentItem` row.
- Known metric nuance: a skipped fetch increments `transcript.fetch.attempts` only (backlog).
- **Pre-commit review round 1** (`/code-review high` in a subagent, on `git diff v0.20.0` incl. uncommitted and
  intent-to-add files — 51 files): 8 findings + 1 nit, all low/low-medium, all fixed (red → green each):
  (1) a successful manual *Fetch transcript* kept `next_transcript_attempt_at`, delaying the summary up to 3.5 h —
  now cleared (counter kept); (2) offset paging of the pending/retry selection skipped items once processed or failed
  items left the result — keyset paging (`ci.id > :afterId order by ci.id`); (3) `max-worker-execute-time=600s` below
  the 15 min budget → 1200s; (4) a fetch could start up to 45 s after the deadline — deadline re-checked after the
  pause; (5) after the deadline a failing youtube-legacy fell through to the proxy (its own 15–45 s jitter) — the
  orchestrator checks the deadline before each provider, before the attempts metric; (6) an interrupt during
  `http.send` counted as a failed attempt — skipped for paced clients; (7) only the exact code `en` counted as English
  (`en-GB` manual lost to generated `en`) — ranking over the whole list with `en-*`; (8) retry intervals and time
  budget were validated only at first use — `@Startup` on `TranscriptRetryPolicy` and `OrchestrationManager`,
  budget must be positive. Nit: unused `ProcessingContext.mode()` removed. 3 candidates dropped by the reviewer
  (documented status-override behaviour, Gemini cooldown timing, post-deadline cheap work).
- **Pre-commit review round 2** (whole diff, round-1 fixes confirmed correct): 5 findings + 2 nits.
  (1, medium) a `FAILED` video stays `FAILED` after a successful manual capture while the RUNBOOK promised the next
  run would summarize it — **RUNBOOK corrected** (only `RETRY_LATER`; `FAILED` → per-item *Generate summary* or
  override); turning a manual success into a status change was not done (manual actions do not change the status —
  PO's call if wanted). (2) the library lists caption tracks in `HashMap` order, so ties were arbitrary — fixed:
  exact `en` → spoken language (the generated track's) → language code, 3 tests red → green. (3) lock timeout
  `PT20M` equalled the expected run length — `PT30M`. (4) RUNBOOK's "a fetch never starts after the budget" ignored
  the proxy's own jitter — wording limited to youtube-legacy. (5) runtime errors of the library (NPE on an unexpected
  response) escaped the retry policy and the fall-through — mapped to `ProviderUnavailableException` (skip passed
  on), red → green. Nits: the paced `fetch(String)` documented (required by the interface); the RUNBOOK pacing log
  check restricted to automatic youtube-legacy fetches.
- **Pre-commit review round 3** (whole diff; rounds 1–2 confirmed): 6 findings, all fixed (red → green):
  (1, medium) `HttpRequest.timeout` covers only the response headers on JDK < 26 — a stalled body would hold the
  pacer slot for ever and block every automatic fetch; the whole exchange is now bounded (`sendAsync(...).get(30 s)`,
  cancelled on timeout). (2) a transcript stored by a per-item job whose Gemini call then failed kept the retry due
  time — cleared in `persistTranscript`. (3) a per-topic job overlapping the hourly run could count one failure
  window twice — not counted when the due time is already in the future. (4) the pacer slept the full pause even
  when the fetch would start after the deadline — skipped at once. (5) the run summary reported `budgetExhausted`
  for skips caused by a shutdown — now only when the deadline has passed (`budgetExhausted(now)`). (6) the RUNBOOK
  grep missed the `YouTube transcript selected` lines. The PO stopped further review rounds after round 3.

## 5. Traceability

- **US:** US-17.5, US-UI-006
- **UC:** UC-14 (updated)
- **TC:** TC-ING-008, TC-ING-009, TC-ING-010, TC-ING-011
