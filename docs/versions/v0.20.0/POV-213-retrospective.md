# POV-213 Retrospective — Scheduler-Fired Integration Test for the Scheduled Run; Daytime Cron for Retention and Enrichment

## 1. Planned vs. actual

Planned and delivered: the scheduler-fired integration test (TC-ING-006 → Integration, skip removed), the scheduler
halted in the test profile, the daytime cron for retention and enrichment, and the documentation. The scope grew in
the Brainstorm (halted scheduler in `%test`, cron times — PO decision). The planned production part was dropped in the
pre-commit reviews: `concurrentExecution = SKIP` and the `identity` on the production job (§2). The first implementation took 10 min
against the 1.85 h estimated for test, config, red run and cron; the review fix and the flaky run it uncovered took
longer than the implementation itself.

## 2. Deviations & why

- **Phase 1 before Phase 0** — the exploration needs the identity, `halted` and the test cron.
- **The first two exploration runs reported "no events"** although the scheduler fired them: the test read a field
  of the `@ApplicationScoped` observer bean through its client proxy. Caught by checking the observer resolution
  and the scheduler's bytecode (`StatusEmitterInvoker` is always installed, `Events.fire` is sync + async) before
  drawing a conclusion — had the "no events" result been taken at face value, the plan would have fallen back to the
  gauge/`previousFireTime` wait the plan review had rejected as racy.
- **Plan slip:** the revision row number (2.11 in the plan, 2.16 actual).
- **`SKIP` was a production change made for a test.** The plan review (`/code-review` finding 1) proposed it to
  remove a race in the test, and the plan added a production argument (no second hourly run next to a run longer
  than an hour) that the per-date lock and the 15-min budget already covered — the agent overstated it. Neither the
  plan nor its reviews weighed the production cost; the pre-commit review did: one hung run would block every later
  scheduled run until restart, where the stale-lock take-over recovers after 30 min. The PO's question "is SKIP only
  for the test race?" exposed it; a test-side wait replaced it, production stays unchanged. The PO made the lesson a
  rule — QA rule 4 in `quality-mandates.md`.
- **The first test-side fix was flaky.** Counting `StartedExecution` events to detect runs in flight made 2 of 13
  repeated runs fail with `ContextNotActiveException` inside the job; without that observer (gauge instead) 10 of 10
  passed. Running the test repeatedly — not once — is what found it. The agent then wrote the observer up as the
  cause; review round 2 showed the numbers cannot carry that (Fisher ≈ 31 %), and the docs now call it an
  observation.
- **The second test-side fix was still wrong about overlap.** Its comment and §2.6 said a second run "finds the date
  lock held and skips both dates"; the lock is per date, so it would skip only yesterday and both runs would build
  yesterday's briefing. Review round 2 caught it; the test now pauses right after the first firing, so no second run
  exists. Round 2 also found the job `identity` — the same kind of production change for a test as `SKIP`, in a
  section written to forbid exactly that. Removed; `OrchestrationManager` is unchanged.

## 3. Technical findings

- With `quarkus.scheduler.start-mode=halted`, `Scheduler.resume(identity)` does not fire a job — the scheduler as a
  whole is not running. Firing one job in a test = pause the others by id + global `resume()`. Not documented in the
  scheduler reference; established by the Phase 0 exploration (Quarkus 3.40.1).
- Halted mode still parses the crons and creates the triggers, so a typo in a `${…}` cron fails the test boot
  (`allScheduledJobsHaveASchedule` makes it explicit).
- `SuccessfulExecution`/`FailedExecution` are fired synchronously on the executing thread after the method returns
  — a reliable "run finished" signal for tests, unlike `Trigger.getPreviousFireTime()`. `StartedExecution` is fired
  on the same thread right before the method; a variant observing it failed 2 of 13 runs with
  `ContextNotActiveException` in the job — cause unknown, base rate without it in the walkthrough.
- The orchestration lock is per date, not per run: overlapping runs split the dates and race on the briefing insert
  (pre-existing, backlog).
- The red run confirmed the Brainstorm hypothesis: the defect is only visible through the scheduler; the scheduled
  method swallows every step failure, so only effects (items, briefing, failure counter) reveal it.

## 4. Process-weight evidence (v0.20 carry-forward theme 4)

- Plan reviews before approval: self-review (3 findings) and `/code-review` of the plan (9 findings, all fixed) —
  several of them (symmetric pause/resume, fire-time filter, teardown wait) made the test green on its first run and
  red for the right reason. One of them (`SKIP`) solved a test problem with a production change; the plan reviews
  checked whether the fix worked, not what it cost in production.
- Pre-commit review round 1: 3 findings (1 medium with production impact, 1 low, 1 nit), all fixed. Round 2: 5
  findings (1 low–medium, 3 low, 1 nit), all fixed — one in the round-1 fix itself (wrong overlap claim), one left
  from the plan (identity), one in the agent's write-up of the flaky run. Round 3: 6 findings (3 low, 3 nits), all
  fixed — the midnight guard would have skipped instead of failing a run that fetched wrong dates, the test did not
  catch a briefing regenerated on every run, and teardown still trusted the gauge round 2 had rejected. Each round found defects the previous fix introduced or
  left; the review loop paid for itself here. Further rounds and the YouTrack record cost: see the Time Log
  (`agent_logs/POV-213 … task.md`).

## 5. Feed-forward

- **Any test that needs a scheduled job:** follow `docs/guides/testing.md` §2.6; the scheduler is halted in `%test`.
- **Observer/recorder beans in tests:** expose state through methods — a field read on a client proxy is silently
  empty.
- **All tasks — QA rule 4:** a plan that changes existing behaviour says why and what it changes in production; a
  production change for a test is the last resort and is flagged to the PO as test-motivated.
- **New timing-dependent tests:** run them repeatedly (≥ 10×) before calling them green.
- **Exploration results that say "nothing happened"** deserve one check of the measuring instrument before they
  change a design decision.
- **Backlog:** a guard against non-`void` `@Scheduled` methods would close the POV-197 class for every job, not only
  the orchestration.
