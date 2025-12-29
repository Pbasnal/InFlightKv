package com.bcorp.api;

import com.bcorp.CacheResponse;
import com.bcorp.kvstore.KeyValueStore;

import java.util.concurrent.CompletableFuture;

public class StringGetRequestHandler implements IHandleRequests<String> {

    @Override
    public CompletableFuture<CacheResponse<String>> handle(CacheRequest request, KeyValueStore store) {
        return null;
    }
}
