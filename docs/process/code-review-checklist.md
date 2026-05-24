# Code & Documentation Review Checklist

This document is the mandatory checklist to be mentally reviewed before any code is committed. Its purpose is to uphold the highest standards of software quality, maintainability, security, and performance, and to prevent the types of regressions and inconsistencies experienced in the past (e.g., during the POV-103 refactoring).

## 0. Preparation & Scope Discovery

*   [ ] **Identify Context:** Read `agent_logs/task.md` to understand the specific requirements and acceptance criteria for the current task.
*   [ ] **Identify Changes:** Run `git status` to list all modified, new, or deleted files.
*   [ ] **Read the Diff:** Run `git diff HEAD` to inspect all line-by-line changes since the last commit. **Do not rely on memory.**

## 1. Design & Architecture

*   [ ] **Adherence to SOLID Principles:**
  *   **Single Responsibility:** Does the class/method do one thing and do it well?
  *   **Open/Closed:** Is the code open for extension but closed for modification? (e.g., using strategies, interfaces).
  *   **Liskov Substitution:** Can subclasses be substituted for their base classes without altering behavior?
  *   **Interface Segregation:** Are interfaces lean and focused? Clients should not be forced to depend on methods they do not use.
  *   **Dependency Inversion:** Does the code depend on abstractions, not on concrete implementations? (e.g., using `@Inject` with interfaces).
*   [ ] **Loose Coupling:** Can the component be tested in isolation? Does it have minimal, well-defined dependencies?
*   [ ] **Architectural Consistency:** Does the change follow established patterns within the project (e.g., Service/Resource layers, Panache entities, DTOs)?

## 2. Code Quality & Readability

*   [ ] **Intention-Revealing Names:** Are variables, methods, and classes named clearly and unambiguously?
*   [ ] **Simplicity (KISS/YAGNI):** Is the code as simple as possible? Is there any unnecessary complexity or functionality that isn't needed right now (You Ain't Gonna Need It)?
*   [ ] **Comments Explain "Why", Not "What":** Are comments used to explain complex business logic or design trade-offs, rather than restating what the code does?
*   [ ] **No Magic Numbers/Strings:** Are constants and configuration values defined in a central, named location?

## 3. Security

*   [ ] **Input Validation:** Is all external input (API requests, user forms) validated and sanitized to prevent injection attacks (SQLi, XSS)?
*   [ ] **Authentication & Authorization:** Are protected endpoints correctly secured? Are checks performed to ensure the user has the right permissions for the requested data (especially with multi-tenancy)?
*   [ ] **Secret Management:** Are secrets, API keys, and passwords loaded from configuration/environment variables, not hardcoded in the source?
*   [ ] **Dependency Security:** Have the new/updated dependencies been checked for known vulnerabilities?

## 4. Testing

*   [ ] **Correct Test Type:** Is the appropriate test type used? (`@QuarkusTest` for component tests with mocks, `@QuarkusIntegrationTest` for black-box E2E tests, `@TestTransaction` for DB tests).
*   [ ] **Meaningful Coverage:** Do tests cover not just the "happy path" but also edge cases, error conditions, and security checks?
*   [ ] **Readable & Maintainable Tests:** Is the test easy to understand? Does it follow a clear Given/When/Then structure?
*   [ ] **No Fragile Tests:** Does the test avoid dependencies on implementation details that might change (e.g., asserting on exact error message strings that include variable parts)?

## 5. Frontend Consistency (React/i18n/Accessibility)

*Lessons from POV-188: defects in these areas consistently appear in PR reviews and should be caught before the first push.*

*   [ ] **i18n Completeness:** If a value is localized in one component (e.g. status badge in a table), it must be localized in **every** component that renders it (filter dropdowns, modals, audit trails). Search for the raw enum value string across all components.
*   [ ] **Accessibility — Icon-only buttons:** Every button that contains only an icon or symbol (no visible text) must have `aria-label`. Check all `<button>` elements without visible text.
*   [ ] **Accessibility — Non-native interactive elements:** Any `<div>`, `<span>`, or other non-interactive element with an `onClick` must be converted to a `<button type="button">` with `aria-label`. The backdrop overlay pattern must use `<button>` not `<div>`.
*   [ ] **Entity field names vs. DTO names:** When building sort whitelists or filter params that reference backend entity fields, verify the name against the actual entity class field (e.g. `BackgroundJob.type`, not the DTO/query-param name `jobType`).
*   [ ] **Exception type consistency:** Within a single method or service, use one consistent exception class for the same category of error. In this project: `BusinessRuleException` for semantic/state violations, `NotFoundException` only for genuinely missing resources.
*   [ ] **Idempotency on POST endpoints that create resources:** Any POST endpoint that creates a resource (job trigger, restart) should carry `@Idempotent` to prevent duplicate creation on client retry.
*   [ ] **`text-interactive` vs `font-bold`:** `text-interactive` is for interactive element labels (button text). Non-interactive labels (`<label>`, `<h3>`, table headers) use `font-bold`. Do not mix them.
*   [ ] **Array keys:** Never use array index as React key. Use a stable identifier from the data, or a named string constant for static skeleton arrays.
*   [ ] **Admin tool confirmation modals:** In internal admin tools with a limited user base and easily reversible actions, confirmation modals for destructive actions may be omitted to prioritize operational efficiency — provided the design decision is documented and recovery cost is minimal.

## 6. Performance & Scalability

*   [ ] **Database Efficiency:** Are there any obvious N+1 query problems? Are queries indexed appropriately?
*   [ ] **Reactive Purity:** Is blocking code (`Thread.sleep`, non-reactive JDBC) avoided in reactive pipelines (`Uni`, `Multi`)?
*   [ ] **Resource Management:** Are resources like streams or connections properly closed in `finally` blocks or with try-with-resources?

## 7. Refactoring & Documentation Hygiene

*This section is especially critical when moving or renaming files.*

*   [ ] **Subtraction Check:** Was the original content **deleted** from its source location after being moved? (Prevents DRY violations).
*   [ ] **Orphan Check:** Is every new file correctly referenced in an index or configuration (`AGENT.md`, `pom.xml`, etc.)?
*   [ ] **Dangling Pointer Check:** Have all internal references (e.g., "see Section VI") been updated to point to the new file paths (e.g., `docs/protocols/verification.md`)?
*   [ ] **Document Structure:** Does each modular document start with a Level 1 heading (`#`) and have a logical heading structure?
*   [ ] **Index Accuracy:** Does the description of a file in any index accurately reflect its contents?
*   [ ] **Knowledge Base Update:** Have any major architectural workarounds, bug fixes, or new integration patterns discovered during this task been added to `docs/process/lessons-learned.md`?
*   [ ] **Migration identifiers:** When reviewing database migrations, distinguish between legacy SQL column/enum names being removed and new domain model values. Do not flag a legacy identifier as inconsistent if it is explicitly part of a migration or removal step.

## 8. Agent-Specific Protocols (AI Context)

*   [ ] **Bootstrap Logic:** Does `AGENT.md` contain an explicit, non-negotiable instruction to read required protocols *before* starting work? (The "Cold Start" fix).
*   [ ] **Token Efficiency:** Is the agent's entry point (`AGENT.md`) lean? Is it loading only the context it needs for the specific task?

## 9. Git Hygiene

*   [ ] **Conventional Commits:** Does the commit message follow the `type(scope): description` format?
*   [ ] **Atomic Commits:** Does the commit represent a single, logical change? Are unrelated changes (e.g., a refactoring and a bug fix) in separate commits?
*   [ ] **Clean `git status`:** Have all new files been staged? Are there any unintended changes included?

---

**Instruction for Agent:** To perform this review, you must first **execute Step 0 (Scope Discovery)** by reading `agent_logs/task.md` and using `git status` and `git diff HEAD`. Then, mentally walk through the rest of the checklist against those specific changes. If you find a violation, you must correct it before proceeding.
