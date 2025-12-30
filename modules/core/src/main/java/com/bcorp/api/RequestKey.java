package com.bcorp.api;

import com.sun.net.httpserver.Request;

public sealed interface CacheRequest
        permits GetRequest, SetRequest, RemoveRequest {
}

public record RemoveRequest<K>(K key) implements CacheRequest {
}

