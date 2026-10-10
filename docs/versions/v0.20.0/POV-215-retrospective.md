# POV-215 Retrospective — Upgrade orval to 8.41.0 and Vitest to 5.0.3 (CRITICAL Advisories)

## 1. Planned vs. actual

The carry-forward item "orval ≥ 8.22" grew in the Brainstorm: `npm audit` showed two more CRITICAL advisories outside
orval (`tinypool`, via Vitest) and a HIGH one in `source-map-js`; the PO took both in, Vitest as its own phase and
commit. Estimate ~6 h, of which ~2 h for the Vitest 3 → 5 migration. Implementation of all three upgrades took 20 min
including two phase gates and one review round; the planned migration risk did not materialise at runtime (149/149 at
the first run), the real cost was a type-check failure the plan had sized as a one-line conditional fix. The task was
handed over to a fresh session after the plan approval and resumed from `task.md` and the plan without loss — the
exploration's scratchpad was gone, but its recorded numbers (83 files, +1615/−1106) were enough to confirm the real
regeneration matched.

## 2. Deviations & why

- **`tsc -b` red after the Vitest bump:** the plan expected at most a type reference in `vite.config.ts`
  (`tsconfig.node`); the failure was in the app project — jest-dom's Jest-flavoured types and Node's `global`, both
  carried in by Vitest 3's types. The tests stayed green because Vitest does not type-check; only `tsc -b` (in
  `npm run build`, hence CI) showed it. Fixed at the cause in the test setup.
- **Review round on Phase 2 reviewed less than its scope:** `/code-review low` skipped the test files and the lockfile,
  i.e. everything but `package.json`. The result "no findings" was reported to the PO with that limitation; the PO
  accepted it.
- **The category review of the generated diff missed a behaviour change:** orval 8.41 disables detail queries only for
  `null`/`undefined` ids, not for any falsy one. The agent grouped the removed lines and read only the 25 most frequent
  groups; the 13 guard lines were below that cut. Review round 2 found it, and with it the one runtime defect of the
  task (`/sources/abc` would have sent `GET /api/v1/sources/NaN`).
- **Documentation claims read as verified and were not** — in every whole-diff round: round 2 (unnamed guard and
  `status` explosion, header evidence checked on a code path the change does not touch, package and advisory counts
  mixed, "since 8.41" in the RUNBOOK), round 3 (detail-page guards in the CHANGELOG, `model/index.ts`, a count in this
  file), round 4 (an unmeasured Vitest 3 coverage figure presented as measured, a guard "equivalence" that is false
  for negative ids, the backlog's original claim misquoted, counts in this file again).
- **A tooling upgrade changed a metric silently:** the coverage check after Vitest 5 confirmed only that a report was
  written, not what it measured (round 3, see §3).
- **A rejection was attributed to the PO:** the agent first rejected "state `clearMocks: true`" as already decided in
  P2; P2 had decided something else (remove the calls, no `clearMocks: false`). The PO corrected it; the finding was
  fixed on its merits. The agent also offered a backlog entry for a finding it had just shown to have no
  consequence — the PO questioned it and it was dropped.

## 3. Technical findings

- Vitest 5 runs on Vite 6.4 (`vite ^6.4.0 || ^7 || ^8`); Vitest does pin a Vite range, but the backlog's conclusion
  that Vitest 5 belongs with Vite 8 was wrong and would have tied a CRITICAL fix to a Vite major.
- Vitest 4 removed `coverage.all`: without `coverage.include` the report counts only files a test imports. On
  Vitest 5 that is 66 files / 57.37 % statements; with `include: ['src/**/*.{ts,tsx}']` 179 files / 28.65 %. Vitest 3
  was not measured; by its documented defaults (`all: true`, `include: ['**']`) it covered at least those 179 files
  plus a few root-level ones, so its figure was close to the lower one. A metric can change silently under a tooling
  upgrade — compare the report's scope, not only that it exists.
- Vitest 4 removed the Jest-compat types: `import '@testing-library/jest-dom'` still registers the matchers at runtime
  but no longer types them — `@testing-library/jest-dom/vitest` does both.
- orval 8.41.0: hooks return `withQueryKey(query, queryKey)` (getters) instead of a spread, which restores TanStack
  Query's tracked-property re-render optimisation; array params configured as `explode` are repeated keys; body
  requests normalise `Headers`/pairs; detail queries are skipped only for `null`/`undefined` ids (`0`/`NaN`/`''` now
  fetch — callers with a parsed route param need their own `enabled`); the header no longer carries the version, so
  future bumps produce smaller diffs.
- The global `esbuild ^0.25.0` override was meant as a security floor; as a caret on 0.x it is also a ceiling and
  silently held a newer consumer (orval, `^0.28.0`) below its range. A floor on a 0.x package needs `>=`, or no
  override once every consumer's own range is above the floor.
- `npm audit --package-lock-only` on a copy of an old `package.json` + lockfile reproduces the advisory list of any
  commit without touching `node_modules`.

**Process-weight evidence (POV-212 feed-forward):** Brainstorm 31 min (Q1–Q6), plan 27 min (P1–P5); implementation
20 min for three phases. Reviews (findings counted as the reviewer reported them):

| Round | Scope | Findings | Fixed | Rejected |
| --- | --- | --- | --- | --- |
| 1 `low` | Phase 2 diff (covered `package.json` only) | 0 | — | — |
| 2 `high` | whole diff | 10 | 9 — 1 runtime defect, 4 documentation claims, 2 overrides, 1 config statement, 1 pair of leftovers | 1 |
| 3 `high` | whole diff | 8 | 4 — coverage scope, 3 documentation claims | 4 (pre-existing or hypothetical) |
| 4 `high` | whole diff | 6 | 5 — 4 documentation claims, 1 simplification | 1 |

Round 3 also led to one pre-existing defect (briefing 404, backlog). After round 4 the PO stopped further rounds
(fixes were wording and one expression). Phase 1 had no review (PO). The per-phase gate (P4) added three PO
interactions for a 20-minute implementation; every defect was found by a whole-diff `high` round, and documentation
claims needed fixing in each of them. Defects found outside review: one (type check, by `tsc -b`).

## 4. Feed-forward

- **Deploy of POV-215:** the regenerated client ships in the bundle — smoke `/admin/users` and `/admin/jobs` sorting,
  `/admin/content` (with a status filter), dashboard, a briefing detail, a source detail and `/sources/abc` ("Source
  not found", no `/api/v1/sources/NaN` request); browser console free of CSP violations.
- **Run `tsc -b` after every test-tooling bump** — a green `vitest run` says nothing about types; and compare the
  coverage report's file count and totals before and after.
- **Before trusting a dependency-grouping claim in the backlog, check the peer ranges in the registry**
  (`npm view <pkg>@<ver> peerDependencies`).
- **A review whose result names a narrower scope than asked is a partial review** — report the gap with the result, as
  here, and let the PO decide.
- **Review a regenerated client by every changed line pattern, not the most frequent ones** — after grouping, read
  every group (or grep the old and new forms of `enabled`, URL building, headers, return values); then check each
  behaviour change against every hand-written caller.
- **Cite a PO decision only with its text** — quote the interview row before calling something "decided".
- **Remaining frontend advisories:** `@tailwindcss/typography` / `postcss-selector-parser` (backlog, no clean fix) and
  `i18next-http-backend` (backlog group (d)).
