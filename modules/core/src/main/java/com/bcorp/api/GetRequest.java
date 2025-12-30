package com.bcorp.api;

public record GetRequest<K>(K key) implements CacheRequest {
}
