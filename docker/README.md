# Docker Setup for InFlightKv

This directory contains Docker configuration files to run the InFlightKv Spring Web application in a containerized environment.

## Quick Setup Options

### Single Node (Development/Testing)
For development, testing, or simple single-instance deployments:

```bash
# Linux/Mac
./docker/run-single.sh up-d    # Start in background
./docker/run-single.sh test    # Run basic API tests

# Windows
docker\run-single.bat up-d     # Start in background
docker\run-single.bat test     # Run basic API tests
```

**Single Node Access:**
- API: http://localhost:8080
- Health: http://localhost:8080/actuator/health

### Multi-Node Cluster (Production)
For full cluster deployments with multiple nodes:

## Prerequisites

- Docker installed on your system
- Docker Compose installed on your system
- **Application must be built first:** `./gradlew :modules:spring-web:build -x test`

## Quick Start

### Using Runner Scripts (Recommended)

**Linux/Mac:**
```bash
# Start all services in foreground
./docker/run.sh up

# Start all services in background
./docker/run.sh up-d

# Stop all services
./docker/run.sh down

# View logs for all services
./docker/run.sh logs

# Check status of all services
./docker/run.sh status

# Start specific service (e.g., only service 1)
./docker/run.sh up-1

# View logs for specific service
./docker/run.sh logs-1
```

**Windows:**
```cmd
REM Start all services in foreground
docker\run.bat up

REM Start all services in background
docker\run.bat up-d

REM Stop all services
docker\run.bat down

REM View logs for all services
docker\run.bat logs

REM Check status of all services
docker\run.bat status

REM Start specific service (e.g., only service 1)
docker\run.bat up-1

REM View logs for specific service
docker\run.bat logs-1
```

### Single Node Setup

**Using Runner Scripts:**
```bash
# Linux/Mac
./docker/run-single.sh up-d     # Start in background
./docker/run-single.sh logs     # View logs
./docker/run-single.sh test     # Run API tests
./docker/run-single.sh down     # Stop

# Windows
docker\run-single.bat up-d      # Start in background
docker\run-single.bat logs      # View logs
docker\run-single.bat test      # Run API tests
docker\run-single.bat down      # Stop
```

**Using Docker Compose Directly:**
```bash
# Build the application
./gradlew :modules:spring-web:build -x test

# Start single node
docker-compose -f docker/docker-compose-single.yml up --build

# Start in background
docker-compose -f docker/docker-compose-single.yml up -d --build

# Stop
docker-compose -f docker/docker-compose-single.yml down
```

### Multi-Node Cluster Setup

**Using Runner Scripts (Recommended):**

**Linux/Mac:**
```bash
# Start all services in foreground
./docker/run.sh up

# Start all services in background
./docker/run.sh up-d

# Stop all services
./docker/run.sh down

# View logs for all services
./docker/run.sh logs

# Check status of all services
./docker/run.sh status

# Start specific service (e.g., only service 1)
./docker/run.sh up-1

# View logs for specific service
./docker/run.sh logs-1
```

**Windows:**
```cmd
REM Start all services in foreground
docker\run.bat up

REM Start all services in background
docker\run.bat up-d

REM Stop all services
docker\run.bat down

REM View logs for all services
docker\run.bat logs

REM Check status of all services
docker\run.bat status

REM Start specific service (e.g., only service 1)
docker\run.bat up-1

REM View logs for specific service
docker\run.bat logs-1
```

**Using Docker Compose Directly:**

1. **Build the application:**
   ```bash
   ./gradlew :modules:spring-web:build -x test
   ```

2. **Build and run the containers:**
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

### Single Node
- **API:** http://localhost:8080
- **Health Check:** http://localhost:8080/actuator/health
- **Cluster API:** http://localhost:8080/api/cluster/route/{key}

### Multi-Node Cluster
- **Service 1:** http://localhost:8080
- **Service 2:** http://localhost:8081
- **Service 3:** http://localhost:8082

**Health Checks:**
- Service 1: http://localhost:8080/actuator/health
- Service 2: http://localhost:8081/actuator/health
- Service 3: http://localhost:8082/actuator/health

**Cluster API Endpoints:**
- Service 1: http://localhost:8080/api/cluster/route/{key}
- Service 2: http://localhost:8081/api/cluster/route/{key}
- Service 3: http://localhost:8082/api/cluster/route/{key}

## Configuration

### Single Node Configuration

Single-node setup uses the `single-node` Spring profile which configures the application as a standalone instance:

- **Profile:** `single-node`
- **Node ID:** `node-1` (configurable via `NODE_ID`)
- **Cluster:** Contains only itself as a node
- **No redirects:** All operations handled locally

### Multi-Node Cluster Configuration

Multi-node setup uses the `docker` Spring profile with full cluster configuration:

- **Profile:** `docker`
- **Nodes:** 3-node cluster (configurable)
- **Node Information:** ID, name, host, port, URLs
- **Health Monitoring:** Each node can check others' health
- **Service Discovery:** Nodes know about all other instances

### Available Cluster API Endpoints

- `GET /api/cluster/route/{key}` - Route key to determine responsible node

### Key Routing Algorithm

The cluster uses consistent hashing to distribute keys across nodes:

1. **Hash Calculation**: `key.hashCode()`
2. **Node Selection**: `Math.abs(hash) % node_count`
3. **Result**: Returns the node responsible for the key and whether to redirect

**Example:**
```bash
# Check which node handles "my-key"
curl http://localhost:8080/api/cluster/route/my-key

# Response:
{
  "nodeId": "node-2",
  "nodeName": "inflight-kv-2",
  "host": "inflight-kv-2",
  "port": 8080,
  "internalUrl": "http://inflight-kv-2:8080",
  "shouldRedirect": true
}
```

**Usage in Code:**
```java
@Autowired
private ClusterService clusterService;

// Route a key
KeyRoutingResult result = clusterService.routeKey("my-key");
if (result.isShouldRedirect()) {
    // Redirect to result.getInternalUrl()
} else {
    // Handle locally
}
```

### Environment Variables

You can customize the application behavior by setting environment variables in the `docker-compose.yml`:

```yaml
environment:
  - SPRING_PROFILES_ACTIVE=docker
  # Add other Spring Boot properties as needed
```

### Port Configuration

The services run on ports 8080, 8081, and 8082 by default. You can change these in `docker-compose.yml`:

```yaml
services:
  inflight-kv-1:
    ports:
      - "9090:8080"  # Change host port for service 1
  inflight-kv-2:
    ports:
      - "9091:8080"  # Change host port for service 2
  inflight-kv-3:
    ports:
      - "9092:8080"  # Change host port for service 3
```

All services run on container port 8080 internally.

## Development Workflow

1. **Make code changes** in the `modules/spring-web` directory
2. **Rebuild the application:**
   ```bash
   ./gradlew :modules:spring-web:build -x test
   ```
3. **Restart containers:**
   ```bash
   ./docker/run.sh up-d    # Restart all services
   # OR
   ./docker/run.sh restart-1  # Restart only service 1
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
│   ├── docker-compose.yml     # Multi-service Docker Compose configuration
│   ├── run.sh                # Linux/Mac multi-service runner script
│   ├── run.bat               # Windows multi-service runner script
│   └── README.md             # This file
└── modules/spring-web/
    └── Dockerfile            # Single-stage runtime Docker build
```

## Production Considerations

For production deployment, consider:

- **Current setup:** Application is pre-built, resulting in faster container startup
- Adding proper health checks (already included)
- Configuring proper logging
- Setting up proper resource limits
- Using environment-specific configuration files
- Consider switching to multi-stage builds for CI/CD pipelines
