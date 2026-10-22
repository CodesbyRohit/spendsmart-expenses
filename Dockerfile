# syntax=docker/dockerfile:1
# Single-origin image: Spring Boot serves the API and the built React app on one port.

# --- Frontend build ---------------------------------------------------------
FROM node:22-alpine AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npx vite build

# --- Backend build ----------------------------------------------------------
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /src/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
RUN ./mvnw -B -q -DskipTests package

# --- Runtime ----------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --from=backend /src/backend/target/spendsmart.jar /app/spendsmart.jar
COPY --from=frontend /src/frontend/dist /app/static
ENV SPRING_PROFILES_ACTIVE=prod \
    APP_STATIC_DIR=/app/static \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
USER app
EXPOSE 8080
# /dev/tcp is a bash feature (the image's /bin/sh is dash); using bash avoids adding curl to the runtime image.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q UP <&3"]
ENTRYPOINT ["java", "-jar", "/app/spendsmart.jar"]
