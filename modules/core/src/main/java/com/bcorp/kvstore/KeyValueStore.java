package com.bcorp.kvstore;

import com.bcorp.pojos.CachedDataValue;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.RequestDataValue;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class KeyValueStore {
    private final KeyValuePartition[] partitions;
    private final KvStoreClock clock;

    public KeyValueStore(KvStoreClock _clock) {
        this.clock = _clock;
        this.partitions = new KeyValuePartition[32];
        for (int i = 0; i < this.partitions.length; i++) {
            this.partitions[i] = new KeyValuePartition(i, clock);
        }
    }

    // 2 options:
    /*
    1. take codec provider in constructor and resolve the required codec based on datatype
    2. Take the specific codec as an input argument and resolution happens in the api.
        Cons in 2: How would I know the data type before reading the key?
    3. Why have codec here? Let's make it part of the Api layer of the core
     */
    public CompletableFuture<CachedDataValue> get(DataKey key) {
        return partitions[getPartition(key)].get(key);
    }

    public CompletableFuture<Boolean> containsKey(DataKey key) {
        return partitions[getPartition(key)].containsKey(key);
    }

    public CompletableFuture<CachedDataValue> set(DataKey key, RequestDataValue value, Long prevVersion) {
        return partitions[getPartition(key)].set(key, value, prevVersion);
    }

    public CompletableFuture<CachedDataValue> remove(DataKey key) {
        return partitions[getPartition(key)].remove(key);
    }

    public CompletableFuture<Long> totalKeys() {
        CompletableFuture<Long>[] futures = Arrays.stream(partitions)
                .map(KeyValuePartition::totalKeys)
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
                .thenApply(v -> Arrays.stream(futures)
                        .mapToLong(CompletableFuture::join)
                        .sum()
                );

    }

    public CompletableFuture<List<DataKey>> getAllKeys() {
        // 1. Create an array of futures from your partitions
        CompletableFuture<Set<DataKey>>[] futures =
                Arrays.stream(partitions)
                        .map(KeyValuePartition::getAllKeys)
                        .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
                .thenApply(v -> Arrays.stream(futures)
                        .map(CompletableFuture::join)
                        .flatMap(Set::stream)
                        .collect(Collectors.toList())
                );
    }


    private int getPartition(DataKey key) {
        return (key.hashCode() & 0x7fffffff) % partitions.length;
    }
}


