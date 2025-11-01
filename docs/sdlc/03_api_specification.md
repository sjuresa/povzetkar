# API Specification - Project Povzetkar

**Version:** 1.2

## Revision History

| Version | Date               | Author       | Changes                                                                                             |
|---------|--------------------|--------------|-----------------------------------------------------------------------------------------------------|
| 1.2     | November 1, 2025   | Povzetkar AI | Refactored the specification to use the OpenAPI 3.0 standard for better tooling and clarity.        |
| 1.1     | November 1, 2025   | Povzetkar AI | Overhauled spec for v0.6. Removed Strangler Fig context and added endpoints for the new management UI. |
| 1.0     | September 8, 2025  | Povzetkar AI | Initial version for the Strangler Fig migration API.                                                 |

## 1. Introduction

This document specifies the REST API for the Povzetkar application. The API provides endpoints for managing core resources and retrieving generated intelligence, primarily serving the application's web interface and preparing for future client applications.

The API is versioned under the `/api/v1/` path.

## 2. Authentication

*(Future)* All endpoints will be secured. For server-to-server communication, API keys will be used. For user-facing applications, OAuth2/OIDC will be implemented. For the current simple web UI, authentication is not yet implemented.

## 3. Endpoints

This section provides the formal OpenAPI 3.0 specification for the API.

```yaml
openapi: 3.0.3
info:
  title: "Povzetkar API"
  version: "1.1"
  description: "API for managing topics and retrieving AI-generated briefings."
paths:
  /api/v1/users/{userId}/topics:
    get:
      summary: "Get All Topics by User"
      description: "Retrieves a list of all topics belonging to a specific user."
      parameters:
        - name: userId
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '200':
          description: "A list of topics."
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Topic'
    post:
      summary: "Add a New Topic"
      description: "Creates a new topic for a user."
      parameters:
        - name: userId
          in: path
          required: true
          schema:
            type: integer
            format: int64
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                name:
                  type: string
                  example: "Quantum Computing"
      responses:
        '201':
          description: "Topic created successfully."
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Topic'

  /api/v1/topics/{topicId}:
    put:
      summary: "Update a Topic"
      description: "Updates the details of an existing topic."
      parameters:
        - name: topicId
          in: path
          required: true
          schema:
            type: integer
            format: int64
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                name:
                  type: string
                  example: "Quantum Mechanics"
      responses:
        '200':
          description: "Topic updated successfully."
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Topic'
    delete:
      summary: "Delete a Topic"
      description: "Deletes a topic and its associated content sources."
      parameters:
        - name: topicId
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '204':
          description: "Topic deleted successfully."

  /api/v1/topics/{topicId}/briefings:
    get:
      summary: "Get Briefings for a Topic"
      description: "Retrieves a list of all generated briefings for a specific topic, ordered by date descending."
      parameters:
        - name: topicId
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '200':
          description: "A list of briefings."
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Briefing'

components:
  schemas:
    Topic:
      type: object
      properties:
        id:
          type: integer
          format: int64
        name:
          type: string
        contentSources:
          type: array
          items:
            $ref: '#/components/schemas/ContentSource'
    ContentSource:
      type: object
      properties:
        id:
          type: integer
          format: int64
        name:
          type: string
    Briefing:
      type: object
      properties:
        id:
          type: integer
          format: int64
        title:
          type: string
        briefingDate:
          type: string
          format: date
        metaSummary:
          type: string
```
