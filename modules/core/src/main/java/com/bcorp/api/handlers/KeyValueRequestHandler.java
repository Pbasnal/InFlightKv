package com.bcorp.api.handlers;

import com.bcorp.api.filters.Filter;
import com.bcorp.kvstore.KeyValueStore;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface KeyValueRequestHandler<K, V, R> {
    CompletableFuture<R> handle(K key, V value, List<Filter> filters, KeyValueStore keyValueStore);
}
