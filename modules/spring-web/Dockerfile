# InFlightKv Spring Web Application Docker Image
FROM eclipse-temurin:21-jre

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Create a non-root user for security
RUN addgroup --system spring && adduser --system spring --ingroup spring

RUN echo "=== DOCKER BUILD DEBUG $(date) ===" && pwd && ls -la
# Copy the pre-built JAR file (executable JAR with dependencies)
# This assumes the application has been built first: ./gradlew :modules:spring-web:build -x test
COPY ./modules/spring-web/build/libs/spring-web-0.0.1-SNAPSHOT.jar app.jar

# Change ownership to the spring user
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring

# Expose the port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]
