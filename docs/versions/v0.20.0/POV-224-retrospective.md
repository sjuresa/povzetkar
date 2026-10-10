# POV-224 Retrospective — Upgrade shell-quote, Scan npm Dev Dependencies in CI, Trivy 0.75.0

## 1. Planned vs. actual

Planned at first: a lockfile update of `shell-quote` and an `npm audit` check. The Brainstorm widened it to the gap
that let the advisory through. Trivy skipped npm development dependencies, and the `npm audit` named as the substitute
ran nowhere. The CI filesystem scan now includes them. The local scan command in the RUNBOOK was fixed and shortened.
The PO added the Trivy 0.75.0 bump in planning. Delivered in three commits as planned. Commits 1 and 2 were pushed
early for the CI red/green evidence.

## 2. Deviations & why

- **A speed claim made from a narrower measurement.** The plan said the local scan would drop from ~380 s to ~15 s by
  hiding `.git`. That was measured on the npm lockfiles with the vulnerability scanner alone. The full command
  (secret scanner, Maven) still took 13 min. A `--debug` run showed why: the secret scanner walks ~35 000 untracked
  build files (`transcript-proxy/.venv`, `backend/target`, `frontend/coverage`, `frontend/dist`). Skipping them gave
  ~2 min. The PO approved the extra `--skip-dirs`. The plan review had flagged that the measurement was narrower
  (finding C3), and the plan kept a step to re-measure. That step caught it before the RUNBOOK was written.
- **A project fact not read during planning.** The DoD asked for `npm run test` locally. The project memory already
  noted that it fails under `cmd.exe` on Windows (`./mvnw`). It ran instead with `--script-shell` set to Git Bash.
  `npm-run-all` was still exercised and both suites ran.
- **Maven Central rate limit (429)** blocked the online form of the local scan twice. The RUNBOOK's offline variant
  worked as documented.
- **Environment:** the dev database had been stopped for two days, so the first `npm run dev` smoke failed on the
  connection. It was started for the retry and stopped again afterwards.

## 3. Technical findings

- **Trivy skips npm `devDependencies` by default** (`--include-dev-deps`). `trivy-action` has no input for this, but
  passes `TRIVY_*` environment variables through. The CI red run proved the step-level `env:` reaches the composite
  action. Trivy and `npm audit` reported the same advisories with the same severities on this repository.
- **Local Trivy on Windows/Docker Desktop:** about 6 min of a 6.3-min npm-only scan went to Trivy's git-repository
  check. The cause is likely the file sharing; it was not measured. The secret scanner's walk over untracked build
  output cost most of the rest. CI on Linux scans the whole repository in 22 s.
- **The crypto scanner new in Trivy 0.75.0** builds an inventory, not findings: it is CycloneDX-only, image-only and
  off by default. It cannot affect a `table` gate.

## 4. Process weight (feed-forward from POV-212)

- Brainstorm 72 min: five decisions, most of the time in measurements the PO asked for (Trivy vs `npm audit`,
  durations, cause of the slow scan).
- Planning 57 min: four decisions and one plan review round with 13 findings (A 1, B 4, C 8), all verified and fixed.
- Implementation: see the walkthrough. The plan review's C3 and the "re-measure" step it added paid off directly.

## 5. Feed-forward

- **Read the project memory's environment notes before writing a DoD that runs local commands** (here: root
  `npm run test` on Windows).
- **A duration or speed-up claim names the exact command it was measured with.** A subset measurement does not carry
  over to the full command.
- **v0.20.0 finalisation, checklist 3.1a:** Trivy is already 0.75.0, and `trivy-action` v0.36.0 is the latest release.
  The `.trivyignore` audit (without `--ignorefile`) can use the new RUNBOOK command.
- **Backlog:** `npm-run-all` → `npm-run-all2`. Root scripts under `cmd.exe` ("Cross-platform Script Wrapper").
