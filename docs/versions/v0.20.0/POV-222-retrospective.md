# POV-222 Retrospective — Human-Like Serial Pacing of YouTube Calls, Bounded Per-Video Retries, Any-Language Transcripts

## 1. Planned vs. actual

A production incident (YouTube 429 on the prod IP) became a task the same morning. The Brainstorm (35 min) turned
the prod log into numbers — attempts per day, parallel fetches, 136 failed retries on 19 caption-less videos — and
the PO's priority (a pause between every call). Planning took longer than the implementation (62 vs. 35 min): two
internal review rounds found that a per-call lock lets fetches interleave and that a deadline checked at item start
is ineffective (all items start within seconds), and the PO simplified the design several times (no global 429
cooldown, manual capture fully exempt, one pause range per level, 15–45 s, a time budget per run). The
implementation then needed no design change; the task was handed over to a fresh session after the plan approval
and resumed from `task.md` and the plan without loss. Estimate ~9 h.

## 2. Deviations & why

- **Plan assumed a test that did not exist** (`ContentItemService`): written new. A plan's "test updated" needs a
  grep for the test class, not an assumption.
- **Phase 4 red only at compile level:** the new signatures were written before the behaviour tests ran, so the
  first runtime run was green. The scheduler gate's red was demonstrated afterwards by disabling the condition.
  Writing the behaviour test against the old signature first (or a stub) keeps red meaningful.
- **Scripted test edits:** most signature changes in tests were mechanical (`any(ProcessingContext.class)`); a
  multi-line replace silently did not match (line endings) — caught by reading the diff.

## 3. Technical findings

- youtube-transcript-api 0.4.0: `findTranscript()` without arguments means `["en"]` (the cause of "no transcripts
  [en]" failures); one fetch = 3 calls (video page, innertube POST, captions); the default client has no timeout and
  cannot be wrapped — `createWithClient` is the seam.
- A fair lock held for the whole fetch is what makes fetches serial when callers run concurrently; a per-call lock
  only serialises calls.
- A deadline must be checked where the waiting happens (at the slot), not where the work is scheduled.
- Hibernate 7.4 resolves a subtype attribute on a supertype path in HQL (single table) without `treat`; rows of the
  supertype see the column as null.
- `StructuredLogger` quotes string values (`action="…"`) — documentation of log lines must use the real format.

**Process-weight evidence (POV-212 feed-forward):** Brainstorm 35 min (Q1–Q8), planning 62 min (P1–P6 plus two
review rounds that each changed the design), implementation 35 min. Defects found in execution: one wrong plan
assumption (missing test), one test defect (re-stubbing a lenient answer), no production defect so far. Pre-commit
review: see the walkthrough §4.

## 4. Feed-forward

- **Deploy of POV-222:** check `.env.docker` for `TRANSCRIPT_PROVIDERS_YOUTUBE_LEGACY_JITTER_*` (ignored, can go);
  after the next scheduled run read the pacing lines and the run summary (RUNBOOK §13.5); watch whether 15 min fits
  a morning catch-up (`budgetExhausted`).
- **If 429s continue despite the pacing**, the next lever is fewer fetches (larger pauses, budget) or the proxy as
  primary — not retries.
- **Plans that say "test updated" name the test file** found by a search.
- **Keep behaviour reds meaningful when signatures change:** add the new signature as a stub first, run red, then
  implement.
