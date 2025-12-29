package com.bcorp;

public record CacheResponse<T>(
        T data,
        long version
) {
}
