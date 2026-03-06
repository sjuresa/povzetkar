# Project Lessons Learned & Developer Knowledge Base

This centralized document encapsulates the critical technical discoveries, bug fixes, and optimization patterns formulated during the development of `Povzetkar` (specifically versions `v0.10` and `v0.11`). 

It serves as the definitive runbook and "cheat sheet" for any developer or AI Agent touching the codebase to ensure consistency and prevent the recurrence of complex systemic issues.

---

## 1. Database & Persistence (Hibernate / Panache)

### 1.1 Pagination & N+1 Queries
When building paginated APIs that return entities with collections (e.g., `User` containing `roles` or `Topic` containing `sources`), direct Panache queries can lead to severe N+1 query problems and incorrect pagination.
- **The Trap:** Using `JOIN FETCH` (e.g., `FROM User u LEFT JOIN FETCH u.roles`) on a paginated query causes Hibernate to fetch the *entire* cartesian product into memory before paginating, leading to `HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!` warnings and catastrophic performance degradation.
- **The Standard:** Always use the **two-step query pattern** for paginating relationships:
  1. Retrieve *only* the IDs of the paginated root entities first using standard `page()` methods.
  2. Use those IDs in a secondary query with `JOIN FETCH` to eagerly load the collections.
  
  ```java
  // Step 1: Get the paged IDs
  List<User> usersForPage = User.<User>find("FROM User u ORDER BY u.name ASC").page(pageIndex, pageSize).list();
  List<Long> userIds = usersForPage.stream().map(u -> u.id).toList();
  
  // Step 2: Fetch the full graph for exactly those IDs
  List<User> usersWithRoles = User.list("FROM User u LEFT JOIN FETCH u.roles WHERE u.id IN ?1", userIds);
  ```

### 1.2 Graceful Failure & String Truncation
When background jobs or data-sync commands fail, the exception stack trace can be massive.
- **The Trap:** Writing an un-truncated exception message to a database column (e.g., `VARCHAR(2000)`) triggers a `DataTruncation` transaction rollback. The status update transaction fails entirely, leaving the entity/job permanently orphaned in a `RUNNING` or `PENDING` state.
- **The Standard:** Always assert string lengths against an explicitly defined entity constant (e.g., `BackgroundJob.ERROR_MESSAGE_MAX_LENGTH`). Log the full unfiltered exception using `logger.errorf` to preserve debugging context, then securely truncate the string locally via `.substring(0, MAX_LENGTH)` before persistence.

---

## 2. API Design & Consistency

### 2.1 Database DoS Protection
- **The Standard:** Malicious end-users requesting massive page sizes (`?size=10000`) can overwhelm the database and application memory. Always clamp the `pageSize` parameter against a global constant before executing queries:
  ```java
  pageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);
  ```

### 2.2 Deterministic DTO Mapping (MapStruct)
- **The Trap:** Fetching generic associations like `Set<UserIdentity> identities` lacks a guaranteed order from the database. When mapping these to a concatenated string or extracting priority fields in a DTO (`authProvider`), the sequence can flip sporadically, causing flaky integration tests and unstable UI rendering.
- **The Standard:** Always enforce deterministic ordering using `.stream().sorted()` on collections inside custom `@Named` mapping methods before joining or extracting values.

---

## 3. Asynchronous Execution & System Commands

### 3.1 Async Transaction Boundaries
When refactoring endpoints to CQRS command models or executing orchestration `Uni` subscriptions executed in the background:
- **The Trap:** Self-invoking `@Transactional` methods from within a background executor thread (lacking a CDI/JTA request context) completely bypasses the CDI interceptor proxy. This leads to `jakarta.transaction.TransactionRequiredException` when attempting to `.persist()` entities in the background task.
- **The Standard:** Always define explicit, programmatic transaction boundaries for background DB operations. Use `io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> { ... })` or `.call(...)`. Do not rely solely on `@Transactional` for methods called within a `Runnable` or pipeline executing on a worker thread.

### 3.2 Testing Async State with Awaitility & Panache
Testing asynchronous REST actions (e.g., a `202 Accepted` that queues a job) requires `Awaitility` to assert the eventual state change.
- **The Trap:** Using Panache Active Record static methods (e.g., `BackgroundJob.listAll()`) inside `await().until(...)` conditions throws `IllegalStateException` or `ContextNotActiveException`. Panache fails to locate the active CDI context on Awaitility's separate polling thread, even if wrapped in a `QuarkusTransaction`.
- **The Standard:** Inside Awaitility polling conditions, bypass Panache completely. Inject the raw `EntityManager` (`@Inject EntityManager em;`) and execute standard HQL queries within an explicit programmatic transaction block:
  ```java
  await().atMost(5, TimeUnit.SECONDS).until(() -> {
      List<BackgroundJob> jobs = QuarkusTransaction.requiringNew().call(() -> 
          em.createQuery("FROM BackgroundJob", BackgroundJob.class).getResultList()
      );
      return jobs.size() == 1 && jobs.getFirst().status == JobStatus.COMPLETED;
  });
  ```

### 3.3 Accurate Timestamping & Auditing
- **The Standard:** Do not guess timestamps retroactively (e.g., applying "fallback" `startedAt` timestamps when a job reaches `COMPLETED` if it was somehow skipped). Enforce strict state-transition boundaries. `startedAt` must ONLY be assigned precisely when the state machine transitions from `QUEUED`/`PENDING` directly into `RUNNING` to correctly preserve latency and queue duration metrics.
