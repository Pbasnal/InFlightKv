package com.bcorp.api.handlers;

import com.bcorp.api.CacheRequestMethod;

public record KeyValueHandlerProperties(CacheRequestMethod method, Class<?> keyClass, Class<?> valueClass) {
}
