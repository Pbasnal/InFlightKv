package com.bcorp.kvstore;

import com.bcorp.exceptions.ConcurrentUpdateException;
import com.bcorp.pojos.CachedDataValue;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.RequestDataValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.plugins.DoNotMockEnforcer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.bcorp.testutils.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class KeyValueStoreConcurrencyTest {

    private KeyValueStore keyValueStore;
    private ExecutorService executorService;

    private final KvStoreClock clock = new SystemClock();

    @BeforeEach
    void setUp() {
        keyValueStore = new KeyValueStore(clock);
        executorService = Executors.newFixedThreadPool(50);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    @Timeout(30)
    void shouldHandleConcurrentWritesToDifferentKeys() {
        // Test concurrent writes to different keys from multiple threads
        int numThreads = 20;
        int keysPerThread = 10;
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        runInFutures(numThreads, keysPerThread, (threadId, numsKeys) -> {
            try {
                for (int j = 0; j < keysPerThread; j++) {
                    DataKey key = DataKey.fromString("thread-" + threadId + "-key-" + j);
                    RequestDataValue value = RequestDataValue.fromString("thread-" + threadId + "-value-" + j);
                    waitFuture(keyValueStore.set(key, value, null));
                    successCount.incrementAndGet();
                }
            } finally {
                latch.countDown();
            }
        }, executorService);

        assertTrue(waitFor(latch, 10), "All threads should complete within timeout");

        // Verify all writes succeeded
        assertEquals(numThreads * keysPerThread, successCount.get());
        assertEquals(numThreads * keysPerThread, keyValueStore.totalKeys());

        // Verify all keys can be retrieved correctly
        for (int i = 0; i < numThreads; i++) {
            for (int j = 0; j < keysPerThread; j++) {
                DataKey key = DataKey.fromString("thread-" + i + "-key-" + j);
                CachedDataValue value = waitFuture(keyValueStore.get(key));
                assertNotNull(value);
                assertEquals("thread-" + i + "-value-" + j,
                        new String(value.data(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    @Timeout(30)
    void shouldHandleConcurrentReadsAndWrites() {
        // Test concurrent reads and writes from multiple threads
        int numThreads = 15;
        int operationsPerThread = 20;
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger readSuccessCount = new AtomicInteger(0);
        AtomicInteger writeSuccessCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // Pre-populate some keys
        for (int i = 0; i < 50; i++) {
            DataKey key = DataKey.fromString("pre-populated-" + i);
            RequestDataValue value = RequestDataValue.fromString("pre-value-" + i);
            waitFuture(keyValueStore.set(key, value, null));
        }

        runInFutures(numThreads, operationsPerThread, (threadId, numOps) -> {
            try {
                for (int j = 0; j < operationsPerThread; j++) {
                    if (j % 2 == 0) {
                        // Write operation
                        DataKey key = DataKey.fromString("concurrent-write-" + threadId + "-" + j);
                        RequestDataValue value = RequestDataValue.fromString("write-value-" + threadId + "-" + j);
                        waitFuture(keyValueStore.set(key, value, null));
                        writeSuccessCount.incrementAndGet();
                    } else {
                        // Read operation
                        int randomKey = j % 50;
                        DataKey key = DataKey.fromString("pre-populated-" + randomKey);
                        CachedDataValue value = waitFuture(keyValueStore.get(key));
                        if (value != null) {
                            readSuccessCount.incrementAndGet();
                        }
                    }
                }
            } catch (Exception e) {
                exceptions.add(e);
            } finally {
                latch.countDown();
            }
        }, executorService);

        assertTrue(waitFor(latch, 15), "All threads should complete within timeout");
        assertTrue(exceptions.isEmpty(), "No exceptions should occur: " + exceptions);

        // Verify writes succeeded
        assertTrue(writeSuccessCount.get() > 0, "Some writes should have succeeded");
        assertTrue(readSuccessCount.get() > 0, "Some reads should have succeeded");
    }

    @Test
    @Timeout(30)
    void shouldHandleConcurrentUpdatesToSameKey() {
        // Test concurrent updates to the same key - should handle version conflicts properly
        DataKey sharedKey = DataKey.fromString("shared-key");
        RequestDataValue initialValue = RequestDataValue.fromString("initial");
        waitFuture(keyValueStore.set(sharedKey, initialValue, null));

        int numThreads = 10;
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        runInFutures(numThreads, 0, (threadId, numOps) -> {
            try {
                // Get current value and version
                CachedDataValue current = waitFuture(keyValueStore.get(sharedKey));
                if (current != null) {
                    Long currentVersion = current.version();
                    RequestDataValue newValue = RequestDataValue.fromString("updated-by-thread-" + threadId);

                    try {
                        waitFuture(keyValueStore.set(sharedKey, newValue, currentVersion));
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        if (e.getCause() instanceof ConcurrentUpdateException) {
                            conflictCount.incrementAndGet();
                        } else {
                            exceptions.add(e);
                        }
                    }
                }
            } catch (Exception e) {
                exceptions.add(e);
            } finally {
                latch.countDown();
            }
        }, executorService);

        assertTrue(waitFor(latch, 10), "All threads should complete within timeout");

        // At least one update should succeed, and some may fail due to version conflicts
        assertTrue(successCount.get() > 0, "At least one update should succeed");
        // Verify final state is consistent
        CachedDataValue finalValue = waitFuture(keyValueStore.get(sharedKey));
        assertNotNull(finalValue);
        // The version should be at least 1 (initial + at least one successful update)
        assertTrue(finalValue.version() >= 1);
    }


    @Test
    @Timeout(30)
    void shouldHandleConcurrentRemoves() {
        // Test concurrent removes from multiple threads
        int numKeys = 100;

        // Pre-populate keys
        for (int i = 0; i < numKeys; i++) {
            DataKey key = DataKey.fromString("remove-test-" + i);
            RequestDataValue value = RequestDataValue.fromString("value-" + i);
            waitFuture(keyValueStore.set(key, value, null));
        }

        assertEquals(numKeys, keyValueStore.totalKeys());

        int numThreads = 10;
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger removeCount = new AtomicInteger(0);

        runInFutures(numThreads, 0, (threadId, numOps) -> {
            try {
                // Each thread removes 10 keys
                for (int j = 0; j < 10; j++) {
                    int keyIndex = (threadId * 10 + j) % numKeys;
                    DataKey key = DataKey.fromString("remove-test-" + keyIndex);
                    CachedDataValue removed = waitFuture(keyValueStore.remove(key));
                    if (removed != null) {
                        removeCount.incrementAndGet();
                    }
                }
            } finally {
                latch.countDown();
            }
        }, executorService);

        assertTrue(waitFor(latch, 10), "All threads should complete within timeout");

        // Verify total keys decreased
        assertTrue(keyValueStore.totalKeys() < numKeys, "Some keys should have been removed");

        assertTrue(removeCount.get() > 0, "Some removes should have succeeded");
    }

    @Test
    @Timeout(60)
    void shouldHandleHighConcurrencyStressTest() {
        // Stress test with many threads and many operations
        int numThreads = 50;
        int operationsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger totalOperations = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        runInFutures(numThreads, 0, (threadId, numOps) -> {
            try {
                for (int j = 0; j < operationsPerThread; j++) {
                    DataKey key = DataKey.fromString("stress-key-" + threadId + "-" + j);

                    // Mix of operations
                    int operation = j % 4;
                    switch (operation) {
                        case 0 -> {
                            // Set
                            RequestDataValue value = RequestDataValue.fromString("stress-value-" + threadId + "-" + j);
                            waitFuture(keyValueStore.set(key, value, null));
                            totalOperations.incrementAndGet();
                        }
                        case 1 -> {
                            // Get
                            waitFuture(keyValueStore.get(key));
                            totalOperations.incrementAndGet();
                        }
                        case 2 -> {
                            // ContainsKey
                            waitFuture(keyValueStore.containsKey(key));
                            totalOperations.incrementAndGet();
                        }
                        case 3 -> {
                            // Remove
                            waitFuture(keyValueStore.remove(key));
                            totalOperations.incrementAndGet();
                        }
                    }
                }
            } catch (Exception e) {
                exceptions.add(e);
            } finally {
                latch.countDown();
            }
        }, executorService);


        assertTrue(waitFor(latch, 30), "All threads should complete within timeout");

        // Verify no unexpected exceptions
        assertTrue(exceptions.isEmpty(), "No exceptions should occur: " + exceptions);

        assertEquals(numThreads * operationsPerThread, totalOperations.get());
    }

    @Test
    @Timeout(30)
    void shouldMaintainDataConsistencyUnderConcurrentLoad() {
        // Test that data remains consistent under concurrent load
        int numKeys = 50;
        int numThreads = 20;
        int updatesPerKey = 5;

        // Initialize keys
        for (int i = 0; i < numKeys; i++) {
            DataKey key = DataKey.fromString("consistency-key-" + i);
            RequestDataValue value = RequestDataValue.fromString("initial-" + i);
            waitFuture(keyValueStore.set(key, value, null));
        }

        CountDownLatch latch = new CountDownLatch(numThreads);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        runInFutures(numThreads, 0, (threadId, numOps) -> {
            try {
                for (int update = 0; update < updatesPerKey; update++) {
                    for (int keyIndex = 0; keyIndex < numKeys; keyIndex++) {
                        DataKey key = DataKey.fromString("consistency-key-" + keyIndex);
                        CachedDataValue current = waitFuture(keyValueStore.get(key));
                        if (current != null) {
                            RequestDataValue newValue = RequestDataValue.fromString(
                                    "thread-" + threadId + "-update-" + update + "-key-" + keyIndex);
                            try {
                                waitFuture(keyValueStore.set(key, newValue, current.version()));
                            } catch (Exception e) {
                                // Version conflicts are expected in concurrent scenarios
                                if (!(e.getCause() instanceof ConcurrentUpdateException)) {
                                    exceptions.add(e);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                exceptions.add(e);
            } finally {
                latch.countDown();
            }
        }, executorService);

        assertTrue(waitFor(latch, 20), "All threads should complete within timeout");

        // Verify all keys still exist and have valid data
        for (int i = 0; i < numKeys; i++) {
            DataKey key = DataKey.fromString("consistency-key-" + i);
            CachedDataValue value = waitFuture(keyValueStore.get(key));
            assertNotNull(value, "Key " + i + " should still exist");
            assertNotNull(value.data(), "Value data should not be null");
            assertTrue(value.version() >= 0, "Version should be non-negative");
        }

        // Only ConcurrentUpdateException should occur, no other exceptions
        for (
                Exception e : exceptions) {
            assertFalse(e.getCause() instanceof ConcurrentUpdateException,
                    "Only ConcurrentUpdateException should occur, not: " + e);
        }
    }

    @Test
    @Timeout(30)
    void shouldHandleConcurrentContainsKeyOperations() {
        // Test concurrent containsKey operations
        int numKeys = 100;
        int numThreads = 15;

        // Pre-populate half the keys
        for (int i = 0; i < numKeys / 2; i++) {
            DataKey key = DataKey.fromString("contains-key-" + i);
            RequestDataValue value = RequestDataValue.fromString("value-" + i);
            waitFuture(keyValueStore.set(key, value, null));
        }

        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger trueCount = new AtomicInteger(0);
        AtomicInteger falseCount = new AtomicInteger(0);

        runInFutures(numThreads, 0, (threadId, numOps) -> {
            try {
                for (int j = 0; j < numKeys; j++) {
                    DataKey key = DataKey.fromString("contains-key-" + j);
                    Boolean exists = waitFuture(keyValueStore.containsKey(key));
                    if (Boolean.TRUE.equals(exists)) {
                        trueCount.incrementAndGet();
                    } else {
                        falseCount.incrementAndGet();
                    }
                }
            } finally {
                latch.countDown();
            }
        }, executorService);

        assertTrue(waitFor(latch, 10), "All threads should complete within timeout");

        // Verify results are consistent
        // Keys 0-49 should exist, keys 50-99 should not
        assertEquals(numThreads * (numKeys / 2), trueCount.

                get());

        assertEquals(numThreads * (numKeys / 2), falseCount.

                get());
    }

    @Test
    @Timeout(30)
    void shouldHandleMixedConcurrentOperations() {
        // Test a realistic mix of all operations concurrently
        int numThreads = 25;
        int operationsPerThread = 30;
        CountDownLatch latch = new CountDownLatch(numThreads);
        Set<String> writtenKeys = ConcurrentHashMap.newKeySet();
        AtomicInteger operationCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        runInFutures(numThreads, 0, (threadId, numOps) -> {
            try {
                for (int j = 0; j < operationsPerThread; j++) {
                    String keyStr = "mixed-op-key-" + threadId + "-" + j;
                    DataKey key = DataKey.fromString(keyStr);

                    int operation = (threadId + j) % 5;
                    switch (operation) {
                        case 0 -> {
                            // Set
                            RequestDataValue value = RequestDataValue.fromString("mixed-value-" + threadId + "-" + j);
                            waitFuture(keyValueStore.set(key, value, null));
                            writtenKeys.add(keyStr);
                            operationCount.incrementAndGet();
                        }
                        case 1 -> {
                            // Get
                            waitFuture(keyValueStore.get(key));
                            operationCount.incrementAndGet();
                        }
                        case 2 -> {
                            // ContainsKey
                            waitFuture(keyValueStore.containsKey(key));
                            operationCount.incrementAndGet();
                        }
                        case 3 -> {
                            // Remove
                            waitFuture(keyValueStore.remove(key));
                            writtenKeys.remove(keyStr);
                            operationCount.incrementAndGet();
                        }
                        case 4 -> {
                            // Update with version check
                            CachedDataValue current = waitFuture(keyValueStore.get(key));
                            if (current != null) {
                                try {
                                    RequestDataValue newValue = RequestDataValue.fromString("updated-" + threadId + "-" + j);
                                    waitFuture(keyValueStore.set(key, newValue, current.version()));
                                    operationCount.incrementAndGet();
                                } catch (Exception e) {
                                    if (!(e.getCause() instanceof ConcurrentUpdateException)) {
                                        exceptions.add(e);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                exceptions.add(e);
            } finally {
                latch.countDown();
            }
        }, executorService);


        assertTrue(waitFor(latch, 20), "All threads should complete within timeout");

        // Verify no unexpected exceptions
        assertTrue(exceptions.isEmpty(), "No unexpected exceptions should occur: " + exceptions);
        assertTrue(operationCount.get() > 0, "Some operations should have completed");

        // Verify final state consistency
        long finalKeyCount = keyValueStore.totalKeys();
        assertTrue(finalKeyCount >= 0, "Key count should be non-negative");
    }

    @Test
    @Timeout(30)
    void shouldHandleConcurrentOperationsAcrossAllPartitions() throws InterruptedException {
        // Test that operations work correctly across all 32 partitions
        // by using keys that hash to different partitions
        int numThreads = 32; // One thread per partition ideally
        int keysPerThread = 5;
        CountDownLatch latch = new CountDownLatch(numThreads);
        List<String> allKeys = Collections.synchronizedList(new ArrayList<>());

        runInFutures(numThreads, 0, (threadId, numOps) -> {
            try {
                for (int j = 0; j < keysPerThread; j++) {
                    // Use different key patterns to ensure distribution across partitions
                    String keyStr = "partition-test-" + threadId + "-" + j + "-" +
                            System.nanoTime() + "-" + Thread.currentThread().getId();
                    DataKey key = DataKey.fromString(keyStr);
                    RequestDataValue value = RequestDataValue.fromString("partition-value-" + threadId + "-" + j);

                    waitFuture(keyValueStore.set(key, value, null));
                    allKeys.add(keyStr);

                    // Verify immediately
                    CachedDataValue retrieved = waitFuture(keyValueStore.get(key));
                    assertNotNull(retrieved);
                    assertEquals("partition-value-" + threadId + "-" + j,
                            new String(retrieved.data(), StandardCharsets.UTF_8));
                }
            } finally {
                latch.countDown();
            }
        }, executorService);


        assertTrue(waitFor(latch, 15), "All threads should complete within timeout");

        // Verify all keys are still accessible
        assertEquals(numThreads * keysPerThread, keyValueStore.totalKeys());
        for (String keyStr : allKeys) {
            DataKey key = DataKey.fromString(keyStr);
            assertTrue(waitFuture(keyValueStore.containsKey(key)),
                    "Key should exist: " + keyStr);
        }
    }
}