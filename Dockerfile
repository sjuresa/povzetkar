# Stage 1: Build Frontend
FROM node:24-alpine AS build-frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN apk update && apk upgrade --no-cache && npm ci

COPY frontend/src ./src
COPY frontend/public ./public
COPY frontend/index.html ./
COPY frontend/tsconfig*.json ./
COPY frontend/*.config.* ./
# Imported by vite.config.ts (read only by `vite preview`, the Playwright CSP check - POV-214)
COPY frontend/csp-policy.ts ./
RUN npm run build

# Stage 2: Build Backend
FROM eclipse-temurin:25-jdk-alpine AS build-backend
RUN apk update && apk upgrade --no-cache && apk add --no-cache maven
WORKDIR /app
COPY pom.xml .
COPY backend/pom.xml ./backend/
RUN mvn dependency:go-offline -pl backend -am
COPY backend/src ./backend/src
# Embed frontend artifacts into Quarkus static resources
COPY --from=build-frontend /app/frontend/dist ./backend/src/main/resources/META-INF/resources
RUN mvn package -DskipTests -pl backend -am
RUN mkdir -p /logroot/app

# Stage 3: Build custom static healthcheck tool for Distroless compatibility
FROM golang:1.26.9-alpine AS build-healthcheck
WORKDIR /app
COPY build/healthcheck/main.go healthcheck.go
RUN go build -ldflags="-s -w" -o /go/bin/healthcheck healthcheck.go

# Stage 4: Runtime - Official Distroless Java 25 (Ultra-Hardened)
# Resolves CVE-2026-27135 (nghttp2), CVE-2025-60876 (busybox), CVE-2016-2781 (coreutils)
FROM gcr.io/distroless/java25-debian13

WORKDIR /app

# Copy the healthcheck binary from the build stage
COPY --from=build-healthcheck /go/bin/healthcheck /usr/local/bin/healthcheck

# Copy application artifacts with correct ownership (nonroot user)
COPY --from=build-backend --chown=nonroot:nonroot /app/backend/target/quarkus-app/ ./

# Log directory for the prod file log (compose mounts the app_logs volume here): a new volume
# inherits this nonroot ownership. Copied as the content of /logroot so --chown covers the
# directory itself (distroless has no shell for mkdir/chown).
COPY --from=build-backend --chown=nonroot:nonroot /logroot/ /var/log/

# Expose the application port. The management port (9000: /q/health, /q/metrics, /q/openapi) is
# not listed here; EXPOSE is metadata only and restricts nothing. What keeps 9000 private is that
# it is never published (`ports:`) nor routed by the Cloudflare tunnel — any container on the same
# Docker network can still reach it.
EXPOSE 8080/tcp

# Standard Quarkus environment variables
ENV QUARKUS_HTTP_HOST=0.0.0.0
ENV QUARKUS_HTTP_PORT=8080
ENV JAVA_OPTS="-Djava.util.logging.manager=org.jboss.logmanager.LogManager"

# Run as nonroot
USER nonroot

# Operational healthcheck using the custom static binary
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD ["/usr/local/bin/healthcheck", "http://localhost:9000/q/health/ready"]

# Definitive entrypoint targeting the official Distroless java binary
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
