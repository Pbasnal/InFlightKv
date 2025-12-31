package com.bcorp.api;

public interface CacheRequestMethod {
    record Get() implements CacheRequestMethod {
    }

    record Set() implements CacheRequestMethod {
    }

    record Remove() implements CacheRequestMethod {
    }

    static CacheRequestMethod get() {
        return new Get();
    }

    static CacheRequestMethod set() {
        return new Set();
    }

    static CacheRequestMethod remove() {
        return new Remove();
    }

}

