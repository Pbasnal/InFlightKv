package com.bcorp.api;

import com.bcorp.api.filters.Filter;
import com.bcorp.api.filters.VersionFilter;
import com.bcorp.api.handlers.HandlerResolver;
import com.bcorp.api.handlers.KeyOnlyRequestHandler;
import com.bcorp.api.handlers.KeyValueRequestHandler;
import com.bcorp.kvstore.KeyValueStore;

import java.util.Collections;
import java.util.List;

public class KeyValueStoreEngine {
    private final KeyValueStore keyValueStore;
    private final HandlerResolver handlerResolver;

    public KeyValueStoreEngine(KeyValueStore _keyValueStore,
                               HandlerResolver _handlerResolver) {
        this.keyValueStore = _keyValueStore;
        this.handlerResolver = _handlerResolver;
    }

    public <K, V, R> R setCache(K key,
                                V value,
                                Long ifVersion,
                                CacheRequestMethod method) {
        KeyValueRequestHandler<K, V, R> setHandler = handlerResolver.resolveHandler(method, key, value);

        List<Filter> filters = ifVersion != null ?
                List.of(new VersionFilter(ifVersion)) :
                Collections.emptyList();

        return setHandler.handle(key, value, filters, keyValueStore).join();
    }

    public <K, R> R getCache(K key, CacheRequestMethod method) {
        KeyOnlyRequestHandler<K, R> getHandler = handlerResolver.resolveHandler(method, key);
        return getHandler.handle(key, Collections.emptyList(), keyValueStore).join();
    }

    public <K, R> R removeCache(K key, CacheRequestMethod method) {
        KeyOnlyRequestHandler<K, R> getHandler = handlerResolver.resolveHandler(method, key);
        return getHandler.handle(key, Collections.emptyList(), keyValueStore).join();
    }

}
