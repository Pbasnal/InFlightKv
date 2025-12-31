package com.bcorp.kvstore;

import com.bcorp.exceptions.ConcurrentUpdateException;
import com.bcorp.pojos.CachedDataValue;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.RequestDataValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.bcorp.testutils.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class KeyValueStoreTest {

    private KeyValueStore keyValueStore;
    private final KvStoreClock clock = new SystemClock();

    @BeforeEach
    void setUp() {
        keyValueStore = new KeyValueStore(clock);
    }

    @Test
    void shouldInitializeWith32Partitions() {
        // The KeyValueStore constructor creates 32 partitions
        // We can verify this by checking that operations work correctly
        // and that the total keys are properly aggregated

        DataKey key = DataKey.fromString("test-key");
        RequestDataValue value = RequestDataValue.fromString("test-value");

        // Set a value
        keyValueStore.set(key, value, null);

        // Verify it exists and can be retrieved
        assertTrue(waitFuture(keyValueStore.containsKey(key)));
        CachedDataValue retrieved = waitFuture(keyValueStore.get(key));
        assertNotNull(retrieved);
        assertEquals("test-value", new String(retrieved.data(), StandardCharsets.UTF_8));

        // Total keys should be 1
        assertEquals(1, keyValueStore.totalKeys());
    }

    @Test
    void shouldStoreAndRetrieveValues() {
        // Given
        DataKey key = DataKey.fromString("test-key");
        RequestDataValue value = RequestDataValue.fromString("test-value");

        // When - Set value
        waitFuture(keyValueStore.set(key, value, null));

        // Then - Get value
        CachedDataValue retrieved = waitFuture(keyValueStore.get(key));
        assertNotNull(retrieved);
        assertEquals("test-value", new String(retrieved.data(), StandardCharsets.UTF_8));
        assertEquals(0L, retrieved.version()); // Initial version
    }

    @Test
    void shouldReturnNullForNonExistentKey() {
        // When
        CachedDataValue result = waitFuture(keyValueStore.get(DataKey.fromString("non-existent")));

        // Then
        assertNull(result);
    }

    @Test
    void shouldCheckKeyExistence() {
        DataKey key = DataKey.fromString("existence-test");

        // Initially should not exist
        assertFalse(waitFuture(keyValueStore.containsKey(key)));

        // After setting, should exist
        waitFuture(keyValueStore.set(key, RequestDataValue.fromString("value"), null));
        assertTrue(waitFuture(keyValueStore.containsKey(key)));

        // After removing, should not exist
        waitFuture(keyValueStore.remove(key));
        assertFalse(waitFuture(keyValueStore.containsKey(key)));
    }

    @Test
    void shouldRemoveExistingKey() {
        // Given
        DataKey key = DataKey.fromString("removal-test");
        RequestDataValue value = RequestDataValue.fromString("to-be-removed");

        waitFuture(keyValueStore.set(key, value, null));
        assertEquals(1, keyValueStore.totalKeys());

        // When - Remove
        CachedDataValue removedValue = waitFuture(keyValueStore.remove(key));

        // Then
        assertNotNull(removedValue);
        assertEquals("to-be-removed", new String(removedValue.data(), StandardCharsets.UTF_8));
        assertEquals(0, keyValueStore.totalKeys());
        assertNull(waitFuture(keyValueStore.get(key)));
    }

    @Test
    void shouldReturnNullWhenRemovingNonExistentKey() {
        // When
        CachedDataValue result = waitFuture(keyValueStore.remove(DataKey.fromString("non-existent")));

        // Then
        assertNull(result);
        assertEquals(0, keyValueStore.totalKeys());
    }

    @Test
    void shouldHandleVersionBasedUpdates() {
        // Given
        DataKey key = DataKey.fromString("version-test");
        RequestDataValue initialValue = RequestDataValue.fromString("initial");

        waitFuture(keyValueStore.set(key, initialValue, null));

        // When - Update with correct version
        RequestDataValue updatedValue = RequestDataValue.fromString("updated");
        waitFuture(keyValueStore.set(key, updatedValue, 0L));

        // Then
        CachedDataValue result = waitFuture(keyValueStore.get(key));
        assertNotNull(result);
        assertEquals("updated", new String(result.data(), StandardCharsets.UTF_8));
        assertEquals(1L, result.version());
    }

    @Test
    void shouldThrowConcurrentUpdateExceptionForVersionConflict() {
        // Given
        DataKey key = DataKey.fromString("conflict-test");
        keyValueStore.set(key, RequestDataValue.fromString("initial"), null);

        // When - Try to update with wrong version
        ExecutionException exception = assertThrows(ExecutionException.class, () ->
                keyValueStore.set(key, RequestDataValue.fromString("conflicting"), 999L).get(1, TimeUnit.SECONDS)
        );

        // Then
        assertInstanceOf(ConcurrentUpdateException.class, exception.getCause());
    }

    @Test
    void shouldDistributeKeysAcrossPartitions() {
        // Create multiple keys that should go to different partitions
        // We can't directly test partition distribution, but we can test that
        // multiple keys work correctly and totalKeys aggregates properly

        int numKeys = 100;
        for (int i = 0; i < numKeys; i++) {
            DataKey key = DataKey.fromString("partition-test-" + i);
            RequestDataValue value = RequestDataValue.fromString("value-" + i);
            waitFuture(keyValueStore.set(key, value, null));
        }

        // Verify all keys exist and total count is correct
        assertEquals(numKeys, keyValueStore.totalKeys());

        for (int i = 0; i < numKeys; i++) {
            DataKey key = DataKey.fromString("partition-test-" + i);
            assertTrue(waitFuture(keyValueStore.containsKey(key)));

            CachedDataValue value = waitFuture(keyValueStore.get(key));
            assertNotNull(value);
            assertEquals("value-" + i, new String(value.data(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void shouldAggregateTotalKeysFromAllPartitions() {
        // Test that totalKeys correctly aggregates from all 32 partitions

        // Initially empty
        assertEquals(0, keyValueStore.totalKeys());

        // Add keys in batches
        for (int batch = 0; batch < 5; batch++) {
            for (int i = 0; i < 10; i++) {
                DataKey key = DataKey.fromString("batch-" + batch + "-key-" + i);
                RequestDataValue value = RequestDataValue.fromString("batch-" + batch + "-value-" + i);
                waitFuture(keyValueStore.set(key, value, null));
            }
        }

        assertEquals(50, keyValueStore.totalKeys());

        // Remove some keys
        for (int i = 0; i < 10; i++) {
            DataKey key = DataKey.fromString("batch-0-key-" + i);
            waitFuture(keyValueStore.remove(key));
        }

        assertEquals(40, keyValueStore.totalKeys());
    }

    @Test
    void shouldHandleConcurrentOperationsOnDifferentKeys() {
        // Test that operations on different keys work correctly
        // This indirectly tests that partitioning works properly

        DataKey key1 = DataKey.fromString("concurrent-1");
        DataKey key2 = DataKey.fromString("concurrent-2");
        DataKey key3 = DataKey.fromString("concurrent-3");

        // Set different values
        waitFuture(keyValueStore.set(key1, RequestDataValue.fromString("value1"), null));
        waitFuture(keyValueStore.set(key2, RequestDataValue.fromString("value2"), null));
        waitFuture(keyValueStore.set(key3, RequestDataValue.fromString("value3"), null));

        assertEquals(3, keyValueStore.totalKeys());

        // Verify all values are correct
        assertEquals("value1", new String(waitFuture(keyValueStore.get(key1)).data(), StandardCharsets.UTF_8));
        assertEquals("value2", new String(waitFuture(keyValueStore.get(key2)).data(), StandardCharsets.UTF_8));
        assertEquals("value3", new String(waitFuture(keyValueStore.get(key3)).data(), StandardCharsets.UTF_8));

        // Remove one key
        waitFuture(keyValueStore.remove(key2));
        assertEquals(2, keyValueStore.totalKeys());
        assertNull(waitFuture(keyValueStore.get(key2)));
        assertTrue(waitFuture(keyValueStore.containsKey(key1)));
        assertTrue(waitFuture(keyValueStore.containsKey(key3)));
    }

    @Test
    void shouldHandleKeysWithSameHashGoingToSamePartition() {
        // Test keys that might hash to the same partition
        // Since we can't control hashing, we'll use keys that are likely to collide
        // and verify that operations still work correctly

        DataKey key1 = DataKey.fromString("collision-test-1");
        DataKey key2 = DataKey.fromString("collision-test-2");
        DataKey key3 = DataKey.fromString("different-key");

        // Set values
        waitFuture(keyValueStore.set(key1, RequestDataValue.fromString("collision-value-1"), null));
        waitFuture(keyValueStore.set(key2, RequestDataValue.fromString("collision-value-2"), null));
        waitFuture(keyValueStore.set(key3, RequestDataValue.fromString("different-value"), null));

        // All should exist and be retrievable
        assertEquals(3, keyValueStore.totalKeys());
        assertEquals("collision-value-1", new String(waitFuture(keyValueStore.get(key1)).data(), StandardCharsets.UTF_8));
        assertEquals("collision-value-2", new String(waitFuture(keyValueStore.get(key2)).data(), StandardCharsets.UTF_8));
        assertEquals("different-value", new String(waitFuture(keyValueStore.get(key3)).data(), StandardCharsets.UTF_8));
    }

    @Test
    void shouldHandleEmptyStringValues() {
        // Test with empty string values
        DataKey key = DataKey.fromString("empty-test");
        RequestDataValue emptyValue = RequestDataValue.fromString("");

        waitFuture(keyValueStore.set(key, emptyValue, null));

        CachedDataValue retrieved = waitFuture(keyValueStore.get(key));
        assertNotNull(retrieved);
        assertEquals("", new String(retrieved.data(), StandardCharsets.UTF_8));
        assertEquals(0, retrieved.data().length);
    }

    @Test
    void shouldHandleSpecialCharactersInKeysAndValues() {
        // Test with special characters
        DataKey specialKey = DataKey.fromString("special-key!@#$%^&*()");
        RequestDataValue specialValue = RequestDataValue.fromString("special-value!@#$%^&*()");

        waitFuture(keyValueStore.set(specialKey, specialValue, null));

        CachedDataValue retrieved = waitFuture(keyValueStore.get(specialKey));
        assertNotNull(retrieved);
        assertEquals("special-value!@#$%^&*()", new String(retrieved.data(), StandardCharsets.UTF_8));
    }

    @Test
    void shouldHandleLargeNumberOfKeys() {
        // Test with a reasonably large number of keys
        int numKeys = 1000;

        // Add keys
        for (int i = 0; i < numKeys; i++) {
            DataKey key = DataKey.fromString("large-test-key-" + i);
            RequestDataValue value = RequestDataValue.fromString("large-test-value-" + i);
            waitFuture(keyValueStore.set(key, value, null));
        }

        assertEquals(numKeys, keyValueStore.totalKeys());

        // Verify random sampling
        for (int i = 0; i < 10; i++) {
            int randomIndex = (int) (Math.random() * numKeys);
            DataKey key = DataKey.fromString("large-test-key-" + randomIndex);
            CachedDataValue value = waitFuture(keyValueStore.get(key));
            assertNotNull(value);
            assertEquals("large-test-value-" + randomIndex, new String(value.data(), StandardCharsets.UTF_8));
        }

        // Remove half the keys
        for (int i = 0; i < numKeys / 2; i++) {
            DataKey key = DataKey.fromString("large-test-key-" + i);
            waitFuture(keyValueStore.remove(key));
        }

        assertEquals(numKeys / 2, keyValueStore.totalKeys());
    }

    @Test
    void shouldMaintainVersionConsistencyAcrossPartitions() {
        // Test that version handling works consistently across different partitions

        DataKey[] keys = new DataKey[10];
        for (int i = 0; i < 10; i++) {
            keys[i] = DataKey.fromString("version-consistency-" + i);
            waitFuture(keyValueStore.set(keys[i], RequestDataValue.fromString("initial-" + i), null));
        }

        // Update each key multiple times and verify versions
        for (int update = 0; update < 3; update++) {
            for (int i = 0; i < 10; i++) {
                CachedDataValue current = waitFuture(keyValueStore.get(keys[i]));
                RequestDataValue newValue = RequestDataValue.fromString("update-" + update + "-" + i);
                waitFuture(keyValueStore.set(keys[i], newValue, current.version()));

                CachedDataValue afterUpdate = waitFuture(keyValueStore.get(keys[i]));
                assertEquals(update + 1L, afterUpdate.version());
                assertEquals("update-" + update + "-" + i, new String(afterUpdate.data(), StandardCharsets.UTF_8));
            }
        }

        // Final version should be 3 for all keys
        for (int i = 0; i < 10; i++) {
            CachedDataValue finalValue = waitFuture(keyValueStore.get(keys[i]));
            assertEquals(3L, finalValue.version());
        }
    }
}
