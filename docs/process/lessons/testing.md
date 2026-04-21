# Testing Patterns & Standards

## Testing Async State with Awaitility & Panache
Testing asynchronous REST actions (e.g., a `202 Accepted` that queues a job) requires `Awaitility` to assert the eventual state change.
- **The Trap:** Using Panache Active Record static methods (e.g., `BackgroundJob.listAll()`) inside `await().until(...)` conditions throws `IllegalStateException`. Panache fails to locate the active CDI context on Awaitility's separate polling thread.
- **The Standard:** Inside Awaitility polling conditions, bypass Panache. Inject the raw `EntityManager` (`@Inject EntityManager em;`) and execute standard HQL queries within an explicit programmatic transaction block.
  ```java
  await().atMost(5, TimeUnit.SECONDS).until(() -> {
      List<BackgroundJob> jobs = QuarkusTransaction.requiringNew().call(() -> 
          em.createQuery("FROM BackgroundJob", BackgroundJob.class).getResultList()
      );
      return jobs.size() == 1 && jobs.getFirst().status == JobStatus.COMPLETED;
  });
  ```

## Avoiding `Thread.sleep`
- **The Trap:** Using `Thread.sleep()` to wait for asynchronous operations in tests leads to flaky and slow tests.
- **The Standard:** Always use **Awaitility** to poll for the expected state change. Awaitility provides built-in support for timeouts and clear failure messages.

## Integration Testing with `@TestSecurity`
When testing endpoints protected by `@RolesAllowed` that accept multiple roles (e.g., `@RolesAllowed({"user", "admin"})`):
- **The Trap:** Using `@TestSecurity(user = "testuser", roles = {"ADMIN_JOB_UPDATE"})` might fail if the endpoint also expects the base `"user"` role.
- **The Standard:** Always map the exact full suite of roles the test user hypothetically possesses: `@TestSecurity(user = "testuser", roles = {"user", "ADMIN_JOB_UPDATE"})`.

## Explicit Timezones in Testing & API Logic
When asserting logic bound to rolling windows (e.g., "last 7 days") or specific dates:
- **The Trap:** Using `LocalDate.now()` in both the application and the test suite can lead to inconsistent behavior across different environments (local vs CI).
- **The Standard:** Always instantiate dates and timestamps explicitly relative to UTC. Use `LocalDate.now(java.time.ZoneOffset.UTC)` for server-side logic and identically in the test builder setup.

## Vitest Coverage Engine Requirements
- **The Trap:** Forgetting to install the coverage provider separately. Vitest core does not include the coverage engine; you must explicitly add `@vitest/coverage-v8` as a dev dependency.
- **The Standard:** When enabling code coverage in the frontend (`vitest run --coverage`), ensure the configuration explicitly excludes `node_modules`, `dist`, and configuration files (like `eslint.config.js`).

## Zustand Mocking Patterns (POV-177)
Testing components that consume Zustand stores requires careful spy injection.
- **The Trap:** Patching a store with `useStore.setState({ spyFn: vi.fn() })` *after* the `render()` call. If the component destructures `spyFn` at the top level or inside a `useEffect` during mount, it will capture the "old" real function, not the spy.
- **The Standard:** Always perform the spy injection and state setup **BEFORE** calling `render`. This ensures that when the component renders, it pulls the mock reference from the store immediately.

- **The Standard:** Derive the condition (e.g. `isComplete = progress === 100`) from the store or props. Prefer **Manual Acknowledgement** (dismiss buttons) over auto-timers for concluding complex UX flows like celebration states; this eliminates the need for managing `setTimeout` lifecycle and cleanup.

## Permanent Master Test Specification (POV-166)

### 3-Digit Sequential Indexing (`TC-DOM-0XX`)
As a project grows, simple sequential IDs (`TC-01`) quickly become ambiguous.
- **The Standard:** Implement a **3-digit sequential index** prefixed by a Domain code (e.g., `TC-REP-024`). The 3-digit format (`001`-`999`) provides headroom for large-scale enterprise suites and ensures consistent sorting.

### Domain 0: Global Resilience (GRX)
Cross-cutting stability features (Error Boundaries, Skeleton Loaders, Theme Persistence) often "fall through the cracks."
- **The Standard:** Establish a **Domain 0 (Global Resilience & UX)**. This domain is mandatory for every release and covers the baseline "Polish" that must hold true across all pages.

## Requirements Traceability Hardening (POV-166)

### The US ➔ UC ➔ TC Chain
In rapid agile development, User Stories often become disconnected from the actual implementation and testing.
- **The Standard:** Enforce a strict **Traceability Chain**:
  1. **User Story (US-ID)**: The high-level "Why".
  2. **Use Case (UC-ID)**: The "How", explicitly linked to a `US-ID`.
  3. **Test Case (TC-ID)**: The "Verify", explicitly linked to a `UC-ID`.
  
  This chain ensures that every requirement is covered by at least one test, and every test exists to satisfy a specific user requirement.

### Process Gates for Documentation Sync
- **The Standard:** Documentation parity must be an **Explicit Phase 2 Gate** in the development checklist. Do not wait until the end of the release to "clean up" docs. Every task must include a "Master Specification Sync" step where new scenarios are added and old ones updated BEFORE the task is marked as complete.

## Backend-Agnostic E2E Stability (POV-178)
When scaling Playwright E2E tests in a complex microservices or OIDC-guarded environment:
- **The Trap:** Relying on a running local backend and/or OIDC provider for smoke tests. This introduces massive flakiness due to network latency, session expiration, and state contamination.
- **The Standard:** Implement **Network-Level Mocking** using `page.route`.
  - **Auth Isolation:** Intercept `/api/v1/users/me` and `/auth/login` to simulate 401 (Unauthenticated) or 200 (Authenticated) states. This allows testing "Redirect to Landing" logic without a real auth server.
  - **Static Mocking:** Return standard JSON DTOs for core endpoints to test UI rendering paths in isolation from DB state.
- **Selector Hardening:** Move away from brittle text-based or role-based selectors (`getByText`, `getByRole`) which break on localization or layout shifts.
  - **The Standard:** Use **`data-testid`** on core navigational anchors (Logo, Hero CTA, Primary Buttons). This decouples test logic from the visual design and i18n layer.

---

## Related Documents
- [Master Test Specification](../../analysis/test_specification.md)
- [Development Checklist](../../sdlc/11_development_checklist.md)

