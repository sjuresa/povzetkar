# Project Lessons Learned & Developer Knowledge Base

This centralized document encapsulates the critical technical discoveries and optimization patterns formulated during the development of `Povzetkar`. 

It serves as the definitive runbook and "portal" for any developer or AI Agent touching the codebase to ensure consistency and prevent the recurrence of complex systemic issues.

---

## 🚀 Recent Highlights (v0.17.0)

> [!IMPORTANT]
> **Account State Machine (POV-185)**:
> New users arrive as `PENDING_APPROVAL` with only the `demo` role. The `OidcSecurityIdentityAugmentor` re-fetches DB roles on every request — no new server-side session code is needed to invalidate sessions on `disable`. `@Authenticated` on `/me` (not `@RolesAllowed`) is essential so non-ENABLED users can fetch their own status.

> [!TIP]
> **Demo Impersonation Pattern (POV-185)**:
> `UserContextService.getContentViewingUser()` detects `securityIdentity.hasRole(ROLE_DEMO)` and transparently returns the demo account's data. Call-sites requiring this are reads only — writes stay on `getAuthenticatedUser()`. A `getContentViewingUser()` helper on `BaseApiResource` delegates to the service and centralizes the unwrap.

> [!TIP]
> **`Map.of()` Limit**:
> `java.util.Map.of()` supports a maximum of 10 key-value pairs. When `RolePermissionsMap` grows beyond 5 roles, switch to `Map.ofEntries(Map.entry(...), ...)` or `Collections.unmodifiableMap(new HashMap<>(...))`.

> [!IMPORTANT]
> **HTTP/Service Layer Separation for Optimistic Locking (POV-185)**:
> ETag parsing (`If-Match` header → version integer) belongs in the resource layer (`BaseApiResource.checkOptimisticLock()`), not in the service. Services should accept a pre-loaded entity. This keeps HTTP protocol concerns out of business logic. Strip weak ETag prefix (`W/"…"`) before parsing to avoid spurious 409s from compliant clients.

> [!WARNING]
> **Flyway Migrations Are Immutable Once Applied**:
> Any edit to an applied migration file causes "Migrations have failed validation" on next startup. Never edit an applied migration — always create a new versioned script. This applies even in the same dev session if the local DB was started between the initial apply and the desired change.

> [!TIP]
> **`@Version` Increments on `em.merge()`**:
> In JPA, `em.merge()` on a `@Version`-annotated entity increments the version counter. Test helpers that mutate entity state must return the post-merge version and use it in subsequent optimistic-locked request headers. Sending the pre-mutation version will produce a 409 even when the test is logically correct.

> [!TIP]
> **AtomicLong Double-Checked Locking for Application-Scoped Caches**:
> For `@ApplicationScoped` beans that lazily resolve a singleton value, use `AtomicLong(0)` where `0` = uninitialised and `-1` = "not found". The hot path reads without synchronisation; the first-write path uses a `synchronized` block with a re-check inside. Avoids a lock on every request while remaining thread-safe under concurrent initialisation.

> [!WARNING]
> **`react-markdown` Plugin Options Must Use Tuple Syntax (POV-186)**:
> `rehypePlugins` expects `[plugin, options]` tuples — never `plugin(options)` inline. Calling `rehypeExternalLinks({ target: '_blank', rel: [...] })` invokes the factory at render time and passes its return value as the plugin, which crashes at runtime. Correct: `[rehypeExternalLinks, { target: '_blank', rel: ['noopener', 'noreferrer'] }]` as a tuple element in the array.

> [!IMPORTANT]
> **`rehypeSanitize` Strips Unknown Attributes — Plugin Order Is a Security Constraint (POV-186)**:
> Always run `rehypeExternalLinks` (and any attribute-adding plugin) **after** `rehypeSanitize`. Running it before causes `target` and `rel` to be silently stripped on every render, defeating the security purpose entirely. Required order: `[rehypeRaw, rehypeSanitize, rehypeExternalLinks]`.

> [!TIP]
> **`router.state.location.searchStr` vs `search` (POV-187)**:
> TanStack Router's `ParsedLocation.search` is a **parsed object**, not a string. Stringifying it produces `[object Object]`. The raw query string (e.g., `?foo=bar`) lives on `searchStr: string`. Always use `searchStr` when constructing redirect URLs in router-aware code. Note: the Axios 401 interceptor in `client.ts` uses `globalThis.location` (not the router) to avoid a circular dependency — `globalThis.location.search` is already a raw string, so `searchStr` does not apply there.

> [!IMPORTANT]
> **Never-resolving promise to suppress error propagation after navigation (POV-187)**:
> After setting `globalThis.location.href = '...'` to trigger a full-page navigation, JS execution continues briefly before the browser navigates away. If the calling code eventually `throw`s, React's error boundary may fire during that window. Return `new Promise(() => {})` immediately after the location assignment to produce a permanently pending promise — Axios treats it as a cancelled request, the error boundary is never triggered, and the browser navigation takes over cleanly.

> [!TIP]
> **Read the backend before designing frontend auth flows (POV-187)**:
> Before implementing `sessionStorage` or client-side redirect logic, check whether the backend already supports a `?redirect=` (or equivalent) parameter. `LoginResource` in this project already validates and applies a `?redirect=<path>` param via `resolveSafeDestination()`. This eliminated the need for `sessionStorage`, a `LandingPage` `useEffect`, and a `validateSearch` union extension — discovered by reading `LoginResource.java` during the planning interview.

> [!WARNING]
> **Vite proxy forwards OIDC 302→Google to the browser; Axios follows it cross-origin causing CORS failure (POV-187)**:
> In Quarkus `web-app` OIDC mode, unauthenticated API requests receive a 302 redirect to `accounts.google.com`. The Vite dev proxy forwards this 302 back to the browser. Axios (running as XHR) follows the cross-origin redirect to Google, which rejects the CORS preflight — so Axios gets a network error, not a 401, and our interceptor never fires. The fix: add a `proxyRes` handler in `vite.config.ts` that rewrites `302 + Location: accounts.google.com` → `401` for requests with `X-Requested-With: XMLHttpRequest`. This keeps OIDC redirect behaviour intact for normal browser navigations while giving Axios a clean 401 it can handle. Note: `followRedirects: false` does NOT solve this — the proxy passes the 302 to the browser regardless; it's the browser that follows it.

## 🚀 Recent Highlights (v0.16.0)

> [!IMPORTANT]
> **AI Prompt Architecture (POV-170)**: 
> We have migrated from positional string formatting to an explicit **named variable** syntax (e.g., `{{transcript}}`) using a high-performance **single-pass Matcher strategy**. This decouples AI prompts from backend logic and eliminates O(N^2) replacement overhead.

> [!TIP]
> **Auditory Feedback (POV-171)**: 
> UI sounds are now synthesized dynamically using the **Native Web Audio API**. This provides a 0KB asset weight and instant low-latency feedback without external asset dependencies.

> **Documentation Modularization (POV-172)**: 
> The monolithic Knowledge Base has been decomposed into specialized domain guides to improve discovery and maintainability.

> [!TIP]
> **Polymorphic UI Components (POV-173)**: 
> We have unified sharing/export logic into a single, type-aware component. This ensures feature parity across entities (Briefings vs. Summaries) and eliminates context-specific UI debt.

> [!IMPORTANT]
> **N+1 Optimization & Differential Testing (POV-175)**: 
> We have eliminated O(N) database bottlenecks in the Briefing API using **HQL Join Fetching**. Performance is now verified via **Differential Query Assertions** (math proof of O(1) complexity), preventing regressions in the CI/CD pipeline.

> [!TIP]
> **Bundle Optimization & Render Consolidation (POV-176)**: 
> We unified HTML export generation onto a lean, direct **unified pipeline** (`remark-parse`, `remark-gfm`, `remark-rehype`, `rehype-stringify`). This avoids pulling the expensive `react-dom/server` engine into the browser bundle while maintaining 100% Markdown parity and intrinsic XSS safety.

> [!IMPORTANT]
> **Gamified Onboarding & Hybrid Persistence (POV-177)**: 
> We implemented an interactive 5-step checklist with **visual how-to guides** and **action-based triggers**. The progress is managed via a hybrid Zustand store that performs a **one-time sync from the DB** for existing users, ensuring the "Golden Path" is always visible and actionable.

> [!IMPORTANT]
> **Monochromatic Design Hardening (POV-178)**: 
> We achieved 100% design purity by purging all legacy blue/green color literals. The system now enforces a monochromatic **Zinc-Neutral taxonomy** using a global **`accent-color`** strategy for native form controls and **data-testid** selectors for backend-agnostic E2E stability.

---

## 📚 Domain Directories

Select a domain below to view the full specification of "Standards" and "Traps":

| Domain | Description | Link |
|:---:|:---|:---|
| **Backend API** | Security, DTO mapping, and API design. | [backend-api.md](./lessons/backend-api.md) |
| **Database** | Hibernate, JPQL projections, and N+1 fixes. | [database.md](./lessons/database.md) |
| **Frontend UX** | React, Tailwind v4, Routing, and Themes. | [frontend-ux.md](./lessons/frontend-ux.md) |
| **Reactive** | Mutiny pipelines and real-time SSE events. | [reactive.md](./lessons/reactive.md) |
| **Testing** | Awaitility, Master Spec, and Traceability. | [testing.md](./lessons/testing.md) |
| **Infra & Ops** | Docker, Maven, CI/CD, and Observability. | [infrastructure-ops.md](./lessons/infrastructure-ops.md) |

---

## 📜 Historical Records

For sprint migration logs and legacy architectural notes (pre-v0.15.0), refer to the consolidated archive:
- [Legacy Process Logs](./archive/legacy-logs.md)
