package com.bcorp.middlewares;

import com.bcorp.CacheResponse;

import java.util.concurrent.CompletableFuture;

public interface KvOperationsMiddleware<T, R> {
//    CompletableFuture<GenericDataValue<T>> execute(
//            String key,
//            Function<String, CompletableFuture<R>> kvOperation);

    CompletableFuture<CacheResponse<T>> execute(
            String key,
            T value,
            FunctionSet<String, T, CompletableFuture<R>> kvOperation);
}

