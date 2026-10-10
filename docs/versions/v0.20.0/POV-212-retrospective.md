# POV-212 Retrospective — v0.20.0 Version Initialisation

## 1. Planned vs. actual

Planned and delivered: checklist Phase 1 (branch, version bump, roadmap, hub, carry-forward review), the checklist 4.3 tag fix, a time-bound skip on TC-ING-006 and the Hibernate backlog status. Three task candidates were created in YouTrack at the review itself (POV-213, POV-214, POV-215), not "later", which is where the TC-ING-006 candidate was lost in v0.19.

## 2. Deviations & why

- **The agent's first answer to the CI consequence of the bump was wrong.** It proposed merging this PR with a known-red planning audit; `workflow-mandatory.md` Step 8 requires green CI on every exit. Withdrawn on re-check. The PO chose the documented mechanism instead: a time-bound `validate:skip until v0.21.0` with a justification, removed by POV-213.
- **The first backend suite run failed in `clean`** (`Failed to delete backend\target`, most likely held by an IDE); the unchanged re-run passed.

## 3. Technical findings

- Bumping to `X.Y.0-SNAPSHOT` immediately changes the CI planning audit's reference version (`ci.yml` derives it from `package.json`). Any P0 test case whose automation deadline is this version turns CRITICAL on the version-bump commit — before the task that fixes it can run. The P0 guardrail and checklist 1.1 are ordered against each other; the time-bound skip bridges that gap, at a price: for one version the enforcement moves from CI to the Fix version of POV-213 and checklist 3.1b (the skip expires at v0.21.0 — verified). Logged for a structural fix: backlog "Check P0 automation deadlines before the version bump".
- The checklist's code examples are followed literally by the agent (POV-211 tagged `v0.19.0`). An example in a normative document is a specification, not an illustration.

## 4. Feed-forward

- **POV-213 (next task):** remove `<!-- validate:skip until v0.21.0 … -->` from TC-ING-006 in `docs/analysis/test_specification.md` and set its automation status; the planning audit for `v0.20.0` must stay green without the skip.
- **All v0.20 tasks:** record evidence on process weight (review findings per round, time spent on the YouTrack record and the Time Log) in the retrospective — the v0.20 finalisation decides on it (Carry-Forward Review, theme 4).
- If `mvnw clean` fails with `Failed to delete backend\target`, re-run before investigating — the lock is external and transient.
