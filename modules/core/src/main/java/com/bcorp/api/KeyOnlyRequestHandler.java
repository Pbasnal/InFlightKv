package com.bcorp.api;

import com.bcorp.CacheResponse;
import com.bcorp.kvstore.KeyValueStore;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface KeyOnlyRequestHandler<T> {
    CompletableFuture<CacheResponse<?>> handle(T key, List<Filter> filters, KeyValueStore keyValueStore);
}
