# 6. Auditory Feedback Framework

Date: 2026-04-01

## Status

Accepted

## Context

The system performs long-running background tasks (summarization, orchestration). Users are currently limited to visual feedback (Toasts, Skeletons), which can be missed if they are tabbed out or have low vision. We want to add subtle auditory cues to asynchronous events.

## Decision

We will implement a **Standard Sound Framework** for Toast notifications.

Specifically:
1.  **Success**: A short "Ding" (<300ms) for briefing completion.
2.  **Error**: A dual-tone "Alert" for job failures.
3.  **Info**: A neutral "Chime" for generic system messages.
4.  Implementation will use the **Native Web Audio API** (via a custom hook) with **synthesized oscillators** (`OscillatorNode`) to achieve a zero-byte asset footprint while avoiding external dependencies.
5.  Audio will be **muted by default** until the first user interaction, adhering to modern browser autoplay policies. Error-handling will ensure silent failure if the playback is blocked.

## Consequences

**Positive:**
-   **UX**: Immediate feedback for job completion even if the tab is in the background.
-   **Immersive experience**: Adds a "Premium" feel.
-   **Accessibility**: Provides a non-visual feedback channel.

**Negative:**
-   **Performance**: Small increase in initial payload (audio assets < 50kb total).
-   **Friction**: Risk of "Audio Fatigue" if sounds are played too frequently or are too abrasive.
-   **Settings Debt**: Users will want "Mute/Unmute" individual controls, which will be added later.
