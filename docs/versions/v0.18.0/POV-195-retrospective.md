# POV-195 Retrospective — RPD-accurate GenAI counting & remove countTokens

## Planned vs. actual

Planned: make `usageCount` count every request (= Google RPD), keep and surface `errorCount` for all failures, remove the redundant `countTokens` call and its dead `LogKeys` constant, add an admin "Errors" column, update tests. All delivered, no additions or cuts. Full suite green (328 tests, 0 failures; SpotBugs 0 bugs).

## Deviations & why

- **`LogKeys.COUNT` removed (scope extension).** Deleting the `countTokens` log block orphaned the generic `LogKeys.COUNT` constant (it was that block's only consumer). The original plan only authorized removing the task-specific `COUNT_TOKENS`, so the orphaned `COUNT` was first flagged to the PO rather than removed silently. The PO then approved deleting it; confirmed zero references and removed it, updating docs accordingly.
- **Non-`ApiException` failures counted in `usageCount` only.** Accepted in planning: the pre-increment fires before any throw, so a non-`ApiException` (client-side) failure inflates `usageCount` without touching `errorCount`. Conservative and extremely rare; not worth a second `catch`.

## Technical findings

- **Single choke point confirmed.** `VideoSummaryService.generateContent` is the *only* call site of `getModels().generateContent` and `countTokens`. Any future GenAI-accounting change belongs here — no scattering across strategies. `YouTubeSummarizationStrategy` and `createBriefingFromSummaries` both route through it.
- **`usageCount` is a metric, not a gate.** There is *no* pre-call RPD enforcement for GenAI (unlike transcript providers, which have `TranscriptProviderQuotaService`). `usageCount`/`errorCount` are display-only via `AdminResource` → `admin.html`. If RPD *enforcement* is ever wanted, it's a new mechanism, not a tweak to this counter. (Feed-forward.)
- **`errorCount` was a dead column.** It had been persisted since inception but never rendered. This task surfaced it; no schema change was needed.
- **Counting semantics meaning changed.** Operators reading historical "Daily Usage Count" should know the number's meaning shifted from "successful calls" to "total requests (RPD)" as of this version — the two are not comparable across the boundary.
- **Usage counter must be transactionally isolated (`REQUIRES_NEW`).** A usage/audit counter that joins the caller's transaction (`@Transactional` default `REQUIRED`) will be rolled back together with the caller when a downstream operation fails — silently undoing the very count the task exists to record. `ApiKeyUsageService.updateOrCreateUsageTransactional` was changed to `REQUIRES_NEW` (raised in PR #262 Gemini review). No current orchestration caller is itself `@Transactional`, so this was latent-risk hardening, not a live bug — but `REQUIRES_NEW` is the correct default for any "count what already happened" side-effect. Note: `ApiKeyUsageServiceTest` is unaffected because it constructs the service via `new` (bypassing the CDI interceptor), so the `TxType` change is not exercised by those unit tests.

## Feed-forward

- If the next task wants to *prevent* GenAI calls once RPD is near-exhausted, that is **enforcement** — a pre-call check against `getUsageForToday()` with a configured daily cap, plus a fallback/queue strategy. Out of scope here; this task only fixed the metric.
