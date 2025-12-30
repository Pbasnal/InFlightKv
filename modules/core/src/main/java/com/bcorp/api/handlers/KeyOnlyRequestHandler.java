package com.bcorp.api.handlers;

import com.bcorp.api.filters.Filter;
import com.bcorp.kvstore.KeyValueStore;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface KeyOnlyRequestHandler<T, R> {
    CompletableFuture<R> handle(T key, List<Filter> filters, KeyValueStore keyValueStore);
}
