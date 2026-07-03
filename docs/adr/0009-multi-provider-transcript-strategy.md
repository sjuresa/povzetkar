# 9. Multi-Provider Transcript Strategy

Date: 2026-05-20

## Status

Accepted (implemented in v0.17.0 / POV-189)

## Context

Since v0.5.0, the platform fetches YouTube transcripts via a **single integration path** (`YouTubeTranscriptService`) that scrapes the YouTube subtitle endpoint. This single-source dependency creates two latent failure modes:

1. **IP rate-limiting:** Repeated transcript fetches from the same IP trigger Google's anti-abuse measures, producing transient 429/empty responses that are indistinguishable from "video has no transcripts." Symptoms have been observed anecdotally during high-volume orchestration runs.
2. **No fallback:** If the YouTube path fails permanently for any reason (DOM change, IP-level ban, API deprecation), the entire transcript-dependent feature set is unavailable.

A deeper architectural concern: the application is deployed on Oracle Cloud Infrastructure (OCI), whose IP ranges YouTube actively blocks. This makes the legacy scraping path inherently fragile regardless of rate-limiting behaviour.

Three candidate third-party transcript APIs exist with predictable cost/quota structures: Supadata (~100/month free), ytscribe (~50/month free), and transcriptapi ($5/month for 1000 fetches). However, integrating all three simultaneously inflates the work substantially without validating the abstraction first.

## Decision

Introduce a **transcript provider abstraction** with a single pilot integration in v0.17.0, plus a defensive jitter delay on the legacy path.

### Implementation Specifics

1. **Provider interface** (`TranscriptProvider`) — final contract as implemented:
   ```java
   public interface TranscriptProvider {
       String name();
       boolean isEnabled();
       String fetch(String videoId) throws QuotaExhaustedException, ProviderUnavailableException;
   }
   ```
   - `fetch()` takes a `videoId` (not a full URL). Each provider constructs whatever URL format it needs internally.
   - `QuotaExhaustedException` and `ProviderUnavailableException` are checked exceptions; the orchestrator catches them and falls through to the next provider.

2. **Shared formatting contract:** A `TranscriptSegment(double startSeconds, String text)` record is the internal exchange type. `TimeTextFormatter` accepts `List<TranscriptSegment>` and produces `(HH:MM:SS) text` grouped output. Both providers map their native response format to this record before formatting.

3. **Provider implementations in v0.17.0:**
   - `YouTubeLegacyTranscriptProvider` — refactored from `YouTubeTranscriptService`. Adds randomized jitter (`Thread.sleep`) before each fetch to reduce IP rate-limit fingerprinting. Converts `thoroldvix Fragment` (seconds) → `TranscriptSegment`.
   - `SupadataTranscriptProvider` — pilot integration via MicroProfile REST Client. Accepts `videoId`, constructs full YouTube URL internally (`https://www.youtube.com/watch?v=<videoId>`). Calls `GET /v1/transcript?url=<url>&text=false`. Converts Supadata chunks (millisecond `offset`) → `TranscriptSegment` (`offset / 1000.0`). Implements synchronous polling loop for async 202 responses (10s timeout → `ProviderUnavailableException`).

4. **Supadata API contract (verified via spike, 2026-05-24):**
   - Endpoint: `GET https://api.supadata.ai/v1/transcript`
   - Auth: `x-api-key: <key>` header.
   - Parameter: `url` (full YouTube URL) — **not** `videoId`. `text=false` returns timestamped chunks.
   - Response shape: `{lang, availableLangs, content: [{text, offset, duration, lang}]}` where `offset` is milliseconds.
   - HTTP 200: immediate result. HTTP 202: async job — poll `GET /v1/transcript/{jobId}` until `status=completed`.
   - Error codes: 402=quota exhausted, 403/404=unavailable, 429=rate-limited.

5. **Routing strategy: Ordered Fallback Chain.** `TranscriptOrchestrator` queries providers in configured order. On `QuotaExhaustedException` or `ProviderUnavailableException`, falls through to next provider. Chain order: `transcript.providers.order=youtube-legacy,supadata`.

6. **Jitter delay on YouTube legacy path only:** 500–2500ms randomized sleep (configurable via `transcript.providers.youtube-legacy.jitter.min-ms` / `.max-ms`). Set to `0/0` in test configuration to disable. Supadata requires no jitter — it is a metered REST API, not a scraping path.

7. **Quota tracking (best-effort, backend only):** `transcript_provider_quota_usage` table records `(provider_name, period_yyyy_mm, fetches_count)`. Upserted by `TranscriptOrchestrator` on every **successful** fetch only (failed/timed-out calls not counted — aligned with Supadata's 1-credit-per-success billing model). Frontend admin component and `GET /api/v1/admin/transcript/providers` endpoint deferred to v0.18.0.

8. **Configuration flags:**
   - `transcript.providers.order=youtube-legacy,supadata`
   - `transcript.providers.youtube-legacy.enabled=true`
   - `transcript.providers.youtube-legacy.jitter.min-ms=500`
   - `transcript.providers.youtube-legacy.jitter.max-ms=2500`
   - `transcript.providers.supadata.enabled=false` (opt-in default)
   - `SUPADATA_API_KEY` — environment variable for API key secret.

9. **Metrics:** Micrometer counters per provider in `TranscriptOrchestrator`:
   - `transcript.fetch.attempts{provider}`
   - `transcript.fetch.successes{provider}`
   - `transcript.fetch.failures{provider, reason}` — reason: `quota_exhausted`, `unavailable`, `timeout`
   - `transcript.fetch.duration{provider}` — timer

### Package Structure

All transcript classes live in `com.povzetkar.quarkus.service.transcript`:
- `TranscriptProvider` (interface)
- `TranscriptOrchestrator`
- `TranscriptSegment` (record)
- `TranscriptProviderQuotaService`
- `YouTubeLegacyTranscriptProvider`
- `SupadataTranscriptProvider`
- `SupadataClient` (MicroProfile REST Client interface)
- `TimeTextFormatter` (moved from `service/`)

### Why Supadata as the Pilot?

- Higher monthly free tier (100 vs ytscribe's 50).
- Clear REST API contract (no scraping) — validated via spike.
- Distinct technology stack from legacy YouTube path (validates the abstraction's flexibility).

### Future Providers (v0.18.0+)

Adding ytscribe or transcriptapi reduces to: implement a `TranscriptProvider` adapter, append to `transcript.providers.order`. No abstraction change required. The provider order is runtime-configurable — Supadata or a future provider can become the primary without code changes.

> **Update (v0.18.0 / POV-194):** A third provider, `ProxyTranscriptProvider` (`name()="proxy"`), was added against the POV-193 transcript proxy and made the **primary** source (`transcript.providers.order=proxy,youtube-legacy,supadata`) — validating the above with zero interface or orchestrator changes. It applies its own caller-side 15–45s jitter, **throttled to sequential calls** — the jitter is skipped for the first/isolated call (e.g. the manual admin "Fetch Transcript" button) and applied only when the previous call was within `jitterMaxMs`, so batch ingestion stays protected while the manual UI path is responsive. Note: it has no quota concept, so it never throws `QuotaExhaustedException`.
>
> **Update (v0.18.0 / POV-196):** The transcript proxy **host moved from Railway to a self-hosted Synology DS220j**, reached over a Tailscale overlay. Railway's shared datacenter egress IP was being soft-throttled by YouTube (intermittent `502 proxy_blocked` interleaved with `200`s on the same video — evidence of volume-based throttling, not a hard ban or cold-start). A residential, always-on egress IP is throttled far less. **No application code changed** beyond the env-pointed `TRANSCRIPT_PROXY_URL`; the only proxy code change was per-branch failure logging (so `IpBlocked` vs `RequestBlocked` vs `upstream_error` are unambiguous). Railway is kept as a **passive, manual fallback** — one host active at a time, switched via `TRANSCRIPT_PROXY_URL`, both accepting the same `X-API-Key`. Operational detail in RUNBOOK §16.1b.

## Consequences

**Positive:**

- **Resilience:** Single-point-of-failure on YouTube/OCI is mitigated.
- **Cost Visibility:** Quota tracking and per-provider metrics make billing exposure observable before it becomes a surprise.
- **Extensibility:** Abstraction makes future provider additions trivial (no spike needed in v0.18.0).
- **Defensive Posture:** Jitter delay on the existing path reduces rate-limit incidence even before the new provider is enabled.

**Negative:**

- **Latency in Failure Cases:** Fallback chain means a fully-failed primary provider adds its timeout (10s) before the secondary attempt. Acceptable trade-off; orchestration is already a background process.
- **Configuration Surface:** New env vars + flags increase the operational checklist. Documented in [RUNBOOK.md](../RUNBOOK.md).
- **Test Doubles Required:** Unit tests of the orchestrator use provider mocks; `SupadataClient` is mocked via `@InjectMock` in tests.

## Alternatives Considered

1. **All three providers integrated in v0.17.0 (Rejected):** Triples integration cost without proportional value. Abstraction lets us add providers as commodity work.
2. **Only jitter delay, no abstraction (Rejected):** Treats symptoms, not the root architectural fragility. Doesn't help if YouTube scrape path is broken by a DOM change or OCI IP block.
3. **Quota-aware routing (Deferred):** Smart routing based on `remainingQuota()` would prefer the cheapest available provider per-fetch. Deferred until quota tracking has produced enough data to inform a routing policy. v0.17.0 uses simple ordered-fallback.
4. **Caching layer in front of providers (Out of Scope):** A `transcript_cache` table keyed on video URL would eliminate redundant fetches. Recognized as valuable, but orthogonal to the multi-provider concern. Logged in [improvements.md](../backlog/improvements.md).
5. **`fetch(String videoUrl)` interface contract (Rejected):** Interface was drafted with full URL. Spike revealed callers already hold `videoId`; constructing URLs at the call site would be noisy. Each provider constructs its own URL format internally — cleaner separation.
6. **`TranscriptResult` sealed interface instead of exceptions (Rejected):** More verbose for a simple two-outcome case. Checked exceptions map naturally to the orchestrator's try/catch fallback loop.
