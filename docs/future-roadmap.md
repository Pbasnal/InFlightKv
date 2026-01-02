# Future Roadmap

This document outlines the planned features and enhancements for InFlightKv. The roadmap focuses on production readiness, advanced capabilities, and ecosystem integration while maintaining the core design principles of simplicity, performance, and thread safety.

## 🏗️ Foundation Features: Essential KV Store Capabilities

Before advancing to advanced features, InFlightKv requires several foundational capabilities that are essential for any production key-value store. These features address core functionality gaps and operational requirements.

### 1. Eviction Policies & Memory Management

**Goal**: Implement configurable eviction strategies to manage memory usage and prevent unbounded growth.

#### Required Policies
- **LRU (Least Recently Used)**: Evict least recently accessed items
- **LFU (Least Frequently Used)**: Evict least frequently accessed items
- **TTL-based Eviction**: Automatic expiration (complements TTL feature)
- **Size-based Eviction**: Evict largest items when approaching memory limits
- **Random Eviction**: Simple fallback policy

#### Implementation Design
```java
interface EvictionPolicy {
    void onAccess(DataKey key, CachedDataValue value);
    DataKey selectVictim(Map<DataKey, CachedDataValue> candidates);
    void onEviction(DataKey key, CachedDataValue value);
}

class LRUEvictionPolicy implements EvictionPolicy {
    private final LinkedHashMap<DataKey, Long> accessOrder;

    @Override
    public DataKey selectVictim(Map<DataKey, CachedDataValue> candidates) {
        // Return least recently used key
        return accessOrder.entrySet().stream()
            .filter(entry -> candidates.containsKey(entry.getKey()))
            .min(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }
}
```

#### Configuration
```yaml
eviction:
  policy: "LRU"  # LRU, LFU, TTL, SIZE, RANDOM
  maxMemory: "1GB"
  targetMemory: "800MB"  # Start eviction when reached
  checkInterval: "30s"
```

### 2. Data Compression

**Goal**: Reduce memory footprint and storage requirements through intelligent compression.

#### Compression Strategies
- **Key Compression**: Dictionary-based compression for repetitive key patterns
- **Value Compression**: Adaptive compression based on data type and size
- **Batch Compression**: Compress multiple small values together
- **LZ4/Snappy**: Fast compression for real-time operations
- **Zstandard**: High-compression ratio for persistence

#### Implementation Design
```java
interface Compressor {
    byte[] compress(byte[] data);
    byte[] decompress(byte[] compressedData);
    String getAlgorithm();
}

class AdaptiveCompressor implements Compressor {
    private final LZ4Compressor lz4 = new LZ4Compressor();
    private final ZstdCompressor zstd = new ZstdCompressor();

    @Override
    public byte[] compress(byte[] data) {
        // Choose compression based on data size and type
        if (data.length < 1024) {
            return lz4.compress(data); // Fast for small data
        } else {
            return zstd.compress(data); // Better ratio for large data
        }
    }
}
```

#### Performance Impact
- **Memory Savings**: 30-70% reduction depending on data patterns
- **CPU Overhead**: 5-15% increase in read/write operations
- **Configurable**: Can be enabled/disabled per deployment

### 3. Key and Value Validation

**Goal**: Ensure data integrity through configurable validation rules.

#### Key Validation
- **Length Limits**: Maximum key length constraints
- **Character Restrictions**: Allowed character sets
- **Naming Conventions**: Pattern-based validation
- **Reserved Keywords**: Prevent conflicts with system keys

#### Value Validation
- **Size Limits**: Maximum value size constraints
- **Type Validation**: JSON schema validation for structured data
- **Content Filtering**: Custom validation rules
- **Data Integrity**: Checksum validation

#### Implementation Design
```java
interface Validator<T> {
    ValidationResult validate(T input);
    String getName();
}

class KeyValidator implements Validator<String> {
    private final int maxLength;
    private final Pattern allowedPattern;

    @Override
    public ValidationResult validate(String key) {
        if (key == null || key.isEmpty()) {
            return ValidationResult.failure("Key cannot be null or empty");
        }
        if (key.length() > maxLength) {
            return ValidationResult.failure("Key exceeds maximum length: " + maxLength);
        }
        if (!allowedPattern.matcher(key).matches()) {
            return ValidationResult.failure("Key contains invalid characters");
        }
        return ValidationResult.success();
    }
}
```

#### Configuration
```yaml
validation:
  key:
    maxLength: 1024
    pattern: "^[a-zA-Z0-9._-]+$"
    reservedPrefixes: ["system.", "_internal."]
  value:
    maxSize: "10MB"
    jsonSchemaValidation: true
    checksumVerification: true
```

### 4. Comprehensive Logging & Metrics

**Goal**: Provide observability and monitoring capabilities for operations and performance.

#### Structured Logging
- **Request Logging**: All API requests with timing and metadata
- **Error Logging**: Detailed error information with context
- **Audit Logging**: Security-relevant operations
- **Performance Logging**: Slow operation detection

#### Metrics Collection
```java
class KeyValueStoreMetrics {
    // Throughput metrics
    Counter requestsTotal;
    Counter operationsTotal;

    // Latency metrics
    Histogram operationDuration;
    Histogram queueWaitTime;

    // Resource metrics
    Gauge memoryUsage;
    Gauge activeConnections;

    // Error metrics
    Counter errorsTotal;
    Counter validationErrors;

    // Business metrics
    Gauge totalKeys;
    Histogram keySizeDistribution;
    Histogram valueSizeDistribution;
}
```

#### Integration Points
- **Prometheus**: Metrics endpoint for monitoring
- **OpenTelemetry**: Distributed tracing support
- **ELK Stack**: Log aggregation and analysis
- **Health Checks**: Kubernetes-ready status endpoints

### 5. Performance Benchmarking Framework

**Goal**: Establish performance baselines and continuous monitoring.

#### Benchmark Suite
- **Read/Write Benchmarks**: Single operations and batch operations
- **Concurrency Benchmarks**: Multi-threaded performance testing
- **Memory Benchmarks**: Memory usage under various loads
- **Persistence Benchmarks**: Recovery time and durability testing

#### Implementation
```java
class BenchmarkRunner {
    public BenchmarkResult runReadWriteBenchmark(BenchmarkConfig config) {
        // Warmup phase
        warmup(config);

        // Test phase
        List<OperationResult> results = executeOperations(config);

        // Analysis phase
        return analyzeResults(results);
    }

    public BenchmarkResult runConcurrencyBenchmark(BenchmarkConfig config) {
        ExecutorService executor = Executors.newFixedThreadPool(config.getConcurrency());

        // Submit concurrent operations
        List<CompletableFuture<OperationResult>> futures = submitConcurrentOperations(executor, config);

        // Wait for completion and analyze
        return analyzeConcurrentResults(futures);
    }
}
```

#### Key Metrics
- **P50/P95/P99 Latencies**: Response time percentiles
- **Throughput**: Operations per second
- **Memory Usage**: Peak and average memory consumption
- **CPU Utilization**: Core usage patterns
- **Error Rates**: Failure percentages under load

### 6. Proper Event Loop Architecture Migration

**Goal**: Replace single-threaded executors with true event loops for maximum performance.

#### Current Limitations
- Single-threaded executors are not true event loops
- Limited scalability beyond 32 partitions
- Blocking operations can stall partitions
- No efficient I/O multiplexing

#### True Event Loop Design
```java
class EventLoopPartition implements Runnable {
    private final Selector selector;
    private final Queue<Operation> operationQueue;
    private final Map<DataKey, CachedDataValue> store;

    @Override
    public void run() {
        while (running) {
            // Phase 1: Handle queued operations
            processQueuedOperations();

            // Phase 2: Poll for I/O events (if any)
            int readyChannels = selector.select(10); // 10ms timeout
            if (readyChannels > 0) {
                processIOEvents();
            }

            // Phase 3: Handle timers (TTL, metrics)
            processExpiredTimers();

            // Phase 4: Yield if no work
            if (operationQueue.isEmpty() && readyChannels == 0) {
                Thread.yield();
            }
        }
    }
}
```

#### Benefits
- **True Concurrency**: Handle thousands of concurrent operations
- **I/O Efficiency**: Non-blocking I/O with efficient polling
- **Scalability**: Support for many more partitions
- **Timer Integration**: Built-in timer support for TTL
- **Resource Efficiency**: Single thread handles multiple concerns

#### Migration Strategy
1. **Phase 1**: Implement basic event loop for one partition
2. **Phase 2**: Add I/O multiplexing capabilities
3. **Phase 3**: Migrate all partitions to event loops
4. **Phase 4**: Add advanced features (timers, signals)

#### Performance Gains
- **Throughput**: 5-10x improvement for I/O bound workloads
- **Latency**: Reduced jitter and more predictable response times
- **Scalability**: Support for 100+ partitions
- **Efficiency**: Better CPU cache utilization

---

## 🎯 Phase 1: Production Readiness (Q1-Q2 2025)

### 1. Persistence for Durability

**Goal**: Enable InFlightKv to survive process restarts and system failures while maintaining high performance.

#### Current State
- In-memory only storage
- Data lost on application restart
- No durability guarantees

#### High-Level Design

##### Write-Ahead Logging (WAL)
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   KeyValueStore │────│  WAL Manager     │────│   WAL Files     │
│                 │    │                  │    │   (Sequential)  │
│  Operations ──► │    │ - Log all writes │    │ - Append-only   │
│                 │    │ - Async writes   │    │ - Checksummed   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

- **WAL Manager**: Dedicated component that asynchronously writes all mutations to sequential log files
- **Log Format**: Binary format with operation type, key, value, version, and checksum
- **Background Process**: Separate thread handles WAL writes to avoid blocking main operations
- **File Rotation**: Automatic log rotation based on size/time with compression for older logs

##### Snapshot System
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   KeyValueStore │────│ Snapshot Manager │────│ Snapshot Files  │
│                 │    │                  │    │   (Compressed)  │
│  Periodic ─────►│    │ - Point-in-time │    │ - Background     │
│  Snapshots      │    │ - Copy-on-write │    │ - Recovery base  │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

- **Snapshot Manager**: Creates periodic snapshots of the entire dataset
- **Copy-on-Write**: Efficient snapshots using shared memory segments
- **Compression**: LZ4 compression for storage efficiency
- **Incremental**: Only changed data since last snapshot

##### Recovery Process
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   WAL Files     │────│  Recovery Mgr   │────│   KeyValueStore │
│   (Chronological)│    │                  │    │   (Restored)   │
│ - Load latest   │    │ - Replay WAL     │    │ - Ready for ops│
│   snapshot      │    │ - Validate ops   │    └─────────────────┘
└─────────────────┘    └──────────────────┘
                      │ - Rebuild state  │
                      └──────────────────┘
```

- **Startup Sequence**:
  1. Load latest snapshot into memory
  2. Replay WAL entries since snapshot
  3. Validate data integrity
  4. Mark store as ready


##### Implementation Strategy
1. **Phase 1**: WAL-only persistence (immediate durability)
2. **Phase 2**: Add snapshot system (faster recovery)
3. **Phase 3**: Optimize with compression and indexing
4. **Phase 4**: Add backup/restore APIs

##### Performance Impact
- **Write Latency**: +10-20% with async WAL
- **Recovery Time**: Proportional to WAL size since last snapshot
- **Storage Overhead**: 2-3x data size (WAL + compressed snapshots)
- **Memory Usage**: Minimal additional overhead

##### Migration Path
- **Backward Compatible**: Existing in-memory mode remains default
- **Gradual Rollout**: Can enable persistence per deployment
- **Zero Downtime**: WAL replay works with running system

---

## 🎯 Phase 2: Enhanced User Experience (Q3-Q4 2025)

### 2. TTL (Time To Live) Support

**Goal**: Allow keys to automatically expire, reducing manual cleanup burden and enabling cache-like behavior.

#### Current State
- Keys persist indefinitely
- Manual deletion required
- No automatic cleanup

#### High-Level Design

##### TTL Metadata Extension
```java
class CachedDataValue {
    private byte[] data;
    private String dataType;
    private long lastAccessTime;
    private long version;
}
```

##### Expiration Tracking
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   KeyValueStore │────│  Expiration Mgr  │────│ Priority Queue  │
│                 │    │                  │    │   (By Time)     │
│  TTL Operations │    │ - Track expiries│    │ - O(log n) ops  │
│  ──────────────►│    │ - Background     │    │ - Efficient     │
└─────────────────┘    │   cleanup        │    └─────────────────┘
                      └──────────────────┘
```

- **Expiration Manager**: Background service that periodically cleans up expired keys
- **Priority Queue**: Min-heap ordered by expiry time for efficient next-expiry lookup
- **Partition-Level**: Each partition manages its own expirations independently

##### API Extensions
```bash
# Set with TTL (seconds)
PUT /kv/session?ttl=3600
{"userId": "123", "data": "..."}

# Set with TTL (milliseconds)
PUT /kv/cache?ttlMs=30000
{"result": "computation"}

# Update TTL
PATCH /kv/session?extendTtl=1800
{"lastActivity": "2025-01-01T12:00:00Z"}

# Get remaining TTL
GET /kv/session?withTtl=true
# Response includes: {"ttl": 3547, "data": {...}}
```

##### Cleanup Strategy
- **Lazy Cleanup**: Remove expired keys during access operations
- **Background Cleanup**: Dedicated thread periodically scans for expired keys
- **Hybrid Approach**: Combine both for optimal performance

##### Performance Considerations
- **Memory Overhead**: ~8 bytes per key with TTL
- **Cleanup Frequency**: Configurable background scan interval
- **Access Patterns**: Lazy cleanup adds small overhead to reads

## 📊 Implementation Priorities

### Immediate (Next 3 Months)
- [ ] WAL-based persistence
- [ ] Basic snapshot system
- [ ] TTL metadata support

### Medium-term (3-6 Months)
- [ ] Optimized WAL with compression
- [ ] Background TTL cleanup
- [ ] Event publishing system

### Long-term (6-12 Months)
- [ ] Advanced Pub/Sub with WebSocket support
- [ ] Clustering improvements
- [ ] Metrics and monitoring
- [ ] Backup/restore APIs

### Success Metrics
- **Persistence**: <5 second recovery time, <10% write latency impact
- **TTL**: <1% cleanup overhead, configurable precision
- **Pub/Sub**: <100ms event delivery, support for 1000+ subscribers

## 🤝 Community & Ecosystem

### Integration Libraries
- **Java Client**: Optimized async client library
- **Spring Boot Starter**: Auto-configuration for Spring applications
- **Kubernetes Operator**: Automated deployment and scaling
- **CLI Tool**: Command-line interface for administration

### Monitoring & Observability
- **Metrics**: Prometheus-compatible metrics endpoint
- **Distributed Tracing**: Integration with OpenTelemetry
- **Health Checks**: Kubernetes-ready health endpoints
- **Log Aggregation**: Structured logging with correlation IDs

---

*This roadmap represents current planning and may evolve based on technical constraints*
