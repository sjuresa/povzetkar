# Third-Party Assets & Compliance Registry

This document serves as the official registry for all third-party assets used in Povzetkar. It ensures transparency, proper attribution, and license compliance across the **Four Pillars** of our supply chain.

---

## 1. Media Pillar (Sounds, Icons, Fonts)

| Asset Group | Source | Author | License | Usage |
| :--- | :--- | :--- | :--- | :--- |
| **Auditory Feedback** | Dynamically Synthesized | N/A | Project License | `useAudioFeedback.ts` |
| **Icons (Lucide)** | [Lucide](https://lucide.dev/) | Lucide Contributors | [ISC](https://lucide.dev/license) | `frontend/` components |
| **Fonts (Inter)** | [Google Fonts](https://fonts.google.com/specimen/Inter) | Rasmus Andersson | [OFL](http://scripts.sil.org/OFL) | Global CSS |

> [!NOTE]
> Auditory feedback for success/error/info notifications is synthesized in real-time using the **Web Audio API (OscillatorNode)**. No binary audio files are bundled or fetched, ensuring zero-byte asset overhead.

---

## 2. Code Pillar (Libraries & Snippets)

| Reference | Source | Context | License |
| :--- | :--- | :--- | :--- |
| **NPM Dependencies** | `frontend/package.json` | System Logic | Various (MIT/Apache 2.0) |
| **Maven Dependencies** | `backend/pom.xml` | System Logic | Various (MIT/Apache 2.0) |

---

## 3. Services Pillar (APIs & SaaS)

| Provider | Purpose | Link |
| :--- | :--- | :--- |
| **OpenAI** | LLM Summarization | [Privacy Policy](https://openai.com/policies/privacy-policy) |
| **Anthropic** | LLM Summarization | [Privacy Policy](https://www.anthropic.com/privacy) |
| **YouTube API** | Video Metadata | [Terms of Service](https://www.youtube.com/t/terms) |

---

## 4. Infrastructure Pillar (Containers & Build)

| Component | Provider | Image/Source |
| :--- | :--- | :--- |
| **Base Image** | Docker Hub | `eclipse-temurin:25-jre-alpine` |
| **Runtime Environment** | Docker Hub | `node:24-alpine` |

---

> [!TIP]
> This registry is a living document. Any new third-party dependency added during development MUST be recorded here immediately.
