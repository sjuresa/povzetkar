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