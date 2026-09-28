# POV-219 Retrospective — Sources and Content Scoped to the User's Topics

## 1. Planned vs. actual

Delivered as planned: per-user visibility of sources and content items (briefing clause included), scoped endpoints, topics that accept only visible source IDs plus Quick Add entries created on save, `POST /sources` admin-only, the legacy `/ui/*` admin-only, the Quick Add / modal / Sources page fixes and four resource test classes aligned with QA rule 3. The plan grew in the interview — channel-ID normalization (P2), the QA rule 3 rewrite of four test classes (P4), "Design first" in QA rule 3 (P5), the legacy UI (P6) — and in two self-reviews (`SourceDetailPage` 404, legacy-UI test roles, PATCH ordering). Not done by the agent: the manual check in the running app (interactive Google login).

## 2. Deviations & why

- **The brainstorm found more doors than the PO had named.** The PO listed the Sources menu and Add Source; the code had four more read paths, a write path by guessed ID (`resolveSources`) and — found only in planning — the legacy Qute UI. Enumerating every read *and write* path per entity, legacy routes included, belongs in the brainstorm of any ownership change.
- **A "near-impossible race" was designed, then removed.** The first P2 put `findOrCreate` into `requiringNew` with unique-violation recovery; the PO's test question ("isn't this going back to DB tests?") exposed that the code existed only to be tested. Dropping it simplified the service and made the PATCH-rollback question trivial (validate before mutate). This became QA rule 3 "Design first".
- **The handle bug came from a side question.** Asking why one channel could exist twice led to the ingestion bug (handle/URL sources never fetched); the PO's prod query confirmed the orphan. The PO chose a manual delete over a data migration.
- **Plan self-reviews paid off twice**: the second self-review found that the error boundary swallowed the "Source not found" page and that `patchEntity` mutated the topic before validation.

## 3. Technical findings

- **`ContentScope` with a private constructor** keeps "unrestricted" explicit; a record would have exposed `new ContentScope(null)` (fail-open) and clashed with the factory name.
- **`EXISTS` instead of a join** for visibility keeps list pagination and `count()` correct when a source is in several topics of the same user (tested).
- **Both join tables had primary keys leading with the other column** — lookups from the user side need their own index (V37).
- **Mocking the Hibernate `Session` also captures infrastructure writes**: `AuditLogFilter` persists an `AuditLog` through it, so `verify(session, never()).persist(any())` fails — verify the entity type.
- **JAX-RS passes an absent multi-valued query parameter as an empty list**, not `null` (`statuses`).
- **The IDE's Java compiler writes into `backend/target`**; after it compiled a test class with errors, Maven reported "Unresolved compilation problems" until `clean`.
- **`throwOnError: true` globally** means a component's own not-found branch is unreachable for a 404 unless the query opts out.

**Process-weight evidence (v0.20 carry-forward theme 4):** interview + self-reviews took most of the time (brainstorm 44 min, planning 123 min vs. implementation 31 min); the self-reviews found five plan defects before any code was written. Pre-commit review rounds: see the walkthrough and YouTrack record.

## 4. Feed-forward

- **Ownership changes: list every read and write path per entity first**, legacy routes and "attach by ID" included; express visibility once per entity (`ContentScope` pattern) and pass the scope in.
- **Remove transaction mechanics before testing them** (QA rule 3 "Design first").
- **POV-218** — the inline HQL of `ContentSourceResource`, `SourceItemApiResource`, `ContentItemApiResource` and `TopicApiResource` is done; the remaining classes stay.
- **POV-217 (SSE per user)** is the last known channel that shows other users' activity (job status events).
- **Backlog candidates from this task:** per-user summary sharing; removing the legacy Qute UI.
- **Local test runs:** start `povzetkar-db-test` first (`docker start`); build with `clean` after the IDE compiled with errors.
