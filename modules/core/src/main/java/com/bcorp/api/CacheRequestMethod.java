package com.bcorp.api;

public interface CacheRequestMethod {
    record Get() implements CacheRequestMethod {
    }
    record Set() implements CacheRequestMethod {
    }

    static CacheRequestMethod get() {
        return new Get();
    }

    static CacheRequestMethod set() {
        return new Set();
    }
}

