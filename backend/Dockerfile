# Multi-stage Dockerfile for Viaduct/Ktor backend
# Docker context is project root (.) to access schema/migrations

# Stage 1: Build the application
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy Gradle wrapper and build files first (for better layer caching)
COPY backend/gradlew gradlew
COPY backend/gradle/ gradle/
COPY backend/build.gradle.kts build.gradle.kts
COPY backend/settings.gradle.kts settings.gradle.kts
COPY backend/gradle.properties gradle.properties

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code (includes src/main/viaduct/ schema files)
COPY backend/src/ src/

# Copy Viaduct API files at root level
COPY backend/viaduct/ viaduct/

# Build the application distribution
RUN ./gradlew installDist --no-daemon -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jre

WORKDIR /app

# Install PostgreSQL client for migrations
RUN apt-get update && apt-get install -y --no-install-recommends \
    postgresql-client \
    && rm -rf /var/lib/apt/lists/*

# Copy the distribution from builder stage
COPY --from=builder /app/build/install/viaduct-backend/ /app/

# Copy migration files and script from project root
COPY schema/migrations/ /app/migrations/
COPY backend/scripts/run-migrations.sh /app/scripts/run-migrations.sh

# Make scripts executable
RUN chmod +x /app/bin/viaduct-backend /app/scripts/run-migrations.sh

# Expose port (Render sets PORT env var, defaults to 8080)
EXPOSE 8080

# JVM options for container environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 --add-opens java.base/java.lang=ALL-UNNAMED"

# Create entrypoint script that runs migrations then starts the app
RUN printf '#!/bin/bash\nset -e\necho "Running database migrations..."\n/app/scripts/run-migrations.sh\necho "Starting application..."\nexec /app/bin/viaduct-backend\n' > /app/entrypoint.sh && chmod +x /app/entrypoint.sh

# Run the entrypoint script
CMD ["/app/entrypoint.sh"]
