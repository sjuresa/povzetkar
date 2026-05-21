# 10. Session Expiry: returnTo via sessionStorage

Date: 2026-05-21

## Status

Accepted

## Context

When a user's session expires mid-use (e.g., while reading a briefing), the Axios response interceptor in [client.ts](../../frontend/src/api/client.ts) currently handles the resulting 401 by calling `clearAuth()` and re-throwing the error. This causes the error to surface at the React error boundary, producing a confusing "glitch in the matrix" experience rather than a recoverable sign-in prompt (POV-187, Discussion 5 in [v0.17.0-planning-process.md](../versions/v0.17.0/v0.17.0-planning-process.md)).

The fix requires:
1. Intercepting the 401 before it reaches the error boundary.
2. Redirecting the user to the landing page to re-authenticate.
3. Optionally restoring the user to the page they were on before expiry.

Because authentication is handled via server-side OAuth (Google via Quarkus OIDC — see [ADR-0002](0002-auth-mechanism.md)), the `returnTo` path must survive a round-trip through the external OAuth provider. There is no `/login` SPA route; `login()` in `authStore` performs `window.location.href = '/auth/login'`, a full-page redirect.

## Decision

Preserve the `returnTo` path in **`sessionStorage`** before initiating re-authentication. After a successful OAuth round-trip the landing page consumes and clears the stored path, then navigates there via the TanStack Router singleton.

### Flow

```text
401 received in Axios interceptor
  │
  ├─ sessionStorage.setItem('auth.returnTo', currentPath + currentSearch)
  ├─ useAuthStore.getState().clearAuth()
  ├─ router.navigate({ to: '/', search: { notice: 'session_expired' } })
  │
  └─ [User sees landing page with "session expired" toast, clicks Sign In]
       │
       └─ login() → /auth/login → Google → OAuth callback → lands on /
            │
            └─ indexRoute.beforeLoad: checkAuth() succeeds
                 │
                 └─ LandingPage: reads sessionStorage['auth.returnTo']
                      ├─ exists  → removeItem, router.navigate({ to: returnTo })
                      └─ missing → normal landing page render
```

### Implementation details

1. **`client.ts` case 401**: import `router` from `../router` (singleton, no circular dependency since router.tsx imports nothing from client.ts). Import `useAuthStore`. On 401: save path, clear auth, navigate. Guard: if `router.state.location.pathname === '/'`, skip navigate (prevents loop if landing page itself triggers a 401, which should not happen but is defensive).

2. **`returnTo` validation**: before `router.navigate`, validate that the stored path is a relative path (starts with `/`, no `//`, no scheme). If invalid, discard silently — user lands on dashboard.

3. **`LandingPage.tsx`**: in `useEffect` (runs after auth check), if `isAuthenticated && sessionStorage['auth.returnTo']` exists, consume and navigate.

4. **`session_expired` notice**: extend existing `notice` search param on `/` (already handles `notice=restricted`) with a `session_expired` case that shows the i18n string `auth.session.expired`.

## Alternatives Considered

### Backend OAuth `state` parameter (rejected for v0.17.0)

Pass `returnTo` as a query param to `/auth/login`; backend embeds it in the OAuth `state` parameter; after callback, redirects the browser to the stored path.

This is the architecturally canonical OAuth pattern and survives multi-tab scenarios. It was **rejected for v0.17.0** because:
- Requires non-trivial Quarkus OIDC customization (custom `OidcAuthenticationMechanism` or `TenantResolver`) which would consume the majority of the POV-187 budget (estimated 4-6h total).
- The sessionStorage approach is a legitimate, widely-used SPA pattern (used by Auth0 SPA SDK, Firebase Auth, etc.) and is not a "hack".
- For Povzetkar's scale (closed platform, limited user base), multi-tab edge cases are negligible.

This alternative is **not logged as future backlog** — it would require replacing working code with equivalent functionality. The sessionStorage approach is accepted as the permanent solution.

### No `returnTo` (rejected)

On 401, redirect to landing page, user re-authenticates, lands on dashboard. Simpler but materially worse UX — user loses all context of where they were (especially costly when reading a long briefing). Rejected in favour of the sessionStorage approach at no significant cost.

### TanStack Router `redirect` with `search.returnTo` (rejected)

Encode `returnTo` in the URL: `/?returnTo=%2Fbriefings%2F123`. Exposes the destination path in the browser address bar and in server logs for the landing page request. No technical advantage over sessionStorage for this use case.

## Consequences

- The 401 interceptor is the single place in the frontend where session expiry is handled. `ErrorPage` is no longer triggered by expired-session 401s.
- `sessionStorage` key `auth.returnTo` is a frontend convention; if a future migration to server-side sessions or a different auth library is undertaken, this key must be removed.
- The `notice=session_expired` search param on `/` must be kept in sync with the `auth.session.expired` i18n key.
