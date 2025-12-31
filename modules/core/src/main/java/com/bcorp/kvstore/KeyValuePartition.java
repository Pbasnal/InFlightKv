package com.bcorp.kvstore;

import com.bcorp.exceptions.ConcurrentUpdateException;
import com.bcorp.pojos.DataValue;
import com.bcorp.pojos.DataKey;

import java.util.Arrays;
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

            if (value == null) {
                resultFuture.complete(null);
            } else {
                // to update the last access time
                DataValue updatedValue = new DataValue(value.data(),
                        value.dataType(),
                        System.currentTimeMillis(),
                        value.version());

                keyValueStore.put(key, updatedValue);
                resultFuture.complete(updatedValue);
            }
        });
        return resultFuture;
    }

    public CompletableFuture<DataValue> set(DataKey key,
                                            DataValue value,
                                            Long expectedOldVersion) {
        CompletableFuture<DataValue> resultFuture = new CompletableFuture<>();

        eventLoop.execute(() -> {
            switch (operationType(key, expectedOldVersion, value)) {
                case INSERT -> {
                    DataValue updatedValue = new DataValue(value.data(),
                            value.dataType(),
                            System.currentTimeMillis(),
                            0L);
                    keyValueStore.put(key, updatedValue); // returns null if the value doesn't exist
                    totalKeys.incrementAndGet();
                    resultFuture.complete(updatedValue);
                }
                case UPDATE -> {
                    keyValueStore.put(key, new DataValue(value.data(),
                            value.dataType(),
                            System.currentTimeMillis(),
                            keyValueStore.get(key).version() + 1));
                    resultFuture.complete(keyValueStore.get(key));
                }
                case SKIP -> resultFuture.complete(keyValueStore.get(key));
                case VERSION_MISMATCH -> resultFuture.completeExceptionally(new ConcurrentUpdateException());
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

    public CompletableFuture<Boolean> containsKey(DataKey key) {
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();

        eventLoop.execute(() -> resultFuture.complete(keyValueStore.containsKey(key)));

        return resultFuture;
    }

    public long totalKeys() {
        return totalKeys.get();
    }

    private OperationType operationType(DataKey key, Long expectedOldVersion, DataValue newValue) {

        if (!keyValueStore.containsKey(key)) return OperationType.INSERT;

        DataValue existingEntry = keyValueStore.get(key);
        if (Arrays.equals(newValue.data(), existingEntry.data())) return OperationType.SKIP;

        if (expectedOldVersion == null) return OperationType.UPDATE;

        long actualOldVersion = existingEntry.version();
        return actualOldVersion == expectedOldVersion ?
                OperationType.UPDATE : OperationType.VERSION_MISMATCH;
    }


    enum OperationType {
        INSERT,
        UPDATE,
        SKIP, VERSION_MISMATCH
    }
}


