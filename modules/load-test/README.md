# InFlightKv Load Test Application

A console application for load testing the InFlightKv distributed key-value store.

## Features

- **Multi-threaded Testing**: Configurable number of concurrent threads
- **Comprehensive Operations**: Tests PUT, GET, PATCH, DELETE, and GET ALL KEYS operations
- **Realistic Load Distribution**:
  - 30% PUT operations (creates new keys)
  - 30% GET operations (retrieves existing keys)
  - 20% PATCH operations (updates existing keys)
  - 15% DELETE operations (removes existing keys)
  - 5% GET ALL KEYS operations (cluster-wide key listing)
- **Shared Key Tracking**: Concurrent tracking of keys created by all threads
- **Configurable Parameters**: Host, port, thread count, requests per thread, and timeout

## Building

```bash
./gradlew :modules:load-test:build
```

## Running

### Basic Usage
```bash
java -jar modules/load-test/build/libs/load-test-0.0.1-SNAPSHOT.jar
```

### With Custom Configuration
```bash
java -jar modules/load-test/build/libs/load-test-0.0.1-SNAPSHOT.jar \
  --threads 20 \
  --requests 500 \
  --host kv-cluster.example.com \
  --port 8080 \
  --duration 600
```

### Command Line Options

- `-t, --threads <count>`: Number of concurrent threads (default: 10)
- `-r, --requests <count>`: Number of requests per thread (default: 100)
- `-h, --host <host>`: Target host (default: localhost)
- `-p, --port <port>`: Target port (default: 8080)
- `-d, --duration <seconds>`: Maximum test duration in seconds (default: 300)
- `--help`: Show help message

## Output

The application provides detailed results including:

- Total requests executed
- Success/failure rates
- Throughput (requests per second)
- Keys created and currently tracked
- Test duration

Example output:
```
=== Load Test Results ===
Total Requests: 1000
Successful Requests: 987
Failed Requests: 13
Success Rate: 98.70%
Total Duration: 4523ms
Requests/Second: 221.15
Keys Created: 298
Keys Currently Tracked: 156
========================
```

## Architecture

### Components

1. **LoadTestApplication**: Main entry point with CLI argument parsing
2. **LoadTestConfig**: Configuration holder for test parameters
3. **LoadTestService**: Core service managing thread execution and results aggregation
4. **KvStoreHttpClient**: HTTP client for making requests to the KV store REST API
5. **LoadTestResults**: Results container with statistics and metrics

### Thread Safety

- Uses `ConcurrentSkipListSet` for shared key tracking
- Atomic counters for request statistics
- Thread-safe HTTP client operations

### Key Tracking

The application maintains a shared, thread-safe set of keys that all threads contribute to:

- **PUT operations**: Add new unique keys to the shared set
- **DELETE operations**: Remove keys from the shared set
- **GET/PATCH operations**: Use existing keys from the shared set

This ensures realistic testing patterns where threads operate on keys created by other threads.

## Prerequisites

- Java 21+
- Running InFlightKv cluster
- Network access to the KV store REST API

## Integration with InFlightKv

This load test application is designed to work with the InFlightKv REST API endpoints:

- `PUT /kv/{key}`: Create/update key-value pairs
- `GET /kv/{key}`: Retrieve values by key
- `PATCH /kv/{key}`: Partially update values
- `DELETE /kv/{key}`: Remove key-value pairs
- `GET /kv`: List all keys (NDJSON format)
