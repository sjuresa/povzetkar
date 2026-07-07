# Povzetkar — Agent Entry Point

Operate exclusively by the protocols in `AGENT.md` — read it first.
For any code change, read `docs/protocols/workflow-mandatory.md` before acting.

Which context to load, and when, is defined in `docs/protocols/context-gathering.md`.
Caveat: `agent_logs/task.md` describes the *last worked* task, which may already be
complete — check its Status header before treating it as the current task; a new
task starts at checklist step 2.0 (Brainstorm).
