# Legacy Process Logs & Migration History

This document serves as a historical record of major system migrations, architectural pivots, and sprint-specific lessons learned for versions prior to `v0.15.0`.

## v0.14.0: Quarkus 3 & Jakarta EE Migration

### MapStruct Component Model
- **The Trap:** Using `@Mapper(componentModel = "cdi")` in a Quarkus 3 project can lead to `jakarta.enterprise.inject.spi.DeploymentException` if the underlying implementation uses `@javax.inject.Inject`.
- **The Standard:** Always explicitly use **`componentModel = "jakarta"`** for all MapStruct mappers in Quarkus 3+. This ensures the generated code utilizes the correct `jakarta.*` annotations for dependency injection.

### Transaction Visibility in Integration Tests
- **The Trap:** When a service method executes internal logic inside `QuarkusTransaction.requiringNew()`, it starts and commits a *separate* database transaction. If the main test method (also transactional) just created the data, that data will be **invisible** to the background transaction because it hasn't been committed yet from the test's perspective.
- **The Standard:** Before triggering an asynchronous background job or a `requiringNew()` service call in an integration test, you MUST manually synchronize the persistence context. Use **`entityManager.flush()`** to push pending changes to the database and **`entityManager.clear()`** to ensure subsequent reads see the actual DB state.

### API Robustness & Idempotency
- **The Trap:** Returning `409 Conflict` when a user tries to "add" an entity that already exists (e.g. a Content Source) can lead to frustrating UI experiences and redundant error handling logic.
- **The Standard:** For "creation" endpoints that are conceptually "idempotent adds," the API should return **`200 OK`** and the existing object if it already exists.

### Concurrency & Thread Safety in Background Orchestration
- **The Trap:** Passing a JPA entity (e.g., a `Topic` fetched on the main request thread) into multiple background lambdas. Hibernate may attempt to lazily initialize collections using the *original* session from the parent thread. This leads to `java.util.ConcurrentModificationException` or `PSQLException: This statement has been closed` errors.
- **The Standard:** **Never** pass managed entities across thread boundaries. Instead, pass the **entity ID** (`Long topicId`) to the background task. Inside the task's own transaction, refetch the entity from the database using `Topic.findById(topicId)`. This ensures each parallel task has its own isolated Hibernate session and state tracking.

### ESLint 9+ Hardening
- **The Trap:** Using a single ESLint configuration for both production code and test files. Test files often use `any` casting and complex mocking patterns that conflict with strict production type-checking rules.
- **The Standard:** Use a **Multi-Target Flat Configuration** (`eslint.config.js`). Define a **Strict Profile** for production code and a **Relaxed Profile** for tests.

---

## v0.10.0 & v0.11.0: Foundation Logs

### Initial Hibernate & Panache Setup
- Early lessons in v0.10.0 focused on stabilizing the Panache repository pattern and initial OIDC security integration.
- v0.11.0 introduced the first background job orchestration framework and early auditory feedback experiments using raw assets.

### Release-Specific POV Logs
- **POV-155 (Content Management)**: First validation of API & Backend isolation using DTOs vs Entities in paginated responses.
- **POV-156 (Infrastructure & Security)**: Hardening containerized application as a non-root user.
- **POV-158 (Automated Metadata Grounding)**: Implementation of the first "Source of Truth" sync pattern using Markdown documentation.
- **POV-161 (Placeholder Scope Identification)**: Lesson learned regarding implicit placeholder auditing during task planning.
- **POV-163 (Quality Hardening)**: Introduction of `void` wrappers for unhandled promises within event handlers.
