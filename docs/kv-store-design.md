# Key-Value Store Design Documentation

## Overview

This document describes the design and implementation of the in-memory key-value store system, focusing on the `KeyValueStore` and `KeyValuePartition` classes. The system provides thread-safe, high-performance storage with support for versioning and concurrency control.

## Current Implementation

### KeyValueStore Class

The `KeyValueStore` class serves as the main entry point for the key-value storage system. It implements a partitioned architecture with the following key characteristics:

- **Partitioning**: Uses 32 fixed partitions to distribute keys across multiple storage units
- **Routing**: Routes operations to partitions based on key hash using `(key.hashCode() & 0x7fffffff) % partitions.length`
- **Asynchronous Operations**: All operations return `CompletableFuture` for non-blocking execution
- **Aggregation**: Handles cross-partition operations like `totalKeys()` and `getAllKeys()` by aggregating results from all partitions

**Core Methods:**
- `get(DataKey key)`: Retrieves a value by key
- `set(DataKey key, RequestDataValue value, Long prevVersion)`: Stores/updates a value with optional version checking
- `remove(DataKey key)`: Deletes a value
- `containsKey(DataKey key)`: Checks key existence
- `totalKeys()`: Returns total number of keys across all partitions
- `getAllKeys()`: Returns all keys from all partitions

### KeyValuePartition Class

Each `KeyValuePartition` represents a single partition within the store, implementing thread-safe operations through an event loop pattern:

- **Event Loop**: Uses a single-threaded `ExecutorService` to serialize all operations
- **Storage**: `HashMap<DataKey, CachedDataValue>` for O(1) key lookups
- **Version Control**: Implements optimistic concurrency control with version numbers
- **Last Access Tracking**: Updates access timestamps on read operations
- **Atomic Operations**: All mutations are executed atomically within the event loop

**Concurrency Control Logic:**
- **INSERT**: New key-value pairs
- **UPDATE**: Existing key with version increment
- **SKIP**: No-op when data hasn't changed or conditional insert fails
- **VERSION_MISMATCH**: Concurrent modification detected

## Alternative Approaches

### Approach 1: Synchronized Global HashMap

**Implementation:**
```java
public class SynchronizedKeyValueStore {
    private final Map<DataKey, CachedDataValue> store = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public CachedDataValue get(DataKey key) {
        lock.lock();
        try {
            return store.get(key);
        } finally {
            lock.unlock();
        }
    }

    public CachedDataValue set(DataKey key, RequestDataValue value, Long prevVersion) {
        lock.lock();
        try {
            // Version checking and update logic
            return store.put(key, new CachedDataValue(value, version));
        } finally {
            lock.unlock();
        }
    }
}
```

**Pros:**
- Simple implementation with minimal code complexity
- Strong consistency guarantees through global locking
- Easy to reason about thread safety
- No partitioning complexity

**Cons:**
- Poor scalability: Single lock becomes bottleneck under high concurrency
- No parallelism: Only one operation can execute at a time
- Lock contention increases with more threads
- Not suitable for high-throughput scenarios

### Approach 2: ConcurrentHashMap with CAS Operations

**Implementation:**
```java
public class ConcurrentHashMapStore {
    private final ConcurrentHashMap<DataKey, CachedDataValue> store = new ConcurrentHashMap<>();

    public CachedDataValue get(DataKey key) {
        return store.get(key);
    }

    public CachedDataValue set(DataKey key, RequestDataValue value, Long prevVersion) {
        CachedDataValue newValue = new CachedDataValue(value, System.currentTimeMillis());

        if (prevVersion == null) {
            // Blind update
            CachedDataValue existing = store.put(key, newValue);
            return newValue;
        }

        // CAS-based version check
        CachedDataValue existing = store.get(key);
        if (existing == null && prevVersion == -1) {
            store.putIfAbsent(key, newValue);
            return newValue;
        }

        if (existing != null && existing.version() == prevVersion) {
            store.replace(key, existing, newValue);
            return newValue;
        }

        throw new ConcurrentUpdateException();
    }
}
```

**Pros:**
- High concurrency: Multiple operations can execute simultaneously
- Lock-free reads: No blocking on read operations
- Better scalability than global locking
- Built-in Java concurrent utilities

**Cons:**
- Complex version control: CAS operations can be tricky to implement correctly
- Potential ABA problems: Version numbers help but add complexity
- Memory overhead: ConcurrentHashMap has higher memory footprint
- Limited atomicity: Complex multi-key operations require external synchronization

### Approach 3: Actor-Based Architecture (Akka/Vert.x Style)

**Implementation:**
```java
public class ActorBasedStore {
    private final Map<Integer, ActorRef> partitions = new HashMap<>();

    public ActorBasedStore() {
        for (int i = 0; i < 32; i++) {
            partitions.put(i, actorSystem.actorOf(KeyValueActor.props(i)));
        }
    }

    public CompletableFuture<CachedDataValue> get(DataKey key) {
        int partitionId = getPartition(key);
        return ask(partitions.get(partitionId), new GetMessage(key));
    }

    // KeyValueActor handles messages serially
    static class KeyValueActor extends AbstractActor {
        private final Map<DataKey, CachedDataValue> store = new HashMap<>();

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                .match(GetMessage.class, msg -> {
                    sender().tell(store.get(msg.key), self());
                })
                .match(SetMessage.class, msg -> {
                    // Version checking logic
                    CachedDataValue result = performSet(msg);
                    sender().tell(result, self());
                })
                .build();
        }
    }
}
```

**Pros:**
- Natural concurrency model: Actors handle one message at a time
- Fault tolerance: Actor supervision can handle failures gracefully
- Location transparency: Easy to distribute across nodes
- Composable: Actors can be combined for complex workflows

**Cons:**
- High complexity: Actor frameworks add significant overhead
- Learning curve: Requires understanding actor model concepts
- Resource intensive: Each actor has overhead (threads, memory)
- Debugging difficulty: Asynchronous message passing complicates debugging

## Why Current Implementation is Better

The current implementation strikes an excellent balance between simplicity, performance, and thread safety by combining partitioning with single-threaded event loops. Here's why it outperforms the alternatives:

### Key Advantages

1. **Optimal Concurrency**: 32 independent partitions allow for true parallel execution across CPU cores, far better than the single-lock bottleneck of Approach 1.

2. **Thread Safety Without Complexity**: Single-threaded event loops per partition eliminate race conditions without the complexity of CAS operations (Approach 2) or actor frameworks (Approach 3).

3. **Memory Efficiency**: Simple HashMap storage without the overhead of ConcurrentHashMap's internal structures or actor system memory costs.

4. **Version Control Simplicity**: Event loop serialization makes version checking and atomic operations straightforward to implement and reason about.

5. **Performance Predictability**: Deterministic single-threaded execution per partition provides consistent latency without lock contention spikes.

### Tradeoffs and Limitations

**Scalability Ceiling**: Fixed at 32 partitions limits horizontal scaling within a single JVM. Adding more partitions increases memory overhead.

**Cross-Partition Operations**: Aggregating operations like `totalKeys()` and `getAllKeys()` require coordination across all partitions, creating potential bottlenecks for frequent global queries.

**Thread Pool Overhead**: Each partition maintains its own single-threaded executor, consuming more threads than a shared thread pool approach would.

**Memory Fragmentation**: Data distribution across 32 separate HashMaps can lead to less efficient memory usage compared to a single large ConcurrentHashMap.

**No Dynamic Scaling**: Fixed partition count doesn't adapt to workload changes or allow runtime reconfiguration.

Despite these tradeoffs, the current implementation provides excellent performance for most in-memory key-value store use cases, with clear thread safety guarantees and predictable behavior. The simplicity of the design makes it maintainable and reliable for production use.
