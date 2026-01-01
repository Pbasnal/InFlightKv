# Docker Setup for InFlightKv

This directory contains Docker configuration files to run the InFlightKv Spring Web application in a containerized environment.

## Prerequisites

- Docker installed on your system
- Docker Compose installed on your system
- **Application must be built first:** `./gradlew :modules:spring-web:build -x test`

## Quick Start

### Using Runner Scripts (Recommended)

**Linux/Mac:**
```bash
# Start in foreground
./docker/run.sh up

# Start in background
./docker/run.sh up-d

# Stop application
./docker/run.sh down

# View logs
./docker/run.sh logs

# Check status
./docker/run.sh status
```

**Windows:**
```cmd
REM Start in foreground
docker\run.bat up

REM Start in background
docker\run.bat up-d

REM Stop application
docker\run.bat down

REM View logs
docker\run.bat logs

REM Check status
docker\run.bat status
```

### Using Docker Compose Directly

1. **Build the application:**
   ```bash
   ./gradlew :modules:spring-web:build -x test
   ```

2. **Build and run the container:**
   ```bash
   docker-compose up --build
   ```

3. **Run in detached mode (background):**
   ```bash
   docker-compose up -d --build
   ```

4. **Stop the application:**
   ```bash
   docker-compose down
   ```

## Accessing the Application

Once the container is running, the InFlightKv API will be available at:
- **URL:** http://localhost:8080
- **Health Check:** You can verify the application is running by checking if the port is accessible

## Configuration

### Environment Variables

You can customize the application behavior by setting environment variables in the `docker-compose.yml`:

```yaml
environment:
  - SPRING_PROFILES_ACTIVE=docker
  # Add other Spring Boot properties as needed
```

### Port Configuration

The application runs on port 8080 by default. You can change this in `docker-compose.yml`:

```yaml
ports:
  - "9090:8080"  # Host port : Container port
```

## Development Workflow

1. **Make code changes** in the `modules/spring-web` directory
2. **Rebuild the application:**
   ```bash
   ./gradlew :modules:spring-web:build -x test
   ```
3. **Restart containers:**
   ```bash
   docker-compose up --build
   ```

## Docker Image Details

- **Build Approach:** Pre-built JAR (build application first, then containerize)
- **Base Image:** Eclipse Temurin JRE 21 (slim runtime)
- **Working Directory:** `/app`
- **Exposed Port:** 8080
- **Health Check:** `/actuator/health` endpoint
- **User:** Non-root `spring` user for security
- **JAR Source:** `modules/spring-web/build/libs/*.jar`
- **Entrypoint:** Runs the Spring Boot JAR file

## Troubleshooting

### Common Issues

1. **Port already in use:**
   ```bash
   # Change the port mapping in docker-compose.yml
   ports:
     - "9090:8080"
   ```

2. **Build fails due to missing JAR:**
   Ensure the application is built first:
   ```bash
   ./gradlew :modules:spring-web:build -x test
   ```
   Check that the JAR file exists: `modules/spring-web/build/libs/*.jar`

3. **Container won't start:**
   Check the logs:
   ```bash
   docker-compose logs inflight-kv
   ```

### Logs

View application logs:
```bash
docker-compose logs -f inflight-kv
```

## File Structure

```
/
├── .dockerignore              # Files to exclude from Docker context
├── docker/
│   ├── docker-compose.yml     # Docker Compose configuration
│   ├── run.sh                # Linux/Mac runner script
│   ├── run.bat               # Windows runner script
│   └── README.md             # This file
└── modules/spring-web/
    └── Dockerfile            # Multi-stage Docker build
```

## Production Considerations

For production deployment, consider:

- **Current setup:** Application is pre-built, resulting in faster container startup
- Adding proper health checks (already included)
- Configuring proper logging
- Setting up proper resource limits
- Using environment-specific configuration files
- Consider switching to multi-stage builds for CI/CD pipelines
