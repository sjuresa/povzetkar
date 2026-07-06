# POV-202 Walkthrough — Optimize frontend (initial-load bundle)

**Git-style title:** `perf(frontend): route-level code-splitting + lazy markdown, remove dead SSG deps`
**Date:** 2026-07-05

## 1. What was done

The frontend shipped as a single **1.29 MB raw / 370 KB gzip** JavaScript bundle: `router.tsx` statically imported all 16 route components, so every visit — including the public landing page — eagerly loaded the entire authenticated + admin surface and the markdown rendering pipeline. POV-202 cut the **initial-load JS to 167 KB gzip (−55%)** through three changes:

1. **Route-level code-splitting** (`frontend/src/router.tsx`): every authenticated route (dashboard, profile, topics, briefings, briefing-detail, sources, source-detail), the entire admin cluster (audit-log, content, jobs, users), and the public share route (`/s/$uuid`) are now lazy-loaded via TanStack Router's idiomatic **`createLazyRoute` + `route.lazy()`** mechanism. Each split route gained a 3-line `*.lazy.tsx` wrapper (12 files); `router.tsx` chains `.lazy(() => import('./X.lazy').then(d => d.Route))` on each `createRoute`. The **landing page and both auth-guard layout routes stay eager**, so the async `beforeLoad` guard always runs *before* a lazy child chunk is requested. A minimal `defaultPendingComponent` (`RoutePending`, extracted to its own file) covers slow chunk loads; chunk-load failures degrade to the existing `ErrorPage`.

2. **Lazy-loaded `SystemMetadata`** (the changelog/roadmap modal): wrapped in `React.lazy` + `<Suspense>` behind its `isOpen` trigger in **both** `LandingPage.tsx` **and** `AppShell.tsx` (the modal had two eager importers — see Deviations). Its markdown pipeline now lives in a deferred chunk loaded only when the modal opens.

3. **Removed the dead SSG apparatus**: `react-router-dom`, `vite-react-ssg`, the `ssgOptions` block in `vite.config.ts`, and the nested `vite-react-ssg` override — never wired to a mount since project init (`createRoot` was used from commit `613cffab`). `npm install` removed 23 packages.

**Verification (all green):** `tsc -b`; `vitest` (68 passed); full `playwright` E2E (11 passed, incl. a new `briefing-detail.spec.ts`); `npm run build`. Landing-page Lighthouse **85 → 93** (FCP −1.0 s, LCP −0.5 s).

## 2. Planned vs. actual scope

| | Planned | Actual |
| --- | --- | --- |
| Route lazy split | 12 routes | 12 routes ✅ |
| Lazy `SystemMetadata` | `LandingPage` only | **`LandingPage` + `AppShell`** (2nd eager importer found) |
| Remove SSG | full removal | full removal ✅ (−23 pkgs) |
| `manualChunks` | conditional | **skipped** (evidence-based — vendor split wouldn't cut first-load bytes) |
| New E2E | 1 (briefing-detail) | 1 ✅ |
| `RoutePending` | inline in `router.tsx` | **extracted to own file** (lint) |
| Docs | CHANGELOG, 3 backlog, P-5 NFR, walkthrough, retro | + 1 unplanned backlog item (pre-existing lint debt) |

Net additions: the `AppShell` lazy fix, the `RoutePending` extraction, and one extra backlog item — all in-scope discoveries, no scope removed.

## 3. Time delta

**Estimated:** 3.5–5 h base, 5–6.5 h with contingency.
**Actual:** ~1.5 h focused execution (Phases 0–7). Well under estimate — the flagged dominant risk (lazy-behind-async-guard ordering) did not materialize, and the split's idiomatic mechanism was confirmed against TanStack docs before writing, avoiding rework beyond the one route-ID-prefix typecheck fix.

## 4. Deviations

1. **`SystemMetadata` had a second eager importer (`AppShell`).** Lazying only `LandingPage`'s copy was a no-op — `AppShell` (the authenticated shell, in the eager entry chunk via the eager `protectedLayoutRoute`) held a static import, keeping the modal + markdown in the initial load. Fixed by mirroring the `lazy()` + `<Suspense>` pattern in `AppShell` too (PO-approved Option A). This is what unlocked the second ~40 KB gzip drop (Phase 1 → Phase 2: 210 → 171 KB).
2. **`createLazyRoute` IDs are layout-id-prefixed, not URL-path.** Because the protected layout sets `id: 'authenticated'`, child route IDs are `/authenticated/dashboard`, `/authenticated/admin/audit-log`, etc. — caught by `tsc -b`, fixed across 11 wrappers.
3. **`RoutePending` extracted to its own file.** Defining it inside `router.tsx` (which exports the non-component `router`) tripped `react-refresh/only-export-components` — the one lint problem POV-202 briefly introduced. Extraction restored a lint-neutral diff.
4. **BriefingDetailPage Lighthouse measured by chunk-delta, not authed Lighthouse.** The repo's bare Lighthouse harness cannot inject a session; the auth-guarded route redirects to `/` unauthenticated. PO-approved substitute: verify via the route chunk leaving every initial load (it does) rather than a misleading redirect-profiled score. Landing-page Lighthouse recorded normally.
5. **`manualChunks` skipped.** Post-split analysis showed the entry chunk is the irreducible eager core (react-dom, router, query, i18next, axios, zustand) with no leaked app/route code; a `vendor-react` split would only shift bytes between eager files for a second-order caching benefit, not reduce first-load bytes.

## 5. Traceability

N/A — structural perf refactor; anchor is the P-5 NFR (initial-load bundle budget).
