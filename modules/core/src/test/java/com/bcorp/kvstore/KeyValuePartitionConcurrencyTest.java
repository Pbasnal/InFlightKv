package com.bcorp.kvstore;

import com.bcorp.exceptions.ConcurrentUpdateException;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.DataValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;

import static com.bcorp.testutils.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class KeyValuePartitionConcurrencyTest {

    private KeyValuePartition partition;

    @BeforeEach
    void setUp() {
        partition = new KeyValuePartition(0);
    }

    @Test
    @Timeout(30)
    void shouldHandleConcurrentReadsAndWritesOnIndependentKeys() throws InterruptedException, ExecutionException, TimeoutException {
        int numThreads = 10;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        try {
            // Submit concurrent operations
            // Wait for all operations to complete
            CompletableFuture.allOf(runInFutures(numThreads,
                    operationsPerThread,
                    (threadId, numOperations) -> {
                        String keyPrefix = "key-" + threadId;

                        for (int op = 0; op < numOperations; op++) {
                            DataKey key = DataKey.from(keyPrefix);
                            DataValue value = DataValue.fromString("value-" + threadId + "-" + op);

                            // Alternate between different operations
                            switch (op % 3) {
                                case 0 ->
                                        assertDoesNotThrow(() -> partition.set(key, value, null).get(2, TimeUnit.SECONDS));
                                case 1 -> {
                                    // GET operation
                                    DataValue retrieved = assertDoesNotThrow(() -> partition.get(key).get(2, TimeUnit.SECONDS));
                                    if (retrieved != null) {
                                        assertTrue(new String(retrieved.data(), StandardCharsets.UTF_8).startsWith("value-" + threadId));
                                    }
                                }
                                case 2 -> {
                                    // CONTAINS operation
                                    boolean exists = assertDoesNotThrow(() -> partition.containsKey(key).get(2, TimeUnit.SECONDS));
                                    // Key should exist after first set operation
                                    assertTrue(exists, "Key should exist after initial set");
                                }
                            }

                            // Small delay to increase chance of interleaving
                            assertDoesNotThrow(() -> Thread.sleep(1));
                        }

                        // Final operation: set a predictable final value
                        DataKey finalKey = DataKey.from("key-" + threadId);
                        DataValue finalValue = DataValue.fromString("final-value-" + threadId);
                        assertDoesNotThrow(() -> partition.set(finalKey, finalValue, null).get(2, TimeUnit.SECONDS));
                    },
                    executor)).join();

            // Verify final state
            assertEquals(numThreads, partition.totalKeys());

            // Verify all keys have expected values
            for (int i = 0; i < numThreads; i++) {
                DataKey key = DataKey.from("key-" + i);
                DataValue value = partition.get(key).get(5, TimeUnit.SECONDS);
                assertNotNull(value, "Key " + i + " should exist");
                assertEquals("final-value-" + i, new String(value.data(), StandardCharsets.UTF_8));

                assertEquals(17L, value.version(), "Key " + i + " should have been updated multiple times");
            }

        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    @Timeout(20)
    void shouldHandleConcurrentOperationsOnSameKeyWithVersioning() throws InterruptedException, ExecutionException, TimeoutException {
        int numThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        DataKey sharedKey = DataKey.from("shared-key");
        AtomicInteger successfulUpdates = new AtomicInteger(0);

        try {
            // Initialize the key
            DataValue initialValue = DataValue.fromString("initial");
            partition.set(sharedKey, initialValue, null).get();

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Each thread tries to update the same key multiple times
            for (int threadId = 0; threadId < numThreads; threadId++) {
                final int tId = threadId;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < 10; i++) {
                        // Try to update with current version
                        DataValue current = assertDoesNotThrow(() -> partition.get(sharedKey).get(2, TimeUnit.SECONDS));
                        if (current == null) continue;

                        DataValue newValue = DataValue.fromString("update-" + tId + "-" + i);
                        assertIfThrows(ConcurrentUpdateException.class, () -> {
                            partition.set(sharedKey, newValue, current.version()).get(2, TimeUnit.SECONDS);
                            successfulUpdates.incrementAndGet();
                        });
                    }
                }, executor);
                futures.add(future);
            }

            // Wait for all operations to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

            // Verify final state
            DataValue finalValue = partition.get(sharedKey).get(5, TimeUnit.SECONDS);
            assertNotNull(finalValue, "Shared key should still exist");
            assertTrue(successfulUpdates.get() > 0, "At least one update should have succeeded");
            assertEquals(1, partition.totalKeys(), "Should still have only one key");

        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    @Timeout(15)
    void shouldHandleConcurrentRemovalsAndInserts() throws InterruptedException, ExecutionException {
        int numThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        try {
            CompletableFuture<Void>[] futures = runInFutures(numThreads, 0,
                    (tId, ops) -> {
                        DataKey key = DataKey.from("test-key-" + tId);

                        // Each thread performs a cycle: insert -> remove -> insert
                        for (int cycle = 0; cycle < 3; cycle++) {
                            // Insert
                            DataValue value = DataValue.fromString("value-" + tId + "-" + cycle);
                            assertDoesNotThrow(() -> partition.set(key, value, null).get(2, TimeUnit.SECONDS));

                            // Verify it exists
                            boolean existsAfterInsert = assertDoesNotThrow(() -> partition.containsKey(key).get(2, TimeUnit.SECONDS));
                            assertTrue(existsAfterInsert, "Key should exist after insert in thread " + tId);

                            // Small random delay
                            assertDoesNotThrow(() -> Thread.sleep(ThreadLocalRandom.current().nextInt(5) + 1));

                            // Remove
                            DataValue removed = assertDoesNotThrow(() -> partition.remove(key).get(2, TimeUnit.SECONDS));
                            assertNotNull(removed, "Should return removed value in thread " + tId);

                            // Verify it doesn't exist
                            boolean existsAfterRemove = assertDoesNotThrow(() -> partition.containsKey(key).get(2, TimeUnit.SECONDS));
                            assertFalse(existsAfterRemove, "Key should not exist after remove in thread " + tId);

                            // Small random delay
                            assertDoesNotThrow(() -> Thread.sleep(ThreadLocalRandom.current().nextInt(5) + 1));
                        }
                    }, executor);

            // Wait for all operations to complete
            CompletableFuture.allOf(futures).get();

            // Verify final state - all keys should be removed
            assertEquals(0, partition.totalKeys(), "All keys should be removed in the end");

        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    @Timeout(25)
    void shouldHandleHighFrequencyConcurrentOperations() throws InterruptedException, ExecutionException, TimeoutException {
        int numThreads = 15;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        Collection<String> keys = Collections.synchronizedCollection(new HashSet<>());

        try {
            CompletableFuture<Void>[] futures = runInFutures(numThreads, operationsPerThread,
                    (tId, ops) -> {
                        for (int op = 0; op < operationsPerThread; op++) {
                            DataKey key = DataKey.from("freq-key-" + (tId * operationsPerThread + op) % 50); // Reuse 50 keys

                            // High-frequency operations without delays
                            DataValue value = DataValue.fromString("freq-" + tId + "-" + op);
                            waitFuture(partition.set(key, value, null));
                            keys.add(key.key());
                            if (op % 2 == 0) {
                                // SET operation
                                waitFuture(partition.get(key));
                            }

                        }
                    }, executor);

            // Wait for all operations to complete
            CompletableFuture.allOf(futures).get();

            // Verify final state - should have 50 keys (the reused ones)
            assertEquals(50, partition.totalKeys(), "Should have 50 keys after high-frequency operations");

            // Verify all keys exist and have reasonable versions
            for (int i = 0; i < 50; i++) {
                DataKey key = DataKey.from("freq-key-" + i);
                DataValue value = partition.get(key).get(5, TimeUnit.SECONDS);
                assertNotNull(value, "Key " + i + " should exist");
                assertTrue(value.version() >= 0, "Key " + i + " should have valid version");
            }

        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    @Timeout(10)
    void shouldHandleConcurrentReadOperationsOnly() throws InterruptedException, ExecutionException, TimeoutException {
        // Setup: Pre-populate with test data
        int numKeys = 20;
        for (int i = 0; i < numKeys; i++) {
            DataKey key = DataKey.from("readonly-key-" + i);
            DataValue value = new DataValue(
                    ("readonly-value-" + i).getBytes(StandardCharsets.UTF_8),
                    String.class,
                    System.currentTimeMillis(),
                    0L
            );
            partition.set(key, value, null).get();
        }

        int numReaderThreads = 10;
        int readsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numReaderThreads);

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int threadId = 0; threadId < numReaderThreads; threadId++) {
                final int tId = threadId;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        for (int read = 0; read < readsPerThread; read++) {
                            // Read random keys
                            int keyIndex = ThreadLocalRandom.current().nextInt(numKeys);
                            DataKey key = DataKey.from("readonly-key-" + keyIndex);

                            DataValue value = partition.get(key).get(2, TimeUnit.SECONDS);
                            assertNotNull(value, "Read-only key should exist in thread " + tId);
                            assertEquals("readonly-value-" + keyIndex,
                                    new String(value.data(), StandardCharsets.UTF_8),
                                    "Value should be correct in thread " + tId);

                            // Also test containsKey
                            boolean exists = partition.containsKey(key).get(2, TimeUnit.SECONDS);
                            assertTrue(exists, "Key should exist in thread " + tId);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Reader thread " + tId + " failed", e);
                    }
                }, executor);
                futures.add(future);
            }

            // Wait for all read operations to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

            // Verify data integrity - all original keys should still exist
            assertEquals(numKeys, partition.totalKeys(), "All read-only keys should still exist");

            // Verify no keys were corrupted
            for (int i = 0; i < numKeys; i++) {
                DataKey key = DataKey.from("readonly-key-" + i);
                DataValue value = partition.get(key).get(5, TimeUnit.SECONDS);
                assertNotNull(value, "Key " + i + " should still exist");
                assertEquals("readonly-value-" + i, new String(value.data(), StandardCharsets.UTF_8));
                assertEquals(0L, value.version(), "Read-only keys should maintain original version");
            }

        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    @Timeout(30)
    void shouldHandleMixedReadWriteWorkload() throws InterruptedException, ExecutionException, TimeoutException {
        int numWriterThreads = 5;
        int numReaderThreads = 8;
        int operationsPerThread = 30;
        ExecutorService executor = Executors.newFixedThreadPool(numWriterThreads + numReaderThreads);

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Writer threads
            for (int threadId = 0; threadId < numWriterThreads; threadId++) {
                final int tId = threadId;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        for (int op = 0; op < operationsPerThread; op++) {
                            DataKey key = DataKey.from("mixed-key-" + (tId + op) % 10); // 10 shared keys

                            DataValue value = new DataValue(
                                    ("mixed-write-" + tId + "-" + op).getBytes(StandardCharsets.UTF_8),
                                    String.class,
                                    System.currentTimeMillis(),
                                    0L
                            );

                            partition.set(key, value, null).get(2, TimeUnit.SECONDS);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Writer thread " + tId + " failed", e);
                    }
                }, executor);
                futures.add(future);
            }

            // Reader threads
            for (int threadId = 0; threadId < numReaderThreads; threadId++) {
                final int tId = threadId;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        for (int op = 0; op < operationsPerThread; op++) {
                            DataKey key = DataKey.from("mixed-key-" + op % 10); // Read from same 10 keys

                            DataValue value = partition.get(key).get(2, TimeUnit.SECONDS);
                            if (value != null) {
                                assertTrue(new String(value.data(), StandardCharsets.UTF_8).startsWith("mixed-write-"),
                                        "Reader thread " + tId + " should see writer data");
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Reader thread " + tId + " failed", e);
                    }
                }, executor);
                futures.add(future);
            }

            // Wait for all operations to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

            // Verify final state
            assertEquals(10, partition.totalKeys(), "Should have 10 keys after mixed workload");

            // All keys should exist and have been updated
            for (int i = 0; i < 10; i++) {
                DataKey key = DataKey.from("mixed-key-" + i);
                DataValue value = partition.get(key).get(5, TimeUnit.SECONDS);
                assertNotNull(value, "Key " + i + " should exist");
                assertTrue(value.version() >= 0, "Key " + i + " should have been updated");
            }

        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
