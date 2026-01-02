# InFlightKv - High-Performance In-Memory Key-Value Store

InFlightKv is a high-performance, thread-safe, in-memory key-value store built with Java and Spring Boot. It features automatic partitioning, optimistic concurrency control, and support for JSON data types.

## 🚀 Features

- **High Performance**: In-memory storage with efficient partitioning
- **Thread Safety**: Single-threaded event loops per partition ensure consistency
- **Version Control**: Optimistic locking with automatic version increment
- **JSON Native**: Stores any valid JSON data with type safety
- **REST API**: Full RESTful API with WebFlux for reactive operations
- **Clustering**: Distributed architecture with automatic key routing
- **Async Operations**: Non-blocking operations using CompletableFuture
- **Comprehensive Testing**: Full integration tests with Cucumber BDD

## 📋 Requirements

- **Java**: JDK 17 or later (tested with JDK 21)
- **Spring Boot**: 3.4.x (compatible with Spring Boot 4.0.1)
- **Gradle**: 9.2.1
- **Operating System**: Linux, macOS, or Windows

## 🏗️ Architecture

InFlightKv uses a partitioned architecture with the following components:

- **Core Module** (`modules/core`): Low-level key-value storage with partitioning
- **Spring Web Module** (`modules/spring-web`): REST API with WebFlux and clustering
- **Load Test Module** (`modules/load-test`): Performance testing utilities
- **Test Docs Module** (`modules/test-docs`): Integration tests and API documentation

### Key Design Decisions

- **32 Fixed Partitions**: Keys are distributed across 32 partitions using hash-based routing
- **Single-Threaded Event Loops**: Each partition uses a dedicated thread for thread safety
- **JSON-First**: All values stored as JSON for type safety and interoperability
- **Optimistic Concurrency**: Version-based conflict detection and resolution

## 🚀 Quick Start

### 1. Clone and Build

```bash
git clone <repository-url>
cd InFlightKv
./gradlew build
```

### 2. Run Single Node

```bash
cd modules/spring-web
../../gradlew bootRun --args='--spring.profiles.active=single-node'
```

The API will be available at `http://localhost:8080`

### 3. Test Basic Operations

```bash
# Store a JSON object
curl -X PUT http://localhost:8080/kv/user \
  -H "Content-Type: application/json" \
  -d '{"name": "John", "age": 30}'

# Retrieve the value
curl http://localhost:8080/kv/user

# List all keys
curl http://localhost:8080/kv
```

## 📖 Documentation

### 📚 Design Documentation (`docs/`)

- **[KV Store Design](docs/kv-store-design.md)**: Comprehensive architecture documentation
- **[Event Loop vs Thread Pool](docs/event-loop-vs-threadpool.md)**: Deep dive into concurrency models and trade-offs
- **[API Usage Guide](docs/api-usage-guide.md)**: Practical guide for using the REST API
- **[Future Roadmap](docs/future-roadmap.md)**: Planned features including persistence, TTL, and Pub/Sub

### 🧪 API Documentation via Tests (`modules/test-docs`)

The `test-docs` module contains living documentation through Cucumber BDD tests that serve as both validation and comprehensive API documentation:

#### Feature Files:
- **[Core Key-Value Operations](modules/test-docs/src/test/resources/features/keyvaluecore/key_value_store.feature)**:
  - Basic set/get operations
  - Version control and optimistic locking
  - Error handling and limitations
  - Data types and storage behavior

- **[API Integration Tests](modules/test-docs/src/test/resources/integration-features/singleNodeTest/api-integration-tests.feature)**:
  - Complete REST API examples
  - JSON data handling
  - Version control workflows
  - Error scenarios and edge cases

#### Running API Documentation Tests:

```bash
# Start the application first
cd modules/spring-web
../../gradlew bootRun --args='--spring.profiles.active=single-node'

# In another terminal, run the tests
cd modules/test-docs
../../gradlew test --tests "*SingleNodeIntegrationTest*"
```

### 📖 Test Documentation

Each test scenario includes detailed documentation explaining:
- API behavior and usage patterns
- Request/response formats
- Error handling
- Best practices

## 🔧 Configuration

### Single Node Mode

Use `application-single-node.yaml` for development and testing:

```bash
cd modules/spring-web
../../gradlew bootRun --args='--spring.config.location=classpath:application-single-node.yaml'
```

### Docker Setup

See `docker/` directory for containerized deployment options.

## 🧪 Testing

### Unit Tests
```bash
./gradlew test
```

### Integration Tests
```bash
# Start application in single-node mode first
cd modules/spring-web
../../gradlew bootRun --args='--spring.profiles.active=single-node'

# Run integration tests
cd modules/test-docs
../../gradlew test --tests "*SingleNodeIntegrationTest*"
```

### Load Testing
```bash
cd modules/load-test
../../gradlew bootRun
```

## 📊 Performance Characteristics

- **32 Partitions**: Fixed partitioning provides predictable performance
- **Single-Threaded per Partition**: Eliminates race conditions, maximizes cache efficiency
- **Async Operations**: Non-blocking API with CompletableFuture
- **Memory Efficient**: JSON storage with minimal overhead
- **Low Latency**: In-memory operations with sub-millisecond response times

## 🔒 Thread Safety & Consistency

InFlightKv provides strong consistency guarantees through:
- **Partition Isolation**: Each partition operates independently
- **Single-Threaded Execution**: Event loops prevent race conditions
- **Version-Based Locking**: Optimistic concurrency control
- **Atomic Operations**: All mutations are serialized per partition

## 🚀 API Overview

### Endpoints

- `PUT /kv/{key}` - Store/update a value
- `GET /kv/{key}` - Retrieve a value
- `PATCH /kv/{key}` - Partial update with merging
- `DELETE /kv/{key}` - Remove a key
- `GET /kv` - List all keys (NDJSON format)

### Version Control

```bash
# Store initial value (version 0)
PUT /kv/user
{"name": "John"}

# Update with version check
PUT /kv/user?ifVersion=0
{"name": "John", "age": 30}

# Partial update (merge)
PATCH /kv/user?ifVersion=1
{"city": "New York"}
```

### Data Format

All values must be valid JSON strings:

```bash
# ✅ Correct
curl -X PUT http://localhost:8080/kv/greeting \
  -d '{"name": "John"}'

# ❌ Incorrect
curl -X PUT http://localhost:8080/kv/greeting \
  -d "Hello World"
```

## 🤝 Contributing

1. **Design Documentation**: All architecture decisions are documented in `docs/`
2. **Test-Driven**: New features require corresponding tests in `modules/test-docs`
3. **Code Standards**: Follow existing patterns for partitioning and concurrency
4. **API Compatibility**: Maintain backward compatibility for existing endpoints

## 📈 Limitations

- **In-Memory Only**: Data is lost on restart (by design for performance)
- **Fixed Partitioning**: 32 partitions cannot be reconfigured
- **No Persistence**: No disk persistence or replication
- **No TTL/Expiration**: Keys don't expire automatically
- **No Transactions**: Operations are independent, no rollback capability

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🆘 Support

For issues and questions:
1. Check the [API Usage Guide](docs/api-usage-guide.md)
2. Review the integration test scenarios in `modules/test-docs`
3. Examine the design documentation in `docs/`
4. Check application logs for error details

## 🔗 Links

- [API Usage Guide](docs/api-usage-guide.md)
- [Design Documentation](docs/kv-store-design.md)
- [Event Loop Architecture](docs/event-loop-vs-threadpool.md)
- [Future Roadmap](docs/future-roadmap.md)
- [Core Tests](modules/test-docs/src/test/resources/features/keyvaluecore/key_value_store.feature)
- [Integration Tests](modules/test-docs/src/test/resources/integration-features/singleNodeTest/api-integration-tests.feature)
