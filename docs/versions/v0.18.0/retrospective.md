# v0.18.0 Version Retrospective — Cross-Task Synthesis

**Date:** 2026-06-26
**Status:** Final
**Input:** All nine task retrospectives in this directory (POV-191 … POV-199) plus the finalization task (POV-200).

This is a pattern-level synthesis across the version's tasks — trends, systemic patterns, and process health. It is not a re-listing of per-task findings; for those, see the individual `POV-XXX-retrospective.md` files.

---

## 1. The Version Theme in Hindsight

v0.18.0 was declared retrospectively around a single spine: **make transcript ingestion survive a hostile, rate-limiting upstream, and make the scheduler that drives it actually run.** Five of the nine tasks (POV-193, 194, 196, 197, 198) are facets of that one problem — the proxy, its Quarkus integration, its relocation, the scheduler fix, and transcript persistence. POV-195 (RPD accounting) and POV-199 (CVEs) are adjacent operational-hygiene work. POV-192 (demo access) and POV-191 (methodology) are the outliers. The version was not planned as a theme; the theme emerged because the proxy work kept surfacing the next weakness in the same pipeline. This validates the task-first model introduced in POV-191: the spine was discovered by following the work, not by up-front version planning.

## 2. Systemic Patterns Across Tasks

**Pattern A — Invisible third-party behavior repeatedly invalidated our assumptions.** Three independent tasks were derailed or reshaped by behavior that was real but undocumented in our plans:
- POV-195: the Google GenAI SDK silently retried up to 5× (`RetryInterceptor` default), inflating real RPD ~4–5× and masking failures from our own circuit breaker.
- POV-193: the planned `youtube-transcript-api` version and method signatures were fictional; the maintained 1.x line has a different API.
- POV-196: the Synology shipped Python 3.8 (not the assumed 3.12), `/tmp` was `noexec`, and the DSM Python had no `pip`.

The throughline: **a network-touching or runtime-touching dependency has hidden contracts (retries, versions, mount flags, runtimes) that must be verified against the real surface, not assumed from memory.** The "Know The API" principle now demonstrably extends to PyPI libraries, vendor SDKs, and the deploy host itself.

**Pattern B — "Persist/record the expensive or important thing in its own transaction, decoupled from the fallible step that follows."** This exact shape appeared twice, independently:
- POV-198: persist the transcript *before* the Gemini call, so a 503 retry doesn't re-fetch.
- POV-195: write the usage counter in a `REQUIRES_NEW` programmatic transaction, so a downstream rollback doesn't silently undo the count.

Both are the same lesson — a side-effect that must outlive a fallible operation cannot share that operation's transaction or control flow. POV-195 added the sharp corollary: **`@Transactional` on a method reached only by same-class self-invocation is a silent no-op** (CDI interceptors fire only through the proxy). Programmatic `QuarkusTransaction.requiringNew()` is the invocation-path-independent fix.

**Pattern C — Bugs that "never worked" hid behind mocks.** POV-197's scheduled orchestration had *never* run successfully (IO-thread blocking violation on every invocation), yet tests were green because `@InjectMock OrchestrationLockService` no-op'd the blocking DB call. The same shape recurs in POV-192 (the `demo` role had no permission test at all) and POV-194/196 (the proxy ships with no automated tests). **Mocking the very component whose real behavior triggers the bug guarantees the test cannot catch it** — a recurring blind spot for scheduler/integration-boundary code this version.

**Pattern D — Caller-side semantics beat protocol/infrastructure cleverness.** POV-193 collapsed an async job-queue design to a synchronous endpoint once we recognized the inter-request delay belongs to the *caller*. POV-194 then refined that jitter into a *between-sequential-calls throttle* so the first/isolated call isn't penalized. Both are the same instinct: push timing/state to the caller rather than building it into the service. (Mirrored in POV-193's rejection of `425 Too Early` in favor of a `reason` field in a 404 body — keep HTTP transport-level, domain state in the payload.)

## 3. Process Health

**Step 2.14 (handle unplanned discoveries inline) carried real weight this version.** Four tasks made in-scope additions through it — POV-192 (V35 migration), POV-197 (`SKIPPED_TOO_SHORT` filter + Vert.x timeout), POV-199 (Go builder bump + new `.trivyignore` entry), POV-194 (skip-first jitter throttle). All were minimal, low-risk, and directly coupled to the task's goal. None derailed scope. The gate worked as designed: discoveries were either absorbed (when minimal and aligned) or logged to `improvements.md`.

**Brainstorm-before-implementation removed cost, not just risk.** POV-193's biggest design win (dropping the async queue) happened *in the brainstorm*, before any code. POV-191 confirmed the relentless-interview approach works even for process-only tasks (14 decisions resolved up front, zero mid-execution reversals). The interview is paying for itself by deleting work before it's written.

**PR review is a consistent second-pass safety net — and a source of the deepest lessons.** POV-195's most important finding (the self-invocation `@Transactional` no-op) only surfaced in *round 2* of PR #262 review; round 1's annotation fix was itself wrong. Multiple improvements.md items trace to Gemini reviews (PR #252, #262, #265). The review loop is catching correctness bugs that the suite does not.

**The new methodology artifacts (POV-191) were exercised for the first time and held.** The version hub (`version.md`), per-task walkthrough/retrospective pairs, and the co-located `docs/versions/vX.Y.Z/` structure all worked as consumers this version. The one gap the model itself created — that the *release template* was never synced to the new methodology — was caught during this finalization (POV-200) and fixed. Lesson for future methodology changes: **when a process task changes the lifecycle, audit every downstream template/checklist that encodes the old lifecycle in the same task.**

## 4. Carry-Forward Themes (for the next version)

These are version-level threads, not individual backlog items (those live in `improvements.md`):

- **Transcript pipeline is resilient but unsupervised.** The proxy now resists IP throttling, but it has no crash-restart supervision, no log rotation, no automated tests, and a single residential egress IP that can still be soft-throttled under burst. The "refetch waste on retry" lever (partially addressed by POV-198) and a transcript cache table remain the structural fixes if throttling reappears.
- **GenAI cost is now *measured* accurately but not *enforced*.** POV-195 fixed the meter; there is still no pre-call RPD gate. If RPD exhaustion becomes real, that's a new enforcement mechanism (a `TranscriptProviderQuotaService` analog for GenAI), not a counter tweak.
- **Scheduler/integration-boundary test coverage is the standing blind spot.** Three tasks this version hit bugs that mocks hid. An integration-test variant that uses real lock/DB services for `@Scheduled` paths would close the class of defect POV-197 exposed.
- **The `.trivyignore` libexpat entries are recurring debt.** Three CVEs are suppressed pending Debian Trixie backports; this needs periodic monitoring, not a one-time fix.

---

## Revision History

| Version | Date | Author | Changes |
| --- | --- | --- | --- |
| 1.0 | 2026-06-26 | Povzetkar AI | Initial cross-task synthesis for v0.18.0 finalization (POV-200). First version-level retrospective under the task-first model (POV-191). |
