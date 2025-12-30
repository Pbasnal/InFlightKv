package com.bcorp.api;

public record KeyValueHandlerKey(CacheRequestMethod method, Class<?> keyClass, Class<?> valueClass) {
}
