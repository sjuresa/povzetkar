# POV-225 Retrospective — v0.20.0 Version Finalisation & Release

## 1. Planned vs. actual

Planned and delivered: checklist Phase 3, the read-only CI token, and the theme-4 synthesis the v0.20 Carry-Forward
Review had assigned to this finalisation. Phase 4 (release PR, tag, deploy, YouTrack release) follows the commit.
The audit changed nothing: `.trivyignore` still matches the image exactly, the Trivy pins are current, and the living
documents held no stale v0.20 claim.

## 2. Deviations & why

- **The FS audit needed the offline variant again** (Maven Central `429`, as in POV-211). Running the backend suite
  first filled the Maven cache with exactly this tree's POMs, which the offline variant requires. Ordering the suite
  before the FS scan made the second attempt sufficient.
- **A first search of the living documents returned nothing because its glob matched no file.** A count over all of
  `docs/` showed the hits, and the real search found only accurate or historical mentions. An empty search result
  needs one check of the search itself.
- **The plan assumed a roadmap status convention that did not exist** ("Planned"); the self-review grounded it in
  `sync_metadata.py`.

## 3. Technical findings

- **The process-weight data existed but was spread out.** The Time Logs held every work-item split and every
  YouTrack-record interval, but no retrospective added them up. Put together, they answered theme 4 in minutes.
  YouTrack counts a day as 7.5 h (`1d 2h 28m` = 598 min), which matters when the bookings are compared with the
  Time Logs.
- **Running the planning audit with the next version as reference** is a cheap way to see whether the next version
  bump turns any P0 test case CRITICAL. It passed for v0.21.0.
- `sync_metadata.py` again dropped the oldest landing-page version and the final newline (known backlog defects).

## 4. Feed-forward

- **v0.21 initialisation (checklist 1.5):** the Carry-Forward Themes are in the v0.20.0 retrospective §4. The
  adopted theme-4 proposals (§3) need one process task that changes `workflow-mandatory.md` and the checklist
  together.
- **`.trivyignore`:** all nine entries expire on 2026-12-31. Plan the re-check before then, or CI turns red on that
  day.
- **Local FS audit:** run the backend build before the offline Trivy scan.
