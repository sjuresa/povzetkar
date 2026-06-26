# v0.18.0 Version Hub

**Status:** Completed
**YouTrack Milestone:** [v0.18.0](https://youtrack.example.com/youtrack/agiles) *(update with actual milestone link)*
**Scope Statement:** v0.18.0 — **Transcript Ingestion Resilience & Scheduler Correctness.** Delivered an IP-block-resistant transcript path (standalone FastAPI proxy → primary `ProxyTranscriptProvider` → self-hosted on Synology DS220j over Tailscale), fixed two long-standing scheduler defects (orchestration that had never run on a schedule; transcript lost on Gemini-retry re-fetch), made GenAI daily usage reflect true RPD consumption (including disabling the Google SDK's hidden 5× retry), resolved a demo-user access regression, cleared all blocking Trivy CVEs, and evolved the development methodology to task-first planning. See [retrospective.md](retrospective.md) for the cross-task synthesis.

---

## Completed Tasks

| Task | Walkthrough | Retrospective |
| --- | --- | --- |
| POV-191 — Development Process Evolution: Task-First Planning | [walkthrough](POV-191-walkthrough.md) | [retrospective](POV-191-retrospective.md) |
| POV-192 — Fix Demo User Access | [walkthrough](POV-192-walkthrough.md) | [retrospective](POV-192-retrospective.md) |
| POV-193 — Transcript Proxy Service | [walkthrough](POV-193-walkthrough.md) | [retrospective](POV-193-retrospective.md) |
| POV-194 — ProxyTranscriptProvider (Quarkus integration) | [walkthrough](POV-194-walkthrough.md) | [retrospective](POV-194-retrospective.md) |
| POV-195 — RPD-accurate GenAI counting & remove countTokens | [walkthrough](POV-195-walkthrough.md) | [retrospective](POV-195-retrospective.md) |
| POV-196 — Relocate transcript proxy from Railway to Synology DS220j | [walkthrough](POV-196-walkthrough.md) | [retrospective](POV-196-retrospective.md) |
| POV-197 — Scheduled Orchestration Fix & Periodic Content Fetch | [walkthrough](POV-197-walkthrough.md) | [retrospective](POV-197-retrospective.md) |
| POV-198 — Persist Transcript Before Gemini Call | [walkthrough](POV-198-walkthrough.md) | [retrospective](POV-198-retrospective.md) |
| POV-199 — Fix Trivy Security Vulnerabilities | [walkthrough](POV-199-walkthrough.md) | [retrospective](POV-199-retrospective.md) |
| POV-200 — Finalize Documentation for Release v0.18.0 | [walkthrough](POV-200-walkthrough.md) | [retrospective](POV-200-retrospective.md) |
