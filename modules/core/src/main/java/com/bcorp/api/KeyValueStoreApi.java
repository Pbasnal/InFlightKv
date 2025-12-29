package com.bcorp.api;

import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.codec.CodecProvider;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

public class KeyValueStoreApi {
    private final KeyValueStore keyValueStore;
    private final Router router;

    public KeyValueStoreApi(KeyValueStore _keyValueStore,
                            Router _router) {
        this.keyValueStore = _keyValueStore;
        this.router = _router;
    }

//    public CompletableFuture<?> get(String key) {
//        CacheRequest request = new CacheRequest(key,
//                CacheRequestMethod.GET,
//                Collections.emptyList());
//        IHandleRequests<?> handler = router.getHandler(request, null);
//
//        return handler.handle(request, keyValueStore);
//    }
}
