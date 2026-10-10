# v0.20.0 Version Retrospective — Cross-Task Synthesis

**Date:** 2026-10-10
**Status:** Final
**Input:** The eleven task retrospectives in this directory (POV-212 … POV-216, POV-219 … POV-224) plus the
finalisation task (POV-225); the task Time Logs and the booked work items for the process-weight figures (§3).

This is a pattern-level synthesis across the version's tasks — trends, systemic patterns, and process health. It is
not a re-listing of per-task findings; for those, see the individual `POV-XXX-retrospective.md` files.

---

## 1. The Version Theme in Hindsight

v0.20.0 was initialised with three forced items from v0.19 — the production edge (POV-214), the scheduler
integration test (POV-213) and the orval advisories (POV-215). Seven more tasks arrived on the way, most of them
found by the previous one or by production: the per-user scoping bug (POV-219) surfaced while fixing the onboarding
guide (POV-216); edge hardening (POV-221) bundled the backlog items POV-214 had left; a YouTube block of the
production IP became POV-222 the same morning; making the repository public (POV-223) produced the `shell-quote`
follow-up (POV-224).

The label that fits in hindsight is **"what the outside sees"**. Almost every task was about something an outside
party receives: the headers and the cache policy a browser gets (POV-214), what a scanner learns from a probe path
(POV-221), which other users' sources and content a signed-in user can open (POV-219), what the landing page
promises a visitor (POV-220), what YouTube sees of the app's traffic (POV-222), and finally what anyone can read in
the repository itself (POV-223).

## 2. Systemic Patterns Across Tasks

**Pattern A — Production showed what the tests could not, until the tests ran what production runs.**
- POV-214: the access log's user field held the Google display name; it was found only in the deploy smoke test.
  The task also found production metrics down for a day, because the dev compose project had taken over the
  production containers.
- POV-213: the retention and enrichment jobs had silently stopped running.
- POV-222: YouTube rate-limited the production IP.
- POV-223: production had never set the transcript proxy URL, so the proxy was calling a host that no longer exists.

The fixes that lasted made the tests run the production artefact. The scheduler now fires the job in the test
(POV-213). The end-to-end specs run against the production build under the real CSP (POV-214). A stand-in
`index.html` makes the SPA fallback testable (POV-221). POV-221 put the rule plainly: tests that cannot see a
production artefact cannot see its bugs.

**Pattern B — Unchecked premises, continued from v0.19, now about what the agent cannot see.** v0.19's Pattern B
(claims from memory were wrong) recurred, and it shifted towards facts outside the agent's view.
- POV-223 built a configuration change on what production supposedly set. The agent may not read `.env.docker` and
  did not ask.
- POV-221 told the PO the backend could not be started because a file was unreadable. Quarkus reads that file
  itself.
- POV-213 overstated a production argument for a test-motivated change. POV-224 made a speed claim from a narrower
  measurement. POV-222's plan assumed a test that did not exist.
- POV-215: every whole-diff review round fixed documentation claims that read as verified and were not.

Each was settled by a file, a measurement or one question to the PO. The working rule that came out of v0.20: when
the agent cannot see a value, asking the PO is the check (POV-223 feed-forward).

**Pattern C — Rules were written from task evidence, not ahead of it.** Three QA rules and one protocol came out of
concrete misses.
- QA rule 3 was rewritten (POV-216). The old ban on mocking Panache had come from a v0.6.2 knowledge gap.
- QA rule 3 gained "design first" (POV-219): a race-handling mechanism existed only to be tested, and removing it
  beat testing it.
- New QA rule 4 (POV-213): a production change made for a test is the last resort.
- New `public-content.md` protocol (POV-223), for the repository going public.

In each case the PO turned a finding into a rule within the same task. The rule text cites its origin.

**Pattern D — The Brainstorm is where tasks got corrected.**
- POV-214's first question reversed the carried-forward item: Caddy was removed rather than put on the traffic path
  (ADR-0012).
- POV-221 asked why the 401 rewrite depended on `Origin` and removed CORS altogether.
- POV-224 widened a lockfile bump to the CI gate gap that had let the advisory through.
- POV-219 found four read paths and one write path beyond the two the PO had named.
- POV-215 found two more critical advisories outside orval.

The scope that shipped was rarely the scope that was filed. The cost of that correction shows up in §3: Brainstorm
and planning are the largest single share of task time.

## 3. Process Health

**Process weight versus value — the evidence (v0.19 carry-forward theme 4).** v0.20 was the first version run
entirely under the POV-209 rules. Booked time per task (YouTrack *Spent time*). The split is from the work items
recorded in each Time Log; those sum to 3,004 min, 43 min less than the bookings:

| Task | Booked | Documentation | Development | Testing | Pre-commit review rounds (findings) | YouTrack record |
| --- | --- | --- | --- | --- | --- | --- |
| POV-212 | 1 h 17 min | 23 | 34 | 20 | 2 (7, 5) | 2 min |
| POV-213 | 3 h 59 min | 56 | 82 | 92 | 5 (3, 5, 6, 2, 2) | 7 min |
| POV-214 | 5 h 16 min | 126 | 109 | 81 | 1 (9 + 1 build defect) | 8 min |
| POV-215 | 3 h 40 min | 67 | 86 | 67 | 4 (0 partial, 10, 8, 6) | 4 min |
| POV-216 | 3 h 45 min | 133 | 44 | 48 | 3 + 1 stopped (4, 2, 6 + 5 normative) | 6 min |
| POV-219 | 5 h 08 min | 175 | 44 | 81 | 1 (8) | 6 min |
| POV-220 | 4 h 34 min | 137 | 73 | 47 | 3 (10, 3 docs, 2) | ≤ 8 min |
| POV-221 | 5 h 11 min | 86 | 151 | 74 | 2 (6, 6) | ≤ 8 min |
| POV-222 | 4 h 26 min | 106 | 77 | 83 | 3 (9, 7, 6) | not logged separately |
| POV-223 | 9 h 58 min | 384 | 158 | 56 | 1 (28) | 10 min |
| POV-224 | 3 h 33 min | 135 | 37 | 32 | 1 (4) | not logged separately |
| **Total** | **50 h 47 min** | **1,428 (48 %)** | **895 (30 %)** | **681 (23 %)** | | |

What the numbers and the retrospectives show:
- **Documentation is the largest share — about half of all task time.** It covers Brainstorm, planning and the
  close-out documents. Most of it is the definition and plan interviews and their internal reviews, which the PO
  requested per task:
  - POV-219: Brainstorm and planning 167 min against 31 min of implementation.
  - POV-223: 6 h, with nine document review rounds.
  - POV-220: 18 findings.

  These reviews found real defects cheaply, before code existed. POV-223 was the extreme, and it was also the one
  irreversible task (publication).
- **The first whole-diff pre-commit round paid for itself.** It found POV-213's production-impacting change and
  POV-214's missing `COPY` that no test could see. In POV-215 it was round 2 (round 1 covered only `package.json`)
  that found the task's only runtime defect, a request to `/api/v1/sources/NaN`. In POV-216 the review skills did
  not run in round 1; manual reviews found the concurrency and load-order defects there.
- **Later pre-commit rounds did not converge.** POV-213 went 3 → 5 → 6 → 2 → 2, POV-222 9 → 7 → 6, POV-215
  10 → 8 → 6. Most later findings were documentation claims or defects introduced by the previous fix. The PO
  stopped or limited the rounds in every task (11 of 11, each recorded in its Time Log). The v0.19 proposal that the agent
  should propose the stop (`07_development_methodology_comparison.md` §7) was not adopted, and the PO kept doing it.
- **The close-out documents feed the review.** The same fact is written into the CHANGELOG, the walkthrough, the
  retrospective, the version hub and the YouTrack comments. Each copy is a review surface: POV-215's rounds 2–4 all
  fixed documentation claims, and POV-213's round 2 fixed the agent's own write-up of a flaky run.
- **The verbatim YouTrack record is cheap: 2–10 min per task, at most 3 % of the task.** It is the only shared copy
  of `task.md` and the plan, which are gitignored. Since POV-223 the walkthrough is a public file in the repository,
  so its verbatim copy in YouTrack duplicates it.
- **The Time Log costs a few minutes per phase.** It is the source of every figure in this table. Its weak spots
  were timestamps reconstructed after the fact (POV-220) and boundaries logged as approximate.
- **Estimates were about twice the actual time.** The ten estimated tasks were estimated at 79 h 15 min and took
  40 h 49 min (POV-223 had no estimate). The v0.19 advice to price review and deploy explicitly over-corrected:
  POV-214 15 h 15 min → 5 h 16 min, POV-220 14 h → 4 h 34 min, POV-219 11 h 30 min → 5 h 08 min.

**Proposal and PO decisions (theme 4).** Each point below is a proposal for a v0.21 process task. This
finalisation changes no protocol.

| # | Gate | Proposal | Evidence | PO decision |
| --- | --- | --- | --- | --- |
| 1 | First pre-commit review round (`/code-review high`, whole diff) | **Keep** for every task | Found the task's real defects in POV-213, 214 and (round 2, the first whole-diff round) POV-215 | **Adopted** |
| 2 | Further pre-commit rounds | **Reduce:** a further round runs only if the previous one found a defect of medium or higher severity in code or normative text. Otherwise the agent proposes to stop, with reasons (the v0.19 §7 stopping rule, made a rule) | Rounds did not converge; the PO stopped or limited them in every task (11 of 11) | **Amended:** a further round runs only with the PO's confirmation; after each round the agent proposes whether another one is needed, with its reasons |
| 3 | Verbatim YouTrack record | **Reduce:** keep the verbatim task definition and plan (gitignored, only shared copy); replace the verbatim walkthrough comment with its repository path and commit | Walkthrough is public since POV-223; record costs 2–10 min | **Rejected:** the walkthrough stays verbatim in YouTrack |
| 4 | Time Log | **Keep** unchanged | Source of every figure here; cost a few minutes per phase | **Adopted** |
| 5 | Internal reviews of definition and plan | **Keep** as the PO's choice per task, with no fixed count | Cheap defects found before code; volume justified for irreversible work (POV-223) | **Adopted** |
| 6 | Close-out documents | **Reduce duplication:** the walkthrough carries the evidence; the CHANGELOG, the version hub and the retrospective refer to it instead of restating figures | Later review rounds mostly fixed restated claims | **Adopted** |
| 7 | Estimation | **Calibrate:** estimate from comparable v0.20 tasks' booked time instead of per-phase reasoning | Estimates about 2 × actual | **Adopted** |

**Session hand-overs worked.** POV-215, POV-221 and POV-222 were handed to a fresh session after plan approval.
Each resumed from `task.md` and the plan file without loss. The Step 1 hand-over rule did what it was written for.

**The repository went public mid-version.** POV-223 moved production operations into a private runbook and added
the public-content rule. From POV-224 on, every record is written for a public reader. The rule was already applied
to this retrospective.

## 4. Carry-Forward Themes (for the next version)

These are version-level threads, not individual backlog items (those live in `improvements.md`):

- **Decide and apply the process-weight proposals.** §3 holds seven proposals with the PO's decisions. The adopted
  ones need one process task that changes `workflow-mandatory.md` and the checklist together, so that the next
  version runs on the lighter rules and can be measured the same way.
- **Per-user isolation is not finished.** POV-219 scoped sources and content, but three gaps remain:
  - live job events still go to every user (POV-217);
  - a summary's public share is global per item, not per user (backlog);
  - inline HQL remains in the classes POV-218 lists.

  Together these are one theme: what one user can learn about another.
- **Supply-chain gates have dates and gaps.** All nine `.trivyignore` entries expire on 2026-12-31 and will turn CI
  red on purpose. Two lower-severity frontend advisories have no clean fix (backlog). `npm-run-all` is unmaintained,
  with `npm-run-all2` as its successor. Dependabot alerts are now visible on the public repository.
- **Design documents drift as a family.** POV-223 found that the version-0.11-era design catalogues (API catalogue,
  frontend specification, test plan, NFRs, database design) had all drifted, because task-first delivery updates
  code, CHANGELOG and walkthroughs but not the catalogues. Since POV-223 they carry status notes and are publicly
  visible as stale. Re-synchronising them, or retiring them, is a decision for v0.21.

---

## Revision History

| Version | Date | Author | Changes |
| --- | --- | --- | --- |
| 1.0 | 2026-10-10 | Povzetkar AI | Initial cross-task synthesis for v0.20.0 finalisation (POV-225). |
