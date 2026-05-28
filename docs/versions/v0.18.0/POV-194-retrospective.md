# POV-194 Retrospective — ProxyTranscriptProvider

## Planned vs. actual

Scoped: one `TranscriptProvider` adapter + REST client + response records + config + unit tests, with the proxy as primary in the fallback chain. Delivered exactly that, no interface or orchestrator changes. `mvn clean verify` passed on the first run (full suite green, spotbugs clean).

## Deviations & why

- No material deviations. The Supadata provider was a near-complete template; the proxy adapter is structurally simpler (synchronous, no quota, no async polling), so `fetch()` was written inline rather than copying Supadata's helper-method scaffolding (decision H2).
- Test config choice: the proxy is `enabled=false` and absent from `transcript.providers.order` in the test profile, with a deliberately invalid URL, so `@QuarkusTest` wiring never makes a real network call. Confirmed clean startup via the full suite.

## Technical findings

- **Snake_case deserialization gotcha.** The project relies on Jackson defaults (no global `PropertyNamingStrategy.SNAKE_CASE`). Existing records (Supadata) are camelCase because that API returns camelCase. The proxy returns `video_id`, so `@JsonProperty("video_id")` is required on that one field. Any future provider whose API uses snake_case must annotate per-field (or the project must adopt a global strategy — out of scope here).
- **`start` unit alignment.** The proxy returns `start` in seconds, matching `TranscriptSegment.startSeconds` directly — no `/1000.0` conversion (unlike Supadata's millisecond `offset`). One less place to get wrong.
- **Provider abstraction held up.** Adding a primary provider required zero changes to the interface or orchestrator — ADR-0009's "future provider becomes primary without code changes" claim is now empirically validated.
- **Non-200 body reading.** Reading the error body as a raw `String` (decision H1) captures the proxy's `{detail:{reason:...}}` discriminator in logs without typed error records — sufficient because the reason drives no runtime branching this task.

## Skip-first jitter throttle (2.14 in-scope addition)

The original plan moved the 15–45s jitter onto the proxy caller but applied it to *every* call. During execution the PO noted this penalises the manual admin "Fetch Transcript" button, which shares the orchestrator path with batch ingestion. The fix reframes jitter as a *throttle between sequential calls*: `applyJitter()` sleeps only if the prior call was less than `jitterMaxMs` (45s) ago, so the first/isolated call (the manual UI case) is never delayed. This is the semantically correct model — jitter exists to space out *consecutive* requests, and the first request in a sequence has nothing to space against.

Implementation notes for future providers: this lives entirely in `ProxyTranscriptProvider` (one `volatile long` field + a window comparison), with no interface or orchestrator change. If a third provider ever needs the same behaviour, fold it into the shared `JitterDelay` helper already logged in improvements.md. The window deliberately reuses `jitterMaxMs` rather than introducing a new config key.

## Feed-forward

- **Retry-later is the natural next task.** The `not_available_yet` vs `video_unavailable` distinction is currently *logged only*. A future task should add a "stop chain" signal (for `video_unavailable`) and retry-later orchestration (for `not_available_yet`) — this likely warrants its own ADR, as it changes the `TranscriptProvider`/orchestrator contract.
- **Live verification still pending.** Unit tests are mock-based. End-to-end validation against the live Railway proxy (with `TRANSCRIPT_PROXY_API_KEY` set) — confirming `fetched successfully via provider 'proxy'` and the `transcript.fetch.successes{provider="proxy"}` metric — should be done in a deployed environment before relying on the proxy as primary in production.
- **Quota metric semantics.** The orchestrator records a quota "fetch" on every success, including proxy successes, even though the self-hosted proxy has no billing quota. Harmless (it's just a counter) but worth noting if quota-aware routing (ADR-0009 alternative #3) is ever built.
- **Shared jitter helper.** `applyJitter()` is now duplicated in two providers; a third caller should trigger extraction of a shared `JitterDelay` helper (logged in improvements.md).
