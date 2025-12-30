package com.bcorp.kvstore;

import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.DataValue;

import java.util.concurrent.CompletableFuture;

public class KeyValueStore {
    private final KeyValuePartition[] partitions;

    public KeyValueStore() {
        this.partitions = new KeyValuePartition[32];
        for (int i = 0; i < this.partitions.length; i++) {
            this.partitions[i] = new KeyValuePartition(i);
        }
    }

    // 2 options:
    /*
    1. take codec provider in constructor and resolve the required codec based on datatype
    2. Take the specific codec as an input argument and resolution happens in the api.
        Cons in 2: How would I know the data type before reading the key?
    3. Why have codec here? Let's make it part of the Api layer of the core
     */
    public CompletableFuture<DataValue> get(DataKey key) {
        return partitions[getPartition(key)].get(key);
    }

    public CompletableFuture<Boolean> containsKey(DataKey key) {
        return partitions[getPartition(key)].containsKey(key);
    }

    public CompletableFuture<DataValue> set(DataKey key, DataValue value, Long prevVersion) {
        return partitions[getPartition(key)].set(key, value, prevVersion);
    }

    public CompletableFuture<DataValue> remove(DataKey key) {
        return partitions[getPartition(key)].remove(key);
    }

    public long totalKeys() {
        long totalKeyCount = 0;
        for (int i = 0; i < partitions.length; i++) {
            totalKeyCount += partitions[i].totalKeys();
        }

        return totalKeyCount;
    }

    private int getPartition(DataKey key) {
        return (key.hashCode() & 0x7fffffff) % partitions.length;
    }
}


