# POV-202 Retrospective — Optimize frontend (initial-load bundle)

**Date:** 2026-07-05

## 1. Planned vs. actual

**Scoped:** route-level lazy split (12 routes), lazy `SystemMetadata`, remove dead SSG apparatus, conditional `manualChunks`, one new briefing-detail E2E, before/after bundle + Lighthouse, docs (CHANGELOG / 3 backlog / P-5 NFR).

**Delivered:** all of the above, plus a second lazy boundary in `AppShell` (unplanned but required — see Deviations), the `RoutePending` file extraction, and a 4th backlog item (pre-existing lint debt). `manualChunks` skipped on evidence. **Primary gate exceeded:** initial-load JS **370 → 167 KB gzip (−55%)**, below both the ≤250 KB (must) and ≤230 KB (stretch) targets and below the ~200 KB floor the task predicted. Landing Lighthouse **85 → 93**.

## 2. Deviations & why

- **`AppShell` was a second eager importer of `SystemMetadata`.** Planning traced only `LandingPage`; the first build proved the entry chunk unchanged because `AppShell` (eager, via the eager protected-layout route) held a static import. Fixed by lazying both. *Rationale:* a single lazy boundary is worthless while any eager path to the module survives.
- **Route IDs prefixed by the layout's `id`.** `createLazyRoute('/authenticated/dashboard')`, not `/dashboard`. Caught by typecheck.
- **`manualChunks` skipped**, `RoutePending` extracted, BriefingDetail measured by chunk-delta — all detailed in the walkthrough; each was an evidence- or lint-driven correction, not a scope change.

## 3. Technical findings (durable record)

1. **A `React.lazy`/`route.lazy` boundary only helps if it severs *every* eager path to the module.** Vite reports this precisely: `"X is dynamically imported by A but also statically imported by B, dynamic import will not move module into another chunk."` Grep all importers before assuming a lazy boundary works. This bit twice this task (SystemMetadata via AppShell) and was foreshadowed by the `authStore` warning in Phase 0.

2. **gzip size, raw size, and executed-code size are three different budgets:**
   - **gzip** predicts *transfer time* only.
   - **raw (uncompressed) bytes** drive *decompression + parse* cost — this dominates first paint. POV-202's FCP improved ~1 s though gzip shrank only ~200 KB, because the *raw* delta was ~745 KB.
   - **executed-code size** drives *bootup* (parse+compile+execute). This barely moved (~320 ms → ~320 ms) despite ~40 chunks leaving the eager graph — because that code never *executed* on the landing page, only loaded. Lazy-splitting wins on transfer/parse, not on execution of code that wasn't running.

3. **TanStack code-based lazy routing:** `createLazyRoute('<full-route-id>')({ component })` in a `*.lazy.tsx` file, consumed by `createRoute({...}).lazy(() => import('./X.lazy').then(d => d.Route))`. **Critical options** (`beforeLoad`, `loader`, `validateSearch`, `path`) MUST stay in the eager `createRoute`; only non-critical options (`component`, `pendingComponent`, `errorComponent`) go in the `.lazy` file. This is exactly why keeping auth-guard *layout* routes eager neutralizes the lazy-behind-guard ordering hazard: the guard is a critical option that never defers.

4. **"Install size" ≠ "bundle contribution."** The task's "~370 KB markdown pipeline" was `node_modules` source size; the actual tree-shaken + gzipped `markdown` chunk is **3.8 KB gzip**. Same conflation the task itself flagged for date-fns. When sizing a dependency's real cost, measure the built chunk, not the install.

5. **The repo's Lighthouse harness (`lighthouse_audit.py`) hard-codes a global `lighthouse` binary and does no auth injection.** For local runs, `npx lighthouse` works without a global install; auth-guarded routes can't be profiled by it without a live session.

## 4. Feed-forward (for the next task)

- **The frontend lint baseline is red (31 pre-existing errors).** Until the "Frontend lint debt cleanup" backlog item is done, `npm run lint` clean is not a meaningful DoD gate — interpret it as "introduce no *new* lint problems" and verify with a stash test (`git stash` your changes, re-lint, compare counts).
- **P-5 NFR now exists** (`docs/sdlc/02_non_functional_requirements.md`): landing initial JS ≤ ~250 KB gzip, Lighthouse ≥ 90, seeded at 167 KB / 93. It's aspirational, not CI-enforced. Two backlog items (bundle-size budget, Lighthouse CI) are the enforcement fast-follow.
- **`SystemMetadata` still has 3 markdown call-sites without the sanitize plugin stack** (pre-existing, see the "Markdown Rendering Inconsistency (DRY)" backlog item) — untouched by POV-202, which was purely structural. A future `<MarkdownContent>` wrapper task should fold in these call-sites.
- **When adding any eager-imported component to `router.tsx`, `App.tsx`, `AppShell.tsx`, `client.ts`, or a store**, remember they anchor the initial-load graph. Prefer lazy boundaries for anything heavy, and always check the Vite "also statically imported by" warnings after a build.
