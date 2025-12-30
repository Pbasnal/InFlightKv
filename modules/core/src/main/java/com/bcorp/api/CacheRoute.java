package com.bcorp.api;

public record CacheRoute<T>(CacheRequestMethod method, Class<T> clazz) {
}

