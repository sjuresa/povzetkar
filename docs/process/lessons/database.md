# Database & Persistence (Hibernate / Panache)

## Pagination & N+1 Queries
When building paginated APIs that return entities with collections (e.g., `User` containing `roles` or `Topic` containing `sources`), direct Panache queries can lead to severe N+1 query problems and incorrect pagination.
- **The Trap:** Using `JOIN FETCH` (e.g., `FROM User u LEFT JOIN FETCH u.roles`) on a paginated query causes Hibernate to fetch the *entire* cartesian product into memory before paginating, leading to `HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!` warnings and catastrophic performance degradation.
- **The Standard:** Always use the **two-step query pattern** for paginating relationships:
  1. Retrieve *only* the IDs of the paginated root entities first using standard `page()` methods.
  2. Use those IDs in a secondary query with `JOIN FETCH` to eagerly load the collections.
  
  ```java
  // Step 1: Get the paged IDs (Select only IDs to avoid loading root entities)
  List<Long> userIds = User.<Long>find("SELECT u.id FROM User u ORDER BY u.name ASC").page(pageIndex, pageSize).list();
  
  if (userIds.isEmpty()) {
      return Collections.emptyList();
  }
  
  ```java
  // Step 2: Fetch the full graph for exactly those IDs
  List<User> usersWithRoles = User.list("FROM User u LEFT JOIN FETCH u.roles WHERE u.id IN ?1 ORDER BY u.name ASC", userIds);
  ```

## Join Fetch for To-One Associations in Paginated HQL (POV-175)
Unlike to-many collections, fetching a **to-one** relationship (e.g., `Briefing` to `Topic`) in a paginated HQL query does **not** create a cartesian product that breaks Hibernate's limit/offset logic.
- **The Standard:** Use `LEFT JOIN FETCH` directly in the main paged HQL for all `ManyToOne` or `OneToOne` relationships required by the DTO mapper.
- **Root Aliasing:** Always use an explicit root alias (e.g., `SELECT b FROM Briefing b LEFT JOIN FETCH b.topic t`) to ensure that dynamic filters and sort paths applied to the `PanacheQuery` are unambiguous.

## Differential Query Count Assertions (POV-175)
When asserting database performance in integration tests, hardcoding an exact query count (e.g., `assertCount(1)`) is brittle because it captures infrastructure overhead (Auth lookups, Audit logging).
- **The Protocol:** Use the **Differential Strategy**:
  1. Measure the query count for **1 record**.
  2. Seed **N additional records**.
  3. Measure again for **N+1 records**.
  4. Assert the count remains **identical** (Delta = 0). This mathematically proves the O(1) performance of the Join Fetch regardless of result set size.

## Source Transparency (POV-148)
The implementation of the Source Library and Source Detail pages significantly improved system observability. Users can now "drill down" from a high-level briefing to the raw source items that informed it, closing the loop on content transparency.

## Graceful Failure, String Truncation & Error Sanitization
When background jobs or data-sync commands fail, the exception stack trace can be massive and contain sensitive structure details.
- **The Trap:** Writing an un-truncated exception message to a database column triggers a `DataTruncation` transaction rollback. Exposing the raw exception string to the API layer can also lead to the leakage of internal stack traces or credentials if they are part of the exception context.
- **The Standard:** Always assert string lengths against an explicitly defined entity constant (e.g., `BackgroundJob.ERROR_MESSAGE_MAX_LENGTH`). Log the full unfiltered exception internally using `logger.errorf` to preserve debugging context, but securely sanitize the string (e.g., generic user-facing message) before persisting it to an entity exposed via API.

## JPQL Constructor Projections
- **The Trap:** Fetching full Hibernate entities and their associations (via `JOIN FETCH`) only to map them to DTOs in the application layer. This causes "over-fetching" (retrieving data from the DB that is never used) and high memory allocation.
- **The Standard:** Use JPQL constructor expressions (`SELECT NEW com.dto.MyDTO(p.id, p.name) FROM ...`) for read-only aggregation or summary endpoints. This moves the mapping logic to the database level, significantly reduces I/O traffic, and lowers the memory footprint of the application.

## Stable Identity (Equals & HashCode)
- **The Trap:** Relying on default Object.equals() or standard JPA identity can cause entities to be treated as different objects when reloaded across transaction boundaries or passed into asynchronous collection-based task queues.
- **The Standard:** Always implement stable equals() and hashCode() using **Business Keys** (e.g., `slug`, `username`, `videoId`).
- **Generated IDs:** For entities with a generated ID, implement hashCode() to use the ID's hash code when not null and fall back to a stable alternative like super.hashCode() when the ID is null: return id != null ? id.hashCode() : super.hashCode();.
- **Lazy Associations:** When implementing equals() and hashCode() for an entity with a lazily-loaded (FetchType.LAZY) association, use the associated entity's ID (e.g., associatedEntity.id) for comparisons to avoid triggering unnecessary database queries or LazyInitializationException.

## Consolidated Count Queries
When multiple aggregate counts are needed at once (e.g., for a dashboard or metrics endpoint), avoid multiple roundtrips with `count()`. Use a single native query with sub-selects to collapse them into one result row.

```java
Object[] result = (Object[]) em.createNativeQuery(
        "SELECT " +
                "(SELECT COUNT(*) FROM table_a), " +
                "(SELECT COUNT(*) FROM table_b WHERE flag = true)")
        .getSingleResult();
// Map results using result[0], result[1], etc.
```

## Independent Transaction for Auditing
- **The Trap:** Persisting audit logs or job audit trails within the same transaction as the main business logic. If the business transaction rolls back (e.g., due to a constraint violation), the audit record is also lost, leaving no trace of the failure.
- **The Standard:** Always wrap persistence of audit records (API audits, job execution audits) in **`QuarkusTransaction.requiringNew()`**. This ensures the audit record is committed independently of the success or failure of the main operation, providing a reliable diagnostic trail.

## Chunked Data Deletion (Retention)
- **The Trap:** Attempting to delete a large volume of historical data (e.g., "Initial Big Cleanup") in a single transaction. This can cause the transaction to time out, exhaust the database's undo logs, or hold exclusive locks on the table for an extended period, blocking other operations.
- **The Standard:** Always implement mass deletions using a **chunked, multi-transactional loop**. 
  1. Retrieve a limited set of IDs (e.g., 1,000) for records that meet the deletion criteria.
  2. Delete exactly those IDs in a new, independent transaction (`QuarkusTransaction.requiringNew()`).
  3. Repeat until no more records match. 
  
  This ensures that each chunk is committed independently, and the database remains responsive. Since `DELETE ... LIMIT` is not standard across all SQL dialects (like PostgreSQL), use a two-step `SELECT id ... LIMIT X` followed by `DELETE ... WHERE id IN (...)` for a portable and performant implementation in Hibernate.

## Do Not Hold a Transaction Open During External Network Calls (POV-189)
- **The Trap:** Annotating a service method with `@Transactional` when it also performs external HTTP calls (e.g., fetching a transcript via a REST client) holds a DB connection from the pool open for the entire duration of the network call — including any polling loops. Under load this exhausts the connection pool.
- **The Standard:** Remove `@Transactional` from any method that mixes network I/O with DB writes. Use `QuarkusTransaction.requiringNew()` to wrap *only* the DB read and the DB write blocks, leaving the network call in a non-transactional context.
- **The Corollary — detached entities:** Once the first `QuarkusTransaction.requiringNew()` block closes, any entity loaded inside it becomes detached. Calling `persist()` on a detached entity in a subsequent transaction throws `PersistentObjectException`. Always re-load the entity inside each new transaction block via `findById(id)` before modifying or persisting it.

```java
// WRONG — holds connection open during network call
@Transactional
public Result process(Long id) {
    Entity e = Entity.findById(id);          // DB read — connection held
    String data = externalApi.fetch(...);     // network call — still holding!
    e.field = data;
    e.persist();                             // DB write
}

// CORRECT — transaction scoped to DB operations only
public Result process(Long id) {
    Entity e = QuarkusTransaction.requiringNew().call(() -> Entity.findById(id)); // closed immediately
    String data = externalApi.fetch(...);    // no transaction held
    QuarkusTransaction.requiringNew().run(() -> {
        Entity managed = Entity.findById(e.id); // re-load — entity was detached
        managed.field = data;
        managed.persist();
    });
}
```

## Transactional Consistency in Background Jobs

- **The Problem:** Background jobs triggered from executor threads lack a default CDI request or transaction context. This leads to `ContextNotActiveException` when calling Panache methods.
- **The Standard:** Always wrap background database operations in an explicit programmatic transaction: `QuarkusTransaction.requiringNew().call(() -> { ... })`.

## Eager Loading for Background Processing
- **The Problem:** When processing entities in a *new* transaction (common in background jobs), lazy-loaded collections (like `@ManyToMany`) will throw `LazyInitializationException` if they weren't fetched in the same transaction session.
- **The Standard:** Implement dedicated finders that use `JOIN FETCH` (e.g., `Topic.findByIdWithSources()`) to eagerly load all required associations before passing the entity to the background worker. Avoid relying on open-session-in-view patterns for background tasks.

## Hibernate Session Isolation in Parallel Tasks (POV-155)
When executing parallel database tasks using a `ManagedExecutor` or similar thread pool in Quarkus/Panache:
- **The Trap:** Passing a JPA entity (e.g., a `Topic` fetched on the main request thread) into multiple background lambdas. Even if each lambda uses `QuarkusTransaction.requiringNew()`, Hibernate may attempt to lazily initialize collections or manage the entity state using the *original* session from the parent thread. This leads to `java.util.ConcurrentModificationException` within Hibernate's `ResultsetsTrackingContainer` or `PSQLException: This statement has been closed` errors.
- **The Standard:** **Never** pass managed entities across thread boundaries. Instead, pass the **entity ID** (`Long topicId`) to the background task. Inside the task's own transaction, refetch the entity from the database using `Topic.findById(topicId)`. This ensures each parallel task has its own isolated Hibernate session and state tracking.

## EXISTS Subquery vs JOIN for Filtered Pagination (POV-161)
The EXISTS subquery pattern was successfully validated for source-based briefing filtering.
- **Pattern:** `EXISTS (SELECT 1 FROM Summary s WHERE s MEMBER OF summaries AND s.contentItem.contentSource.id = :sourceId)` correctly filters briefings that contain summaries from a specific source without introducing cartesian products on the paginated main query.
- **Confirmed:** This approach correctly interacts with Panache's `.count()` and `.page()` methods, producing accurate pagination metadata.

## Dual-Predicate Pattern for Dynamic Filters with Explicit Alias (POV-184)
When combining dynamic WHERE clauses with Panache's predicate-based `count()` and an explicit-alias `FROM Entity e` HQL query, the alias must be handled separately.
- **The Trap:** Panache's `Entity.count(predicate, params)` constructs `SELECT COUNT(*) FROM Entity WHERE <predicate>` — with no alias. Using `u.email` inside this predicate causes `Could not interpret path expression 'u.email'`. The same aliased predicate works in `FROM User u WHERE u.email …` but not in `count()`.
- **The Standard:** Maintain **two parallel StringBuilder instances** — one with bare field names for `count()`, one with the alias prefix for the explicit-alias find query. Keep them in sync at every append site.

```java
StringBuilder predicate = new StringBuilder();        // bare names → for count()
StringBuilder aliasedPredicate = new StringBuilder(); // u. prefix  → for FROM User u ...
Map<String, Object> params = new LinkedHashMap<>();

if (email != null && !email.isBlank()) {
    predicate.append("LOWER(email) LIKE LOWER(CONCAT('%', :email, '%'))");
    aliasedPredicate.append("LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%'))");
    params.put("email", email.strip());
}

String whereClause = aliasedPredicate.isEmpty() ? "" : "WHERE " + aliasedPredicate;

long totalCount = predicate.isEmpty()
        ? User.count()
        : User.count(predicate.toString(), params);

List<User> page = User.<User>find("FROM User u " + whereClause, panacheSort, params)
        .page(pageIndex, pageSize).list();
```

- **Corollary — avoid dual code paths:** Do not split into a "no-filter" path using `User.findAll()` and a "filtered" path using explicit-alias HQL. A `Sort.by("u.email")` passed to `findAll()` will also fail because `findAll()` uses no alias. Always use the explicit-alias form for both branches.

## Hibernate 7.4: Paging a Collection Fetch over a SINGLE_TABLE Hierarchy (POV-206)
- **The Trap:** Hibernate 7.4 stopped paging collection-fetch queries in memory (`HHH90003004`) and pages them in SQL through a derived table. When the query root is a SINGLE_TABLE root (`ContentItem` → `YouTubeVideo`), that derived table carries only the root's columns while the outer select still reads the subclass columns → `column ci1_0.channel_id does not exist`, HTTP 500. `firstResult()`/`firstResultOptional()` counts as paging (limit 1). A *to-one* join to the hierarchy from a non-polymorphic root (`Summary` → `ContentItem`) is **not** affected — the subclass columns do reach the derived table there (verified with a standalone reproducer; the POV-206 admin-page failures came from its `ContentItem` lists). A characterization test on a *non-polymorphic* query (Topic) stayed green — the existing API tests caught it.
- **Second 7.4 bug — the plan cache ignores the limit:** the same HQL executed once without and once with a limit shares one cached plan, whichever ran first. Unlimited first → the limited run silently returns **all** rows (no offset/fetch in the SQL, no warning); limited first on a SINGLE_TABLE root → the unlimited run gets the derived-table SQL and fails as above. Passes with `hibernate.query.plan_cache_enabled=false`.
- **Opt-out:** the per-query hint `org.hibernate.limitInMemory=true` restores the 7.3 in-memory paging (Hibernate 7.4 migration guide, "Limits and fetch joins"); there is no global setting (`hibernate.query.fail_on_pagination_over_collection_fetch` only makes in-memory paging fail). Not used — paging over ids is cheaper.
- **Upstream:** both bugs reproduced on 7.4.0–7.4.10 and 8.0.0.Beta3 (7.3.13 passes) with no existing HHH issue as of 2026-09-26; reports and reproducers (hibernate-test-case-templates format) are prepared outside the repo (`C:\dev\hibernate\`). See `docs/backlog/improvements.md`.
- **The Standard:**
  1. Page over ids, then load the page with its fetches: `SELECT c.id … ORDER BY …` + `.page()`, then `… WHERE c.id IN ?1` with the same sort (`ContentItem.listByIdsWithAssociations`, `AdminResource`).
  2. Single-row lookups by a unique key use `singleResultOptional()` — no limit, no derived table; fetch-joined roots are de-duplicated.
  3. Give paged sorts a unique tiebreaker (`createdAt DESC, id DESC`) and **qualify every sort field with the alias** — with fetch joins an unqualified `id`/`createdAt` is ambiguous (`Ambiguous unqualified attribute reference`); `QueryParser.parseSort(..., alias)` does it for client-chosen sorts.
  4. After a Hibernate minor upgrade, grep for `.page(` / `firstResult` on queries with `join fetch` of a collection and make sure a test executes each.
  5. Never run the same collection-fetch HQL string both limited and unlimited (e.g. one Panache `find(hql)` used for `.list()` and `.page()`) — see the plan-cache bug above. The id-first pattern avoids it: the limited query has no fetch.
