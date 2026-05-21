# 9. Multi-Provider Transcript Strategy

Date: 2026-05-20

## Status

Proposed

## Context

Since v0.5.0, the platform fetches YouTube transcripts via a **single integration path** (`YouTubeTranscriptService`) that scrapes the YouTube subtitle endpoint. This single-source dependency creates two latent failure modes:

1. **IP rate-limiting:** Repeated transcript fetches from the same IP trigger Google's anti-abuse measures, producing transient 429/empty responses that are indistinguishable from "video has no transcripts." Symptoms have been observed anecdotally during high-volume orchestration runs.
2. **No fallback:** If the YouTube path fails permanently for any reason (DOM change, IP-level ban, API deprecation), the entire transcript-dependent feature set is unavailable.

Three candidate third-party transcript APIs exist with predictable cost/quota structures: Supadata (~100/month free), ytscribe (~50/month free), and transcriptapi ($5/month for 1000 fetches). However, integrating all three simultaneously inflates the work substantially without validating the abstraction first.

## Decision

Introduce a **transcript provider abstraction** with a single pilot integration in v0.17.0, plus a defensive jitter delay on the legacy path.

### Implementation Specifics

1. **Provider interface** (`TranscriptProvider`):
   ```java
   interface TranscriptProvider {
       String name();
       TranscriptResult fetch(String videoUrl);  // throws QuotaExhausted, ProviderUnavailable
       QuotaStatus remainingQuota();             // optional, returns UNKNOWN if unsupported
   }
   ```
2. **Provider implementations in v0.17.0:**
   - `YouTubeLegacyTranscriptProvider` (refactored from existing `YouTubeTranscriptService`).
   - `SupadataTranscriptProvider` (new — pilot integration).
3. **Routing strategy: Ordered Fallback Chain.** A `TranscriptOrchestrator` queries providers in configured order. On `QuotaExhausted` or `ProviderUnavailable`, it falls through to the next provider. The chain order is configured via `transcript.providers.order=youtube-legacy,supadata` (default puts the free, unmetered path first).
4. **Jitter delay on YouTube legacy path:** A randomized 500-2500ms sleep before each fetch (configurable: `transcript.providers.youtube-legacy.jitter.min-ms`, `.max-ms`). Reduces request-rate fingerprinting without dramatically slowing the orchestration loop.
5. **Quota tracking (best-effort):** A `transcript_provider_quota_usage` table records `(provider_name, period_yyyy_mm, fetches_count)`. Used for monitoring/dashboards; not relied upon for routing decisions in v0.17.0 (Supadata's free tier resets monthly, tracked but enforced by Supadata itself).
6. **Configuration flags:**
   - `transcript.providers.supadata.enabled` (default `false`) — opt-in.
   - `transcript.providers.supadata.api-key` — secret, sourced from environment variable.
7. **Metrics:** Each provider exposes Micrometer counters: `transcript.fetch.attempts`, `transcript.fetch.successes`, `transcript.fetch.failures{reason=...}`, `transcript.fetch.duration_ms`. Visible in the existing Grafana dashboards established in v0.15.0.

### Why Supadata as the Pilot?

- Higher monthly free tier (100 vs ytscribe's 50).
- Clear API documentation and REST contract (no scraping).
- Distinct technology stack from legacy YouTube path (validates the abstraction's flexibility).

### Future Providers (v0.18.0+)

Adding ytscribe or transcriptapi reduces to: implement a `TranscriptProvider` adapter, append to the chain order. No abstraction change required.

## Consequences

**Positive:**
- **Resilience:** Single-point-of-failure on YouTube is mitigated.
- **Cost Visibility:** Quota tracking and per-provider metrics make billing exposure observable before it becomes a surprise.
- **Extensibility:** Abstraction makes future provider additions trivial (no spike needed in v0.18.0).
- **Defensive Posture:** Jitter delay on the existing path reduces rate-limit incidence even before the new provider is enabled.

**Negative:**
- **Latency in Failure Cases:** Fallback chain means a fully-failed primary provider adds its timeout (e.g., 10s) before the secondary attempt. Acceptable trade-off; orchestration is already a background process.
- **Configuration Surface:** New env vars + flags increase the operational checklist. Documented in [RUNBOOK.md](../../../RUNBOOK.md).
- **Test Doubles Required:** Unit tests of the orchestrator need provider mocks; integration tests against real Supadata require either a recorded fixture or a sandboxed dev key.

## Alternatives Considered

1. **All three providers integrated in v0.17.0 (Rejected):** Triples integration cost without proportional value. Abstraction lets us add providers as commodity work.
2. **Only jitter delay, no abstraction (Rejected):** Treats symptoms, not the root architectural fragility. Doesn't help if YouTube scrape path is broken by a DOM change.
3. **Quota-aware routing (Deferred):** Smart routing based on `remainingQuota()` would prefer the cheapest available provider per-fetch. Deferred until quota tracking has produced enough data to inform a routing policy. v0.17.0 uses simple ordered-fallback.
4. **Caching layer in front of providers (Out of Scope):** A `transcript_cache` table keyed on video URL would eliminate redundant fetches. Recognized as valuable, but orthogonal to the multi-provider concern. Logged in [improvements.md](../../backlog/improvements.md).
