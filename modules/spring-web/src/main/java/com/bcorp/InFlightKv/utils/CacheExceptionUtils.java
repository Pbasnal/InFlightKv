package com.bcorp.InFlightKv.utils;

import com.bcorp.InFlightKv.pojos.CacheErrorCode;
import com.bcorp.InFlightKv.pojos.CacheResponse;
import com.bcorp.exceptions.ConcurrentUpdateException;

import java.util.concurrent.CompletionException;

public class CacheExceptionUtils {
    public static CacheResponse<String> handleCacheExceptions(Throwable e) {
        // CompletableFuture wraps exceptions in CompletionException
        Throwable cause = (e instanceof CompletionException) ? e.getCause() : e;

        if (cause instanceof ConcurrentUpdateException) {
            return handleConflict((ConcurrentUpdateException) cause);
        }
        return handleGenericError(cause);
    }

    private static CacheResponse<String> handleConflict(ConcurrentUpdateException ex) {
        return CacheResponse.failure(CacheErrorCode.CONFLICT,
                "Value got updated concurrently by a different request. Try again");
    }

    private static CacheResponse<String> handleGenericError(Throwable ex) {
        return CacheResponse.failure(CacheErrorCode.INTERNAL_ERROR,
                "Something failed during processing of the request. Try again");
    }
}
