package com.bcorp.kvstore;

import com.bcorp.exceptions.ConcurrentUpdateException;
import com.bcorp.pojos.DataValue;
import com.bcorp.pojos.DataKey;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class KeyValuePartition {
    protected int partitionId;
    protected ExecutorService eventLoop;
    private final Map<DataKey, DataValue> keyValueStore;

    private final AtomicLong totalKeys;

    public KeyValuePartition(int _partitionId) {
        this.partitionId = _partitionId;
        this.eventLoop = Executors.newSingleThreadExecutor();
        this.keyValueStore = new HashMap<>();
        this.totalKeys = new AtomicLong(0);
    }

    public CompletableFuture<DataValue> get(DataKey key) {
        CompletableFuture<DataValue> resultFuture = new CompletableFuture<>();
        eventLoop.execute(() -> {
            DataValue value = keyValueStore.get(key);
            keyValueStore.put(key, new DataValue(value.data(),
                    value.dataType(),
                    System.currentTimeMillis(),
                    value.version()));
            resultFuture.complete(value);
        });
        return resultFuture;
    }

    public CompletableFuture<DataValue> set(DataKey key,
                                            DataValue value,
                                            Long expectedOldVersion) {
        CompletableFuture<DataValue> resultFuture = new CompletableFuture<>();

        eventLoop.execute(() -> {
            switch (operationType(key, expectedOldVersion)) {
                case INSERT -> {
                    DataValue updatedValue = new DataValue(value.data(),
                            value.dataType(),
                            System.currentTimeMillis(),
                            0);
                    keyValueStore.put(key, updatedValue); // returns null if the value doesn't exist
                    totalKeys.incrementAndGet();
                    resultFuture.complete(updatedValue);
                }
                case UPDATE -> {
                    DataValue updatedValue = keyValueStore.put(key, new DataValue(value.data(),
                            value.dataType(),
                            System.currentTimeMillis(),
                            keyValueStore.get(key).version() + 1));
                    resultFuture.complete(updatedValue);

                }
                default -> resultFuture.completeExceptionally(new ConcurrentUpdateException());
            }
        });

        return resultFuture;
    }

    public CompletableFuture<DataValue> remove(DataKey key) {
        CompletableFuture<DataValue> resultFuture = new CompletableFuture<>();

        eventLoop.execute(() -> {
                    DataValue value = keyValueStore.remove(key);
                    if (value != null) totalKeys.decrementAndGet();

                    resultFuture.complete(value);
                }
        );

        return resultFuture;
    }

    public long totalKeys() {
        return totalKeys.get();
    }

    private OperationType operationType(DataKey key, Long expectedOldVersion) {

        if (!keyValueStore.containsKey(key)) return OperationType.INSERT; // insert

        if (expectedOldVersion == null) return OperationType.UPDATE; // update the existing version

        // update
        DataValue existingEntry = keyValueStore.get(key);
        long actualOldVersion = existingEntry.version();

        return actualOldVersion == expectedOldVersion ?
                OperationType.UPDATE : OperationType.VERSION_MISMATCH; // update using new version or fail
    }

    enum OperationType {
        INSERT,
        UPDATE,
        VERSION_MISMATCH
    }
}


