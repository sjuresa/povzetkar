# Functional Requirements Document (FRD) - Project Povzetkar

## Revision History

| Version | Date             | Author       | Changes                                                                                         |
|---------|------------------|--------------|-------------------------------------------------------------------------------------------------|
| 1.4     | March 30, 2026   | Povzetkar AI | Assigned unique IDs to all User Stories for Traceability Matrix hardening (POV-166). |
| 1.3     | January 17, 2026 | Povzetkar AI | Added requirements for topic management, granular orchestration, and error visibility (v0.7.0). |
| 1.2     | January 6, 2026  | Povzetkar AI | Marked self-healing requirement as implemented (v0.6.2).                                        |
| 1.1     | November 1, 2025 | Povzetkar AI | Synced with v0.6: marked stories as implemented, updated user roles, and refreshed terminology. |
| 1.0     | August 15, 2025  | Povzetkar AI | Initial version of the functional requirements based on the project specification.              |

## 1. Introduction

This document outlines the functional requirements for Project Povzetkar. It describes *what* the system will do from the perspective of the end-user and system administrators. It is based on the project vision of creating an automated intelligence platform.

## 2. User Roles

*   **User/Consumer:** The primary user who consumes the generated intelligence. Currently, this involves viewing aggregated reports (briefings) via a simple web interface. In the future, this role will interact with a conversational interface.
*   **Administrator:** The user responsible for managing the system's configuration. This includes adding and managing users, topics, and the data sources associated with them.

## 3. User Stories

This section breaks down the system's functionality into user-centric stories.

### 3.1. Data Ingestion & Processing

*   **US-ING-001** (Implemented) **As an Administrator, I want to** configure a list of content sources (e.g., YouTube channels) for a topic so that the system automatically monitors them for new content.
*   **US-ING-002** (Future) **As an Administrator, I want to** define keyword searches for YouTube so that the system finds relevant new videos on specific topics.
*   **US-ING-003** (Future) **As an Administrator, I want to** add a list of websites, blogs, and Reddit communities so the system can ingest articles and posts.
*   **US-ING-004** (Implemented) **As a System, I want to** automatically fetch new content from all configured sources on a periodic basis.
*   **US-ING-005** (Implemented) **As a System, I want to** generate a concise, accurate summary for every new piece of content.
*   **US-ING-006** (Implemented) **As a System, I want to** capture and persist detailed error codes and messages when processing fails, enabling targeted troubleshooting.
*   **US-ING-007** (Implemented) **As a System, I want to** automatically re-process content that failed due to transient errors so that the system can self-heal and ensure data completeness.
*   **US-ING-008** (Future) **As a System, I want to** generate vector embeddings for all content and summaries to enable semantic search.

### 3.2. Analysis & Reporting

*   **US-REP-001** (Future) **As a System, I want to** analyze the sentiment (positive, negative, neutral) of each piece of content.
*   **US-REP-002** (Future) **As a System, I want to** automatically categorize each summary based on a predefined taxonomy.
*   **US-REP-003** (Implemented) **As a User, I want to** view a daily aggregated report (briefing) that includes a meta-summary of all individual summaries from that period.
*   **US-REP-004** (Future) **As a User, I want to** see emerging trends and patterns identified from the content within a specific time frame.
*   **US-REP-005** (Future) **As a User, I want to** view an assessment of a content source's potential bias based on its historical content.

### 3.3. Conversational Interface (RAG)

*   **US-RAG-001** (Future) **As a User, I want to** ask natural language questions about the collected information.
*   **US-RAG-002** (Future) **As a User, I want to** receive answers that are synthesized from the underlying source documents.
*   **US-RAG-003** (Future) **As a User, I want to** see the sources (with links and timestamps) that were used to generate an answer.
*   **US-RAG-004** (Future) **As a User, I want to** have a continuous conversation, where the system remembers the context of my previous questions.

### 3.4. Content Creation

*   **US-CRT-001** (Future) **As a User, I want to** select a topic or a report and have the system generate a draft for a blog post.
*   **US-CRT-002** (Future) **As a User, I want to** generate a series of social media posts (e.g., for X/Twitter) based on a specific summary or trend.

### 3.5. User Interface & Management

*   **US-UI-001** (Implemented) **As a User, I want to** navigate a simple web interface to select a topic to view the latest briefings.
*   **US-UI-002** (Implemented) **As an Administrator, I want to** add, edit, and delete topics and manage their associated content sources through the web interface.
*   **US-UI-003** (Implemented) **As an Administrator, I want to** enable or disable specific topics to control which content is actively processed without deleting the topic configuration.
*   **US-UI-004** (Implemented) **As an Administrator, I want to** use a simple web interface to trigger system-level actions like content enrichment and orchestration.
*   **US-UI-005** (Implemented) **As an Administrator, I want to** granularly trigger specific phases of the orchestration process (e.g., only processing pending items or only generating briefings).
*   **US-UI-006** (Implemented) **As an Administrator, I want to** view specific error details for failed content items and manually trigger a retry for transcript fetching.
