# POV-220 Retrospective — Landing Page Redesign

## 1. Planned vs. actual

The task started as "evaluate and redesign the landing page" and grew during the Brainstorm and planning, each step
approved by the PO: app-wide first-visit language/theme (the landing goal "a Slovenian visitor understands at once"
was unreachable while the app always opened in English), a shared public header (three duplicated toggle copies), and
an app-wide Slovenian vocabulary (the landing's term had to match the app it leads into). Estimate grew from ~9 h to
~12.5 h accordingly. After implementation the PO reviewed the page itself ("ok but boring … should be more
marketing") — amendment A1 (+~1.5–2 h) changed the tone and added visual depth; the pre-existing crash of the page
without a backend was fixed on the way. Everything planned was delivered; the privacy-notice page stays in the backlog as decided.

## 2. Deviations & why

- One test outside the component folder (`src/test/App.test.tsx`) asserted a deleted i18n key — found by the full suite,
  not by the impact analysis.
- The manual screenshot review found two alignment defects no test could see (header vs. content inset, FAQ centring).
- The pre-paint script's language detection was made independent of storage access — found while writing it.
- **The first design was too literal.** Decisions 1 and 4 (honest early access, only roadmap-backed promises) were
  right, but applied to the *tone* they produced a sober, report-like page. Honesty belongs in the claims (what is
  promised, what is stored); the voice can still sell. A1 kept every claim and changed the voice.

## 3. Technical findings

- **Honest copy is a requirement you can trace**: every claim on the page was checked against code (YouTube-only
  sources, `PENDING_APPROVAL` + `demo` role, stored `User` fields and `linkIdentity`) or `roadmap.md`. The old page's
  "Reality Check" existed nowhere but in its own copy.
- **Theme flash had two causes**: the class set in `useEffect`, and an unlayered inline `<style>` in `index.html`
  overriding the token background from `@layer base`.
- **The project has no Tailwind animation plugin** — `animate-in fade-in …` classes used elsewhere in the app do
  nothing; the landing defines its own keyframe under `prefers-reduced-motion: no-preference`.
- **The global `QueryClient` `throwOnError: true`** turns any failing decorative query into a route error page;
  decorative or optional queries must opt out (`throwOnError: false`).
- **Tailwind v4 has no `xs:` breakpoint** — `hidden xs:block` silently means "always hidden".
- **i18n key removal ripples into tests** because the global `t` mock returns keys.
- **Measuring P-5 needs a like-for-like baseline**: the bundle had grown from 167 KB to 179 KB before this task; a
  `HEAD` build in a temporary worktree (with a junction to `node_modules`) separated the task's +0.1 KB from that drift.
- **Process weight (v0.20 observation directive):** the PO asked for three internal reviews before approval (task
  definition, plan ×2): R1–R6 and F1–F12 — 18 findings, among them real defects (a FAQ that omitted stored data, a
  language "choice" frozen on init, a missing hook mock, an invisible subtitle). They were cheap to fix on paper.

## 4. Feed-forward

- **Show the PO the rendered page before close-out** (screenshots in the chat or a running dev server): the review
  that mattered most here — tone and spacing — was visual and came after the first close-out.
- **Optional queries opt out of `throwOnError`** (global default is `true`); audit other decorative queries.
- **Before removing i18n keys, grep all test and spec files** (`src/**/*.test.*`, `e2e/`) for them.
- **POV-214 (Caddy/CSP):** the inline pre-paint script in `index.html` needs a `sha256` source in `script-src`;
  recompute on every change (backlog note on the Caddy entry).
- **New SL copy follows `docs/guides/i18n-glossary.md`** (dnevni pregled, tikanje, sentence case) — the glossary guard
  in `i18n.test.ts` fails on "poročil"/"briefing".
- **Privacy notice (backlog):** the landing FAQ now states what is stored; the notice page should add purposes and
  retention and be linked from `LandingFooter`.
- **Missing keys `common.summary`, `briefingDetail.unknownAuthor`** (backlog) — a key-existence test would catch the
  class of bug.
