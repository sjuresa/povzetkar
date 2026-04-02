# Backend API Design & Consistency

## Database DoS Protection
- **The Standard:** Malicious end-users requesting massive page sizes (`?size=10000`) can overwhelm the database and application memory. Always clamp the `pageSize` parameter against a global constant before executing queries:
  ```java
  pageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);
  ```

## Deterministic DTO Mapping (MapStruct)
- **The Trap:** Fetching generic associations like `Set<UserIdentity> identities` lacks a guaranteed order from the database. When mapping these to a concatenated string or extracting priority fields in a DTO (`authProvider`), the sequence can flip sporadically, causing flaky integration tests and unstable UI rendering.
- **The Standard:** Always enforce deterministic ordering using `.stream().sorted(Comparator.comparing(e -> e.id))` on collections inside custom `@Named` mapping methods before joining or extracting values. Never rely on natural `.sorted()` order unless the entity explicitly implements `Comparable`, as this can cause runtime `ClassCastException` failures.

## Dependency Injection
- **The Trap:** Using field injection (e.g., `@Inject UserContextService userContextService;`) allows objects to be created in an invalid state, prevents fields from being `final`, and makes unit testing difficult because dependencies aren't explicitly required during instantiation.
- **The Standard:** Always use **constructor injection** for dependencies. This ensures immutable (`final`) references, explicitly declares required dependencies, and makes the code more robust and easier to test without reflection. If a proxy-able bean requires a no-args constructor for CDI, provide a `protected` no-args constructor that sets final fields to `null`.

## Centralized Security Constants
- **The Trap:** Using raw string literals (e.g., `@RolesAllowed("ADMIN")`) or manual `Permission.ROLE.name()` calls inconsistently across the app. Raw strings are prone to typos, and while `Enum.name()` is safe for logic, it CANNOT be used in annotations like `@RolesAllowed` because they require compile-time constants.
- **The Standard:** In your `Permission` enum, provide `public static final String` constants prefixed with `V_` (e.g., `V_ADMIN = "ADMIN"`) specifically for use in annotations. For all programmatic logic (e.g., `identity.hasRole(...)`), always use the idiomatic `Permission.ADMIN.name()`. This ensures absolute sync between the enum definition, the security metadata, and the execution logic.

## Global Response Envelopes
- **The Trap:** Manually wrapping every API response in an `ApiEnvelopeDTO` (e.g., `return Response.ok(new ApiEnvelopeDTO<>(dto)).build()`) is repetitive, error-prone, and clutters the resource logic. It also makes it difficult to change the envelope structure globally.
- **The Standard:** Use a JAX-RS `ContainerResponseFilter` (e.g., `GlobalResponseEnvelopeFilter`) to automatically wrap all successful responses in a standard envelope. API resources should return the **raw DTO** or **`PaginatedResponse`** directly. This keeps the resource code clean and ensures a consistent response structure across the entire API.
- **Exception:** Do not wrap responses that are already envelopes (like `ApiErrorDTO`) or those that should not have an envelope (e.g., raw file downloads or non-JSON responses).
- **Filter Precision:** Enveloping filters must strictly target successful (2xx) status codes. Wrapping error responses (4xx/5xx) leads to inconsistent "double-enveloped" structures that break client-side error handling logic.
- **Name Binding:** Prefer JAX-RS Name Binding (custom annotations like `@Enveloped`) over URL path prefix scanning to target filters. URL scanning is brittle and prone to bypasses as new resources are added.

## Optimistic Locking (Concurrency Control)
- **The Trap:** Deleting or updating entities based solely on their ID without version checks can lead to the "lost update" problem, where concurrent changes by different users overwrite each other or an entity is deleted while another user is trying to update it.
- **The Standard:** Implement **Optimistic Locking** using the `@Version` field on entities. 
  1. For `PATCH`, `PUT`, and `DELETE` operations, the client **MUST** send the current version of the entity in the **`If-Match`** header (usually in quotes, e.g., `If-Match: "5"`).
  2. The server must check this version against the database version before proceeding.
  3. If the header is missing, return **`428 Precondition Required`**.
  4. If the versions mismatch, return **`409 Conflict`** (or a specific Optimistic Lock error).
  5. If the header is malformed (e.g., not an integer), return **`400 Bad Request`**.
  6. In JAX-RS resources, use a helper method (e.g., `baseApiResource.checkOptimisticLock(entity, ifMatch)`) to centralize the version comparison check, while presence validation is handled globally by `OptimisticLockingFilter`.

## Exception Mapping vs Custom Responses
- **The Trap:** Manually constructing `ApiEnvelopeDTO.error(...)` within resource catch blocks leads to fragmented error logic and risks exposing sensitive internal details.
- **The Standard:** Always prefer throwing standard JAX-RS exceptions (e.g., `BadRequestException`, `NotFoundException`, `ForbiddenException`) and let centralized `ExceptionMapper`s handle the translation into the standard RFC 7807 global envelope.
- **The Standard:** A catch-all `ExceptionMapper<Exception>` MUST be used to intercept unexpected 500 errors. It must log the raw stack trace internally using `StructuredLogger.error(...)` to preserve debug context, but emit a sanitized, generic error message (e.g., "An unexpected error occurred") inside the API envelope to prevent infrastructure leaks.
- **The Standard:** For simple confirmation messages (e.g., "Operation started"), use a dedicated `MessageDTO` instead of `Map.of("message", ...)`. This provides a stable OpenAPI schema and improved type safety.

## Systemic Pagination & Filtering
- **The Trap:** Manually building filter components (e.g., `startDate >= :start`) inside each resource file creates repetitive string manipulation logic that is difficult to maintain and potentially susceptible to injection if parameters aren't safely mapped. Relying on default panache sorts without `?sort` parameter parsing limits API flexibility. Dynamically parsing sort columns from user input without validation opens up HQL sort-injection vulnerabilities.
- **The Standard:** Use central utility builders like `FilterBuilder` to perform conditional `AND` logic safely with map-based parameter binding. Parse sequential `?sort` HTTP parameters centrally into `io.quarkus.panache.common.Sort` objects (using `QueryParser`) before handing them off to the repository layer. **Always** pass a strict allow-list `Set<String>` of permitted entity fields to `QueryParser.parseSort` to prevent HQL injection in `ORDER BY` clauses.

## Safe Enum Conversion
- **The Trap:** Passing a raw string name directly to `Enum.valueOf()` in a REST resource. If the input is malformed, it throws an internal `IllegalArgumentException`, which the default Quarkus exception mapper might catch as a `500 Internal Server Error`, obscuring the real cause (bad input).
- **The Standard:** Always wrap enum conversions from request parameters in a `try-catch` block and throw a specialized **`BadRequestException`** with a descriptive message (e.g. "Invalid platform type: 'unknown'"). This ensures the client receives a correct `400` status code instead of a generic system crash.

## API Documentation as Truth
- **The Standard:** Use `quarkus-smallrye-openapi` to generate the API contract directly from the code. This ensures the documentation never drifts from the actual implementation ("Code is Truth").
- **Implementation:** 
  - Annotate JAX-RS methods with `@Operation` for summaries and descriptions.
  - Use `@APIResponse` and `@Content` (with `@Schema`) to document specific response bodies and status codes.
  - For complex generic responses like ApiEnvelopeDTO<T>, create private static inner helper classes that extend the generic DTO with a specific type and use them in the @Schema(implementation = ...) attribute. This allows OpenAPI to generate precise schemas for each endpoint's response envelope.

## Production Security
- **The Trap:** Accidentally exposing Swagger UI in production provides a detailed map of your API to potential attackers.
- **The Standard:** Always explicitly disable Swagger UI in the production profile.
- **Configuration:** Use %prod.quarkus.swagger-ui.always-include=false in application.properties. This is a critical security measure to minimize the attack surface of the application.

## Authentication & Session Management (BFF Pattern)

### Access Control for Public Data
- **The Standard:** Content sources and content items are public data and do not belong to a specific user. Access control should not be based on user ownership for these entities.

### Direct OIDC Initiation for SPAs
- **The Problem:** In a BFF (Backend-for-Frontend) architecture, directing a Single Page Application (SPA) to a server-side "Login" page before the OIDC provider (Google) adds a redundant user interaction.
- **The Standard:** SPAs should navigate directly to the backend's OIDC initiation endpoint (e.g., `/auth/login`). The backend should then immediately redirect to the OIDC provider. This reduces the number of clicks and redirects for the end user.

### Explicit Cookie Clearing & Cache Prevention
- **The Problem:** Modern browsers may cache `302 Found` redirects or retain session cookies if the path/domain isn't precisely matched during logout. Re-visiting a protected resource immediately after logout can trigger an invisible auto-login if the OIDC provider session is still active.
- **The Standard:** 
  1. During logout, manually clear the session cookie by adding a `Set-Cookie` header with `Max-Age=0` and matching `Path=/`.
  2. Set explicit cache-prevention headers (`Cache-Control: no-cache, no-store, must-revalidate`) on all authentication-related redirects. This ensures the browser always evaluates the actual authentication state rather than relying on cached results.

### Canonical Origin Normalization
- **The Problem:** Incoming `Origin` headers may or may not include default ports (e.g., `https://example.com:443`). Simple string comparisons fail when the configured allowed origin differs from the header's port representation.
- **The Standard:** Use `java.net.URI` to parse the origin string and reconstruct it in a canonical form (scheme + host + explicit non-default port). This provides a robust foundation for CORS and redirect validation. Always log malformed origins at `WARN` level to facilitate debugging without crashing the validator.

## Typed Pagination & OpenAPI (Quarkus / Orval)

### Eliminating `unknown[]` in Generated Collections
When using a generic `PaginatedResponse<T>` record or class in Quarkus, `quarkus-smallrye-openapi` often fails to resolve the specific type `T` in the generated `openapi.json`, mapping `items` to a generic `object` or `unknown[]` in the Orval-generated TypeScript models.
- **The Standard:** 
    1. Convert the `PaginatedResponse<T>` from a Java `record` to a standard class to allow for easier inheritance and explicit schema definition.
    2. In each resource class (e.g., `AdminApiResource`), create a `public static` inner class that explicitly extends the generic response: `public static class AuditLogPaginatedResponse extends PaginatedResponse<AuditLogEntryDTO> { ... }`.
    3. Use this specific subclass in the `@APIResponse` annotation: `@Content(schema = @Schema(implementation = AuditLogPaginatedResponse.class))`.
    4. Orval will now generate a dedicated `AuditLogPaginatedResponse` interface with `items: AuditLogEntryDTO[]`, providing 100% end-to-end type safety without manual casting.

## AI Prompt Architecture & Templating (POV-170)

### "System-as-Frame, AI-as-Intelligence"
- **The Trap:** Including UI-level structural elements (e.g., `## Daily Briefing`, `### 1. Executive Summary`) directly in AI prompts. This tightly couples the AI output to a specific UI version, leading to redundant headers, inconsistent styling, and a fragile "all-or-nothing" rendering model.
- **The Standard:** Strip all structural metadata and section numbering from AI prompts. The AI should generate **Pure Intelligence** (clean markdown sections), while the **Frontend System** provides the structural **Frame** (consistent UI headers, platform icons, and links). This ensures a professional, version-stable, and theme-aware presentation.

### Named Variable Injection ({{variable}})
- **The Trap:** Using legacy positional formatting (`String.formatted("%s", ...)`) for complex multi-variable prompts. This is extremely fragile; changing the order of variables in the template requires a corresponding change in the Java caller, leading to "Silent Data Mismatches" that are difficult to debug.
- **The Standard:** Migrate all prompt templates to an explicit **named variable** syntax (e.g., `{{title}}`, `{{transcript}}`, `{{summaries}}`). Use a single-pass Matcher strategy for production-grade prompt templating.

## Data Model Evolution (POV-170)

### Metadata Promotion (Base Entity Elevation)
- **The Trap:** Storing high-value metadata (e.g., `authorName`) only in specific subclasses (e.g., `YouTubeVideo`) while the primary API returns the base type (`ContentItem`). This prevents the UI from performing universal attribution without complex casting or additional API calls.
- **The Standard:** When a metadata field becomes a requirement for "Global Resilience" or "UX Consistency" (e.g., showing the author in a briefing), **promote** that field to the **Base Entity** (`ContentItem`) and the **Global DTO**.

### Single-Pass Template Variable Replacement (Matcher Strategy)
- **The Trap:** Chaining `String.replace()` or iteratively using `StringBuilder.indexOf/replace` for multiple variables. These "multi-pass" approaches result in O(V * N) complexity, causing high memory overhead and performance degradation on large inputs like transcripts.
- **The Standard:** Use **java.util.regex.Matcher.appendReplacement()** with a **pre-compiled static Pattern**. This provides a true O(N) single-pass scan while eliminating the overhead of repeated regex compilation.

---

## Related Documents
- [ADR-0005: Summary Public Sharing](../../adr/0005-summary-public-sharing.md)
- [ADR-0007: Onboarding Logic & Persistence](../../adr/0007-onboarding-logic-persistence.md)
- [Use Cases: Summary Generation](../../analysis/use_cases.md)

