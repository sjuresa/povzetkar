# POV-198 Retrospective — Persist Transcript Before Gemini Call

## Planned vs. actual

Scope delivered exactly as defined. One implementation deviation (see below) was caught and corrected during execution.

## Deviations & why

**Mock chain miscounted during planning:** The plan assumed both existing persistence-exception tests would need a leading `doAnswer` (for the new `persistTranscript` `runner.run()` call). In reality both tests leave `mockVideo.transcript = null`, so `persistTranscript` returns early via the outer null-guard without invoking `runner.run()`. The leading `doAnswer` shifted the throw to the wrong call site and broke both tests. Corrected by reverting to the original chain length. Lesson: when adjusting mock call chains, trace the exact execution path (including early-return guards) rather than assuming every new method always contributes a `run()` call.

## Technical findings

- **Detached-object transcript lifetime**: `ContentCreationService.createContentForVideo()` sets `video.transcript` on a detached JPA object. This value exists only in the JVM heap until something explicitly writes it to DB. Any exception between `createContentForVideo()` and `persistSummary()` silently loses the transcript — no error is surfaced, but the next retry pays full fetch cost again.
- **Best-effort pre-write pattern**: Persisting the transcript before the AI call is the correct pattern for any expensive fetch that precedes a fallible external call. The pre-write should be idempotent (check before writing) and best-effort (catch, log, continue) to avoid introducing a new failure mode.
- **Double guard is necessary**: Outer guard (`video.transcript == null`) avoids a DB roundtrip when there is nothing to save. Inner guard (`attachedVideo.transcript == null`) prevents a double-write on retry when the transcript was already saved by a previous run. Both are needed independently.
- **Test mock chains are order-sensitive**: `doThrow().doAnswer()` stubs on `runner.run()` depend on the exact call count reaching Mockito. Early-return guards in production code silently reduce that count and shift stub positions. Always trace the execution path before adjusting chain length.

## Feed-forward

- If a similar pattern arises elsewhere (e.g. fetching an image/caption before an AI call), apply the same `persistX()` best-effort pre-write pattern.
- If DB errors start appearing in production logs (`action="persistTranscript" status="failed"`), that is the signal to revisit the DB-error stop policy discussed in brainstorm (currently deferred).
