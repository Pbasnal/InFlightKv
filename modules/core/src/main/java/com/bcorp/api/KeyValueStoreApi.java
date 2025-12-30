package com.bcorp.api;

import com.bcorp.CacheResponse;
import com.bcorp.kvstore.KeyValueStore;

import java.util.Collections;

public class KeyValueStoreApi {
    private final KeyValueStore keyValueStore;
    private final Router router;

    public KeyValueStoreApi(KeyValueStore _keyValueStore,
                            Router _router) {
        this.keyValueStore = _keyValueStore;
        this.router = _router;
    }

    public <K, V> CacheResponse<V> setCache(K key,
                                            V value) {
        KeyValueRequestHandler<K, V> setHandler = router.resolveHandler(key, value);
        return setHandler.handle(key, value, Collections.emptyList(), keyValueStore).join();
    }

    public <K> CacheResponse<?> getCache(K key) {

        KeyOnlyRequestHandler<K> getHandler = router.resolveHandler(key);
        return getHandler.handle(key, Collections.emptyList(), keyValueStore).join();
    }

}
