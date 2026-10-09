# Third-Party Assets & Compliance Registry

Registry of the third-party assets, libraries, services and images Povzetkar uses, with their licences or terms.

**Project license:** © 2025–2026 Simon Juresa. All rights reserved. The source is published for review; see the
notice in the [README](../README.md). Third-party components keep their own licences listed below.

---

## 1. Media (sounds, icons, fonts)

| Asset | Source | License | Usage |
| :--- | :--- | :--- | :--- |
| **Auditory feedback** | Synthesized at runtime (Web Audio API, `OscillatorNode`) | Project | `frontend/src/hooks/useAudioFeedback.ts` |
| **Icons** | [Lucide](https://lucide.dev/) (`lucide-react`) | [ISC](https://lucide.dev/license) | `frontend/` components |

No binary audio or font files are bundled. The CSS font stack names *Inter* first
(`frontend/src/index.css`), but the font is neither bundled nor loaded — browsers use it only if it is installed on
the visitor's system, otherwise the system fonts that follow it.

---

## 2. Code (libraries)

| Reference | Source | License |
| :--- | :--- | :--- |
| **npm dependencies** | `package.json`, `frontend/package.json` | Various (mostly MIT, Apache-2.0, ISC) |
| **Maven dependencies** | `pom.xml`, `backend/pom.xml` | Various (mostly Apache-2.0) |
| **Python dependencies** (transcript proxy) | `transcript-proxy/requirements.txt` | Various (MIT, BSD) |

---

## 3. Services (APIs)

| Provider | Purpose | Terms |
| :--- | :--- | :--- |
| **Google Gemini API** | Summaries and briefings (`google-genai` SDK) | [Gemini API Additional Terms](https://ai.google.dev/gemini-api/terms) |
| **YouTube Data API v3** | Channel resolution, new videos, metadata | [YouTube API Services Terms](https://developers.google.com/youtube/terms/api-services-terms-of-service) |
| **YouTube** | Video captions (transcripts) | [YouTube Terms of Service](https://www.youtube.com/t/terms) |
| **Google Sign-In (OIDC)** | User authentication | [Google APIs Terms](https://developers.google.com/terms) |
| **Supadata** (optional, disabled by default) | Fallback transcript provider | [Supadata](https://supadata.ai/) |
| **Cloudflare Tunnel** | Public entry of the production deployment | [Cloudflare terms](https://www.cloudflare.com/website-terms/) |

---

## 4. Infrastructure (container images)

| Component | Image | Where |
| :--- | :--- | :--- |
| Frontend build | `node:24-alpine` | `Dockerfile` |
| Backend build | `eclipse-temurin:25-jdk-alpine` | `Dockerfile` |
| Healthcheck build | `golang:1.26.9-alpine` | `Dockerfile` |
| Runtime | `gcr.io/distroless/java25-debian13` | `Dockerfile` |
| Database | `pgvector/pgvector:pg17` | `backend/docker-compose*.yml` |
| Metrics | `prom/prometheus`, `grafana/grafana-oss` | `backend/docker-compose*.yml` |
| Tunnel | `cloudflare/cloudflared` | `backend/docker-compose.prod.yml` |

---

> [!TIP]
> This registry is a living document. A new third-party dependency, service or image is recorded here when it is
> added.
