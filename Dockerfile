# Stage 1: Build Frontend
FROM node:20-alpine AS build-frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN apk update && apk upgrade --no-cache && npm install

COPY frontend/src ./src
COPY frontend/public ./public
COPY frontend/index.html ./
COPY frontend/tsconfig*.json ./
COPY frontend/*.config.* ./
RUN npm run build

# Stage 2: Build Backend
FROM eclipse-temurin:25-jdk-alpine AS build-backend
RUN apk update && apk upgrade --no-cache && apk add --no-cache maven
WORKDIR /app
COPY pom.xml .
COPY backend/pom.xml ./backend/
# Download dependencies for offline build (cache layer)
RUN mvn dependency:go-offline -pl backend -am
COPY backend/src ./backend/src
# Embed frontend artifacts into Quarkus static resources
COPY --from=build-frontend /app/frontend/dist ./backend/src/main/resources/META-INF/resources
RUN mvn package -DskipTests -pl backend -am

# Stage 3: Runtime
FROM eclipse-temurin:25-jre-alpine
RUN apk update && apk upgrade --no-cache && apk add --no-cache curl
WORKDIR /app
COPY --from=build-backend /app/backend/target/quarkus-app/ ./
EXPOSE 8080
ENV QUARKUS_HTTP_HOST=0.0.0.0
ENV QUARKUS_HTTP_PORT=8080
# Ensure the logging manager is set correctly for the runtime
ENV JAVA_OPTS="-Djava.util.logging.manager=org.jboss.logmanager.LogManager"
CMD ["java", "-jar", "quarkus-run.jar"]
# Verify both backend readiness (DB) and SPA availability (Static assets)
HEALTHCHECK --interval=30s --timeout=10s --retries=3 CMD curl -f http://localhost:8080/q/health/ready && curl -f http://localhost:8080/ || exit 1
