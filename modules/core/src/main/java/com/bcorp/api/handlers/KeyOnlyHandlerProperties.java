package com.bcorp.api.handlers;

import com.bcorp.api.CacheRequestMethod;

public record KeyOnlyHandlerProperties(CacheRequestMethod method, Class<?> clazz) {
}
