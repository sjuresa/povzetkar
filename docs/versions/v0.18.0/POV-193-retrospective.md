# POV-193 Retrospective — Transcript Proxy Service

## Planned vs. actual

Scoped: a stateless Python/FastAPI proxy in `transcript-proxy/`, single-video synchronous `GET`, `X-API-Key` auth, English only, Railway-deployable, Quarkus integration deferred. Delivered exactly that. No scope crept in; the deferred Quarkus adapter stayed deferred.

The brainstorm itself removed significant planned complexity *before* implementation: an early model with an async job queue, batch submission, `job_id`s, and status polling was collapsed to a synchronous single-video endpoint once we recognized the inter-request delay belongs to the caller, not the proxy. That simplification is the main design win of this task.

## Deviations & why

- **`youtube-transcript-api` 0.6.3 → 1.2.4.** The planned version was fictional; the maintained line is 1.x with a different API. Caught at install time because Phase 4 set up a real venv before writing `main.py`. Writing code against the verified runtime surface (rather than the plan's assumed API) avoided shipping against a non-existent contract.
- **Python 3.13 pin.** Default 3.14 is unsupported by the library. Low-impact but a real local-dev and deploy constraint, now documented.
- **Wider permanent-failure exception bucket.** `InvalidVideoId`/`AgeRestricted`/`VideoUnplayable` join `VideoUnavailable` under `video_unavailable` — they share the caller semantics ("don't retry this video").

## Technical findings

- **Verify third-party API surface before planning concrete versions.** The plan pinned a version and method signatures from memory; both were wrong. For external libraries, a quick install + introspection during the planning interview would have grounded the plan. The mandatory-workflow "Know The API" principle applies to PyPI libraries, not just internal code.
- **`youtube-transcript-api` 1.2.4 contract** (for the follow-up Quarkus task): `YouTubeTranscriptApi().fetch(video_id, languages=('en',))` / `.list(video_id)` are instance methods. `TranscriptList.find_manually_created_transcript(langs)` then `find_generated_transcript(langs)` gives manual-first selection. Snippets are `FetchedTranscriptSnippet(text, start: float seconds, duration)`.
- **404-with-reason vs distinct status codes.** We considered `425 Too Early` for "not available yet" and rejected it — 425 carries TLS early-data/replay semantics that can trigger automatic immediate client retries, the opposite of the intended "retry much later." A `reason` field in a 404 body keeps HTTP codes transport-level and domain state in the payload.
- **Statelessness eliminated a whole class of concerns.** No queue means no job IDs, no polling, no restart-recovery, no dedup. The delay that originally seemed to force async lives naturally on the sequential caller.

## Feed-forward

- **Next task (Quarkus integration):** implement `ProxyTranscriptProvider` against the existing `TranscriptProvider` interface (`backend/.../service/transcript/`). Map proxy `200` → segments → `TranscriptSegment`; `502` → `ProviderUnavailableException` (fall through chain); `404 video_unavailable` → stop; `404 not_available_yet` → retry-later semantics (note: the current `TranscriptProvider` interface has no explicit "stop chain" signal — decide whether to extend it or treat `not_available_yet` as a retryable empty result). The 15–45s jitter belongs on the caller, mirroring the existing `youtube-legacy` jitter config.
- **Deployment validation pending:** the whole premise (Railway IPs not blocked by YouTube) is unverified until deployed. Have Fly.io / Hetzner ready as fallbacks if Railway is also blocked.
- **No automated tests** ship with this service — it is a thin wrapper verified manually via curl. If it grows (multi-language, caching), add pytest coverage of the exception→status mapping with a mocked `YouTubeTranscriptApi`.
