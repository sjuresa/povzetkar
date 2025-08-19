# API Specification (OpenAPI) - Project Povzetkar

**Version:** 1.0
**Date:** August 15, 2025

## 1. Introduction

This document provides a preliminary OpenAPI 3.0 specification for the user-facing REST API of the Povzetkar platform. It serves as the contract between the frontend client and the backend service.

## 2. OpenAPI Specification (YAML)

```yaml
openapi: 3.0.3
info:
  title: Povzetkar API
  version: 1.0.0
  description: API for the Povzetkar project.
paths:
  /health:
    get:
      summary: Health Check
      description: Checks if the service is up and running.
      responses:
        '200':
          description: Service is healthy
  /v1/chat:
    post:
      summary: Conversational RAG Endpoint
      description: Sends a user message and receives a synthesized answer from the knowledge base.
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                sessionId:
                  type: string
                  description: Unique identifier for the conversation session.
                message:
                  type: string
                  description: The user's question or message.
      responses:
        '200':
          description: Successful response with an answer.
          content:
            application/json:
              schema:
                type: object
                properties:
                  answer:
                    type: string
                  sources:
                    type: array
                    items:
                      type: object
                      properties:
                        url:
                          type: string
                        title:
                          type: string
  /v1/admin/sources/youtube-channels:
    post:
      summary: Add a YouTube Channel Source
      description: (Admin) Adds a new YouTube channel to be monitored for content.
      security:
        - oAuth2: [admin]
      requestBody:
        # ...
      responses:
        '201':
          description: Source created successfully.