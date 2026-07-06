# POV-197 Walkthrough — Scheduled Orchestration Fix & Periodic Content Fetch

## What Was Done

Diagnosed and fixed a structural bug that caused every scheduled orchestration cron invocation to crash with `BlockingOperationNotAllowedException`, then unified the two former scheduled methods into a single, correctly annotated method.

**Bug fix:** `runOrchestrationScheduled` and `runBriefingGenerationScheduled` both returned `Uni<Void>`. Quarkus Scheduler therefore invoked them on the Vert.x IO thread. On entry, both methods immediately called blocking DB operations synchronously (`OrchestrationLockService.acquireLock` via `QuarkusTransaction.requiringNew()`, `User.listAllEnabled()`, `Topic.findEnabledByUsers()`) — violating the IO thread contract. The bug existed from the very first commit; the scheduled cron had never successfully executed.

**New feature:** Replaced the two methods with a single `runScheduledOrchestration`, annotated `@Blocking` so Quarkus Scheduler invokes it on a worker thread. Each run:
1. `executeSummarizationStepForDate(yesterday, true, true, null)` — fetch + summarize yesterday
2. `executeSummarizationStepForDate(today, true, true, null)` — fetch + summarize today
3. `generateBriefingsForDate(yesterday, null)` — briefing for yesterday (idempotent)

Cron default: `0 0 * * * ?` (every hour on the hour), configurable via `povzetkar.orchestrator.cron`. The separate `povzetkar.briefing.cron` property was removed.

## Planned vs. Actual Scope

Core scope matched the plan. Three unplanned discoveries emerged during execution (see Deviations below): `@Blocking` being ignored for `Uni<Void>` return types, `SKIPPED_TOO_SHORT` videos reaching the summarization scheduler, and the Vert.x worker thread timeout warning. All three were fixed and included in POV-197 scope.

## Time Delta

| Phase | Estimate | Actual |
|---|---|---|
| Brainstorm | — | 22:14–22:46 CET (32 min, including live bug reproduction) |
| Implementation | ~65 min | ~15 min (changes were surgical — 3 files, minimal lines) |
| Full suite verify | 15 min | ~10 min |
| Docs | 20 min | ~20 min |
| **Total** | **~85 min** | **~77 min** |

## Deviations

- **`@Blocking` + `Uni<Void>` does not work in Quarkus Scheduler**: Plan assumed `@Blocking` would redirect a `Uni<Void>`-returning `@Scheduled` method to a worker thread. In practice, Quarkus Scheduler 3.36 ignores `@Blocking` when the return type is `Uni<Void>` — it still invokes on the IO thread, causing the same crash. Fix: changed `runScheduledOrchestration` to return `void` and added `.await().indefinitely()` at the end of the pipeline. With `void` return type, `@Blocking` is correctly honoured and the method runs on a worker thread.
- **`@Blocking` + `Uni<Void>` does not work in Quarkus Scheduler 3.36**: `@Blocking` is ignored when return type is `Uni<Void>`. Fix: `void` return type + `@Blocking` + `.await().indefinitely()`.
- **`SKIPPED_TOO_SHORT` videos were being summarized (unplanned discovery, checklist 2.14)**: First live orchestration run revealed that `TopicProcessor` passed all fetched items to `JustInTimeSummarizationScheduler` without filtering by `processingStatus`. Short videos marked `SKIPPED_TOO_SHORT` by `YouTubeFetchingStrategy` were being summarized anyway. Fixed in `TopicProcessor` with a one-line filter; test added to `TopicProcessorTest`. Included in POV-197 scope per decision.
- **`quarkus.vertx.max-worker-execute-time=600S` (unplanned discovery)**: First live run produced `Thread blocked` WARN after 60 s (Vert.x default). Added 10-minute timeout. Included in POV-197 scope.
- **`BRIEFING_SCHEDULED` log key removed**: Unused constant in `LogKeys.java` cleaned up as part of removing `runBriefingGenerationScheduled`.
- **Live bug reproduction during brainstorm**: Cron temporarily set to `0 30 22 * * ?`, restored to `0 0 * * * ?` in Phase 3.

## Traceability

- **US:** US-ING-004
- **UC:** UC-16 (scheduled orchestration & periodic content fetch)
- **TC:** TC-ING-006
