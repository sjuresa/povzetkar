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
  // Step 1: Get the paged IDs (Select only IDs to avoid loading root entities)
  List<Long> userIds = User.<Long>find("SELECT u.id FROM User u ORDER BY u.name ASC").page(pageIndex, pageSize).list();
  
  if (userIds.isEmpty()) {
      return Collections.emptyList();
  }
  
  // Step 2: Fetch the full graph for exactly those IDs
  List<User> usersWithRoles = User.list("FROM User u LEFT JOIN FETCH u.roles WHERE u.id IN ?1 ORDER BY u.name ASC", userIds);
  ```
*   **Job Stacking UX:** Reverting to stacked toast notifications for background jobs provided a much more informative and less aggressive update pattern than replacing a single toast.
*   **Source Transparency (POV-148):** The implementation of the Source Library and Source Detail pages significantly improved system observability. Users can now "drill down" from a high-level briefing to the raw source items that informed it, closing the loop on content transparency.

### 1.2 Graceful Failure, String Truncation & Error Sanitization
When background jobs or data-sync commands fail, the exception stack trace can be massive and contain sensitive structure details.
- **The Trap:** Writing an un-truncated exception message to a database column triggers a `DataTruncation` transaction rollback. Exposing the raw exception string to the API layer can also lead to the leakage of internal stack traces or credentials if they are part of the exception context.
- **The Standard:** Always assert string lengths against an explicitly defined entity constant (e.g., `BackgroundJob.ERROR_MESSAGE_MAX_LENGTH`). Log the full unfiltered exception internally using `logger.errorf` to preserve debugging context, but securely sanitize the string (e.g., generic user-facing message) before persisting it to an entity exposed via API.

### 1.3 JPQL Constructor Projections
- **The Trap:** Fetching full Hibernate entities and their associations (via `JOIN FETCH`) only to map them to DTOs in the application layer. This causes "over-fetching" (retrieving data from the DB that is never used) and high memory allocation.
- **The Standard:** Use JPQL constructor expressions (`SELECT NEW com.dto.MyDTO(p.id, p.name) FROM ...`) for read-only aggregation or summary endpoints. This moves the mapping logic to the database level, significantly reduces I/O traffic, and lowers the memory footprint of the application.

### 1.4 Stable Identity (Equals & HashCode)
- **The Trap:** Relying on default `Object.equals()` or standard JPA identity can cause entities to be treated as different objects when reloaded across transaction boundaries or passed into asynchronous collection-based task queues.
- **The Standard:** Always implement stable `equals()` and `hashCode()` using **Business Keys** (e.g., `slug`, `username`, `videoId`). This guarantees consistent object identity across different Hibernate sessions and prevents the creation of logical duplicates in sets or maps during background processing.

---

### 1.5 Consolidated Count Queries

When multiple aggregate counts are needed at once (e.g., for a dashboard or metrics endpoint), avoid multiple roundtrips with `count()`. Use a single native query with sub-selects to collapse them into one result row.

```java
Object[] result = (Object[]) em.createNativeQuery(
        "SELECT " +
                "(SELECT COUNT(*) FROM table_a), " +
                "(SELECT COUNT(*) FROM table_b WHERE flag = true)")
        .getSingleResult();
// Map results using result[0], result[1], etc.
```

### 1.6 Independent Transaction for Auditing
- **The Trap:** Persisting audit logs or job audit trails within the same transaction as the main business logic. If the business transaction rolls back (e.g., due to a constraint violation), the audit record is also lost, leaving no trace of the failure.
- **The Standard:** Always wrap persistence of audit records (API audits, job execution audits) in **`QuarkusTransaction.requiringNew()`**. This ensures the audit record is committed independently of the success or failure of the main operation, providing a reliable diagnostic trail.

---

## 2. API Design & Consistency

### 2.1 Database DoS Protection
- **The Standard:** Malicious end-users requesting massive page sizes (`?size=10000`) can overwhelm the database and application memory. Always clamp the `pageSize` parameter against a global constant before executing queries:
  ```java
  pageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);
  ```

### 2.2 Deterministic DTO Mapping (MapStruct)
- **The Trap:** Fetching generic associations like `Set<UserIdentity> identities` lacks a guaranteed order from the database. When mapping these to a concatenated string or extracting priority fields in a DTO (`authProvider`), the sequence can flip sporadically, causing flaky integration tests and unstable UI rendering.
- **The Standard:** Always enforce deterministic ordering using `.stream().sorted(Comparator.comparing(e -> e.id))` on collections inside custom `@Named` mapping methods before joining or extracting values. Never rely on natural `.sorted()` order unless the entity explicitly implements `Comparable`, as this can cause runtime `ClassCastException` failures.

### 2.3 Dependency Injection
- **The Trap:** Using field injection (e.g., `@Inject UserContextService userContextService;`) allows objects to be created in an invalid state, prevents fields from being `final`, and makes unit testing difficult because dependencies aren't explicitly required during instantiation.
- **The Standard:** Always use **constructor injection** for dependencies. This ensures immutable (`final`) references, explicitly declares required dependencies, and makes the code more robust and easier to test without reflection. If a proxy-able bean requires a no-args constructor for CDI, provide a `protected` no-args constructor that sets final fields to `null`.

### 2.4 Centralized Security Constants
- **The Trap:** Using raw string literals (e.g., `@RolesAllowed("ADMIN")`) or manual `Permission.ROLE.name()` calls inconsistently across the app. Raw strings are prone to typos, and while `Enum.name()` is safe for logic, it CANNOT be used in annotations like `@RolesAllowed` because they require compile-time constants.
- **The Standard:** In your `Permission` enum, provide `public static final String` constants prefixed with `V_` (e.g., `V_ADMIN = "ADMIN"`) specifically for use in annotations. For all programmatic logic (e.g., `identity.hasRole(...)`), always use the idiomatic `Permission.ADMIN.name()`. This ensures absolute sync between the enum definition, the security metadata, and the execution logic.

### 2.5 Global Response Envelopes
- **The Trap:** Manually wrapping every API response in an `ApiEnvelopeDTO` (e.g., `return Response.ok(new ApiEnvelopeDTO<>(dto)).build()`) is repetitive, error-prone, and clutters the resource logic. It also makes it difficult to change the envelope structure globally.
- **The Standard:** Use a JAX-RS `ContainerResponseFilter` (e.g., `GlobalResponseEnvelopeFilter`) to automatically wrap all successful responses in a standard envelope. API resources should return the **raw DTO** or **`PaginatedResponse`** directly. This keeps the resource code clean and ensures a consistent response structure across the entire API.
- **Exception:** Do not wrap responses that are already envelopes (like `ApiErrorDTO`) or those that should not have an envelope (e.g., raw file downloads or non-JSON responses).
- **Filter Precision:** Enveloping filters must strictly target successful (2xx) status codes. Wrapping error responses (4xx/5xx) leads to inconsistent "double-enveloped" structures that break client-side error handling logic.
- **Name Binding:** Prefer JAX-RS Name Binding (custom annotations like `@Enveloped`) over URL path prefix scanning to target filters. URL scanning is brittle and prone to bypasses as new resources are added.

### 2.6 Optimistic Locking (Concurrency Control)
- **The Trap:** Deleting or updating entities based solely on their ID without version checks can lead to the "lost update" problem, where concurrent changes by different users overwrite each other or an entity is deleted while another user is trying to update it.
- **The Standard:** Implement **Optimistic Locking** using the `@Version` field on entities. 
  1. For `PATCH`, `PUT`, and `DELETE` operations, the client **MUST** send the current version of the entity in the **`If-Match`** header (usually in quotes, e.g., `If-Match: "5"`).
  2. The server must check this version against the database version before proceeding.
  3. If the header is missing, return **`428 Precondition Required`**.
  4. If the versions mismatch, return **`409 Conflict`** (or a specific Optimistic Lock error).
  5. If the header is malformed (e.g., not an integer), return **`400 Bad Request`**.
  6. In JAX-RS resources, use a helper method (e.g., `baseApiResource.checkOptimisticLock(entity, ifMatch)`) to centralize the version comparison check, while presence validation is handled globally by `OptimisticLockingFilter`.

### 2.7 Exception Mapping vs Custom Responses
- **The Trap:** Manually constructing `ApiEnvelopeDTO.error(...)` within resource catch blocks leads to fragmented error logic and risks exposing sensitive internal details.
- **The Standard:** Always prefer throwing standard JAX-RS exceptions (e.g., `BadRequestException`, `NotFoundException`, `ForbiddenException`) and let centralized `ExceptionMapper`s handle the translation into the standard RFC 7807 global envelope.
- **The Standard:** A catch-all `ExceptionMapper<Exception>` MUST be used to intercept unexpected 500 errors. It must log the raw stack trace internally using `StructuredLogger.error(...)` to preserve debug context, but emit a sanitized, generic error message (e.g., "An unexpected error occurred") inside the API envelope to prevent infrastructure leaks.
- **The Standard:** For simple confirmation messages (e.g., "Operation started"), use a dedicated `MessageDTO` instead of `Map.of("message", ...)`. This provides a stable OpenAPI schema and improved type safety.

### 2.8 Systemic Pagination & Filtering
- **The Trap:** Manually building filter components (e.g., `startDate >= :start`) inside each resource file creates repetitive string manipulation logic that is difficult to maintain and potentially susceptible to injection if parameters aren't safely mapped. Relying on default panache sorts without `?sort` parameter parsing limits API flexibility. Dynamically parsing sort columns from user input without validation opens up HQL sort-injection vulnerabilities.
- **The Standard:** Use central utility builders like `FilterBuilder` to perform conditional `AND` logic safely with map-based parameter binding. Parse sequential `?sort` HTTP parameters centrally into `io.quarkus.panache.common.Sort` objects (using `QueryParser`) before handing them off to the repository layer. **Always** pass a strict allow-list `Set<String>` of permitted entity fields to `QueryParser.parseSort` to prevent HQL injection in `ORDER BY` clauses.

### 2.9 Code Style & Readability: Imports over Fully Qualified Names
- **The Trap:** Using fully qualified class names (e.g., `java.util.Set`, `io.quarkus.panache.common.Sort`) inline within method bodies clutters the code, reduces readability, and makes the logical flow harder to scan.
- **The Standard:** Always use standard `import` statements at the top of the file for all external classes and interfaces. Reserve fully qualified names strictly for resolving explicit class name collisions within the same file.

### 2.10 Idempotency & Caching Response Filters
- **The Trap:** When implementing idempotency caching with JAX-RS `ContainerResponseFilter`, writing the DTO to a cache and later fetching it as a `JsonNode` (via `ObjectMapper`) can cause it to be processed *again* by global envelope filters (like `GlobalEnvelopeFilter`). This leads to nested `"data": {"data": ...}` "double enveloping".
- **The Standard:** Use `@NameBinding` annotations (e.g., `@Idempotent`) to restrict where caching filters apply. Additionally, ensure global filters like `GlobalEnvelopeFilter` are smart enough to bypass generic `com.fasterxml.jackson.databind.JsonNode` objects or ensure filter execution priorities (`@Priority`) explicitly execute caching *after* enveloping.

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

### 3.4 Integration Testing with `@TestSecurity`
When testing endpoints protected by `@RolesAllowed` that accept multiple roles (e.g., `@RolesAllowed({"user", "admin"})`), the `@TestSecurity` annotation must provide **all** roles expected by the authorization logic if the test relies on specific user attributes.
- **The Trap:** Using `@TestSecurity(user = "testuser", roles = {"ADMIN_JOB_UPDATE"})` might fail with `401 Unauthorized` or `403 Forbidden` if the endpoint or surrounding security context also expects the base `"user"` role for standard operations.
- **The Standard:** Always map the exact full suite of roles the test user hypothetically possesses: `@TestSecurity(user = "testuser", roles = {"user", "ADMIN_JOB_UPDATE"})`.

### 3.5 Explicit Timezones in Testing & API Logic
When asserting logic bound to rolling windows (e.g., "last 7 days") or specific dates, relying on the system default timezone is fragile.
- **The Trap:** Using `LocalDate.now()` in both the application and the test suite can lead to inconsistent behavior across different environments (local vs CI) or when tests run across midnight boundaries.
- **The Standard:** Always instantiate dates and timestamps explicitly relative to UTC. Use `LocalDate.now(java.time.ZoneOffset.UTC)` for server-side logic and identically in the test builder setup to guarantee deterministic evaluation.

### 3.6 Declarative Authorization Semantics (`@RolesAllowed`)
When securing endpoints with multiple roles via `@RolesAllowed({"ROLE_A", "ROLE_B"})` in Quarkus:
- **The Standard:** Remember that passing an array of roles evaluates as a logical **OR** condition, meaning the user only needs *at least one* of the specified roles to gain access to the method. Do not natively assume an `AND` condition or strip declarative annotations for manual `if` block checks unless specifically building complex, field-level access control within the method scope itself.

---

## 22. Quarkus 3 & Jakarta EE Migration (v0.14.0)

### 22.1 MapStruct Component Model
- **The Trap:** Using `@Mapper(componentModel = "cdi")` in a Quarkus 3 project can lead to `jakarta.enterprise.inject.spi.DeploymentException` if the underlying implementation (like `TopicMapperImpl`) uses `@javax.inject.Inject` (the legacy namespace) instead of `@jakarta.inject.Inject`. This is common when MapStruct versions are not perfectly aligned with the Jakarta EE 10 baseline.
- **The Standard:** Always explicitly use **`componentModel = "jakarta"`** for all MapStruct mappers in Quarkus 3+. This ensures the generated code utilizes the correct `jakarta.*` annotations for dependency injection, preventing deployment-time failures.

### 22.2 Transaction Visibility in Integration Tests
- **The Trap:** When a service method (e.g. `OrchestrationManager`) executes internal logic inside `QuarkusTransaction.requiringNew()`, it starts and commits a *separate* database transaction. If the main test method (also transactional) just created the data, that data will be **invisible** to the background transaction because it hasn't been committed yet from the test's perspective.
- **The Standard:** Before triggering an asynchronous background job or a `requiringNew()` service call in an integration test, you MUST manually synchronize the persistence context. Use **`entityManager.flush()`** to push pending changes to the database and **`entityManager.clear()`** to ensure subsequent reads see the actual DB state. This bridges the isolation gap between the test runner and the background workers.

---

## 23. API Robustness & Idempotency (v0.14.0)

### 23.1 Idempotent "Add" Operations
- **The Trap:** Returning `409 Conflict` when a user tries to "add" an entity that already exists (e.g. a Content Source) can lead to frustrating UI experiences and redundant error handling logic.
- **The Standard:** For "creation" endpoints that are conceptually "idempotent adds," the API should return **`200 OK`** and the existing object if it already exists. This simplifies the frontend's "Success" path and prevents unnecessary error toasts for non-erroneous states.

### 23.2 Safe Enum Conversion
- **The Trap:** Passing a raw string name directly to `Enum.valueOf()` in a REST resource. If the input is malformed, it throws an internal `IllegalArgumentException`, which the default Quarkus exception mapper might catch as a `500 Internal Server Error`, obscuring the real cause (bad input).
- **The Standard:** Always wrap enum conversions from request parameters in a `try-catch` block and throw a specialized **`BadRequestException`** with a descriptive message (e.g. "Invalid platform type: 'unknown'"). This ensures the client receives a correct `400` status code instead of a generic system crash.

### 3.7 Asynchronous Testing: Avoiding `Thread.sleep`
- **The Trap:** Using `Thread.sleep()` to wait for asynchronous operations in tests (e.g., waiting for a background job to finish or a lock to appear in the database). This leads to flaky tests, slow execution, and brittle code that fails unpredictably depending on environmental load.
- **The Standard:** Always use **Awaitility** to poll for the expected state change. Awaitility provides built-in support for timeouts, polling intervals, and clear failure messages.
  ```java
  // Correct way to wait for async state change
  await().atMost(5, TimeUnit.SECONDS).until(() -> {
      return QuarkusTransaction.requiringNew().call(() -> 
          IdempotentRequest.count("idempotencyKey = ?1", key) > 0
      );
  });
  ```
- **Note:** If you need to simulate a "slow" operation in a test-only resource, use a busy-wait loop that respects thread interruption (`Thread.onSpinWait()`) rather than blocking the thread via `sleep()`.

---

## 4. Reactive Programming (Mutiny)

### 4.1 Separation of I/O and Transformation
When building reactive pipelines with Mutiny (e.g., `Uni` or `Multi`), you should strictly separate blocking operations from data transformations.
- **The Trap:** Mixing database queries and DTO mapping within the same worker-thread block (e.g., inside `QuarkusTransaction.requiringNew().call(...)`) obscures the pipeline's structure and can keep worker threads occupied with CPU-bound mapping logic longer than necessary.
- **The Standard:** Use worker threads purely for the blocking I/O (the database query) and explicitly switch back to the event loop using `.emitOn(Infrastructure.getDefaultExecutor())` before performing CPU-bound data transformations (like DTO mapping) via `.map()`. This ensures worker threads are released as soon as the I/O is complete.
- **Optimization:** Avoid redundant `.emitOn(...)` switches if the "orchestration" logic (e.g., a `flatMap`) simply dispatches more worker pool tasks. Context switches have a non-trivial cost; staying on the worker thread for a few milliseconds of dispatching logic is more efficient than switching back to the event loop just to immediately switch away again.
  
  ```java
  Uni<List<MyDTO>> result = executeInTransaction(() -> em.find(...)) // I/O on Worker Thread
      .emitOn(Infrastructure.getDefaultExecutor())                 // Switch back to Event Loop
      .map(entities -> entities.stream().map(mapper::toDTO).toList()); // Transformation on Event Loop
  ```

### 4.2 Blocking Initial Logic in Reactive Endpoints
- **The Trap:** Assuming that a method returning `Uni` is automatically "safe" from blocking the event loop. The method body itself starts execution on the event loop thread.
- **The Standard:** If any logic *before* the first asynchronous operation in the method is blocking (e.g., resolving the current user from the database), you must either use `@Blocking` on the method or explicitly offload the initial logic: `Uni.createFrom().item(this::getBlockingData).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())`.

### 4.3 Parallelizing Independent Queries (BFF Aggregation)
When building "Backend-for-Frontend" (BFF) aggregation endpoints that fetch multiple disjoint datasets:
- **The Trap:** Executing database queries sequentially leads to additive latency, and aggregating results via positional access (e.g., `tuple.getItem1()`) is fragile and lacks type safety.
- **The Standard:** Use `Uni.combine().all().unis(u1, u2, u3).with(this::assemble)` to execute independent, worker-offloaded queries in parallel. The `with()` method provides a fully typed signature for the aggregation function (Function3, etc.), and using a **method reference** (e.g., `this::assemble`) is the most idiomatic way to handle the result aggregation.
- **Resilience:** For high-visibility dashboards, wrap each individual `Uni` with `.onFailure().recoverWithItem(defaultValue)` before combining. This implements **graceful degradation**, ensuring that a single failing background query doesn't crash the entire request, allowing the UI to display partial data.
- **Centralization:** Centralize specialized JPQL queries (especially those using `select new (...)` projection) as `public static final String` constants within the target entity. This improves reusability, keeps the resource class clean, and maintains the query logic close to the domain model it pertains to.

### 4.4 Transaction Type Safety in Pipelines
- **The Trap:** Assuming `QuarkusTransaction.requiringNew().call(Supplier<T>)` returns a reactive `Uni<T>`. In the standard Hibernate ORM (blocking) extension, it returns the raw type `T`.
- **The Standard:** When integrating blocking transactions into a Mutiny pipeline, you must manually wrap the call: `Uni.createFrom().item(() -> QuarkusTransaction.requiringNew().call(supplier::get)).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())`. This ensures the blocking transaction starts and ends on the correct worker thread.

### 4.5 Collection Type Safety in Mutiny
- **The Trap:** Using `List.of()` as a default value in a reactive pipeline (e.g., in `recoverWithItem()` or as a fallback in a generic method). Without explicit type arguments, `List.of()` creates an empty `List<Object>`. Due to type erasure, this can be unsafely wrapped in a `Uni<List<T>>`, only to fail with a `ClassCastException` at runtime when the pipeline tries to consume the item as a `List<T>`.
- **The Standard:** Always prefer **`Collections.emptyList()`** for empty collection defaults in reactive chains. It allows for better type inference and prevents runtime casting failures. If the compiler still struggles, provide an explicit generic hint at the method call site (e.g., `this.<List<T>>executeInTransactionIf(...)`).

---

## 5. Development Process & Architecture

### 5.1 Spec-First API Design
- **The Trap:** Starting with backend implementation (Entities/Repositories) often leads to an API that exposes internal database structures, resulting in a non-idiomatic "Backend-for-Frontend" (BFF) experience.
- **The Standard:** Always finalize the **Frontend Specification** and **API Contract** *before* beginning backend implementation. This ensures the API is tailored to UI requirements, defines consistent envelope contracts, and catches design inconsistencies (pagination, enums, error models) during the design phase rather than implementation.

### 5.2 Test Data Builder Pattern
- **The Trap:** Manually instantiating complex entities in every test file leads to brittle test suites. Adding a new mandatory field to an entity requires a "mass-fix" across hundreds of test lines.
- **The Standard:** Implement the **Test Data Builder Pattern**. Centralize entity creation in fluent builders (e.g., `TopicBuilder`, `BriefingBuilder`) with sensible defaults and `.withId()` capabilities. This decouples the test logic from entity structural changes and significantly reduces boilerplate.

---

## 6. API Documentation (OpenAPI / Swagger)

### 6.1 Documentation as Truth
- **The Standard:** Use `quarkus-smallrye-openapi` to generate the API contract directly from the code. This ensures the documentation never drifts from the actual implementation ("Code is Truth").
- **Implementation:** 
  - Annotate JAX-RS methods with `@Operation` for summaries and descriptions.
  - Use `@APIResponse` and `@Content` (with `@Schema`) to document specific response bodies and status codes.
  - For complex generic responses like `ApiEnvelopeDTO<T>`, create `private static` inner helper classes that extend the generic DTO with a specific type and use them in the `@Schema(implementation = ...)` attribute. This allows OpenAPI to generate precise schemas for each endpoint's response envelope.

### 6.2 Production Security
- **The Trap:** Accidentally exposing Swagger UI in production provides a detailed map of your API to potential attackers.
- **The Standard:** Always explicitly disable Swagger UI in the production profile.
- **Configuration:** Use `%prod.quarkus.swagger-ui.always-include=false` in `application.properties`. This is a critical security measure to minimize the attack surface of the application.

---

## 7. Automated Security Scanning (CI & SAST)

### 7.1 Static Application Security Testing (SAST)
- **The Trap:** Integrating tools like SpotBugs into a legacy or actively evolving codebase without configuring severity thresholds can cause the build to fail on dozens of pre-existing, low-severity warnings. This instantly blocks the CI pipeline and halts development momentum.
- **The Standard:** Always explicitly configure the threshold (e.g., `<threshold>High</threshold>` in `pom.xml`) for SAST tools to block only critical violations while progressively addressing lower-severity technical debt. Additionally, be aware that mutating inputs sequentially can trigger "dead store" false positives during bytecode analysis.

### 7.2 Dependency Vulnerability Scanning
- **The Trap:** Relying solely on local dependency checks introduces developer friction and lacks centralized visibility of inherited security issues.
- **The Standard:** Integrate vulnerability scanning directly into the CI/CD pipeline. Configure explicit exit codes (e.g., `exit-code: '1'`) and severity levels (e.g., `severity: 'CRITICAL,HIGH'`) to unconditionally break the build logically when actionable CVEs are discovered, guaranteeing insecure code is never merged into the main branch.

---

## 8. Observability & Request Tracing

### 8.1 MDC Propagation to Background Threads & Correlation IDs
- **The Trap:** Assuming that MDC values set on a request thread (e.g., a `correlationId` from a JAX-RS `ContainerRequestFilter`) are automatically available in background tasks dispatched via `Infrastructure.getDefaultExecutor().execute(...)`. MDC is inherently thread-local — it does NOT propagate to worker threads spawned by blocking executors.
- **The Standard:** Always explicitly capture MDC values (specifically the `correlationId`) on the request thread *before* dispatching the background task and restore them inside the worker lambda:
  ```java
  final String correlationId = MDC.get("correlationId");
  Infrastructure.getDefaultExecutor().execute(() -> {
      MDC.put("correlationId", correlationId);
      try {
          // ... background work ...
      } finally {
          MDC.remove("correlationId"); // Prevent leaks to pooled threads
      }
  });
  ```
- **Note:** MDC *does* propagate transparently across Vert.x event loop hops (reactive chains via `Uni`/`Multi`), so this manual pattern is only needed for explicit worker thread dispatches (blocking code).

### 8.2 Audit Logging via ContainerResponseFilter
- **The Standard:** Use a `@Provider`-annotated `ContainerResponseFilter` to persist audit records. This is the only place in the JAX-RS pipeline where both the request context and the final response status are simultaneously available, ensuring the final HTTP status is captured correctly even after exception mappers run.
- **Isolation:** Always wrap audit persistence in `QuarkusTransaction.requiringNew()` to ensure the audit write is independent of any business transaction that may have already committed or rolled back. This decouples auditing from business logic.
- **Resilience:** The audit filter must `catch (Exception e)` and log a warning, never re-throwing. Audit failures must never propagate as API errors to clients.

---

## 9. Server-Sent Events (SSE) & Real-Time Updates

### 9.1 Resource Management (@PreDestroy)
- **The Trap:** Opening a `BroadcastProcessor` or other resource in a `@Singleton` or `@ApplicationScoped` bean without an explicit cleanup hook. This can lead to resource leaks (e.g., active threads or open streams) particularly during hot reloads or application shutdown.
- **The Standard:** Always implement a `@PreDestroy` method to explicitly close or complete the processor:
  ```java
  @PreDestroy
  void shutdown() {
      processor.onComplete();
  }
  ```

### 9.2 SSE Event Order in Integration Tests
- **The Trap:** Firing business events (like `onJobStatusChanged`) immediately after starting an `SseEventSource` in a test. This often results in race conditions where the event is missed because the client discovery/handshake isn't finished.
- **The Standard:** Always push an initial `CONNECTED` event (or similar) from the `GET` stream endpoint and use `await().until(...)` in the test to verify receipt of this event before triggering any business logic that produces subsequent events. This guarantees the subscriber is active and listening.

### 9.3 Client Factory Discovery
- **The Trap:** In tests, `ClientBuilder.newClient()` may fail with a "provider not found" error if the JAX-RS implementation is not correctly discovered by the `FactoryFinder`.
- **The Standard:** Ensure that correctly scoped dependencies (like `quarkus-rest-client-jackson` in `test` scope) are present. Avoid using `SseEventSource` if you can't guarantee a clean provider lookup; however, for Quarkus integration tests, standard Resteasy Reactive clients should work if the classpath is correct.

---

## 10. Observability & Request Tracing (Extended)

### 10.1 Logger Consistency
- **The Trap:** Using `System.out` or `System.err` for logging in tests or utility classes. This bypasses the formal logging configuration, lacks timestamping/metadata, and leads to messy console output that is hard to filter or parse in CI environments. Mixing `org.jboss.logging.Logger` and `slf4j` facades in the same project can lead to confusing configuration and inconsistent MDC behavior.
- **The Standard:** Always standardize on **SLF4J (`org.slf4j.Logger`)** as the primary logging facade for maximum compatibility and consistency with `RUNBOOK.md`. For structured logging with key-value pairs, use the custom `StructuredLogger` helper.

### 10.2 StructuredLogger Argument Count Safety
- **The Trap:** Passing an odd number of arguments for key-value pairs in `StructuredLogger` (e.g., including an exception but neglecting to add the corresponding key/value pairs) triggers an `IllegalArgumentException` during log message construction. This can crash the application or a filter during critical error paths.
- **The Standard:** Always ensure that `StructuredLogger` calls have an **even number** of arguments for key-value pairs (excluding the format string or exception). When logging an exception with additional context, use the explicit `(Throwable t, Object... keyValuePairs)` overload to ensure the stack trace is properly captured without breaking the key-value alignment.

---

## 11. Monorepo & Multi-module Maven

### 11.1 Cross-module Resource Loading
- **The Trap:** Using `${project.basedir}` in a submodule `pom.xml` to refer to resources located at the monorepo root (e.g., `CHANGELOG.md` or global docs). Using relative paths like `${project.basedir}/..` is a common but brittle workaround that breaks if the module structure becomes deeper.
- **The Standard:** To provide a stable reference point to the monorepo root from any submodule, define a property in the root `pom.xml` using Maven's built-in `${maven.multiModuleProjectDirectory}` property (e.g., `<project.root.basedir>${maven.multiModuleProjectDirectory}</project.root.basedir>`). Use this property in submodules to access root-level resources. This approach is more robust than using relative paths like `${project.basedir}/..`.

### 11.2 Aggregator vs. Parent POM
- **The Trap:** Conflating the role of the "Aggregator" (the `pom.xml` that lists `<modules>`) and the "Parent" (the `pom.xml` that submodule `<parent>` blocks inherit from).
- **The Standard:** In a clean monorepo, the root `pom.xml` should serve both roles. It defines the modules for orchestration and provides the `<dependencyManagement>` and `<properties>` shared by all modules. Submodules should explicitly point to the root via `<parent>` to ensure unified versioning and configuration.

### 11.3 Targeted Module Execution
- **The Standard:** When working in a monorepo, avoid running full builds from the root for every change. Use the `-pl <module-name>` (project list) and `-am` (also make dependencies) flags to target specific modules:
  ```bash
  ./mvnw quarkus:dev -pl backend
  ./mvnw clean package -pl backend -am
  ```
  This speeds up the development loop and prevents unnecessary re-building of unrelated modules (like the future frontend).

---

## 12. Frontend Development (React / Vite)

### 12.1 ViteSSG & Beta Compatibility
Vite's ecosystem (especially SSG plugins) moves fast. When using plugins like vite-react-ssg, you may encounter type mismatches with the latest React, react-router-dom, or Vite versions.
- **The Trap:** Spending hours trying to perfectly align @types/* versions when the plugin itself has internally mismatched peer dependencies or hardcoded types in its dist.
- **The Standard:** If a required import like ViteReactSSG is clearly exported in the source but triggers a [MISSING_EXPORT] error or TS type error during build, verify the exact export name in node_modules. If types are the only blocker, use // @ts-expect-error - <reason> with a specific explanation. This keeps the build passing while acknowledging the technical debt.

### 12.2 Tailwind 4 & Mono-CSS
Tailwind CSS 4 introduces a 'CSS-first' approach.
- **The Trap:** Trying to configure Tailwind 4 using a legacy tailwind.config.js with internal plugins that haven't been ported yet.
- **The Standard:** Leverage @tailwindcss/vite and import Tailwind directly in your CSS: @import 'tailwindcss';. Keep configurations in CSS variables where possible.

### 12.3 Monorepo Orchestration (NPM + Maven)
In a monorepo containing both Maven (Java) and NPM (JavaScript) projects:
- **The Trap:** Relying on developers to manually run npm install and mvn compile in separate folders, leading to mismatched build states.
- **The Standard:** Use a root-level package.json with npm-run-all or simple shell scripts to orchestrate cross-project commands. High-level commands like npm run dev should ideally start both the backend and frontend simultaneously.

---

## 13. API Integration (Orval & Axios)

### 13.1 Strict Linting in API Clients
When building production-grade API clients with strict ESLint rules (2025 standards):
- **The Trap:** Using `any` for raw JSON payloads from `JSON.parse` or environment variables (e.g., `import.meta.env`). This triggers `no-unsafe-assignment` and `no-unsafe-return` warnings.
- **The Standard:** Always use `unknown` as the initial type and perform explicit casting via specialized interfaces or `Record<string, string>`. For environment variables, use `(import.meta.env as Record<string, string>)`.
  
### 13.2 Type-Safe Error Handling in Envelopes
Our backend returns all errors wrapped in an `ApiEnvelope` containing a `code` and `message`.
- **The Standard:** In the Axios response interceptor, when an envelope error is detected, reject the promise with a proper `Error` object. To preserve the API error code, use an intersection type `(error as Error & { code?: string }).code = apiCode;`. This allows downstream components to use the standard `error.message` while still having access to the specific error code for conditional logic (e.g., showing a specific modal for `TOPIC_LIMIT_EXCEEDED`).

### 13.3 Header Normalization for Axios
Orval generates `options.headers` as `HeadersInit` (which can be `Headers`, `string[][]`, or `Record<string, string>`). Axios expects a plain object.
- **The Standard:** Always use a normalization helper in the custom fetcher:
  ```typescript
  const toAxiosHeaders = (headers: HeadersInit | undefined): Record<string, string> => {
      if (!headers) return {};
      if (headers instanceof Headers) {
          const obj: Record<string, string> = {};
          headers.forEach((v, k) => obj[k] = v);
          return obj;
      }
      return Array.isArray(headers) ? Object.fromEntries(headers) : (headers as Record<string, string>);
  };
  ```

### 13.4 Idempotency Key Injection
- **The Standard:** The global interceptor should automatically inject an `Idempotency-Key` UUID for all `POST /commands/*` requests. If the request already has one (e.g., passed manually to handle retries), the interceptor must preserve the existing one.

### 13.5 Axios Cancellation & Global Logging
- **The Trap:** Logging all failed requests in a global Axios interceptor without checking for cancellations. In React 18 (Strict Mode) and TanStack Query, requests are frequently aborted by design (e.g., component unmount, rapid re-renders). Logging these as "Errors" leads to confusing console noise and false alarms about network stability.
- **The Standard:** Always check `axios.isCancel(error)` in the error interceptor. These should be ignored or logged at `debug` level, never as `error`.
  ```typescript
  if (axios.isCancel(error)) {
      return Promise.reject(error); // Reject silently
  }
  logger.error('[API] Network Error', { message: error.message });
  ```

---

## 14. Frontend Routing & Auth (TanStack Router + Zustand)

### 14.1 Eliminating "Flash of Unauthenticated Content" (FOUC)
- **The Trap:** Checking auth status only inside components causes the "Unauthenticated" view (like a Landing Page) to flicker for a few milliseconds before the app realizes the user is logged in.
- **The Standard:** Use TanStack Router's `beforeLoad` hook in combination with a state-based "isLoaded" flag. Perform the silent auth check (`GET /api/v1/users/me`) inside `beforeLoad` if the state is not yet loaded. Using `throw redirect(...)` inside `beforeLoad` ensures the route transition never completes into the wrong state.

### 14.2 ESLint & TanStack Router Redirects
- **The Trap:** TanStack Router's `redirect()` function returns a special object intended to be thrown. Some ESLint configurations (like `@typescript-eslint/only-throw-error`) will flag this as an error because it's not a subclass of `Error`.
- **The Standard:** Use `// eslint-disable-next-line @typescript-eslint/only-throw-error` for lines throwing `redirect()`. This acknowledges the framework's intent while keeping strict linting for actual application errors.

### 14.3 Vite & Beta-Plugin Stability
- **The Trap:** Using the absolute latest version of Vite (e.g., Vite 8) while the ecosystem (Tailwind 4, Vitest) is still providing beta-grade support for the previous major version results in severe peer dependency conflicts and build failures.
- **The Standard:** When using bleeding-edge ecosystems like Tailwind 4, pin the build tool (Vite) to the version currently targeted by the plugins. "Bleeding edge" + "Bleeding edge" often equals a broken dev environment.
### 14.4 Standardizing Linting Suppressions (ESLint 9+)
- **The Trap:** Using deprecated or inconsistent suppression rules (e.g., `@typescript-eslint/no-throw-literal`) leads to build warnings and unpredictable linting behavior in modern environments.
- **The Standard:** Always use the latest rule names (e.g., `@typescript-eslint/only-throw-error`). When dealing with framework-specific patterns that use `any` (like test mocks or API response handling), prefer `@typescript-eslint/no-unsafe-assignment` or `@typescript-eslint/no-unsafe-return` over a blanket `no-explicit-any`, as it provides more granular oversight.

---

## 15. Frontend Real-time & Notifications (SSE)

### 15.1 EventSource Lifecycle Management
- **The Trap:** Initializing `EventSource` directly in a component body or incorrectly in a `useEffect` without proper cleanup and ref guards. In React Strict Mode, this leads to double-connections on mount, which can exhaust backend connection pools and cause "Ghost Toast" effects in the UI.
- **The Standard:** Always use a `useRef<EventSource | null>(null)` guard alongside `useEffect`. The ref ensures that if an instance exists (due to a double-mount in dev), we don't spawn a second stream. Always return a cleanup function that calls `es.close()`.
  ```typescript
  const esRef = useRef<EventSource | null>(null);
  useEffect(() => {
      if (esRef.current) return; // Prevent double-init
      const es = new EventSource(url);
      esRef.current = es;
      // ... bindings ...
      return () => {
          es.close();
          esRef.current = null;
      };
  }, []);
  ```

### 15.2 State-Driven Notifications vs Imparative Logic
- **The Trap:** Triggering `toast()` calls directly within a generic API fetcher. This makes it difficult to silence notifications for specific routes or to aggregate multiple rapid events into a single summary.
- **The Standard:** Decouple event processing from notification display using a store (e.g., Zustand `eventStore`). Let the UI components or a specialized "Coordinator Hook" (`useJobEvents`) subscribe to the store and trigger notifications based on state transitions (e.g., `status` changing from `QUEUED` to `RUNNING`).

### 15.3 Minimalist Custom Notification Systems
- **The Trap:** Pulling in heavy external libraries (like `react-toastify`) for simple, themed notifications. This increases bundle size and often requires overriding complex CSS to match a premium design system like "Midnight Slate".
- **The Standard:** Implement a minimal "Shadcn-style" notification system using a simple pub/sub pattern in a hook (e.g., `use-toast.ts`). This allows 100% aesthetic control using standard Tailwind classes and ensures the notification component (`Toaster.tsx`) is perfectly co-located with the application shell.

### 15.4 Unstable Selectors & useSyncExternalStore
- **The Trap:** Creating selectors that return new objects or arrays (e.g., `.filter()`) inside a store hook without an equality function. In React 18+, this causes `useSyncExternalStore` to detect a "change" on every single re-render or store update (even unrelated ones), leading to infinite render loops or "Maximum update depth exceeded" errors.
- **The Standard:** Always use `useShallow` from `zustand/react/shallow` when return a non-primitive or a new collection from a selector. For effects, always use a dedicated selector to extract only the stable actions needed, rather than subscribing to the entire state.
  ```typescript
  // BAD: Returns new array ref every time store changes
  const active = useStore((s) => s.items.filter(i => i.active));
  
  // GOOD: Only re-renders if array content actually changes
  const active = useStore(useShallow((s) => s.items.filter(i => i.active)));
  ```
---

## 16. Authentication & Session Management (BFF Pattern)

### 16.1 Direct OIDC Initiation for SPAs
- **The Problem:** In a BFF (Backend-for-Frontend) architecture, directing a Single Page Application (SPA) to a server-side "Login" page before the OIDC provider (Google) adds a redundant user interaction.
- **The Standard:** SPAs should navigate directly to the backend's OIDC initiation endpoint (e.g., `/auth/login`). The backend should then immediately redirect to the OIDC provider. This reduces the number of clicks and redirects for the end user.

### 16.2 Explicit Cookie Clearing & Cache Prevention
- **The Problem:** Modern browsers may cache `302 Found` redirects or retain session cookies if the path/domain isn't precisely matched during logout. Re-visiting a protected resource immediately after logout can trigger an invisible auto-login if the OIDC provider session is still active.
- **The Standard:** 
  1. During logout, manually clear the session cookie by adding a `Set-Cookie` header with `Max-Age=0` and matching `Path=/`.
  2. Set explicit cache-prevention headers (`Cache-Control: no-cache, no-store, must-revalidate`) on all authentication-related redirects. This ensures the browser always evaluates the actual authentication state rather than relying on cached results.

### 16.3 Canonical Origin Normalization
- **The Problem:** Incoming `Origin` headers may or may not include default ports (e.g., `https://example.com:443`). Simple string comparisons fail when the configured allowed origin differs from the header's port representation.
- **The Standard:** Use `java.net.URI` to parse the origin string and reconstruct it in a canonical form (scheme + host + explicit non-default port). This provides a robust foundation for CORS and redirect validation. Always log malformed origins at `WARN` level to facilitate debugging without crashing the validator.
### 16.4 Orval/Axios Return Type Alignment (Promise<T> vs Promise<AxiosResponse<T>>)
- **The Problem:** In a strict TypeScript environment (Vite/TSC), Orval-generated API functions (e.g., `getApiV1UsersMe`) often default to returning `Promise<T>` (the DTO) even if the underlying `customInstance` returns `Promise<AxiosResponse<T>>`. This causes `TS2322` assignment errors during build.
- **The Trap:** Changing the `customInstance` to return `Promise<T>` (unwrapped data) fixes the compiler but breaks all hand-written logic that expects the Axios wrapper (e.g., `const { data } = await getApiV1...`).
- **The Standard:** 
    1. Standardize `customInstance` to return `Promise<T>` (the unwrapped data) to satisfy the generated types and the `react-query` hooks' expectations.
    2. In the global interceptor, unwrap the `ApiEnvelope` into `response.data`.
    3. Update all hand-written call sites to access the data directly without destructuring or by using appropriate type casting (e.g., `await getApiV1UsersMe() as any as UserDTO`) if the generated union types (Success | Error) are too restrictive.
    4. Ensure unit tests mock the unwrapped DTO directly for consistency.
### 16.5 SPA Routing with Quarkus (Catch-all)
- **The Problem:** In a unified build where Quarkus serves a React SPA, navigating directly to a client-side route (e.g., `/dashboard`) or refreshing the page results in a 404 error because the backend doesn't recognize the path.
- **The Standard:** 
    1. Enable the built-in fallback: `quarkus.http.serve-index-html-for-not-found=true`.
    2. Implement a specialized `SpaRoutingHandler` using a high-priority catch-all route (`@Route(path = "/*", order = 10000)`).
    3. The handler should check if the request is NOT for an API (`/api/*`), static asset (contains a dot), or legacy UI (`/ui/*`), and if so, perform a `rc.reroute("/index.html")`. This keeps the browser URL intact while allowing the SPA to load and handle the routing logic.

---

## 17. Typed Pagination & OpenAPI (Quarkus / Orval)

### 17.1 Eliminating `unknown[]` in Generated Collections
When using a generic `PaginatedResponse<T>` record or class in Quarkus, `quarkus-smallrye-openapi` often fails to resolve the specific type `T` in the generated `openapi.json`, mapping `items` to a generic `object` or `unknown[]` in the Orval-generated TypeScript models.
- **The Trap:** Relying on the generic `PaginatedResponse.class` in the `@Schema(implementation = ...)` attribute of a JAX-RS resource. This results in the loss of type-safety for the collection items in the frontend, requiring unsafe type assertions.
- **The Standard:** 
    1. Convert the `PaginatedResponse<T>` from a Java `record` to a standard class to allow for easier inheritance and explicit schema definition.
    2. In each resource class (e.g., `AdminApiResource`), create a `public static` inner class that explicitly extends the generic response: `public static class AuditLogPaginatedResponse extends PaginatedResponse<AuditLogEntryDTO> { ... }`.
    3. Use this specific subclass in the `@APIResponse` annotation: `@Content(schema = @Schema(implementation = AuditLogPaginatedResponse.class))`.
    4. Orval will now generate a dedicated `AuditLogPaginatedResponse` interface with `items: AuditLogEntryDTO[]`, providing 100% end-to-end type safety without manual casting.

### 18. Frontend UX Resilience Strategy
Building "Professional Grade" applications requires more than just visual polish; it requires handling failures gracefully to maintain user trust.
- **The Strategy:** Use a three-tier resilience strategy:
    1. **Network Resilience:** Integrate `axios-retry` at the global client level to handle transient network issues and gateway errors (502/503/504) with exponential backoff.
    2. **UI Safety Nets:** Wrap the root `RouterProvider` in a React `ErrorBoundary` and integrate with `TanStack Router`'s `defaultErrorComponent` to catch unhandled rendering exceptions and route loader failures.
    3. **Bridge Async state:** Enable `throwOnError: true` in `TanStack Query` to propagate background fetch failures into the UI's error boundary for centralized failure handling.
- **Verification Tip:** When verifying this strategy with E2E tests (Playwright), ensure `expect` timeouts are increased (e.g. 15s+) to allow for multiple network retries before the UI fail-state is rendered.

---

## 19. Theme & i18n Foundations

### 19.1 CSS Variable Semantic Theming (Tailwind 4)
When implementing multi-theme support in Tailwind 4, avoid hardcoding `@theme` blocks with fixed colors. 
- **The Trap:** Defining specific colors like `--color-slate-900` directly in the `@theme` block makes it difficult to swap them for a light theme without re-aliasing every utility class.
- **The Standard:** 
    1. Define **Semantic Variables** (e.g., `--color-background-base`, `--color-text-primary`) in a global `:root` block.
    2. Map these variables in the Tailwind `@theme` block for easy discovery.
    3. Use a `.dark` class (applied to `<html>` or `<body>`) to override these semantic variables with dark-specific values. This allows the rest of the application to use standard classes like `bg-background-base` which automatically adapt to the active theme.

### 19.2 window.matchMedia Mocking in Vitest/JSDOM
Components using `window.matchMedia` (e.g., for system theme detection) will crash `Vitest` tests because `JSDOM` does not implement this method by default.
- **The Trap:** Adding `matchMedia` mocking directly inside every test file leads to duplication and brittle setups.
- **The Standard:** Centralize the mock in `src/test/setup.ts`. Use `Object.defineProperty` on the `window` object and provide a full mock implementation of the `MediaQueryList` interface (including `addEventListener` and `removeEventListener`) to satisfy all modern hooks and components.

### 19.3 i18next Hybrid Loading Strategy
For optimal performance and SEO in a Dual-Language application:
- **The Standard:** 
    1. Bundle the **default language** (e.g. English) as a static JSON or object to prevent a "flash of untranslated content" on initial load.
    2. Use `i18next-http-backend` to **lazy-load additional languages** (e.g. Slovenian) only when requested.
    3. Integrated with `react-i18next` and configure `fallbackLng: 'en'` and `debug: import.meta.env.DEV` to ensure a smooth developer experience and reliable fallbacks.

---

### 3.8 Transactional Consistency in Background Jobs
- **The Problem:** Background jobs triggered from executor threads lack a default CDI request or transaction context. This leads to `ContextNotActiveException` when calling Panache methods.
- **The Standard:** Always wrap background database operations in an explicit programmatic transaction: `QuarkusTransaction.requiringNew().call(() -> { ... })`.

### 3.9 Eager Loading for Background Processing
- **The Problem:** When processing entities in a *new* transaction (common in background jobs), lazy-loaded collections (like `@ManyToMany`) will throw `LazyInitializationException` if they weren't fetched in the same transaction session.
- **The Standard:** Implement dedicated finders that use `JOIN FETCH` (e.g., `Topic.findByIdWithSources()`) to eagerly load all required associations before passing the entity to the background worker. Avoid relying on open-session-in-view patterns for background tasks.

---

## 20. Advanced Routing (TanStack Router)

### 20.1 Structural ID Propagation in Types
When using nested layout routes with an `id` (e.g., `id: 'authenticated'`) but no `path`, TanStack Router's internal type generation may sometimes prepend the ID to the route's path identifier in the generated types (e.g., requiring `from: '/authenticated/dashboard'` instead of `/dashboard`).
- **The Trap:** Relying on the visual browser URL as the source of truth for the `from` property in `useParams` can lead to persistent `TS2345` errors if the internal type tree is structured differently.
- **The Standard:** If `tsc` reports a mismatch for a standard absolute path, use `useParams({ strict: false })` as a pragmatic bypass for layout-heavy route trees where the internal ID mapping is complex. Always perform an explicit `typeof id === 'string'` check after a non-strict `useParams` call to maintain runtime safety.

---

## 21. Frontend Typography & Modal Design

### 21.1 Symmetrical Padding & .prose Layout
When building professional-grade document readers (like the **Summary Modal**) using `@tailwindcss/typography` (`.prose`), the default browser/plugin margins can conflict with container padding.
- **The Trap:** A container with `p-8` will look unbalanced if the first element inside it (like an `H2`) has a default `margin-top: 1.5em`. This creates an asymmetric "gap" at the top compared to the sides.
- **The Standard:** Always apply a CSS override to ensure the first child of a `.prose` container has no top margin. This ensures the content aligns perfectly with the container's top padding, maintaining symmetrical document aesthetics.
  ```css
  .prose > :first-child {
    margin-top: 0 !important;
  }
  ```
- **The Standard:** For modals intended as document readers, favor a fixed max-width (e.g., `max-w-4xl`) and symmetrical internal padding (`p-6 md:p-8`) on the article tag itself to prevent the text from becoming too wide to read easily while maintaining consistent spacing.
---

## 24. Concurrency & Thread Safety in Background Orchestration (v0.14.0)

### 24.1 Hibernate Session Isolation in Parallel Tasks
When executing parallel database tasks using a `ManagedExecutor` or similar thread pool in Quarkus/Panache:
- **The Trap:** Passing a JPA entity (e.g., a `Topic` fetched on the main request thread) into multiple background lambdas. Even if each lambda uses `QuarkusTransaction.requiringNew()`, Hibernate may attempt to lazily initialize collections or manage the entity state using the *original* session from the parent thread. This leads to `java.util.ConcurrentModificationException` within Hibernate's `ResultsetsTrackingContainer` or `PSQLException: This statement has been closed` errors.
- **The Standard:** **Never** pass managed entities across thread boundaries. Instead, pass the **entity ID** (`Long topicId`) to the background task. Inside the task's own transaction, refetch the entity from the database using `Topic.findById(topicId)`. This ensures each parallel task has its own isolated Hibernate session and state tracking.

### 24.2 Environment-Agnostic Date Management
- **The Trap:** Using `ZoneId.systemDefault()` for `Instant` or `OffsetDateTime` conversions. This results in "flaky" date-range logic that works on a developer's machine but fails on a CI server or cloud environment set to a different timezone (e.g., UTC).
- **The Standard:** Always use **`ZoneOffset.UTC`** for all internal orchestration logic and date-to-instant conversions. This ensures consistent, deterministic behavior regardless of the host environment's configuration.

## [2026-03-26] Content Management & Status Overrides (POV-155)

### API & Backend Isolation
- **DTOs vs. Entities**: Never expose JPA entities directly in paginated API responses if specialized DTOs exist. Using `ContentItemDTO` instead of `ContentItem` (JPA) resolved over 100 frontend lint errors and provided a cleaner, more stable API contract.
- **Nested Mapping**: MapStruct's `uses` attribute is essential for mapping nested DTOs (e.g., `ContentItemDTO` -> `ContentSourceDTO`). This ensures consistency across the entire domain model.

### Frontend Synchronization
- **OpenAPI Schema Updates**: Manual backend `package` runs are mandatory after DTO changes to ensure `openapi.json` is correctly regenerated for Orval. Failure to do this leads to stale frontend hooks and type-safety regressions.
- **Casting Paginated Items**: Orval's `PaginatedResponse` often defaults to `unknown[]` for items. Explicitly casting these to the domain DTO (e.g., `(data?.items || []) as ContentItem[]`) in the route components restores full IDE support and type-safety.

### UI/UX & Administrative Tools
- **Glassmorphic Data Grids**: Using `glass` and `animate-in` tokens creates a premium administrative feel that matches the main dashboard, even for high-density tables.
- **Immediate Feedback**: Integrating `useQueryClient.invalidateQueries` in status update hooks provides the near-instant feedback loop expected in modern administrative tools.
