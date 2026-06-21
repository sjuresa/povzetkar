# POV-197 Retrospective — Scheduled Orchestration Fix & Periodic Content Fetch

## Planned vs. Actual Scope

Scope matched plan. The bug fix and unified scheduler were the only deliverables; nothing was added or removed during execution.

## Deviations and Rationale

- **Import style for `@Blocking`**: Used fully-qualified `@io.smallrye.common.annotation.Blocking` inline rather than adding an import statement, to match the existing annotation style in `OrchestrationManager` where other annotations are also qualified. This is a stylistic choice with no functional impact.
- **`BRIEFING_SCHEDULED` constant removal**: Not in the plan, added during execution when the removed method left it orphaned. Correct application of "no dead code" principle; zero risk.

## Unplanned Discoveries (checklist 2.14)

**`SKIPPED_TOO_SHORT` videos summarized:** First live orchestration run showed `duration="00:23"` and `duration="01:04"` videos being summarized despite `povzetkar.content.video.min-duration=PT150S`. Root cause: `YouTubeFetchingStrategy` correctly sets `SKIPPED_TOO_SHORT` but still returns those items in the list; `TopicProcessor.processTopicUni` passed the entire list to `JustInTimeSummarizationScheduler` without filtering. Fix: one-line stream filter in `TopicProcessor` — only `PENDING` items scheduled. Test added to `TopicProcessorTest.testProcessTopicUni_skippedTooShortNotSummarized`.

**Vert.x worker thread timeout:** `Thread blocked for 61041 ms` WARN appeared because orchestration runs longer than Vert.x's default 60 s worker thread timeout. Added `quarkus.vertx.max-worker-execute-time=600S`. Not an error — orchestration completed successfully — but the warning was misleading.

## Technical Findings

**Root cause of the bug:** Quarkus Scheduler has two invocation modes for `@Scheduled` methods:
- `void` return type → invoked on a **worker thread** (blocking-safe by default)
- `Uni<Void>` return type → invoked on the **IO thread** (non-blocking contract assumed)

When a `Uni<Void>` method contains synchronous blocking calls *before* returning the `Uni` (i.e., before the async pipeline starts), those calls execute on the IO thread and throw `BlockingOperationNotAllowedException`. Adding `@Blocking` overrides the invocation thread to a worker thread for both modes.

This is a non-obvious Quarkus contract: returning `Uni<Void>` signals reactive intent, but does not protect the synchronous code path *before* the `Uni` is constructed.

**Why the bug was never caught:** The `@Scheduled` methods were never called in tests directly as part of a scheduler test — they were called manually via `.await().indefinitely()`. In tests, `@InjectMock OrchestrationLockService` replaces the real lock service, so `acquireLock` is a no-op mock and never hits the DB. The IO-thread violation only manifests when the real lock service performs an actual DB call — which only happens in production/dev with a live DB.

## Feed-Forward Notes

- **New `@Scheduled` methods that return `Uni<Void>` and need blocking entry**: always add `@Blocking`. Even if the method delegates to async services, any synchronous call before the first `.chain()` or `Uni.createFrom()` runs on the IO thread.
- **Test coverage gap**: The existing scheduler tests mocked `OrchestrationLockService` entirely, so the blocking violation was invisible. Future scheduler tests should consider an integration variant that uses the real lock service against the test DB to catch this class of bug.
- **`povzetkar.briefing.cron` removed from `application.properties`**: if the Synology `.env` still contains `POVZETKAR_BRIEFING_CRON`, Quarkus will ignore it (no error), but it should be removed from the env file to avoid confusion.
