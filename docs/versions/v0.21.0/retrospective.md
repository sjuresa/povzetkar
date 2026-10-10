# v0.21.0 Version Retrospective

**Status:** In Progress

One retrospective for the version: every task appends its block to the Task Notes at its close-out (checklist 2.11);
the cross-task synthesis follows them when the version is finalised (3.1) and ends with the Carry-Forward Themes. The
facts of each task — scope, deviations, times, evidence — are in its walkthrough; the notes refer to them.

## Task Notes

### POV-233 — v0.21.0 Version Initialisation

**Why it deviated:** POV-233's [walkthrough](POV-233-walkthrough.md) predates this form; the facts are kept below.

- A whole-backlog pass, asked for by the PO, ranked two entries above the theme items — the missing privacy notice (a
  likely legal duty for a publicly reachable app) and the dev ports bound to all interfaces (the cheapest real
  exposure). A theme-only review would have missed them (POV-228, POV-229).
- The question of the hand-set image version led to the common practice — build once in CI, push, deploy by pull —
  which also closes a gap the manual value hid: production rebuilds on the host and can run base layers CI never
  scanned (POV-232).
- The agent's claims were corrected three times in the Brainstorm ("five" adopted decisions — there are six; a Fix
  version on a candidate "contradicts the checklist" — it is a movable proposal; "both frontend advisories have no
  clean fix" — taken over from the v0.20 retrospective unchecked; one is fixed by a major upgrade already in the backlog,
  and the internal review of the definition, requested by the PO, found it). The cause in all three: a claim written without
  checking its source.

**Technical findings:**

- Theme 4's root cause is a Definition-of-Done gap: no protocol or checklist step names the six design documents, and
  a document no step names drifts under task-first delivery. POV-231 carries both the retire-or-re-sync work and the
  DoD fix, because the DoD can name the surviving documents only once they are decided.
- The image tag is only a name: the version the app reports comes from the POM, so a wrong tag mislabels the image
  but does not change what runs.
- Fixed-date exceptions meet unplanned versions: all nine `.trivyignore` entries expire on 2026-12-31; if the
  finalisation comes later, CI turns red mid-version until they are re-checked — accepted by the PO as the intended
  forcing mechanism.

**Feed-forward:**

- POV-230 before POV-232: POV-232 adds Docker actions that must be pinned by SHA; POV-230 sets the pattern and the
  Dependabot config.
- Check before restating: a figure or claim taken over from an earlier document is checked against its source before
  it goes into a new one.

### POV-226 — Apply the v0.20 Process-Weight Decisions

**Why it deviated:** the facts are in the [walkthrough](POV-226-walkthrough.md) §2/§4.

- The scope grew because the PO tested each proposal against the current practice instead of taking the
  retrospective's wording; a premise corrected mid-interview (the task retrospective is per task, not per version)
  turned into the better answer.
- Requirements were written from memory of a step, not from its text: the definition's order of the version
  retrospective and the booking rule's notion of a "gap" both broke rules or practice that reading the text would
  have shown (checklist 3.1; phase-boundary Time Log entries).
- The new self-review rule did not stop the agent from reporting a partial self-review as complete or from writing
  timestamps without reading the clock; the rule names the check, the discipline still has to be applied.

**Technical findings:**

- The history of the retrospectives (v0.5–v0.17 one file per version, v0.18–v0.20 one per task) showed the
  per-version form already worked; its only documented weakness was missing structure, which a fixed block removes.
- A booking rule needs markers it can tell apart and boundaries it can read: "declared pause" and "long wait" shared
  one label, and gaps defined as "time between entries" would have matched every phase.

**Feed-forward:**

- From the next task on: no `POV-XXX-retrospective.md`; append a block here (checklist 2.11). The first review round
  is the default; propose further rounds instead of running them. Estimate from booked time of comparable tasks.
- Read the clock when ending a turn that waits for the PO and when the answer arrives; log `[paused: PO]` for a wait
  of 30 min or more; log `session started` first in every new session; at 2.15 list these waits and the intervals
  between sessions to the PO before booking.