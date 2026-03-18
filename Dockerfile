# Stage 1: Build Frontend
FROM node:20-alpine AS build-frontend
WORKDIR /app
COPY frontend/package*.json ./frontend/
RUN cd frontend && npm install --legacy-peer-deps
COPY frontend/src ./frontend/src
COPY frontend/public ./frontend/public
COPY frontend/index.html frontend/tsconfig*.json frontend/*.config.* ./frontend/
RUN cd frontend && npm run build

# Stage 2: Build Backend
FROM eclipse-temurin:25-jdk-alpine AS build-backend
RUN apk add --no-cache maven
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
WORKDIR /app
COPY --from=build-backend /app/backend/target/quarkus-app/ ./
EXPOSE 8080
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
CMD ["java", "-jar", "quarkus-run.jar"]
HEALTHCHECK --interval=30s --timeout=10s --retries=3 CMD curl -f http://localhost:8080/q/health || exit 1
