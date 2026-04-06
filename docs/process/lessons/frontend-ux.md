# Frontend & UX Design Patterns

## Auditory Feedback & Asset Minimization (POV-171)
When adding auditory feedback (toasts, alerts), the traditional approach of using `.mp3` or `.wav` files introduces bundle size and latency overheads.
- **The Standard:** Use the **Native Web Audio API (`OscillatorNode`)** to synthesize UI sounds dynamically in the browser. 
  - **Zero-Byte Footprint:** 0KB asset overhead.
  - **Instant Feedback:** No network latency.
  - **Absolute Control:** Frequencies and durations are adjustable in code.
  - **Autoplay Handling:** Initialize the `AudioContext` only on the first user interaction to comply with browser security policies.

## Vite, React & Tailwind v4
Tailwind CSS 4 introduces a 'CSS-first' approach.
- **The Trap:** Trying to configure Tailwind 4 using a legacy `tailwind.config.js` with unsupported internal plugins.
- **The Standard:** Leverage `@tailwindcss/vite` and import Tailwind directly in your CSS: `@import 'tailwindcss';`. Keep configurations in CSS variables where possible.

### Bundle Visualization (POV-176)
- **The Trap:** Unknowingly introducing bloated duplicate libraries.
- **The Standard:** Use `vite-bundle-visualizer`. It acts as a zero-config integration point to visually dissect chunk proportions. Run `npm run build:stats` to immediately review payload distribution and safeguard the 1.1MB budget when merging UX enhancements.

## API Integration (Orval & Axios)

### Strict Linting in API Clients
When building production-grade API clients with strict ESLint rules (2025 standards):
- **The Trap:** Using `any` for raw JSON payloads from `JSON.parse` or environment variables (e.g., `import.meta.env`).
- **The Standard:** Always use `unknown` as the initial type and perform explicit casting via specialized interfaces or `Record<string, string>`. 

### Type-Safe Error Handling in Envelopes
Our backend returns all errors wrapped in an `ApiEnvelope` containing a `code` and `message`.
- **The Standard:** In the Axios response interceptor, reject the promise with a proper `Error` object. To preserve the API error code, use an intersection type `(error as Error & { code?: string }).code = apiCode;`. This allows downstream components to use the standard `error.message` while still having access to the specific error code for conditional logic.

### Header Normalization for Axios
Orval generates `options.headers` as `HeadersInit`. Axios expects a plain object.
- **The Standard:** Always use a normalization helper in the custom fetcher to convert `HeadersInit` to `Record<string, string>`.

### Axios Cancellation & Global Logging
- **The Trap:** Logging all failed requests in a global Axios interceptor without checking for cancellations. In React 18 (Strict Mode), requests are frequently aborted by design (e.g., component unmount).
- **The Standard:** Always check `axios.isCancel(error)` in the error interceptor. These should be ignored or logged at `debug` level, never as `error`.

## Frontend Routing & Auth (TanStack Router + Zustand)

### Eliminating "Flash of Unauthenticated Content" (FOUC)
- **The Trap:** Checking auth status only inside components causes the "Unauthenticated" view (like a Landing Page) to flicker before the app realizes the user is logged in.
- **The Standard:** Use TanStack Router's `beforeLoad` hook with a state-based "isLoaded" flag. Perform the silent auth check inside `beforeLoad` if the state is not yet loaded.

### ESLint & TanStack Router Redirects
- **The Trap:** `redirect()` returns a special object intended to be thrown. Some ESLint rules flag this because it's not a subclass of `Error`.
- **The Standard:** Use `// eslint-disable-next-line @typescript-eslint/only-throw-error` for lines throwing `redirect()`.

## Frontend Real-time & Notifications (SSE)

### EventSource Lifecycle Management
- **The Trap:** Initializing `EventSource` directly in a component body or incorrectly in a `useEffect` without proper cleanup and ref guards. This leads to double-connections on mount in Strict Mode.
- **The Standard:** Always use a `useRef<EventSource | null>(null)` guard alongside `useEffect`. The ref ensures no second stream is spawned.

### State-Driven Notifications vs Imperative Logic
- **The Trap:** Triggering `toast()` calls directly within a generic API fetcher. This makes it difficult to silence notifications for specific routes.
- **The Standard:** Decouple event processing from notification display using a store (e.g., Zustand `eventStore`). Let a specialized "Coordinator Hook" subscribe to the store and trigger notifications based on state transitions.

### Unstable Selectors & useSyncExternalStore
- **The Trap:** Creating selectors that return new objects or arrays (`.filter()`) inside a store hook without an equality function. This causes infinite render loops.
- **The Standard:** Always use `useShallow` from `zustand/react/shallow` when returning a non-primitive or a new collection from a selector.

## Frontend UX Resilience Strategy

### Three-Tier Resilience
1. **Network Resilience:** Integrate `axios-retry` at the global client level for transient network errors (502/503/504).
2. **UI Safety Nets:** Wrap the root `RouterProvider` in a React `ErrorBoundary` and integrate with TanStack Router's `defaultErrorComponent`.
3. **Bridge Async State:** Enable `throwOnError: true` in TanStack Query to propagate background fetch failures into the UI's error boundary.

## Theme & i18n Foundations

### CSS Variable Semantic Theming (Tailwind 4)
- **The Standard:** 
    1. Define **Semantic Variables** (e.g., `--color-background-base`, `--color-text-primary`) in a global `:root` block.
    2. Map these variables in the Tailwind `@theme` block.
    3. Use a `.dark` class to override these semantic variables. This allows the application to use classes like `bg-background-base` which automatically adapt to the theme.

### window.matchMedia Mocking in Vitest
`window.matchMedia` is not implemented in `JSDOM` by default.
- **The Standard:** Centralize the mock in `src/test/setup.ts`. Use `Object.defineProperty` and provide a full mock implementation of the `MediaQueryList` interface.

### i18next Hybrid Loading Strategy
- **The Standard:** Bundle the **default language** as static JSON. Use `i18next-http-backend` to **lazy-load additional languages**.

## Advanced Routing (TanStack Router)

### Structural ID Propagation in Types
- **The Trap:** Relying on the visual browser URL as the source of truth for the `from` property in `useParams` can lead to `TS2345` errors if the internal type tree is structured differently.
- **The Standard:** If `tsc` reports a mismatch, use `useParams({ strict: false })` as a pragmatic bypass for layout-heavy route trees.

## Typography & Layout

### Symmetrical Padding & .prose Layout
- **The Trap:** A container with `p-8` looks unbalanced if the first element inside it (like an `H2`) has a default `margin-top`.
- **The Standard:** Always apply a CSS override to ensure the first child of a `.prose` container has no top margin.

### "Executive-Grade" Print Standardization (POV-162)
- **The Standard:** Implement a dedicated **Black & White print-ready HTML export**. Use high-contrast serif typography (`Times New Roman`, `Georgia`). Ensure background colors are removed and text is strictly black.

## Client-Side SEO & Premium Export (POV-162)

### Dynamic Metadata in SPAs (React Helmet)
- **The Standard:** Use `react-helmet-async` to inject dynamic tags (`og:title`, `og:description`) at the route level. Deriving metadata from the specific content item ensures rich social previews.

## Responsive Layout & Header Resilience (POV-170)

### The "flex-1 Wrap Trap"
- **The Trap:** Using `flex-1` on both a long title and its sibling. This causes premature wrapping.
- **The Standard:** In editorial headers, prioritize the text element with **`flex-1 min-w-0`** and set siblings to `flex-shrink-0`.

### Drop-Cap Safety (Pseudo-Element Side Effects)
- **The Trap:** Applying `first-letter:` styling directly to dynamic content. If the text starts with a space or symbol, the styling breaks.
- **The Standard:** Verify that the first non-whitespace character is alphabetic before applying drop-cap classes.

## High-Contrast Semantic Theming (POV-174)

### The "Zinc Palette" & Semantic Taxonomy
Migrating from color-specific variables (e.g., `--color-slate-50`) to semantic descriptors (e.g., `--color-bg-primary`) is essential for maintaining complex dark/light themes.
- **The Standard:** Always define variables based on their **role (BG, Text, Accent, Surface)** rather than their literal hue.
- **Contrast Guardrails:** In Light Mode, standard interactive accents like **Blue-600** often fail WCAG AA (4.5:1) when placed on secondary backgrounds like **Neutral-100**.
  - **The Standard:** Use **Blue-700 (#1d4ed8)** for Light Mode to guarantee a **5.0:1** contrast minimum across all branded surfaces.

### Icon/Surface Separation
- **The Trap:** Using a "Surface Deep" (Neutral-200) background for icons on a "BG Secondary" (Neutral-100) card. This results in muddy, low-contrast UI.
- **The Standard:** Use **BG Primary (White)** as the base for accent-colored icon squares on secondary surfaces. This reaches **6.5:1** contrast and creates a "Premium" clean look.

---

## Related Documents
- [ADR-0004: Frontend Architecture](../../adr/0004-frontend-architecture.md)
- [v0.16.0 UI/UX Manifesto](../../versions/v0.16.0/v0.16.0-ui-ux-manifesto.md)
- [ADR-0006: Auditory Feedback Framework](../../adr/0006-auditory-feedback-framework.md)
