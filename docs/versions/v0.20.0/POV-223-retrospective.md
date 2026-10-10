# POV-223 Retrospective — Prepare the Repository for Public Release

## 1. Planned vs. actual

Planned: a showcase README, no license, stale documentation removed or refreshed, a public-content rule, the
production environment moved out of the current state, then publication. Delivered locally in five commits as
planned, with two PO-approved changes: a tenth stale document deleted, and two screenshots instead of five; a
sixth commit holds the review fixes, among them one behaviour change (transcript proxy off by default). The
publication (Phase 7) follows the commit.

## 2. Deviations & why

- **Screenshots:** the plan fixed "demo topic only" without checking that the author's account can see the demo
  user's content — it cannot (content is scoped per user since POV-219). The constraint was right, the feasibility
  check was missing.
- **Agent read `.env.example` via a script** — the deny rule binds the Read tool, not a script that reads every
  tracked file. The fix is in the script; the lesson is in §3.
- **Claims written before checking** — four in one phase, all caught in the same step because the plan's working
  rule demanded a check per claim. Without that rule they would have reached the review or the public repository.
- **A configuration change built on an unchecked premise.** Commit 1 made `TRANSCRIPT_PROXY_URL` required in the
  production compose file, assuming production set it. The agent may not read `.env.docker` and did not ask; the
  plan's safety argument ("a missing value fails safely at configuration read") was true but answered the wrong
  question. Production had never set it — the next deploy would have stopped, and the proxy had been calling a
  host that no longer exists. Found only because a security-review finding asked whether that host was still
  live. When the agent cannot see a value, asking the PO is the check.
- **Review round 1 was not lost:** it failed on a rate limit and ran the next morning; 28 findings, 16 fixed, the
  rest to the backlog or without action (walkthrough §4.1). The Tier 1 "checked against the code" for LEGAL missed
  the server-side templates — "verified" was claimed for a narrower search than the document covers.
- **Process weight (feed-forward from POV-212):** Brainstorm 3 h 37 min with five task-definition review rounds
  (10/10/9/10/8 findings) and planning 2 h 30 min with four plan review rounds (10/10/19/20), against 1 h 24 min of
  implementation. The rounds found real defects (Deployments still public, Actions logs, force-pushed commits,
  values in two places), so they paid off here — this was a one-way, public-facing task.

## 3. Technical findings

- **A tool permission is not a data boundary.** A deny rule on the Read tool does not stop a script from reading
  the same file. Scripts that walk the repository must apply the same exclusions themselves.
- **"Everything tracked" is not "everything public".** GitHub also publishes Deployments and environments, Actions
  run logs (retained 90 days), commits overwritten by force-pushes (reachable by SHA from PR timelines), PR refs and
  commit metadata. The plan's exposure inventory covered these only after review round 4.
- **Documentation drifts in families.** Version-0.11-era design documents (API catalogue, frontend specification,
  test plan, NFRs, database design) had all drifted for the same reason: task-first delivery updates code, CHANGELOG
  and walkthroughs, not the design catalogues. A status note plus one backlog entry is honest; editing them piecemeal
  would hide the drift.
- **CommonMark details matter in a link checker:** a closing fence has no info string, and heading slugs include
  inline code text — both produced false results on the first runs.
- **`gh api markdown` has two modes:** `gfm` renders like a comment (line breaks become `<br>`); a README is
  rendered with `markdown`.

## 4. Feed-forward

- **v0.20.0 finalisation:** checklist 3.4 (CHANGELOG release section) and 3.5 (`sync_metadata.py`) write public
  text — `docs/protocols/public-content.md` applies; re-run the production-detail sweep after 3.5 (patterns in the
  private operations repository). `sync_metadata.py` regenerates `en.json` from CHANGELOG and keeps a differing
  Slovenian description in `sl.json`.
- **Next deploy:** the transcript proxy is off by default (`TRANSCRIPT_PROXY_ENABLED`); no `.env.docker` change is
  needed. Before the proxy is used again, the PO gives it a new key (the old one was sent to the former host's
  address, which anyone can now claim).
- **Repository settings (Phase 7):** enable private vulnerability reporting right after the visibility flip —
  `SECURITY.md` points to it.
- **Every task from now on** writes walkthroughs, retrospectives, commit messages and PR replies for a public
  reader: plain issue IDs, no environment values (`AGENT.md` principle 7).
- **Before planning around a user's view of the app,** check which account can see the data in question (per-user
  scoping).
- **Before changing how configuration is read,** confirm with the PO what the deployment actually sets when the
  agent cannot read it.
