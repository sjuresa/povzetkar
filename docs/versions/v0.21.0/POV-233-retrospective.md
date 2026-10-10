# POV-233 Retrospective — v0.21.0 Version Initialisation

## 1. Planned vs. actual

Delivered as planned: checklist Phase 1 (branch, version bump, roadmap, hub, carry-forward review) and the backlog markers. The review produced more candidates than v0.20's: seven new issues (POV-226 … POV-232) plus the two that already carried Fix version 0.21.0 (POV-217, POV-218). Three of the seven come from outside the four themes — two backlog entries the PO asked to rank against the themes, and one raised by the PO during the review (POV-232).

## 2. Deviations & why

- **A whole-backlog pass changed the ranking.** The PO asked whether anything in the backlog mattered more than the theme items. Two entries did: the missing privacy notice (a likely legal duty for a publicly reachable app) and the dev stack's ports bound to all interfaces (the cheapest real exposure). Both became candidates (POV-228, POV-229). A theme-only review would have missed them.
- **A process question became a task candidate.** The PO asked how to automate the image version set by hand in the production environment file. Two small options (a version file written by the bump script; dropping the tag) were not what the PO wanted. The common practice — build once in CI, push to a registry, deploy by pull — also closes a gap the manual value hid: production rebuilds the image on the host, so it can run base layers CI never scanned. That became POV-232.
- **The agent's claims were corrected three times in the Brainstorm:**
  - "five" adopted process decisions (there are six);
  - "a Fix version on a candidate contradicts the checklist" — wrong: it is a movable proposal, and checklist 3.1b expects re-targeting;
  - "both frontend advisories have no clean fix" — taken over from the v0.20 retrospective unchecked; one is fixed by a major upgrade already in the backlog. The internal review of the task definition, requested by the PO, found the last one.

## 3. Technical findings

- **Theme 4's root cause is a Definition-of-Done gap, not neglect.** No protocol and no checklist step names the six design documents (five areas — the API catalogue is two files); checklist 2.14 covers only the FRD, `use_cases.md` and `test_specification.md`. A document that no step names drifts under task-first delivery. POV-231 carries both the retire-or-re-sync work and the DoD fix, because the DoD can name the surviving documents only once they are decided.
- **The image tag is only a name.** The version the app reports comes from the POM (`quarkus.application.version`), not from the image tag. A wrong tag mislabels the image; it does not change what runs.
- **The bump was safe this time.** Unlike v0.20 (POV-212), no P0 test case reaches its automation deadline at v0.21.0: the planning audit passed against `v0.21.0` on `main` before the bump.
- **Fixed-date exceptions meet unplanned versions.** All nine `.trivyignore` entries expire on 2026-12-31. Without version planning, the v0.21 finalisation may come later; then CI turns red mid-version and blocks every PR until the entries are re-checked. The PO accepted this as the intended forcing mechanism.

## 4. Feed-forward

- **POV-226 (next task):** the six adopted decisions are listed in its issue; decision 6 (less duplication across close-out documents) was already followed here in spirit — the CHANGELOG entry refers to the hub instead of restating the list.
- **POV-230 before POV-232:** POV-232 adds three Docker actions that must be pinned by SHA; POV-230 sets the pattern and the Dependabot config.
- **Check before restating:** a figure or claim taken over from an earlier document (here a retrospective) is checked against its source before it goes into a new one.
