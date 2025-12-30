package com.bcorp.api;

import java.util.List;
import java.util.Optional;

public record CacheRequest<K>(
        K key,
        CacheRequestMethod method,
        Optional<?> value,
        List<Filter> filters
) {
}



