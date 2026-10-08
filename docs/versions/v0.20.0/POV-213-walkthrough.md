# POV-213 Walkthrough — Scheduler-Fired Integration Test for the Scheduled Run; Daytime Cron for Retention and Enrichment

**Task:** [POV-213](https://jumis.myjetbrains.com/youtrack/issue/POV-213) (Task) · **Branch:** `POV-213` → `v0.20.0` ·
**Date:** 2026-10-07

## 1. What was done

The `@Scheduled` orchestration bug of POV-197 (`BlockingOperationNotAllowedException` on every run) was invisible to
the suite: every test called `runScheduledOrchestration()` directly from the JUnit thread, while the defect lives in
the thread the scheduler picks. TC-ING-006 (P0) was still Manual with a time-bound `validate:skip until v0.21.0`.

**Scheduler control:**

- `OrchestrationManager` is **unchanged**: the planned `identity` and `concurrentExecution = SKIP` were removed after
  the pre-commit reviews (§5, QA rule 4) — the test finds the job by its method (`Trigger.getMethodDescription()`).
- `application.properties`: `%test.quarkus.scheduler.start-mode=halted` — no job fires on its own in any
  `@QuarkusTest` (before, the hourly orchestration, retention, enrichment and the `every = "24h"` idempotency cleanup
  could fire against the test DB during a suite); `%test.povzetkar.orchestrator.cron=0/5 * * * * ?`.

**`ScheduledOrchestrationIntegrationTest`** (TC-ING-006): real `OrchestrationLockService`, transactions, test DB
and scheduler; YouTube fetcher, `TranscriptOrchestrator` and `VideoSummaryService` mocked as in
`OrchestrationIntegrationTest`; test data committed via `QuarkusTransaction.requiringNew()` (no `@Transactional`
test).

- Firing one run: every other job paused by id, then `scheduler.resume()`; the job's `getPreviousFireTime()` is
  polled every 10 ms and the scheduler paused as soon as it is after the resume. The first firing after `resume()` is a catch-up at the
  scheduler's next 1-s check, so the test resumes just after a 5-s boundary of the `%test` cron (`0/5`, widened from
  1 s in the PR review) — at least ~3 s until the next firing; asserted: no second firing, exactly one finished
  execution. The end of the run = a test bean has observed `SuccessfulExecution`/`FailedExecution` for the job with
  a fire time after the resume. Teardown pauses, resumes the other jobs by id, waits for the finished event of the
  last firing, then for gauge `povzetkar_active_orchestrations_current` = 0 (the gauge alone misses a queued run).
- Run 1: both videos (yesterday, today) `COMPLETED`, one `Summary` dated each day, both days fetched, yesterday's
  briefing exists, none for today, `povzetkar_orchestration_failed_total{trigger=scheduled,topic_id=all}` unchanged,
  no `OrchestrationLock` row.
- Run 2 (nothing new): the fetcher was called again for both days (the run really ran), still exactly one briefing,
  not regenerated (`createBriefingFromSummaries` once in total), counter unchanged, no lock row.
- Midnight guard: only if the clock crossed midnight during a run is the test aborted (assumption); unexpected fetched
  dates fail an assertion.
- `allScheduledJobsHaveASchedule`: exactly the four expected jobs exist (by method) and each has a next fire time —
  the `${…}` crons parse (halted mode still creates the triggers), without asserting their values.

**Daytime cron (production):** the host is off at night and missed runs are not caught up (prod log 09-27 → 10-07:
retention 1×, enrichment 0×). `povzetkar.retention.cron=0 0 10 * * ?` (new in `application.properties`; the
annotation default `0 0 3 * * ?` removed, Javadoc aligned), `povzetkar.enrichment.cron=0 0 10 ? * MON`. The crons run
in the JVM zone — UTC in the prod container (verified in the Brainstorm against the log).

**Documentation:** `test_specification.md` TC-ING-006 → `Integration (ScheduledOrchestrationIntegrationTest)`, skip
removed, revision 2.16; `testing.md` §2.6 "Scheduled Jobs in Tests"; RUNBOOK §9.1.2 (enrichment Monday 10:00 UTC,
no rationale — PO) and §9.2.1 (stale orchestration default `0 0 0 * * ?` → actual `0 27 * * * ?`); backlog: scheduler
test entry done, new entries "Guard against `Uni`/`CompletionStage`-returning `@Scheduled` methods" and "Overlapping
orchestration runs race on the briefing and the lock"; QA rule 4 in `quality-mandates.md`; lessons-learned; CHANGELOG.

## 2. Verification

- **Phase 0 exploration** (throw-away `@QuarkusTest`, deleted): `resume("orchestration")` while halted does not fire
  the job (`isRunning() = false`); other jobs paused by id + `resume()` → only the orchestration fires (on
  `executor-thread-1`), the overdue `every = "24h"` cleanup stays silent; `@Observes SuccessfulExecution` is delivered
  synchronously on the executing thread after the method; halted mode creates all four triggers with a next fire
  time.
- **Green first** (to prove the test itself works): 2/2, two scheduled runs on `executor-thread-1`/`-2`.
- **Red with the POV-197 defect reinstated** (`runScheduledOrchestration` temporarily `Uni<Void>`, body unchanged,
  `return Uni.createFrom().voidItem()`; never committed): the run executed on `vert.x-eventloop-thread-4`, all three
  steps logged `BlockingOperationNotAllowedException`, and the test failed after ~2 s on an assertion —
  `expected: <2> but was: <0>` (content items) — not on the timeout:

  ```text
  INFO  [OrchestrationManager] (vert.x-eventloop-thread-4) action="orchestrationScheduled" trigger="scheduled" …
  ERROR [OrchestrationManager] (vert.x-eventloop-thread-4) … message="Yesterday's summarization step failed":
        io.quarkus.runtime.BlockingOperationNotAllowedException: You have attempted to perform a blocking operation on a IO thread. …
  [ERROR] ScheduledOrchestrationIntegrationTest.scheduledRun_fetchesAndSummarizesYesterdayAndToday_andGeneratesYesterdaysBriefingIdempotently:159
          QuarkusTransaction org.opentest4j.AssertionFailedError: expected: <2> but was: <0>
  ```

- **Reverted → green** together with `OrchestrationIntegrationTest`, `OrchestrationManagerTest` and
  `QuarkusConfigKeysTest` (covers the new `%test.quarkus.scheduler.start-mode`): 28 tests, 0 failures.
- **Full suite** 2026-10-07 (`mvnw verify -pl backend -am`; frontend untouched): 645 tests, 0 failures, 3 skipped.
  Only the new test's two runs came from the scheduler (`executor-thread-*`); the other six "Scheduled orchestration
  triggered" lines are `OrchestrationManagerTest`'s direct calls (`main`); no other job fired during the suite.
- **Planning audit** `validate-planning.py --current-version v0.20.0 --fail-on all`: passed without the skip.
- **After the review round 1 fixes** (`SKIP` removed, wait = finished event + no run in flight): the test class 10×
  in a row green; red run repeated with the defect reinstated (event loop, `expected: <2> but was: <0>`), reverted;
  full suite 645 tests, 0 failures, 3 skipped; planning audit passed.
- **After the review round 2 fixes** (exactly one firing, job found by method, `OrchestrationManager` unchanged):
  test class 20× in a row green, no `ContextNotActiveException`; red run repeated (event loop, `expected: <2> but
  was: <0>`), reverted; full suite 645 tests, 0 failures, 3 skipped, no `ContextNotActiveException`; planning audit
  passed.

## 3. Planned vs. actual scope

- Delivered: Phases 0–4. Variant B of the plan (pause the other jobs by id, global `pause()`/`resume()`), as decided
  by Phase 0; Phase 1's production part (identity, `SKIP`) dropped in the reviews.
- Changed in execution (task.md `## Deviations`): Phase 1 applied before Phase 0 (the exploration needs it); one
  fetcher answer per run instead of two `eq(date)` stubs; revision row 2.16 instead of the plan's 2.11;
  **`concurrentExecution = SKIP` and the job `identity` dropped** — no production change to the job (§5); exactly one
  firing via `getPreviousFireTime()` polling instead of a pausing fetcher stub; new QA rule 4 in
  `quality-mandates.md` (PO).
- Not done (out of scope, per definition): scheduler-fired tests for the other three jobs; deploy and prod-log
  verification of the new cron times (PO: only the time change).

## 4. Time delta

Estimate ~5 h for the whole task (incl. close-out, PR loop and YouTrack record).

| Phase (2026-10-07, CET) | Actual |
| --- | --- |
| Brainstorm 20:06–20:30 (Q1–Q6, definition approved) | 24 min |
| Planning 20:30–20:51 (P1–P3, self-review, `/code-review` of the plan, plan approved) | 21 min |
| Implementation 20:53–21:03 (Phases 0–4, red/green) | 10 min |
| Full suite, close-out documents, pre-commit review 21:03– | see the Time Log |

## 5. Deviations

See `agent_logs/POV-213 … task.md` `## Deviations` for the full list; the main ones are in §3. Gotcha found in
Phase 0: a field read on an `@ApplicationScoped` bean's client proxy returns the proxy's own (empty) field — the
first two exploration runs showed no events for this reason; the observer bean exposes its state through methods
(`testing.md` §2.6).

**Pre-commit review round 1** (`/code-review high` in a subagent on `git diff v0.20.0`, 13 files): 3 findings.

1. *Medium, production* — `concurrentExecution = SKIP` (added in the plan to remove a test race) with
   `.await().indefinitely()` per step: one scheduled run that never completes (Gemini client without an explicit
   timeout) would block every later scheduled run until restart; without `SKIP` the next hourly run takes over the
   stale date lock after 30 min. The PO asked whether `SKIP` was there only for the test — essentially yes (its
   production argument, a run longer than an hour, is already covered by the per-date lock and the 15-min budget).
   **Fixed (PO: option D):** `SKIP` removed; the test waits for the finished event and gauge 0 (refined in round 2). The
   lesson became QA rule 4 (justify changes to existing behaviour; production changes for tests last).
2. *Low* — the test paused the other jobs by id and never resumed them (contradicting its own §2.6 rule) — fixed in
   teardown.
3. *Nit* — redundant `Mockito.reset` on `@InjectMock` beans and a try/catch around `verify` — removed.

**Flaky run found while fixing 1:** the first fix also counted `StartedExecution` events to detect runs in flight.
2 of 13 repeated runs failed: the test's second scheduled run hit `ContextNotActiveException` in its first Panache
query (`processPendingAndRetryItemsByPage`). The prod log copy (64 scheduled runs to 2026-10-02) has no such error.
Without the `StartedExecution` observer (gauge instead): 10 of 10 green, no `ContextNotActiveException`. The
event is fired on the executing thread right before the method is invoked (`StatusEmitterInvoker`, bytecode). Round 2
pointed out that 2/13 vs. 0/10 does not tell the variants apart (Fisher ≈ 31 %) — the exception may be a real,
intermittent defect; see the base rate below.

**Pre-commit review round 2** (whole `git diff v0.20.0`, 14 files): 5 findings, all fixed.

1. *Low–medium* — a second run fired before the stub's pause does **not** skip both dates: the lock is per date, so
   it skips yesterday, processes today, and both runs generate yesterday's briefing (check-then-insert, unique
   constraint → `BriefingGenerationException` → failure counter). The test's comment and §2.6 claimed otherwise.
   Needs > 1 s from fire to the first fetch — unlikely, possible on a cold or slow machine.
2. *Low* — the gauge only sees a run inside the method; a run fired but still queued is invisible (the plan had
   rejected the gauge as a "run finished" signal for this reason).
   **Fix for 1+2:** the stub no longer pauses; the test polls the job's `getPreviousFireTime()` every 10 ms after
   `resume()` and pauses as soon as it is after the resume, then waits for the finished event and asserts exactly
   one finished execution. Comments and §2.6 corrected.
3. *Low* — §2.6 told agents to add `@Scheduled(identity = …)` — a production change for a test, contradicting the
   same section and QA rule 4, although a test-side way exists. **Fixed:** identity removed, the test finds the job
   by `Trigger.getMethodDescription()`; `OrchestrationManager` is now unchanged.
4. *Low* — the "do not observe `StartedExecution`" rule rested on statistically weak evidence. **Fixed:** §2.6 states
   it as an observation (cause unknown) and asks to treat a `ContextNotActiveException` in a scheduled job as a
   possible defect; base rate measured: without the observer 0 of 30 runs (10 + 20) failed or logged it, vs. 2 of 13 with it (Fisher ≈ 9 %) — suggestive, still not proof.
5. *Nit* — `allScheduledJobsHaveASchedule` did not check that all four jobs exist — now asserts the set of methods.

Dropped by the reviewer as pre-existing, not touched by this diff: the briefing check-then-insert race and the
`acquireLock` race on a fresh date between overlapping manual and scheduled runs (backlog).

**Pre-commit review round 3** (whole `git diff v0.20.0`, 13 files; the reviewer checked the quarkus-scheduler 3.40.1
source: `getPreviousFireTime()` is set before dispatch and equals the execution's fire time, checks run every 1000 ms,
a cron trigger's first firing after a pause is not skipped — the 10 ms poll has ~990 ms headroom; *the headroom claim
was wrong, corrected in the PR review: the first firing after `resume()` is a catch-up at the next 1-s check, and
the next cron boundary can follow within ~1 s — the test now resumes just after a 5-s boundary of a `0/5` cron*): 6 findings, all
fixed.

1. *Low* — the midnight guard aborted whenever the fetched dates were not {yesterday, today}, so a regression fetching
   wrong dates would have been reported as skipped, not red. Now: abort only if the clock crossed midnight; the
   fetched dates are asserted.
2. *Low* — `Briefing.count() == 1` cannot detect a briefing regenerated on every run (the generator updates the row).
   Now: `createBriefingFromSummaries` called exactly once over both runs.
3. *Low* — teardown still trusted gauge 0 (round 2 finding 2). Now: waits for the finished event of the last firing,
   then the gauge; §2.6 reworded.
4. *Nit* — §2.6 "set a `%test` cron" is not executable for the literal `every = "24h"` job — scoped to property crons,
   one sentence on `every` jobs.
5. *Nit* — retrospective attributed the identity to the round-1 fix; it came from the plan — corrected.
6. *Nit* — walkthrough §1 omitted the new backlog entry — added.

After the round 3 fixes (2026-10-08): test class 10× in a row green; red run repeated (event loop, `expected: <2> but
was: <0>`), reverted; full suite 645 tests, 0 failures, 3 skipped, no `ContextNotActiveException`; planning audit
passed.

## 6. Traceability

- **US:** N/A — test automation of an existing behaviour and a schedule change; no user-facing requirement changes.
- **UC:** UC-16 (unchanged)
- **TC:** TC-ING-006
