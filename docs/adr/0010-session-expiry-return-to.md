# 10. Session Expiry: Auto-Login with ?redirect= Param

Date: 2026-05-21
Updated: 2026-05-23 (POV-187 planning interview — decision revised, see below)

## Status

Accepted

## Context

When a user's session expires mid-use (e.g., while reading a briefing), the Axios response interceptor in [client.ts](../../frontend/src/api/client.ts) currently handles the resulting 401 by calling `clearAuth()` and re-throwing the error. This causes the error to surface at the React error boundary, producing a confusing "glitch in the matrix" experience rather than a recoverable sign-in prompt (POV-187, Discussion 5 in [v0.17.0-planning-process.md](../versions/v0.17.0/v0.17.0-planning-process.md)).

The fix requires:
1. Intercepting the 401 before it reaches the error boundary.
2. Automatically initiating re-authentication.
3. Restoring the user to the page they were on before expiry.

Because authentication is handled via server-side OAuth (Google via Quarkus OIDC — see [ADR-0002](0002-auth-mechanism.md)), the `returnTo` path must survive a round-trip through the external OAuth provider. There is no `/login` SPA route; `login()` in `authStore` performs `window.location.href = '/auth/login'`, a full-page redirect.

Critically: Google sessions typically outlast the application session. This means the OAuth round-trip after expiry is usually invisible to the user — no Google login page is shown, and the redirect completes in under a second.

## Decision

On 401, the interceptor **automatically initiates re-authentication** by redirecting to `/auth/login?redirect=<currentPath>`. The backend `LoginResource` already validates the `redirect` param via `resolveSafeDestination()` (starts with `/`, no `//`, normalized via `URI.normalize()`), and applies it after the OAuth callback completes. The user is returned to their original page seamlessly.

### Flow

```text
401 received in Axios interceptor
  │
  ├─ useAuthStore.getState().clearAuth()
  ├─ globalThis.location.href = '/auth/login?redirect=' + encodeURIComponent(pathname + search + hash)
  │
  └─ [OAuth round-trip — usually invisible if Google session active]
       │
       └─ LoginResource: reads ?redirect, validates, redirects browser to returnTo
            │
            └─ User lands on original page, already authenticated
```

### Implementation details

1. **`client.ts` case 401**: use `globalThis.location` (not the router singleton — importing it creates a circular dependency). On 401: check a module-level `sessionExpiredRedirectInProgress` flag to absorb concurrent failures silently, call `clearAuth()`, read `pathname + search + hash` from `globalThis.location`, set `globalThis.location.href = '/auth/login?redirect=' + encodeURIComponent(path)`. Return `new Promise(() => {})` — never resolves, preventing error boundary activation during navigation.

2. **Path safety**: delegated entirely to `LoginResource.resolveSafeDestination()` — no client-side validation regex needed. If the path is somehow invalid, the backend defaults to `postLoginUri` (`/dashboard`).

3. **No `LandingPage` changes**: user never visits the landing page during expiry recovery.

4. **No `sessionStorage`**: not needed — the `?redirect=` param survives the OAuth round-trip via the backend.

5. **No notice/toast**: the seamless return to the original page is itself the UX confirmation. A toast would require `sessionStorage` machinery and adds no value when the round-trip is invisible.

## Alternatives Considered

### sessionStorage + landing page banner (original ADR-0010 decision — superseded)

Original plan: save `returnTo` in `sessionStorage`, redirect to `/?notice=session_expired`, show a banner, user clicks Sign In, after OAuth the LandingPage consumes `sessionStorage` and navigates to `returnTo`.

Rejected during POV-187 planning interview because:
- Requires the user to manually click Sign In — unnecessary friction when the Google session is still active.
- The OAuth round-trip is typically invisible (sub-second), so the user would see a confusing landing page flash.
- `LoginResource` already provides a validated `?redirect=` mechanism, making `sessionStorage` redundant.
- More moving parts: `sessionStorage` key, `validateSearch` union, `LandingPage` `useEffect`, i18n keys — all eliminated.

### Backend OAuth `state` parameter (rejected for v0.17.0)

Pass `returnTo` as a query param to `/auth/login`; backend embeds it in the OAuth `state` parameter; after callback, redirects the browser to the stored path.

This is architecturally equivalent to what we implemented — `LoginResource` already does exactly this via `resolveSafeDestination()`. No additional work needed.

### No `returnTo` (rejected)

On 401, redirect to `/auth/login` with no redirect param. User re-authenticates, lands on `/dashboard`. Simpler but materially worse UX — user loses all context of where they were. Rejected.

### Flash toast before redirect (rejected)

Show a React toast, then delay redirect by ~1500ms. Adds artificial latency. Rejected — the page navigates away immediately anyway.

## Consequences

- The 401 interceptor is the single place in the frontend where session expiry is handled. `ErrorPage` is no longer triggered by expired-session 401s.
- The `?redirect=` param is validated server-side; invalid paths fall back to `/dashboard`.
- `LoginResource.resolveSafeDestination()` is now relied upon by both the voluntary login flow and the session-expiry flow.
- `sessionStorage` key `auth.returnTo` is **not** used — if a future migration introduces it, ensure no collision with this key name.
