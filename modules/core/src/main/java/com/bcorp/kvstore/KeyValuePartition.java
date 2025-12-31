package com.bcorp.kvstore;

import com.bcorp.exceptions.ConcurrentUpdateException;
import com.bcorp.pojos.CachedDataValue;
import com.bcorp.pojos.RequestDataValue;
import com.bcorp.pojos.DataKey;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class KeyValuePartition {
    protected int partitionId;
    protected ExecutorService eventLoop;
    private final Map<DataKey, CachedDataValue> keyValueStore;
    private final KvStoreClock clock;
    private final AtomicLong totalKeys;

    public KeyValuePartition(int _partitionId, KvStoreClock _clock) {
        this.clock = _clock;
        this.partitionId = _partitionId;
        this.eventLoop = Executors.newSingleThreadExecutor();
        this.keyValueStore = new HashMap<>();
        this.totalKeys = new AtomicLong(0);
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
            OperationType operationType = Optional.ofNullable(existingValue)
                    .map(dataValue -> operationType(expectedOldVersion, value, dataValue))
                    .orElse(OperationType.INSERT);

            switch (operationType) {
                case INSERT -> {
                    CachedDataValue updatedValue = CachedDataValue.createNewFrom(value, clock.currentTimeMs());
                    keyValueStore.put(key, updatedValue); // returns null if the value doesn't exist
                    totalKeys.incrementAndGet();
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

    private OperationType operationType(Long expectedOldVersion, RequestDataValue newValue, CachedDataValue oldValue) {
        if (Arrays.equals(newValue.data(), oldValue.data())) return OperationType.SKIP;

        if (expectedOldVersion == null) return OperationType.UPDATE;

        long actualOldVersion = oldValue.version();
        return actualOldVersion == expectedOldVersion ?
                OperationType.UPDATE : OperationType.VERSION_MISMATCH;
    }


    enum OperationType {
        INSERT,
        UPDATE,
        SKIP, VERSION_MISMATCH
    }
}


