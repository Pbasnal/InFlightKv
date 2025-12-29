package com.bcorp.api;

import com.bcorp.CacheResponse;
import com.bcorp.kvstore.KeyValueStore;

import java.util.concurrent.CompletableFuture;

public interface IHandleRequests<T> {
    CompletableFuture<CacheResponse<T>> handle(CacheRequest request, KeyValueStore store);
}
