# Public Content

The repository is public. Everything committed or posted is public and permanent — git history is not rewritten.

## What counts as public

Every text that leaves the local machine through the repository or its GitHub surfaces:

- tracked files — code, configuration, documentation, walkthroughs, retrospectives, version hubs, ADRs;
- commit messages;
- pull-request titles and bodies, review replies and PR conversation comments (the PR review loop included);
- CI output (GitHub Actions logs are public with the repository).

Gitignored files (`agent_logs/`, `CLAUDE.local.md`, `.env*` files except the `*.example` templates) and the private
operations repository are not public.

## What must not appear

1. **Secrets** — credentials, API keys, tokens, passwords, private keys; also not as "examples" taken from a real
   value. Placeholders only (`your_db_password`, `<generated secret>`).
2. **Personal data** — contact data (e-mail address, phone, postal address) and any data of private persons other
   than the author. The author's name as copyright and attribution (README notice, repository owner) and as existing
   test fixture data is allowed — it is public through the copyright line anyway.
3. **Environment values** — where and how the author's deployment runs: domains and hostnames of the deployment,
   host and hardware descriptions, network addresses, hosting providers and dashboards of the deployment, host
   schedules, ad-hoc container names, local paths of the author's machine. They belong in the private operations
   runbook and in `.env.docker` (configuration values only there; the runbook names the variable, not its value).

## Mechanism vs. environment

**Mechanism stays public** — how the system is built, secured and run, in terms any deployment shares: compose
service and container names, the stack's own Docker network and its fixed addresses, loopback-only host port
bindings, environment variable names and meanings, file names such as `.env.docker`, the Maven groupId and image
name, deploy descriptors such as `railway.toml`, generic hosting guidance. **Environment goes** — the concrete
values of one deployment (above). Public documentation uses placeholders (`<your-domain>`, `povzetkar.<domain>`)
and points to "the private operations runbook" for the author's own procedures. The one exception is the
landing-page URL in the README (PO decision, POV-223).

**Historical records stay as written** — `docs/versions/`, `docs/retrospectives/`, `docs/adr/` and
`docs/process/archive/` record what was true when they were written, and some of them name environment values of
that time. They are not scrubbed (git history keeps the values anyway); the rule binds every new record and every
living document.

## Issue references

Refer to issues by their plain ID (`POV-223`), never by a link to the issue tracker; the tracker is private.
Historical records that already contain tracker links stay as written.

## Describing a scrub

When a task removes environment values or personal data, its own records — walkthrough, retrospective, commit
messages, PR texts — describe the change **by category and count** ("environment values removed from 12 living
documents"), never by listing the removed values. The task's diff itself shows them; the records must not repeat
them in one place.

## Before committing or posting

- Read the text as a stranger would: does it name where the author's deployment runs or how to reach the author?
- Sweep the diff for the patterns in the private operations runbook's `sweep-patterns.txt`; every hit is judged in
  context (mechanism stays).
- A secret that reached a commit or a public text is rotated or revoked at its provider first; removing it from
  the current files does not remove it from history.
