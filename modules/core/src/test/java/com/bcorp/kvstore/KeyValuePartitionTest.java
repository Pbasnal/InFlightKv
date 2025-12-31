package com.bcorp.kvstore;

import com.bcorp.exceptions.ConcurrentUpdateException;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.DataValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class KeyValuePartitionTest {

    private KeyValuePartition partition;
    private DataKey testKey;
    private DataValue testValue;

    @BeforeEach
    void setUp() {
        partition = new KeyValuePartition(0);
        testKey = DataKey.fromString("test-key");
        testValue = new DataValue("test-data".getBytes(StandardCharsets.UTF_8),
                String.class,
                System.currentTimeMillis(),
                0L);
    }

    private void waitAndAssert(CompletableFuture<DataValue> fut) {
        assertDoesNotThrow(() -> fut.get(1, TimeUnit.SECONDS));
    }

    @Test
    void shouldInitializeWithCorrectPartitionId() {
        KeyValuePartition testPartition = new KeyValuePartition(5);
        assertNotNull(testPartition);
        assertEquals(0, testPartition.totalKeys()); // Initially empty
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
        waitAndAssert(partition.set(testKey, testValue, null));
        assertEquals(1, partition.totalKeys());

        // When - Get value
        DataValue getResult = partition.get(testKey).get(1, TimeUnit.SECONDS);

        // Then - Verify get result
        assertNotNull(getResult);
        assertArrayEquals(testValue.data(), getResult.data());
        assertEquals(0L, getResult.version()); // Initial version
    }

    @Test
    void shouldUpdateLastAccessTimeOnGet() throws ExecutionException, InterruptedException {
        // Given - Set initial value
        partition.set(testKey, testValue, null).get();
        CompletableFuture<DataValue> firstGetFuture = partition.get(testKey);
        DataValue firstGetResult = firstGetFuture.get();

        // Wait a bit
        Thread.sleep(10);

        // When - Get again
        CompletableFuture<DataValue> secondGetFuture = partition.get(testKey);
        DataValue secondGetResult = secondGetFuture.get();

        // Then - Last access time should be updated
        assertTrue(secondGetResult.lastAccessTimeMs() > firstGetResult.lastAccessTimeMs());
    }

    @Test
    void shouldIncrementVersionOnUpdate() throws ExecutionException, InterruptedException {
        // Given - Set initial value
        partition.set(testKey, testValue, null).get();

        // When - Update with new value
        DataValue updatedValue = new DataValue("updated-data".getBytes(StandardCharsets.UTF_8),
                String.class,
                System.currentTimeMillis(),
                0L); // version will be ignored in UPDATE
        CompletableFuture<DataValue> updateFuture = partition.set(testKey, updatedValue, null);
        DataValue updateResult = updateFuture.get();

        // Then - Version should be incremented
        assertEquals(1, updateResult.version());
        assertArrayEquals(updatedValue.data(), updateResult.data());
    }

    @Test
    void shouldHandleVersionMismatch() {
        // Given - Set initial value
        partition.set(testKey, testValue, null);

        // When - Try to update with wrong version
        DataValue newValue = new DataValue("new-data".getBytes(StandardCharsets.UTF_8),
                String.class,
                System.currentTimeMillis(),
                0L);
        CompletableFuture<DataValue> future = partition.set(testKey, newValue, 999L); // Wrong version

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
        CompletableFuture<DataValue> future = partition.set(testKey, testValue, null);
        DataValue result = future.get(1, TimeUnit.SECONDS);

        // Then - Should return existing value without version increment
        assertNotNull(result);
        assertEquals(0, result.version()); // Version should not change
        assertArrayEquals(testValue.data(), result.data());
    }

    @Test
    void shouldRemoveExistingKey() throws ExecutionException, InterruptedException, TimeoutException {
        // Given - Set value
        waitAndAssert(partition.set(testKey, testValue, null));
        assertEquals(1, partition.totalKeys());

        // When - Remove
        DataValue removedValue = partition.remove(testKey).get(1, TimeUnit.SECONDS);

        // Then
        assertNotNull(removedValue);
        assertArrayEquals(testValue.data(), removedValue.data());
        assertEquals(0, partition.totalKeys());

        // Verify key is gone
        assertNull(partition.get(testKey).get(1, TimeUnit.SECONDS));
    }

    @Test
    void shouldReturnNullWhenRemovingNonExistentKey() throws ExecutionException, InterruptedException, TimeoutException {
        // When - Try to remove non-existent key
        CompletableFuture<DataValue> future = partition.remove(testKey);

        // Then - Should return null
        DataValue result = future.get(1, TimeUnit.SECONDS);
        assertNull(result);
        assertEquals(0, partition.totalKeys());
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
        assertEquals(0, partition.totalKeys());

        // Add first key
        partition.set(key1, testValue, null).get();
        assertEquals(1, partition.totalKeys());

        // Add second key
        partition.set(key2, testValue, null).get();
        assertEquals(2, partition.totalKeys());

        // Add third key
        partition.set(key3, testValue, null).get();
        assertEquals(3, partition.totalKeys());

        // Remove one key
        partition.remove(key2).get();
        assertEquals(2, partition.totalKeys());

        // Try to remove non-existent key (should not change count)
        partition.remove(DataKey.fromString("non-existent")).get();
        assertEquals(2, partition.totalKeys());
    }

    @Test
    void shouldHandleOptimisticLockingWithCorrectVersion() throws ExecutionException, InterruptedException, TimeoutException {
        // Given - Set initial value
        waitAndAssert(partition.set(testKey, testValue, null));

        // When - Update with correct version
        DataValue newValue = new DataValue("new-data".getBytes(StandardCharsets.UTF_8),
                                          String.class,
                                          System.currentTimeMillis(),
                                          0L);
        DataValue result = partition.set(testKey, newValue, 0L).get(1, TimeUnit.SECONDS); // Correct version

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

        DataValue value1 = new DataValue("data1".getBytes(StandardCharsets.UTF_8), String.class, System.currentTimeMillis(), 0L);
        DataValue value2 = new DataValue("data2".getBytes(StandardCharsets.UTF_8), String.class, System.currentTimeMillis(), 0L);

        // Set both keys
        CompletableFuture<DataValue> setFuture1 = partition.set(key1, value1, null);
        CompletableFuture<DataValue> setFuture2 = partition.set(key2, value2, null);

        DataValue result1 = setFuture1.get(1, TimeUnit.SECONDS);
        DataValue result2 = setFuture2.get(1, TimeUnit.SECONDS);

        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(2, partition.totalKeys());

        // Get both keys
        CompletableFuture<DataValue> getFuture1 = partition.get(key1);
        CompletableFuture<DataValue> getFuture2 = partition.get(key2);

        DataValue getResult1 = getFuture1.get(1, TimeUnit.SECONDS);
        DataValue getResult2 = getFuture2.get(1, TimeUnit.SECONDS);

        assertArrayEquals(value1.data(), getResult1.data());
        assertArrayEquals(value2.data(), getResult2.data());
    }

    @Test
    void shouldHandleEmptyDataArrays() throws ExecutionException, InterruptedException, TimeoutException {
        // Given - Empty data
        DataValue emptyValue = new DataValue(new byte[0], String.class, System.currentTimeMillis(), 0L);

        // When - Set empty value
        CompletableFuture<DataValue> setFuture = partition.set(testKey, emptyValue, null);
        DataValue setResult = setFuture.get(1, TimeUnit.SECONDS);

        // Then
        assertNotNull(setResult);
        assertEquals(0, setResult.data().length);

        // When - Get empty value
        CompletableFuture<DataValue> getFuture = partition.get(testKey);
        DataValue getResult = getFuture.get(1, TimeUnit.SECONDS);

        // Then
        assertNotNull(getResult);
        assertEquals(0, getResult.data().length);
    }

    @Test
    void shouldHandleNullDataInDataValue() throws ExecutionException, InterruptedException, TimeoutException {
        // Given - DataValue with null data (edge case)
        DataValue nullDataValue = new DataValue(null, String.class, System.currentTimeMillis(), 0L);

        // When - Set value with null data
        CompletableFuture<DataValue> setFuture = partition.set(testKey, nullDataValue, null);
        DataValue setResult = setFuture.get(1, TimeUnit.SECONDS);

        // Then - Should handle gracefully
        assertNotNull(setResult);

        // When - Get null value
        CompletableFuture<DataValue> getFuture = partition.get(testKey);
        DataValue getResult = getFuture.get(1, TimeUnit.SECONDS);

        // Then
        assertNotNull(getResult);
        assertNull(getResult.data());
    }

    @Test
    void shouldHandleNegativePartitionId() {
        // When - Create partition with negative ID
        KeyValuePartition negativePartition = new KeyValuePartition(-1);

        // Then - Should work normally
        assertNotNull(negativePartition);
        assertEquals(0, negativePartition.totalKeys());
    }

    @Test
    void shouldHandleVeryLargePartitionId() {
        // When - Create partition with large ID
        KeyValuePartition largePartition = new KeyValuePartition(Integer.MAX_VALUE);

        // Then - Should work normally
        assertNotNull(largePartition);
        assertEquals(0, largePartition.totalKeys());
    }

    @Test
    void shouldHandleSameKeyMultipleSets() throws ExecutionException, InterruptedException {
        // When - Set same key multiple times
        DataValue value1 = new DataValue("value1".getBytes(StandardCharsets.UTF_8), String.class, System.currentTimeMillis(), 0L);
        DataValue value2 = new DataValue("value2".getBytes(StandardCharsets.UTF_8), String.class, System.currentTimeMillis(), 0L);
        DataValue value3 = new DataValue("value3".getBytes(StandardCharsets.UTF_8), String.class, System.currentTimeMillis(), 0L);

        partition.set(testKey, value1, null).get();
        partition.set(testKey, value2, null).get();
        partition.set(testKey, value3, null).get();

        // Then - Should have only one key with latest value and version 2
        assertEquals(1, partition.totalKeys());
        DataValue result = partition.get(testKey).get();
        assertNotNull(result);
        assertEquals(2, result.version());
        assertArrayEquals(value3.data(), result.data());
    }

    @Test
    void shouldHandleBinaryData() throws ExecutionException, InterruptedException {
        // Given - Binary data (not text)
        byte[] binaryData = {0x00, 0x01, 0x02, (byte) 0xFF, 0x10, 0x20};
        DataValue binaryValue = new DataValue(binaryData, byte[].class, System.currentTimeMillis(), 0L);

        // When - Store and retrieve binary data
        partition.set(testKey, binaryValue, null).get();
        DataValue result = partition.get(testKey).get();

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
        DataValue largeValue = new DataValue(largeData, byte[].class, System.currentTimeMillis(), 0L);

        // When - Store and retrieve large data
        partition.set(testKey, largeValue, null).get();
        DataValue result = partition.get(testKey).get();

        // Then - Should handle large data correctly
        assertNotNull(result);
        assertArrayEquals(largeData, result.data());
        assertEquals(1, partition.totalKeys());
    }
}
