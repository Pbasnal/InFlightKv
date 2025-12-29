package com.bcorp;

import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.DataValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DemoInternalShardingTests {
    @Test
    public void onlyOneThreadPerPartition() {
        KeyValueStore store = new KeyValueStore();
        int threads = 50;
        ExecutorService callers = Executors.newFixedThreadPool(threads);

        List<CompletableFuture<?>> futures = new ArrayList<>();

        int totalKeys = 10_000;

        for (int i = 0; i < totalKeys; i++) {
            int id = i;
            futures.add(CompletableFuture.runAsync(() ->
                            store.set(toKey("key-" + id), toValue("v" + id), null),
                    callers)
            );
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        futures.clear();
        for (int i = 0; i < totalKeys; i++) {
            int id = i;
            futures.add(CompletableFuture.runAsync(() ->
                            store.get(toKey("key-" + id))
                                    .whenComplete((value, ex) -> {
                                        assert ex == null;
                                        assert toValue("v" + id).equals(value);
                                    }),
                    callers)
            );
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    @Test
    public void testRemoveKeyShouldRemoveTheKeyFromCorrectPartition() {
        KeyValueStore store = new KeyValueStore();
        int threads = 50;
        ExecutorService callers = Executors.newFixedThreadPool(threads);

        List<CompletableFuture<?>> futures = new ArrayList<>();

        int totalKeys = 10_000;

        for (int i = 0; i < totalKeys; i++) {
            int id = i;
            futures.add(CompletableFuture.runAsync(() ->
                            store.set(toKey("key-" + id), toValue("v" + id), null),
                    callers)
            );
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        assertEquals(totalKeys, store.totalKeys());
        futures.clear();
        for (int i = 0; i < totalKeys; i++) {
            int id = i;
            futures.add(CompletableFuture.runAsync(() ->
                            store.remove(toKey("key-" + id))
                                    .whenComplete((value, ex) -> {
                                        assert ex == null;
                                        assert toValue("v" + id).equals(value);
                                    }),
                    callers)
            );
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        assertEquals(0, store.totalKeys());
    }

    private DataKey toKey(String str) {
        return new DataKey(str);
    }

    private DataValue toValue(String str) {
        return DataValue.fromString(str);
    }

}
