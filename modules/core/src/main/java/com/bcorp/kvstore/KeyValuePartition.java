package com.bcorp.kvstore;

import com.bcorp.exceptions.ConcurrentUpdateException;
import com.bcorp.pojos.CachedDataValue;
import com.bcorp.pojos.RequestDataValue;
import com.bcorp.pojos.DataKey;

import java.util.*;
import java.util.concurrent.*;

public class KeyValuePartition {
    protected int partitionId;
    protected ExecutorService eventLoop;
    private final Map<DataKey, CachedDataValue> keyValueStore;
    private final NavigableSet<DataKey> sortedKeys;
    private final KvStoreClock clock;

    public KeyValuePartition(int _partitionId, KvStoreClock _clock) {
        this.clock = _clock;
        this.partitionId = _partitionId;
        this.eventLoop = Executors.newSingleThreadExecutor();
        this.keyValueStore = new HashMap<>();
        this.sortedKeys = new TreeSet<>();
    }

    public CompletableFuture<CachedDataValue> get(DataKey key) {
        CompletableFuture<CachedDataValue> resultFuture = new CompletableFuture<>();
        eventLoop.execute(() -> {
            CachedDataValue value = keyValueStore.get(key);

            if (value == null) {
                resultFuture.complete(null);
            } else {
                // to update the last access time
                CachedDataValue updatedValue = new CachedDataValue(value.data(),
                        value.dataType(),
                        clock.currentTimeMs(),
                        value.version());

                keyValueStore.put(key, updatedValue);
                resultFuture.complete(updatedValue);
            }
        });
        return resultFuture;
    }

    public CompletableFuture<CachedDataValue> set(DataKey key,
                                                  RequestDataValue value,
                                                  Long expectedOldVersion) {
        CompletableFuture<CachedDataValue> resultFuture = new CompletableFuture<>();

        eventLoop.execute(() -> {
            CachedDataValue existingValue = keyValueStore.get(key);
            OperationType operationType = existingValue == null
                    ? OperationType.INSERT
                    : operationType(key, value, existingValue, expectedOldVersion);

            switch (operationType) {
                case INSERT -> {
                    CachedDataValue updatedValue = CachedDataValue.createNewFrom(value, clock.currentTimeMs());
                    keyValueStore.put(key, updatedValue); // returns null if the value doesn't exist
                    resultFuture.complete(updatedValue);
                }
                case UPDATE -> {
                    CachedDataValue updatedValue = CachedDataValue.createUpdatedFrom(
                            value,
                            clock.currentTimeMs(),
                            existingValue.version() + 1);

                    keyValueStore.put(key, updatedValue);
                    resultFuture.complete(updatedValue);
                }
                case SKIP -> resultFuture.complete(keyValueStore.get(key));
                case VERSION_MISMATCH -> resultFuture.completeExceptionally(new ConcurrentUpdateException());
            }
        });

        return resultFuture;
    }

    public CompletableFuture<CachedDataValue> remove(DataKey key) {
        CompletableFuture<CachedDataValue> resultFuture = new CompletableFuture<>();

        eventLoop.execute(() -> {
                    CachedDataValue value = keyValueStore.remove(key);
                    resultFuture.complete(value);
                }
        );

        return resultFuture;
    }

    public CompletableFuture<Boolean> containsKey(DataKey key) {
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();

        eventLoop.execute(() -> resultFuture.complete(keyValueStore.containsKey(key)));

        return resultFuture;
    }

    public CompletableFuture<Integer> totalKeys() {
        CompletableFuture<Integer> resultFuture = new CompletableFuture<>();
        eventLoop.execute(() -> resultFuture.complete(keyValueStore.size()));
        return resultFuture;
    }

    public CompletableFuture<Set<DataKey>> getAllKeys() {
        CompletableFuture<Set<DataKey>> resultFuture = new CompletableFuture<>();
        eventLoop.execute(() -> {
            resultFuture.complete(keyValueStore.keySet());
        });

        return resultFuture;
    }


    private OperationType operationType(DataKey key, RequestDataValue newValue, CachedDataValue oldValue, Long expectedOldVersion) {
        long actualOldVersion = oldValue.version();

        if (expectedOldVersion == null || actualOldVersion == expectedOldVersion) {
            if (Arrays.equals(newValue.data(), oldValue.data())) return OperationType.SKIP;
            return OperationType.UPDATE;
        }

        if (expectedOldVersion == -1) {
            return keyValueStore.containsKey(key)
                    ? OperationType.VERSION_MISMATCH
                    : OperationType.INSERT;
        }

        return OperationType.VERSION_MISMATCH;
    }


    enum OperationType {
        INSERT,
        UPDATE,
        SKIP, VERSION_MISMATCH
    }
}


