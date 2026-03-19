# Knowledge Item: Frontend UX Resilience Patterns

## Overview
This item documents the standard architecture for ensuring global UX resilience and polished loading states in Povzetkar's React-based frontend.

## 1. Network Resilience (Axios Retry)
To handle transient network errors (e.g., timeouts, 502/503/504), `axios-retry` is integrated into the global Axios client (`src/api/client.ts`).

### Configuration
```typescript
axiosRetry(axiosInstance, {
  retries: 3,
  retryDelay: axiosRetry.exponentialDelay,
  retryCondition: (error) => {
    // Retry on network errors or transient 5xx status codes
    return isNetworkOrIdempotentRequestError(error) || 
           (error.response?.status === 502 || error.response?.status === 503 || error.response?.status === 504);
  },
  onRetry: (retryCount, error) => {
    logger.warn(`[Axios] Retrying request (${retryCount}/3)...`, { error: error.message });
  }
});
```

## 2. Global Error Handling
A two-tier strategy is used to prevent application crashes:

### Root Level (Catastrophic Render Failures)
The `App` component is wrapped in a class-based `ErrorBoundary` (`src/components/error/ErrorBoundary.tsx`) which renders the `ErrorPage` as a fallback.

### Route Level (Router Failures)
The `TanStack Router` configuration (`src/router.tsx`) uses `defaultErrorComponent: ErrorPage`. This catches errors occurring in `beforeLoad` or `loader` functions.

### Query Level (API Failures)
To bridge TanStack Query with the global error boundary, `throwOnError: true` is enabled in the global `QueryClient` (`src/main.tsx`). This forces component-level rendering to fail and trigger the boundary if an API call fails after all retries.

## 3. Polished Loading States (Skeletons)
Consistent loading experiences are provided via the `Skeleton` component (`src/components/ui/Skeleton.tsx`).

### Design Tokens
- **Background:** `bg-slate-900/50`
- **Border:** `border border-slate-800/50`
- **Border Radius:** `rounded-xl`
- **Animation:** `animate-pulse`

## 4. Toast Notifications
`Sonner` is the unified toast bridge. It should be configured with `richColors`, `theme="dark"`, and custom `toastOptions` for consistency with the "Midnight Slate" dashboard aesthetic.

## 5. Testing Patterns (E2E)
When testing resilience in Playwright, account for `axios-retry` latency. Increase `expect` timeouts to at least 15 seconds to allow for 3 retries with exponential backoff.
