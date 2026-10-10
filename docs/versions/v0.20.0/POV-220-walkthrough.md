# POV-220 Walkthrough — Landing Page Redesign

**Task:** [POV-220](https://jumis.myjetbrains.com/youtrack/issue/POV-220) (Feature, Normal) · **Branch:** `POV-220` → `v0.20.0` · **Date:** 2026-09-28

## 1. What was done

The landing page sold a vision instead of the product: "Universal Understanding", an Information → Knowledge → Wisdom
pyramid and a "Reality Check" labelled *Active* — none of it implemented — an abstract hero image, a dead contact
address (`wisdom@povzetkar.ai`, a domain that only appears in planning docs) and a brutalist style the app itself does
not use. It never showed what Povzetkar does, and its CTA ("Create your first briefing") promised something a new user
cannot do: sign-in puts the account in `PENDING_APPROVAL` with the `demo` role.

**Landing page** (`routes/LandingPage.tsx`, `components/landing/*`)
- Early-access framing: headline "Your YouTube channels. One daily briefing." / "Tvoji YouTube kanali. En dnevni
  pregled.", a "Get started with Google" CTA inviting visitors to early access (manual approval is stated in the FAQ),
  and a static **sample daily briefing** (`SampleBriefing`, fictional channels, labelled *Example*) as the main visual.
- Sections: benefit strip, *How it works* (3 steps), *FAQ* (native `<details>`: sign-in flow, stored data incl. Google
  account ID and access-log IPs, supported sources), *Coming soon* (three roadmap-backed items, each labelled
  *Planned*), closing call to action. (First version: "Sign in with Google" with the approval note, *What's next* —
  changed by amendment A1, below.)
- App visual language (tokens, rounded cards, sentence case, one `h1`); `VisionSection`, the pyramid, the status card,
  the contact address and `hero-distillation.png` removed.
- `LandingNav` = shared `PublicHeader` + sign-in / dashboard action; language and theme toggles on every width.

**Amendment A1 — PO review of the page** ("ok but boring; too much space before *How it works*; Roadmap and Updates
are a copy of the project ones — should be more marketing")
- Benefit-led copy (hero subtitle, *How it works*, *Coming soon*, FAQ shortened with the same data list), early access
  as an invitation ("Get started with Google"); new **benefit strip** and **closing call to action**.
- Visual depth within the monochrome tokens: hero glow + faint grid (CSS gradients), a second offset card behind the
  sample briefing, a rise-in animation (`.landing-rise`, off under `prefers-reduced-motion`); *How it works* on a
  full-width band with numbered steps and connectors; section spacing halved.
- Footer links to the engineering changelog/roadmap modal removed from the landing page (the modal stays in the app);
  curated "What's new" for visitors → backlog.
- **2.13 fix:** with the backend unreachable the footer's metadata query threw into the route error boundary ("A
  glitch in the matrix" after ~6 s, pre-existing) — `useSystemMetadata` accepts `throwOnError`, the landing passes
  `false`.

**PR #287 review (Greptile, 4× P2) and review round 3**
- Copy no longer promises one briefing across topics: *How it works* step 3 — "a briefing for each topic: the day's
  essentials first, then a summary of every video"; benefit 2 — "daily briefings"; EN meta "short daily briefings".
- Closing call to action invites to early access ("Join early access") instead of promising a waiting briefing.
- FAQ "What data do we store?" also names the topics, sources and briefings a user creates.
- SL visitors get a Slovenian `<title>` (`Helmet`, `landing.meta.title`); the description and `og:` tags from
  `index.html` are updated in place (`useLocalizedHeadMeta`) and restored on unmount — emitting new tags would have
  left the English ones first in `<head>` (found in review round 3).
- PO: SL "povzetek za vodstvo" → **"bistvo dneva"** (sample briefing, briefing page, onboarding, pending banner, glossary).

**Shared header and modal**
- `components/layout/PublicHeader.tsx` — one header for the landing page and both public share views
  (`PublicBriefingView`, `PublicSummaryView`), reusing `LanguageSwitcher` / `ThemeToggle` instead of three inline
  copies; its subtitle uses `sm:block` (the old `xs:block` is not a Tailwind v4 breakpoint — the subtitle never
  showed). The summary view gets its own subtitle ("Shared summary").
- `SystemMetadata`: Wisdom/Reality Check tab and the "Wisdom" focus banner removed; "Vision Path" → "Roadmap".
- Router: the dead `?notice=` search param (nothing set it) removed.

**First visit: language and theme**
- `i18n/language.ts` (`resolveInitialLanguage`): stored choice → browser language (`sl*` → SL) → EN; the choice is
  stored only by the toggle; `<html lang>` follows the language.
- `themeStore.resolveInitialTheme`: stored choice → system preference.
- `index.html`: pre-paint script applies both before React loads; the inline dark gradient `<style>` and
  `bg-neutral-950` (which beat the token background) removed; title/description describe the YouTube-only product.

**Slovenian UI vocabulary** (app-wide, `sl.json`)
- Briefing = "dnevni pregled" everywhere (was "poročilo" / "briefing" / "povzetek"), tikanje, SL sentence case;
  onboarding steps now name the real labels ("Knjižnica pregledov", "Zaženi orkestracijo", "Izvoz"); RSS no longer
  promised (SL + EN). EN sidebar "Briefing Hub" → "Briefing Library". Glossary: `docs/guides/i18n-glossary.md`.

**Verification**
- Final state (after A1 and review round 1 fixes): `npx vitest run` **29 files, 148 tests green**; `npx playwright
  test` **14 passed**; `tsc -b` clean.
- Before A1: `npx vitest run`: **28 files, 142 tests green** (new: `language.test.ts`, `LandingPage.test.tsx`,
  `PublicHeader.test.tsx`, `SystemMetadata.test.tsx`, glossary guard in `i18n.test.ts`; extended `themeStore.test.ts`).
- `npx playwright test`: **13 passed** (existing specs unchanged + 2 pre-paint tests in `smoke.spec.ts`).
- **Red/green:** the metadata-failure e2e test shows the error page without the fix (2 runs) and passes with it;
  the theme-hydration test fails without the `persist` `merge`; the glossary guard fails on the `HEAD` `sl.json` (lists `navigation.briefings`,
  `dashboard.recentBriefings`, … ) and passes on the new one; both pre-paint e2e tests fail on the `HEAD` `index.html`
  (`<html class="">`) and pass with the script.
- `npx tsc -b` clean; ESLint on all touched files: only the two pre-existing `sonarjs/function-return-type` errors in
  `SystemMetadata.tsx` (`renderVersion`/`renderStatus`, unchanged lines; identical on `HEAD`).
- **NFR P-5:** initial-load JS (files referenced by `dist/index.html`, gzip) **179.1 KB** vs **179.0 KB** on `HEAD`
  (same method, temporary worktree) — +0.1 KB, within the ~250 KB ceiling; the gap to the 167 KB POV-202 baseline
  predates this task.
- Manual review after A1: 16 new screenshots (375/1440 × light/dark × SL/EN); fixed: the step connector crossed the
  step icons (now one segment per step, icon → next number).
- Manual review: screenshots at 375 px and 1440 px × light/dark × SL/EN (16 shots); fixed after the first look:
  header content was 32 px left of the page content, FAQ centred while other sections were left-aligned.
- Planning audit `validate-planning.py --current-version v0.20.0`: passed.

## 2. Planned vs. actual scope

- Added during planning (PO-approved): app-wide initial language/theme incl. pre-paint script (P1/P1b), shared
  `PublicHeader` for the public views (P2), app-wide SL vocabulary + tikanje + glossary (P3).
- Added after the PO's page review: amendment A1 (marketing copy, benefit strip, closing CTA, visual depth, spacing,
  no engineering changelog/roadmap links on the landing page) and the metadata-failure fix (2.13) — both PO-approved.
- Added during execution: `src/test/App.test.tsx` updated (asserted the deleted `landing.hero.cta_primary`; missed in the
  impact analysis); alignment fixes from the manual review.
- Not done (as planned): full privacy-notice page (backlog, tied to POV-214), `SystemMetadata`/`ProfilePage` restyle.

## 3. Time delta

Estimate ~12.5 h for the whole task (incl. PR loop and YouTrack record).

| Phase (2026-09-28, CET) | Actual |
| --- | --- |
| Evaluation of the old page (before the task) | not booked |
| Brainstorm 19:48–20:14 (7 decisions + internal review R1–R6) | 26 min |
| Planning 20:14–21:24 (P1–P4, plan, two internal review rounds F1–F12) | 70 min |
| Implementation 21:24–21:36 (all phases, tests, docs; incl. full suite, e2e, P-5 build) | 12 min |
| Manual review + alignment fixes 21:36–21:39 | 3 min |
| Close-out documents 21:39–~21:40 | ~1 min (most were drafted during implementation) |
| Pre-commit review round 1 ~21:40–~21:45 (subagent, 302 s) | ~5 min |
| Review fixes round 1 ~21:45–21:48 | ~3 min |
| PO page review; metadata-failure fix + e2e 21:48–22:00 | 12 min |
| A1 proposal, copy table, PO approvals 22:00–22:25 | 25 min |
| A1 implementation, tests, screenshots 22:25–22:33 | 8 min |
| A1 documentation 22:33–22:36 | ~3 min |
| Pre-commit review round 2 22:40–22:44 (subagent) | 4 min |
| Review fixes round 2 22:44–22:46 (docs) | 2 min |

The review round's start and end were reconstructed afterwards (marked `~`), not logged at the moment; the
implementation span is short because the plan contained the complete copy and file-level design.

Pre-commit review rounds: see §4.

## 4. Deviations

- **Pre-commit review round 1** (`/code-review high`, `git diff v0.20.0` incl. uncommitted files): 10 findings — 9
  fixed (header overflow at 375 px signed in; stored theme validated on hydration; roadmap doc contradiction; public
  headers aligned with their content; router `Link` for the logo; revision-row order; overlapping times; tests for the
  store default and the i18n wiring; "Revizijske sledi"), 1 partly rejected (shared button constant — the two buttons
  are intentionally different sizes). Details in the task's `## Deviations`.

- **Pre-commit review round 2** (`/code-review medium` — PO chose medium over the protocol's `high`): code — no
  findings; documents — 3 low (walkthrough §1 pre-A1 wording, TC-GRX-006 cross-reference, missing `roadmap.md`
  revision row), all fixed.
- **Amendment A1** after the PO's page review (changed requirements → task/plan amendment approved before
  execution, `plan.md` §Amendment A1); `App.test.tsx` then matched `landing.hero.cta` twice (hero + closing CTA) →
  asserts `hero-cta` by test ID; the new e2e test's fixed wait replaced by `networkidle` (lint rule).
- `App.test.tsx` asserted `landing.hero.cta_primary`, removed by the plan — the impact grep covered component tests but
  not `src/test/`; updated to `landing.hero.cta` (lesson recorded in `lessons/frontend-ux.md`).
- Pre-paint script: browser-language detection moved outside the storage `try` so a blocked `localStorage` still gives
  the same language as `resolveInitialLanguage` (plan: "whole body in try/catch").
- 2.13 discovery → backlog: `common.summary` and `briefingDetail.unknownAuthor` are used but exist in no locale
  (pre-existing).
- Manual review fixes: header padding moved inside the `max-w-6xl` container; FAQ left-aligned.
- `docs/overview/project-structure.md` unchanged — it lists `guides/` generically.

## 5. Traceability

- **US:** US-UI-007
- **UC:** UC-18
- **TC:** TC-UI-005, TC-UI-006, TC-GRX-006
