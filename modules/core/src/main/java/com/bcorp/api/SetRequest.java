package com.bcorp.api;

public record SetRequest<K,V>(K key, V value) implements CacheRequest {
}
