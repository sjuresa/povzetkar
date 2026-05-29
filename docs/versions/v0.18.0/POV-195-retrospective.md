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
- **Usage counter must be transactionally isolated — via programmatic `QuarkusTransaction`, not an annotation.** A usage/audit counter that joins the caller's transaction will be rolled back together with the caller when a downstream operation fails — silently undoing the very count the task exists to record. Two PR #262 review rounds refined this:
  - *Round 1:* added `@Transactional(REQUIRES_NEW)` to `updateOrCreateUsageTransactional`.
  - *Round 2 (the real lesson):* that annotation was a **no-op** — the method is reached only via same-class self-invocation (`incrementUsage`/`incrementErrorCount` → `updateOrCreateUsage` → `updateOrCreateUsageTransactional`), and CDI interceptors only fire through the client proxy, never on `this.` calls. **A `@Transactional` annotation on a privately-reached method silently does nothing.** Fixed with programmatic `QuarkusTransaction.requiringNew().run(...)`, which is invocation-path-independent and matches the existing `AuditLogFilter.persistAuditLogAsync` best-effort-audit pattern. This also fixed the retry loop (each attempt now gets its own transaction instead of a rollback-marked caller tx).
- **Best-effort audit calls must not mask the primary exception.** `incrementErrorCount` in the `catch (ApiException e)` block could itself throw a DB exception and replace the `ApiException` the 503 circuit breaker and callers depend on. Wrapped in try/catch with ERROR-level logging (PR #262 round 2). General rule: a best-effort side-effect inside a `catch` must never be able to suppress the exception being handled.

## Feed-forward

- If the next task wants to *prevent* GenAI calls once RPD is near-exhausted, that is **enforcement** — a pre-call check against `getUsageForToday()` with a configured daily cap, plus a fallback/queue strategy. Out of scope here; this task only fixed the metric.
