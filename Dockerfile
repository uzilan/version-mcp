# Multi-stage build for Maven Version MCP Server
FROM gradle:8.5-jdk21 AS builder

# Set working directory
WORKDIR /app

# Copy build files
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle/ gradle/

# Copy source code
COPY src/ src/

# Build the application
RUN gradle build --no-daemon

# Runtime stage
FROM openjdk:21-jre-slim

# Install necessary packages
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Create app user
RUN groupadd -r mcp && useradd -r -g mcp mcp

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/build/libs/maven-version-mcp-server-*.jar /app/maven-version-mcp-server.jar

# Copy configuration files
COPY config.json.example /app/config.json.example
COPY scripts/ /app/scripts/

# Create logs directory
RUN mkdir -p /app/logs && chown -R mcp:mcp /app

# Switch to app user
USER mcp

# Expose ports for metrics and health check
EXPOSE 8081 8082

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8082/health || exit 1

# Default command
CMD ["java", "-jar", "maven-version-mcp-server.jar"]
