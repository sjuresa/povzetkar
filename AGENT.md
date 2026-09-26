# AGENT.md — Agent Operating Protocols

## Primary Directive

Operate exclusively by the rules in this file and the protocols in `docs/protocols/`. For any task that modifies code, read `docs/protocols/workflow-mandatory.md` before acting and follow its steps — information gathering, an evidence-grounded plan, and explicit user approval before execution. Following the process is part of the deliverable: do not trade steps for speed, and do not begin execution without an approved plan.

## Core Principles

1. **Know the API before you act.** Read the definition of any class or module you are about to use or modify; its actual API is required input to the plan, never an assumption.
2. **The code is the only source of truth.** Every claim in findings and plans must be backed by evidence read from the repository — source, tests, or process documents. This applies to claims about the process itself.
3. **Simplicity is paramount.** Prefer a clean refactor over a new dependency, complex library, or workaround.
4. **Precision over speed.** A fast but wrong answer has negative value; verify outcomes instead of assuming success.
5. **Resolve ambiguity by checking state, then asking.** When an instruction is ambiguous (e.g., "fix the table", "commit the changes"), inspect the working tree and relevant files first; if more than one target remains plausible, list the candidates and ask.
6. **Improve proactively.** Don't just execute commands — surface refactoring and architecture improvements grounded in established design principles; out-of-scope finds go to `docs/backlog/improvements.md` (checklist 2.13).

## Protocol Index

| Protocol | File | Scope |
|:---|:---|:---|
| Mandatory Workflow | `docs/protocols/workflow-mandatory.md` | The step sequence for every code change: task pause/resume, failure handling, pre-commit review, YouTrack task record, and the reviewer-agnostic PR review loop. |
| Verification | `docs/protocols/verification.md` | The Test Mandate — full-suite behavioral verification — and Pre-deploy Verification. |
| Quality Mandates | `docs/protocols/quality-mandates.md` | TDD, change-impact analysis, client-side analysis, QA rules. |
| Commit Conventions | `docs/protocols/commit-conventions.md` | Commit message rules. |
| Context Gathering | `docs/protocols/context-gathering.md` | Which documents to load, and when. |

The phase-level development process — versions, the task lifecycle, and the PO approval gates — is defined in `docs/sdlc/11_development_checklist.md`.
