# v0.19.0 Version Retrospective — Cross-Task Synthesis

**Date:** 2026-09-27
**Status:** Final
**Input:** The ten task retrospectives in this directory (POV-201 … POV-210) plus the finalisation task (POV-211).

This is a pattern-level synthesis across the version's tasks — trends, systemic patterns, and process health. It is not a re-listing of per-task findings; for those, see the individual `POV-XXX-retrospective.md` files.

---

## 1. The Version Theme in Hindsight

v0.19.0 ran in two bursts with a gap of more than two months between them, and each burst has its own spine.

**July (POV-201 … POV-205) — making the process consume its own output.** Three of five tasks were about the development system itself: retrospectives that were written but never read (POV-201), a traceability audit that could not block CI (POV-203), a protocol suite that had grown to 5,600 words of weak-agent crutches (POV-204). POV-202 (−55 % initial-load JS) and POV-205 (read-only API keys) were the product work.

**September (POV-206 … POV-210) — finding out what production actually does.** Planning a routine upgrade (POV-206) exposed unauthenticated `/q/metrics` in production (POV-207); its startup log exposed 13 silently ignored config keys (POV-208); losing Gemini as the PR reviewer turned interim rules into protocol (POV-209); a bot wave showed that the tunnel bypasses Caddy and no request history existed (POV-210). The chain was not planned — each task was found by the previous one, and POV-207 preempted POV-206 mid-planning. As in v0.18, the task-first model let the theme emerge from the work.

The label that fits in hindsight is **"declared versus effective"**: in both bursts the work consisted of discovering that something the project believed it had — a review step, a blocking audit, a metrics guard, a session config, a proxy, a pinned scanner — did not actually operate.

## 2. Systemic Patterns Across Tasks

**Pattern A — Configuration and process text that look like a control are not a control.** The same shape recurred in code, infrastructure and process:
- POV-207: two non-existent properties, a matching Prometheus `bearer_token`, a RUNBOOK section and an env variable described protection that never existed.
- POV-208: 13 `quarkus.*` keys had no effect, including `quarkus.config.secret-keys` that POV-205 had relied on.
- POV-210: Caddy's security headers and logging were configured correctly — on a proxy that received no traffic.
- POV-209: the scanner ran from `@master`; the documented Step 8 thread command did not exist, so the PR loop had never been executable as written.
- POV-203: `continue-on-error: true` made the planning audit unable to block CI at any `--fail-on` level. POV-201: both retrospective levels were write-only.

The remedy was the same each time and is now a project habit: **a mechanical check that fails on the negative case** — `ManagementInterfaceTest` (unauthenticated → rejected), `QuarkusConfigKeysTest` (unknown key → build fails), the generated-client drift check and infra-config validation in CI (POV-206), a genuinely blocking planning audit (POV-203), an SHA-pinned scanner (POV-209). POV-208 added the sharp corollary: a guard is only proven by going red on the *realistic* historical failure, not on an invented typo.

**Pattern B — Claims from memory were wrong, and the principle "the code is the only source of truth" kept widening its scope.** It no longer covers only APIs:
- Process documents: "retrospectives are read during context gathering" (POV-201), "process tasks are exempt from the CHANGELOG by precedent" (POV-209) — both false on checking.
- Repo layout and host: a "committed" `target/openapi.json` that is gitignored; an OCI host that production no longer runs on (POV-207).
- Runtime and traffic: "Caddy's stdout log does not work" when Caddy simply received nothing; a log volume assumed 40× too high (POV-210); "install size" taken for bundle contribution (POV-202).
- Estimates of work: a ten-file sweep that was two files once match content was read (POV-204).

Every one was settled in minutes by reading a file, a log line or a metric. The cost was never the check — it was the plan built on the unchecked claim.

**Pattern C — Review finds what tests cannot, and fixes need review too.** With Gemini gone (2026-09-25), review moved to `/code-review high` in a subagent before the commit. It carried the September tasks: POV-208 (six real defects in round 2, including tests that could never fail), POV-209 (six rounds, 12 → 10 → 9 → 8 → 7 → 7 findings, high ones only in rounds 1–3), POV-210 (two plan reviews with 15 and 9 findings, three pre-commit rounds; round 2 found the only real security defect of the design). Two lessons are consistent across them: reviews of normative text find sequencing and executability defects that no grep can, and fixes introduce defects of their own — hence the rule to re-review the *whole* diff after each fix round. The counterweight also showed up: POV-208's fourth round flip-flopped on a placement the third round had requested, and POV-210's retention design grew safeguards for its own safeguards until the PO asked whether it was worth it.

**Pattern D — Duplicated sources drift.** `release_task_template.md` duplicated the checklist's release steps and fell behind three times (POV-191, POV-201, POV-209) before POV-211 deleted it. The generated API client drifted from the API three ways at once (POV-206). Consolidating eleven protocol files silently lost two normative rules (POV-204). The durable answer in all three was the same: one source plus a mechanical freshness or coverage check — not a reminder to keep copies in sync.

## 3. Process Health

**Estimates inverted between the bursts.** The July tasks came in far under estimate (POV-204 ~1.4 h vs 3.5–4.5 h; POV-205 ~1 h 10 min vs ~5 h). In September the implementation stayed small but review rounds, CVE rounds and deploys took the rest (POV-207 ~3 h 05 min vs ~3 h, of which the planned scope ~1 h; POV-210 ~2 h 15 min to an approved plan for ~60 changed lines). Estimates should now price review and deploy explicitly — the September tasks show they are the larger share.

**The reviewer changed, and the process absorbed it.** Gemini's disappearance could have left the PR loop empty; interim rules (PO-approved 2026-09-25) ran on POV-208 and POV-206 before POV-209 codified them, so the protocol was written from two real runs rather than from speculation. Greptile's trial ended on #278 — the reviewer-agnostic Step 8 and the no-response rule exist precisely for that.

**Work that started outside the process cost more, not less.** POV-210's prototype went straight into the working tree (and its Caddy part into production); its review found nine defects and the investigation showed its premise was wrong. Restarting at the Brainstorm cost ~2 h and changed every design decision that would have shipped. POV-207 → POV-206 was the opposite case: a preemption handled deliberately, which produced the pause/resume procedure (WIP = 1, Step 1).

**Proportionality is now the open process question.** The version added pre-commit review for every task, a verbatim YouTrack record, Time Log wait/pause rules and a supply-chain audit. Each was justified by a concrete miss; together they make a small task expensive. POV-208 and POV-210 both show diminishing returns in later review rounds, and the PO has had to be the one to stop the cascade. The protocol states that the PO decides whether another round runs — the agent should propose stopping, with its reasons, rather than wait to be asked.

**Duplicated process artifacts were removed at the source.** POV-211 retired the release template instead of patching it a fourth time, and fixed `update-version.cjs` (Windows exec path, lockfiles), which had forced a manual workaround at the v0.18.0 release (POV-200) and again at the v0.19.0 initialisation (POV-201).

## 4. Carry-Forward Themes (for the next version)

These are version-level threads, not individual backlog items (those live in `improvements.md`):

- **The production edge is not yet what the architecture describes.** Caddy is off the traffic path, so no security headers are delivered; the SPA fallback answers 200 to probe paths; Micrometer's `uri` tag explodes under scans; nothing alerts on suspicious traffic; expired-session XHRs get a 302 instead of a 401 in production; `grafana.juresa.si` has no DNS record; detail endpoints have no ownership scoping. Each is logged separately — together they are one hardening theme.
- **The scheduler integration test is now forced.** TC-ING-006 is a P0 manual test case; the P0-automation guardrail turns it CRITICAL at v0.20, which blocks CI. The backlog item (real lock service + test DB for the `@Scheduled` path) has been carried since v0.18.
- **Dependency debt has a deadline.** orval ≥ 8.22 (CRITICAL advisories, build-time only), the grouped frontend majors behind the lint-debt cleanup (31 pre-existing errors), and the Hibernate 7.4 workaround awaiting upstream reports. `.trivyignore` entries expire 2026-12-31 and will fail CI on purpose then.
- **Process weight versus value.** v0.20 is the first version run entirely under the POV-209 rules. Watch whether the review rounds, the verbatim YouTrack record and the Time Log discipline keep paying for themselves on small tasks, and record the evidence in the task retrospectives so v0.20's finalisation can decide with data.

---

## Revision History

| Version | Date | Author | Changes |
| --- | --- | --- | --- |
| 1.0 | 2026-09-27 | Povzetkar AI | Initial cross-task synthesis for v0.19.0 finalisation (POV-211). |
