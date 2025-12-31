package com.bcorp.api.handlers;

import com.bcorp.api.CacheRequestMethod;

public record KeyValueHandlerKey(CacheRequestMethod method, Class<?> keyClass, Class<?> valueClass) {
}
