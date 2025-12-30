package com.bcorp.api.handlers;

import com.bcorp.api.CacheRequestMethod;

public record KeyOnlyHandlerKey(CacheRequestMethod method, Class<?> clazz) {
}
