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
