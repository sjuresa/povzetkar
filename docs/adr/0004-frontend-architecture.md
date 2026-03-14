# ADR-0004: Frontend Architecture & Technology Stack

**Status:** Accepted
**Date:** 2026-03-13

## Context
Project **Povzetkar** is moving to a Backend-for-Frontend (BFF) architecture. The frontend must be:
1.  **Production-Ready**: High maintainability and 100% type-safety.
2.  **Contract-First**: Driven by the backend OpenAPI specification.
3.  **Flexible**: Easy to redesign or theme in later stages.
4.  **Highly Interactive**: Supporting real-time updates (SSE) and complex state.

## Decision
We will implement the frontend using a **Monorepo** structure and the following technology stack:

### 1. Monorepo Strategy: Hybrid Directory Structure
*   **Structure**: `/backend` (Quarkus/Maven) and `/frontend` (Vite/NPM).
*   **Management**: No root-level package manager for dependency sharing. Each sub-project remains independent within its own ecosystem. To improve Developer Experience (DX), a minimal root-level `package.json` with scripts (e.g., using `npm-run-all`) may be used to execute cross-project tasks (linting, testing) without the complexity of workspaces.
*   **Integration**: Deployment artifacts will be orchestrated via a multi-stage Dockerfile or the `frontend-maven-plugin` within the backend build.

### 2. Core Framework: React + Vite + TypeScript
*   **Vite**: For superior developer experience and build speed.
*   **TypeScript**: Mandatory for end-to-end type safety.
*   **SEO Strategy (Pre-rendering)**: To satisfy the requirement for an indexable Landing Page without the complexity of a full SSR framework (like Vike or Next.js), we will use a **Pre-rendering plugin** (e.g., `vite-plugin-prerender`) at build time. This renders targeted routes into static HTML files that are served to search engines, while the application remains a standard SPA for the authenticated dashboard.

### 3. Styling: Tailwind CSS + Shadcn/UI
*   **Tailwind CSS**: Uses design tokens (via CSS variables) allowing for easy global theme/design changes later.
*   **Shadcn/UI**: High-quality, accessible components that we "own" (code is copied into the project), allowing for total customization without library lock-in.

### 4. Data Fetching & Contract: Orval + TanStack Query
*   **Orval**: Automatically generates TypeScript types and TanStack Query hooks from the backend's OpenAPI specification. For CI/CD, the backend build will copy the generated `openapi.json` to `/frontend/openapi.json` (or a predictable subdirectory), which the Orval script will consume. This co-location simplifies the build process within the monorepo.
*   **TanStack Query**: Manages all server state, caching, and background refetching.
*   **API Client Resilience**: A global Axios interceptor will handle request-level concerns, while resource-specific state logic will handle mutations:
    *   **Global Interceptor**: Injects `X-Correlation-ID` and `Idempotency-Key` (for `POST /commands/*`), and standardizes error handling for 401, 403, 429, and 503 (respecting `Retry-After`).
    *   **Mutation Layer**: TanStack Query's `mutationFn` is responsible for injecting `If-Match` headers for Optimistic Locking, as it has access to the resource's current `@Version` state.

### 5. Routing: TanStack Router
*   **Decision**: We chose **TanStack Router** over React Router for its **100% type-safe routing**.
*   **Rationale**: Links, parameters, and search queries are checked at compile-time, preventing runtime navigation errors.

### 6. Local State & i18n
*   **Zustand**: Used for client-only state (e.g., UI preferences).
*   **i18next**: Standard library for internationalization, ensuring all strings are manageable outside of component logic.

### 7. Real-Time: SSE (Server-Sent Events)
*   Implementation will use a custom React hook (e.g., `useEventSource`) to consume job status streams defined in v0.11.

### 8. Testing Strategy
*   **Unit/Integration**: **Vitest** for component and logic testing.
*   **End-to-End (E2E)**: **Playwright** for verifying critical user flows (Login, Topic Creation, Briefing Generation).

## Consequences
*   **Positive**:
    - **Compile-time validation** of the entire application flow (API -> State -> Router -> UI).
    - **SEO Compliance**: Static pre-rendering ensures public pages (like the Landing Page) are easily indexable without server-side overhead.
    - **Consistency**: The "TanStack" ecosystem ensures highly predictable state and routing behavior.
*   **Negative**:
    - **Onboarding cost**: Significant learning curve for TanStack Router and Orval integration.
    - **Build Complexity**: Monorepo setup requires careful CI/CD configuration.
    - **Limited Code Sharing**: The absence of a root-level package manager (like NPM workspaces) makes it difficult to share code or dependencies between potential future frontend projects without duplication. (Mitigation: If multiple frontend projects are added, this can be addressed by publishing shared libraries to a private NPM registry).
