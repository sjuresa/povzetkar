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
  
  // Step 2: Fetch the full graph for exactly those IDs
  List<User> usersWithRoles = User.list("FROM User u LEFT JOIN FETCH u.roles WHERE u.id IN ?1 ORDER BY u.name ASC", userIds);
  ```

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

## Transactional Consistency in Background Jobs
- **The Problem:** Background jobs triggered from executor threads lack a default CDI request or transaction context. This leads to `ContextNotActiveException` when calling Panache methods.
- **The Standard:** Always wrap background database operations in an explicit programmatic transaction: `QuarkusTransaction.requiringNew().call(() -> { ... })`.

## Eager Loading for Background Processing
- **The Problem:** When processing entities in a *new* transaction (common in background jobs), lazy-loaded collections (like `@ManyToMany`) will throw `LazyInitializationException` if they weren't fetched in the same transaction session.
- **The Standard:** Implement dedicated finders that use `JOIN FETCH` (e.g., `Topic.findByIdWithSources()`) to eagerly load all required associations before passing the entity to the background worker. Avoid relying on open-session-in-view patterns for background tasks.

## Hibernate Session Isolation in Parallel Tasks (POV-160)
When executing parallel database tasks using a `ManagedExecutor` or similar thread pool in Quarkus/Panache:
- **The Trap:** Passing a JPA entity (e.g., a `Topic` fetched on the main request thread) into multiple background lambdas. Even if each lambda uses `QuarkusTransaction.requiringNew()`, Hibernate may attempt to lazily initialize collections or manage the entity state using the *original* session from the parent thread. This leads to `java.util.ConcurrentModificationException` within Hibernate's `ResultsetsTrackingContainer` or `PSQLException: This statement has been closed` errors.
- **The Standard:** **Never** pass managed entities across thread boundaries. Instead, pass the **entity ID** (`Long topicId`) to the background task. Inside the task's own transaction, refetch the entity from the database using `Topic.findById(topicId)`. This ensures each parallel task has its own isolated Hibernate session and state tracking.

## EXISTS Subquery vs JOIN for Filtered Pagination (POV-161)
The EXISTS subquery pattern was successfully validated for source-based briefing filtering.
- **Pattern:** `EXISTS (SELECT 1 FROM Summary s WHERE s MEMBER OF summaries AND s.contentItem.contentSource.id = :sourceId)` correctly filters briefings that contain summaries from a specific source without introducing cartesian products on the paginated main query.
- **Confirmed:** This approach correctly interacts with Panache's `.count()` and `.page()` methods, producing accurate pagination metadata.
