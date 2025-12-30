package com.bcorp.api;

import com.bcorp.CacheResponse;
import com.bcorp.kvstore.KeyValueStore;

import java.util.Collections;

public class KeyValueStoreEngine {
    private final KeyValueStore keyValueStore;
    private final Resolver resolver;

    public KeyValueStoreEngine(KeyValueStore _keyValueStore,
                               Resolver _resolver) {
        this.keyValueStore = _keyValueStore;
        this.resolver = _resolver;
    }

    public <K, V> CacheResponse<V> setCache(K key,
                                            V value) {
        KeyValueRequestHandler<K, V> setHandler = resolver.resolveHandler(CacheRequestMethod.SET, key, value);
        return setHandler.handle(key, value, Collections.emptyList(), keyValueStore).join();
    }

    public <K> CacheResponse<?> getCache(K key) {

        KeyOnlyRequestHandler<K> getHandler = resolver.resolveHandler(CacheRequestMethod.GET, key);
        return getHandler.handle(key, Collections.emptyList(), keyValueStore).join();
    }

}
