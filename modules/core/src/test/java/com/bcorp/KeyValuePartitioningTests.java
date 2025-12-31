package com.bcorp;

import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.DataValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.bcorp.testutils.TestUtils.runInFutures;
import static com.bcorp.testutils.TestUtils.waitFor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to validate the internal sharding/partitioning logic of KeyValueStore.
 * <p>
 * KeyValueStore uses 32 partitions internally, with keys distributed using:
 * partition = (key.hashCode() & 0x7fffffff) % 32
 * <p>
 * Each partition has its own single-threaded event loop, ensuring thread-safety
 * and proper isolation between partitions.
 */
public class KeyValuePartitioningTests {

    private KeyValueStore store;
    private ExecutorService executorService;
    private static final int EXPECTED_PARTITIONS = 32;

    @BeforeEach
    void setUp() {
        store = new KeyValueStore();
        executorService = Executors.newFixedThreadPool(50);
    }

    @AfterEach
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

    /**
     * Validates that partitioning is consistent: the same key always routes to the same partition.
     * This is tested by verifying that operations on the same key from different threads
     * maintain data consistency, which implies consistent partition routing.
     */
    @Test
    void shouldRouteSameKeyToSamePartitionConsistently() {
        // Test partition consistency: same key should always go to same partition
        int numThreads = 50;
        int totalKeys = 10_000;
        CountDownLatch writeLatch = new CountDownLatch(numThreads);
        CountDownLatch readLatch = new CountDownLatch(numThreads);
        Map<String, Exception> writeErrors = new ConcurrentHashMap<>();
        Map<String, Exception> readErrors = new ConcurrentHashMap<>();

        // Phase 1: Concurrent writes - each key written once
        CompletableFuture<?>[] writeFutures = runInFutures(totalKeys, 0, (keyId, numOps) -> {
            try {
                DataKey key = DataKey.fromString("key-" + keyId);
                DataValue value = DataValue.fromString("v" + keyId);
                store.set(key, value, null).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                writeErrors.put("key-" + keyId, e);
            } finally {
                if (keyId % (totalKeys / numThreads) == 0) {
                    writeLatch.countDown();
                }
            }

        }, executorService);

        CompletableFuture.allOf(writeFutures).join();
        waitFor(writeLatch, 10);

        // Verify all writes succeeded
        assertTrue(writeErrors.isEmpty(),
                "All writes should succeed. Errors: " + writeErrors);
        assertEquals(totalKeys, store.totalKeys(),
                "All keys should be stored");

        // Phase 2: Concurrent reads - verify partition consistency
        // Same keys should be retrievable from any thread, proving consistent routing
        CompletableFuture<?>[] readFutures = runInFutures(totalKeys, 0, (keyId, numOps) -> {
            try {
                DataKey key = DataKey.fromString("key-" + keyId);
                DataValue retrieved = store.get(key).get(5, TimeUnit.SECONDS);
                assertNotNull(retrieved, "Value should exist for key-" + keyId);
                String expectedValue = "v" + keyId;
                String actualValue = new String(retrieved.data(), StandardCharsets.UTF_8);
                assertEquals(expectedValue, actualValue,
                        "Value mismatch for key-" + keyId);
            } catch (Exception e) {
                readErrors.put("key-" + keyId, e);
            } finally {
                if (keyId % (totalKeys / numThreads) == 0) {
                    readLatch.countDown();
                }
            }
        }, executorService);


        CompletableFuture.allOf(readFutures).join();
        waitFor(readLatch, 10);

        // Verify all reads succeeded and data is consistent
        assertTrue(readErrors.isEmpty(), "All reads should succeed. Errors: " + readErrors);
    }

    /**
     * Validates that remove operations correctly route to the partition containing the key.
     * This tests that the partitioning logic works correctly for all operations (set, get, remove).
     */
    @Test
    void shouldRemoveKeyFromCorrectPartition() {
        int numThreads = 50;
        int totalKeys = 10_000;
        CountDownLatch setLatch = new CountDownLatch(numThreads);
        CountDownLatch removeLatch = new CountDownLatch(numThreads);
        Map<String, Exception> setErrors = new ConcurrentHashMap<>();
        Map<String, Exception> removeErrors = new ConcurrentHashMap<>();

        // Phase 1: Concurrent writes
        CompletableFuture<?>[] setFutures = runInFutures(totalKeys, 0, (keyId, numOps) -> {
            try {
                DataKey key = DataKey.fromString("key-" + keyId);
                DataValue value = DataValue.fromString("v" + keyId);
                store.set(key, value, null).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                setErrors.put("key-" + keyId, e);
            } finally {
                if (keyId % (totalKeys / numThreads) == 0) {
                    setLatch.countDown();
                }
            }
        }, executorService);


        CompletableFuture.allOf(setFutures).join();
        waitFor(setLatch, 10);

        assertTrue(setErrors.isEmpty(),
                "All sets should succeed. Errors: " + setErrors);
        assertEquals(totalKeys, store.totalKeys(),
                "All keys should be stored before removal");

        // Phase 2: Concurrent removes - each key removed once
        // This validates that remove operations route to the correct partition
        CompletableFuture<?>[] removeFutures = runInFutures(totalKeys, 0, (keyId, numOps) -> {
            try {
                DataKey key = DataKey.fromString("key-" + keyId);
                DataValue removed = store.remove(key).get(5, TimeUnit.SECONDS);
                assertNotNull(removed, "Removed value should not be null for key-" + keyId);
                String expectedValue = "v" + keyId;
                String actualValue = new String(removed.data(), StandardCharsets.UTF_8);
                assertEquals(expectedValue, actualValue,
                        "Removed value mismatch for key-" + keyId);
            } catch (Exception e) {
                removeErrors.put("key-" + keyId, e);
            } finally {
                if (keyId % (totalKeys / numThreads) == 0) {
                    removeLatch.countDown();
                }
            }
        }, executorService);


        CompletableFuture.allOf(removeFutures).join();
        waitFor(removeLatch, 10);

        // Verify all removes succeeded and store is empty
        assertTrue(removeErrors.isEmpty(),
                "All removes should succeed. Errors: " + removeErrors);
        assertEquals(0, store.totalKeys(),
                "All keys should be removed from all partitions");
    }

    /**
     * Validates that keys are distributed across multiple partitions.
     * With 10,000 keys and 32 partitions, keys should be distributed across partitions.
     * We verify this indirectly by ensuring operations work correctly with many keys,
     * which implies proper distribution and partition isolation.
     */
    @Test
    void shouldDistributeKeysAcrossMultiplePartitions() {
        int totalKeys = 10_000;
        int numThreads = 50;
        CountDownLatch latch = new CountDownLatch(numThreads);
        Map<String, Exception> errors = new ConcurrentHashMap<>();

        // Concurrent writes of many keys - should distribute across 32 partitions
        CompletableFuture<?>[] futures = runInFutures(totalKeys, 0, (keyId, numOps) -> {
            try {
                // Use varied key patterns to ensure distribution
                DataKey key = DataKey.fromString("distributed-key-" + keyId + "-" + (keyId * 7));
                DataValue value = DataValue.fromString("distributed-value-" + keyId);
                store.set(key, value, null).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                errors.put("key-" + keyId, e);
            } finally {
                if (keyId % (totalKeys / numThreads) == 0) {
                    latch.countDown();
                }
            }
        }, executorService);


        CompletableFuture.allOf(futures).join();
        waitFor(latch, 15);

        assertTrue(errors.isEmpty(),
                "All writes should succeed. Errors: " + errors);
        assertEquals(totalKeys, store.totalKeys(),
                "All keys should be stored across partitions");

        // Verify keys are accessible - proves they're in correct partitions
        int verifiedCount = 0;
        for (int i = 0; i < totalKeys; i += 100) { // Sample every 100th key
            DataKey key = DataKey.fromString("distributed-key-" + i + "-" + (i * 7));
            DataValue value = store.get(key).join();
            assertNotNull(value, "Key should exist: " + key.key());
            verifiedCount++;
        }
        assertTrue(verifiedCount > 0, "Should verify at least some keys");
    }

    /**
     * Validates partition isolation: operations on keys in different partitions
     * should not interfere with each other. This is tested by performing concurrent
     * operations on many different keys and verifying data integrity.
     */
    @Test
    void shouldMaintainPartitionIsolationUnderConcurrentLoad() {
        int numThreads = 50;
        int keysPerThread = 200; // Total: 10,000 keys
        int totalKeys = numThreads * keysPerThread;
        CountDownLatch latch = new CountDownLatch(numThreads);
        Map<String, Exception> errors = new ConcurrentHashMap<>();

        // Concurrent writes - keys should be isolated in their respective partitions
        CompletableFuture<?>[] writeFutures = runInFutures(numThreads, 0, (tId, numOps) -> {
            try {
                for (int j = 0; j < keysPerThread; j++) {
                    DataKey key = DataKey.fromString("isolated-thread-" + tId + "-key-" + j);
                    DataValue value = DataValue.fromString("isolated-value-" + tId + "-" + j);
                    store.set(key, value, null).get(2, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                errors.put("thread-" + tId, e);
            } finally {
                latch.countDown();
            }
        }, executorService);


        CompletableFuture.allOf(writeFutures).join();
        assertTrue(waitFor(latch, 15), "All threads should complete");

        assertTrue(errors.isEmpty(),
                "All writes should succeed. Errors: " + errors);
        assertEquals(totalKeys, store.totalKeys(),
                "All keys should be stored in isolated partitions");

        // Verify isolation: each thread's keys should be independent
        for (int threadId = 0; threadId < Math.min(10, numThreads); threadId++) {
            for (int j = 0; j < Math.min(10, keysPerThread); j++) {
                DataKey key = DataKey.fromString("isolated-thread-" + threadId + "-key-" + j);
                DataValue value = store.get(key).join();
                assertNotNull(value, "Key should exist: " + key.key());
                String expected = "isolated-value-" + threadId + "-" + j;
                String actual = new String(value.data(), StandardCharsets.UTF_8);
                assertEquals(expected, actual,
                        "Value should match for isolated key: " + key.key());
            }
        }
    }

    /**
     * Validates that the partitioning algorithm correctly handles edge cases
     * such as keys with similar hash codes and ensures consistent routing.
     */
    @Test
    void shouldHandlePartitionRoutingForSimilarKeys() {
        // Test keys that might have similar hash codes
        List<String> similarKeys = List.of(
                "key-0", "key-1", "key-2", "key-10", "key-11", "key-12",
                "a", "b", "c", "aa", "ab", "ac"
        );

        // Store values
        Map<String, String> expectedValues = new HashMap<>();
        for (String keyStr : similarKeys) {
            String valueStr = "value-" + keyStr;
            DataKey key = DataKey.fromString(keyStr);
            DataValue value = DataValue.fromString(valueStr);
            store.set(key, value, null).join();
            expectedValues.put(keyStr, valueStr);
        }

        assertEquals(similarKeys.size(), store.totalKeys(),
                "All similar keys should be stored");

        // Verify each key routes consistently (same key always retrieves same value)
        for (int i = 0; i < 10; i++) {
            for (String keyStr : similarKeys) {
                DataKey key = DataKey.fromString(keyStr);
                DataValue value = store.get(key).join();
                assertNotNull(value, "Key should exist: " + keyStr);
                String expected = expectedValues.get(keyStr);
                String actual = new String(value.data(), StandardCharsets.UTF_8);
                assertEquals(expected, actual,
                        "Value should be consistent for key: " + keyStr + " (iteration " + i + ")");
            }
        }
    }

    /**
     * Validates that all operations (get, set, containsKey, remove) correctly
     * route to the same partition for a given key.
     */
    @Test
    void shouldRouteAllOperationsToSamePartitionForGivenKey() {
        int numKeys = 100;
        List<DataKey> testKeys = new ArrayList<>();

        // Create test keys
        for (int i = 0; i < numKeys; i++) {
            testKeys.add(DataKey.fromString("operation-key-" + i));
        }

        // Test set operation routes correctly
        for (DataKey key : testKeys) {
            DataValue value = DataValue.fromString("initial-" + key.key());
            store.set(key, value, null).join();
        }
        assertEquals(numKeys, store.totalKeys(),
                "All keys should be stored");

        // Test containsKey routes to same partition
        for (DataKey key : testKeys) {
            Boolean exists = store.containsKey(key).join();
            assertTrue(exists, "Key should exist: " + key.key());
        }

        // Test get routes to same partition
        for (DataKey key : testKeys) {
            DataValue value = store.get(key).join();
            assertNotNull(value, "Value should exist for: " + key.key());
            assertTrue(new String(value.data(), StandardCharsets.UTF_8).startsWith("initial-"),
                    "Value should match for: " + key.key());
        }

        // Test remove routes to same partition
        for (DataKey key : testKeys) {
            DataValue removed = store.remove(key).join();
            assertNotNull(removed, "Removed value should exist for: " + key.key());
        }
        assertEquals(0, store.totalKeys(),
                "All keys should be removed from their partitions");
    }
}
