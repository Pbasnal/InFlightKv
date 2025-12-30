package com.bcorp.api;

import com.bcorp.CacheResponse;
import com.bcorp.kvstore.KeyValueStore;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface KeyValueRequestHandler<K, V> {
    CompletableFuture<CacheResponse<V>> handle(K key, V value, List<Filter> filters, KeyValueStore keyValueStore);
}
