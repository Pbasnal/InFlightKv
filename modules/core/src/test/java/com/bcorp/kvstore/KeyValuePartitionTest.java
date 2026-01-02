package com.bcorp.kvstore;

import com.bcorp.exceptions.ConcurrentUpdateException;
import com.bcorp.pojos.CachedDataValue;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.RequestDataValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.bcorp.testutils.TestUtils.waitFuture;
import static org.junit.jupiter.api.Assertions.*;

class KeyValuePartitionTest {

    private KeyValuePartition partition;
    private DataKey testKey;
    private RequestDataValue testValue;

    private final KvStoreClock clock = new SystemClock();

    @BeforeEach
    void setUp() {

        partition = new KeyValuePartition(0, clock);
        testKey = DataKey.fromString("test-key");
        testValue = new RequestDataValue("test-data".getBytes(StandardCharsets.UTF_8), String.class);
    }

    @Test
    void shouldInitializeWithCorrectPartitionId() {
        KeyValuePartition testPartition = new KeyValuePartition(5, clock);
        assertNotNull(testPartition);
        assertEquals(0, testPartition.totalKeys().join()); // Initially empty
    }

    @Test
    void shouldReturnNullForNonExistentKey() throws ExecutionException, InterruptedException, TimeoutException {
        assertNull(
                partition.get(testKey)
                        .get(1, TimeUnit.SECONDS)
        );
    }

    @Test
    void shouldStoreAndRetrieveValue() throws ExecutionException, InterruptedException, TimeoutException {
        // When - Set value
        waitFuture(partition.set(testKey, testValue, null));
        assertEquals(1, partition.totalKeys().join());

        // When - Get value
        CachedDataValue getResult = partition.get(testKey).get(1, TimeUnit.SECONDS);

        // Then - Verify get result
        assertNotNull(getResult);
        assertArrayEquals(testValue.data(), getResult.data());
        assertEquals(0L, getResult.version()); // Initial version
    }

    @Test
    void shouldUpdateLastAccessTimeOnGet() throws ExecutionException, InterruptedException {
        // Given - Set initial value
        partition.set(testKey, testValue, null).get();
        CompletableFuture<CachedDataValue> firstGetFuture = partition.get(testKey);
        CachedDataValue firstGetResult = firstGetFuture.get();

        // Wait a bit
        Thread.sleep(10);

        // When - Get again
        CompletableFuture<CachedDataValue> secondGetFuture = partition.get(testKey);
        CachedDataValue secondGetResult = secondGetFuture.get();

        // Then - Last access time should be updated
        assertTrue(secondGetResult.lastAccessTimeMs() > firstGetResult.lastAccessTimeMs());
    }

    @Test
    void shouldIncrementVersionOnUpdate() throws ExecutionException, InterruptedException {
        // Given - Set initial value
        partition.set(testKey, testValue, null).get();

        // When - Update with new value
        RequestDataValue updatedValue =  RequestDataValue.fromString("updated-data");
        CompletableFuture<CachedDataValue> updateFuture = partition.set(testKey, updatedValue, null);
        CachedDataValue updateResult = updateFuture.get();

        // Then - Version should be incremented
        assertEquals(1, updateResult.version());
        assertArrayEquals(updatedValue.data(), updateResult.data());
    }

    @Test
    void shouldHandleVersionMismatch() {
        // Given - Set initial value
        partition.set(testKey, testValue, null);

        // When - Try to update with wrong version
        RequestDataValue newValue = RequestDataValue.fromString("new-data");
        CompletableFuture<CachedDataValue> future = partition.set(testKey, newValue, 999L); // Wrong version

        // Then - Should throw ConcurrentUpdateException
        ExecutionException exception = assertThrows(ExecutionException.class, () ->
                future.get(1, TimeUnit.SECONDS)
        );
        assertInstanceOf(ConcurrentUpdateException.class, exception.getCause());
    }

    @Test
    void shouldSkipWhenDataIsUnchanged() throws ExecutionException, InterruptedException, TimeoutException {
        // Given - Set initial value
        partition.set(testKey, testValue, null).get();

        // When - Try to set the same data
        CompletableFuture<CachedDataValue> future = partition.set(testKey, testValue, null);
        CachedDataValue result = future.get(1, TimeUnit.SECONDS);

        // Then - Should return existing value without version increment
        assertNotNull(result);
        assertEquals(0, result.version()); // Version should not change
        assertArrayEquals(testValue.data(), result.data());
    }

    @Test
    void shouldRemoveExistingKey() throws ExecutionException, InterruptedException, TimeoutException {
        // Given - Set value
        waitFuture(partition.set(testKey, testValue, null));
        assertEquals(1, partition.totalKeys().join());

        // When - Remove
        CachedDataValue removedValue = partition.remove(testKey).get(1, TimeUnit.SECONDS);

        // Then
        assertNotNull(removedValue);
        assertArrayEquals(testValue.data(), removedValue.data());
        assertEquals(0, partition.totalKeys().join());

        // Verify key is gone
        assertNull(partition.get(testKey).get(1, TimeUnit.SECONDS));
    }

    @Test
    void shouldReturnNullWhenRemovingNonExistentKey() throws ExecutionException, InterruptedException, TimeoutException {
        // When - Try to remove non-existent key
        CompletableFuture<CachedDataValue> future = partition.remove(testKey);

        // Then - Should return null
        CachedDataValue result = future.get(1, TimeUnit.SECONDS);
        assertNull(result);
        assertEquals(0, partition.totalKeys().join());
    }

    @Test
    void shouldCheckKeyExistence() throws ExecutionException, InterruptedException, TimeoutException {
        // Initially should not exist
        CompletableFuture<Boolean> existsFuture1 = partition.containsKey(testKey);
        assertFalse(existsFuture1.get(1, TimeUnit.SECONDS));

        // After setting, should exist
        partition.set(testKey, testValue, null).get();
        CompletableFuture<Boolean> existsFuture2 = partition.containsKey(testKey);
        assertTrue(existsFuture2.get(1, TimeUnit.SECONDS));

        // After removing, should not exist
        partition.remove(testKey).get();
        CompletableFuture<Boolean> existsFuture3 = partition.containsKey(testKey);
        assertFalse(existsFuture3.get(1, TimeUnit.SECONDS));
    }

    @Test
    void shouldTrackTotalKeysCorrectly() throws ExecutionException, InterruptedException {
        DataKey key1 = DataKey.fromString("key1");
        DataKey key2 = DataKey.fromString("key2");
        DataKey key3 = DataKey.fromString("key3");

        // Initially empty
        assertEquals(0, partition.totalKeys().join());

        // Add first key
        partition.set(key1, testValue, null).get();
        assertEquals(1, partition.totalKeys().join());

        // Add second key
        partition.set(key2, testValue, null).get();
        assertEquals(2, partition.totalKeys().join());

        // Add third key
        partition.set(key3, testValue, null).get();
        assertEquals(3, partition.totalKeys().join());

        // Remove one key
        partition.remove(key2).get();
        assertEquals(2, partition.totalKeys().join());

        // Try to remove non-existent key (should not change count)
        partition.remove(DataKey.fromString("non-existent")).get();
        assertEquals(2, partition.totalKeys().join());
    }

    @Test
    void shouldHandleOptimisticLockingWithCorrectVersion() throws ExecutionException, InterruptedException, TimeoutException {
        // Given - Set initial value
        waitFuture(partition.set(testKey, testValue, null));

        // When - Update with correct version
        RequestDataValue newValue = RequestDataValue.fromString("new-data");
        CachedDataValue result = partition.set(testKey, newValue, 0L).get(1, TimeUnit.SECONDS); // Correct version

        // Then - Should succeed and increment version
        assertNotNull(result);
        assertEquals(1L, result.version());
        assertArrayEquals(newValue.data(), result.data());
    }

    @Test
    void shouldHandleMultipleConcurrentOperations() throws ExecutionException, InterruptedException, TimeoutException {
        // Test concurrent operations on different keys
        DataKey key1 = DataKey.fromString("key1");
        DataKey key2 = DataKey.fromString("key2");

        RequestDataValue value1 = RequestDataValue.fromString("data1");
        RequestDataValue value2 = RequestDataValue.fromString("data2");

        // Set both keys
        CompletableFuture<CachedDataValue> setFuture1 = partition.set(key1, value1, null);
        CompletableFuture<CachedDataValue> setFuture2 = partition.set(key2, value2, null);

        CachedDataValue result1 = setFuture1.get(1, TimeUnit.SECONDS);
        CachedDataValue result2 = setFuture2.get(1, TimeUnit.SECONDS);

        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(2, partition.totalKeys().join());

        // Get both keys
        CompletableFuture<CachedDataValue> getFuture1 = partition.get(key1);
        CompletableFuture<CachedDataValue> getFuture2 = partition.get(key2);

        CachedDataValue getResult1 = getFuture1.get(1, TimeUnit.SECONDS);
        CachedDataValue getResult2 = getFuture2.get(1, TimeUnit.SECONDS);

        assertArrayEquals(value1.data(), getResult1.data());
        assertArrayEquals(value2.data(), getResult2.data());
    }

    @Test
    void shouldHandleEmptyDataArrays() throws ExecutionException, InterruptedException, TimeoutException {
        // Given - Empty data
        RequestDataValue emptyValue = new RequestDataValue(new byte[0], String.class);

        // When - Set empty value
        CompletableFuture<CachedDataValue> setFuture = partition.set(testKey, emptyValue, null);
        CachedDataValue setResult = setFuture.get(1, TimeUnit.SECONDS);

        // Then
        assertNotNull(setResult);
        assertEquals(0, setResult.data().length);

        // When - Get empty value
        CompletableFuture<CachedDataValue> getFuture = partition.get(testKey);
        CachedDataValue getResult = getFuture.get(1, TimeUnit.SECONDS);

        // Then
        assertNotNull(getResult);
        assertEquals(0, getResult.data().length);
    }

    @Test
    void shouldHandleNullDataInDataValue() throws ExecutionException, InterruptedException, TimeoutException {
        // Given - DataValue with null data (edge case)
        RequestDataValue nullRequestDataValue = new RequestDataValue(null, String.class);

        // When - Set value with null data
        CompletableFuture<CachedDataValue> setFuture = partition.set(testKey, nullRequestDataValue, null);
        CachedDataValue setResult = setFuture.get(1, TimeUnit.SECONDS);

        // Then - Should handle gracefully
        assertNotNull(setResult);

        // When - Get null value
        CompletableFuture<CachedDataValue> getFuture = partition.get(testKey);
        CachedDataValue getResult = getFuture.get(1, TimeUnit.SECONDS);

        // Then
        assertNotNull(getResult);
        assertNull(getResult.data());
    }

    @Test
    void shouldHandleNegativePartitionId() {
        // When - Create partition with negative ID
        KeyValuePartition negativePartition = new KeyValuePartition(-1, clock);

        // Then - Should work normally
        assertNotNull(negativePartition);
        assertEquals(0, negativePartition.totalKeys().join());
    }

    @Test
    void shouldHandleVeryLargePartitionId() {
        // When - Create partition with large ID
        KeyValuePartition largePartition = new KeyValuePartition(Integer.MAX_VALUE, clock);

        // Then - Should work normally
        assertNotNull(largePartition);
        assertEquals(0, largePartition.totalKeys().join());
    }

    @Test
    void shouldHandleSameKeyMultipleSets() throws ExecutionException, InterruptedException {
        // When - Set same key multiple times
        RequestDataValue value1 = RequestDataValue.fromString("value1");
        RequestDataValue value2 = RequestDataValue.fromString("value2");
        RequestDataValue value3 =  RequestDataValue.fromString("value3");

        partition.set(testKey, value1, null).get();
        partition.set(testKey, value2, null).get();
        partition.set(testKey, value3, null).get();

        // Then - Should have only one key with latest value and version 2
        assertEquals(1, partition.totalKeys().join());
        CachedDataValue result = partition.get(testKey).get();
        assertNotNull(result);
        assertEquals(2, result.version());
        assertArrayEquals(value3.data(), result.data());
    }

    @Test
    void shouldHandleBinaryData() throws ExecutionException, InterruptedException {
        // Given - Binary data (not text)
        byte[] binaryData = {0x00, 0x01, 0x02, (byte) 0xFF, 0x10, 0x20};
        RequestDataValue binaryValue = new RequestDataValue(binaryData, byte[].class);

        // When - Store and retrieve binary data
        partition.set(testKey, binaryValue, null).get();
        CachedDataValue result = partition.get(testKey).get();

        // Then - Should preserve binary data exactly
        assertNotNull(result);
        assertArrayEquals(binaryData, result.data());
        assertEquals(byte[].class, result.dataType());
    }

    @Test
    void shouldHandleLargeDataArrays() throws ExecutionException, InterruptedException {
        // Given - Large data array (1MB)
        byte[] largeData = new byte[1024 * 1024];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }
        RequestDataValue largeValue = new RequestDataValue(largeData, byte[].class);

        // When - Store and retrieve large data
        partition.set(testKey, largeValue, null).get();
        CachedDataValue result = partition.get(testKey).get();

        // Then - Should handle large data correctly
        assertNotNull(result);
        assertArrayEquals(largeData, result.data());
        assertEquals(1, partition.totalKeys().join());
    }
}
