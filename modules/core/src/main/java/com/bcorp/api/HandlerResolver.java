package com.bcorp.api;

import com.bcorp.exceptions.HandlerNotFoundException;

import java.util.HashMap;
import java.util.Map;

public class HandlerResolver {

    /// ## Using class of types as keys for Handlers
    /// There are multiple conditions based on which we want to resolve the handlers.
    /// For example, to get a GetHandler amd RemoveHandler, want to resolve it based
    /// on the Class of the key but for SetHandlers we want to resolve it based on
    /// the Class of both Key and Value.
    /// Since we require processing of Keys and Values based on their Class types,
    /// This design helps to achieve that. And abstracting the Class types behind
    /// a Key object like GetHandlerKey/SetHandlerKey allows us to add more parameters
    /// in future.
    ///
    /// This has a major drawback. A lot of the code uses inheritance which will
    /// fail the key equality. For example, JsonNode is the abstract class and
    /// ObjectNode is the final implementation. While writing code, it'll be
    /// a good practice to use JsonNode instead of ObjectNode but if JsonNode is
    /// used to map the handler, then during runtime the map will not be able
    /// to resolve the correct handler because ObjectNode will be used.
    /// We can overcome the drawback by custom implementation of equals and contains
    /// for the keys.

    // Operations that depend ONLY on key type (GET, REMOVE, EXISTS)
    private final Map<KeyOnlyHandlerKey, KeyOnlyRequestHandler<?, ?>> keyOnlyHandlers;

    // Operations that depend on BOTH key and value type (SET, PATCH, MERGE)
    private final Map<KeyValueHandlerKey, KeyValueRequestHandler<?, ?, ?>> keyValueHandlers;

    public HandlerResolver() {
        this.keyOnlyHandlers = new HashMap<>();
        this.keyValueHandlers = new HashMap<>();
    }

    public <K, R> void registerKeyOnlyHandler(
            CacheRequestMethod method,
            Class<K> keyClass,
            KeyOnlyRequestHandler<K, R> handler) {
        keyOnlyHandlers.put(new KeyOnlyHandlerKey(method, keyClass), handler);
    }

    public <K, V, R> void registerKeyValueHandler(
            CacheRequestMethod method,
            Class<K> keyClass,
            Class<V> valueClass,
            KeyValueRequestHandler<K, V, R> handler) {
        keyValueHandlers.put(new KeyValueHandlerKey(method, keyClass, valueClass), handler);
    }

    @SuppressWarnings("unchecked")
    public <K, V, R> KeyValueRequestHandler<K, V, R> resolveHandler(CacheRequestMethod method, K key, V value) {
        return (KeyValueRequestHandler<K, V, R>)
                resolve(keyValueHandlers, new KeyValueHandlerKey(method, key.getClass(), value.getClass()));
    }


    @SuppressWarnings("unchecked")
    public <K, R> KeyOnlyRequestHandler<K, R> resolveHandler(CacheRequestMethod method, K key) {
        return (KeyOnlyRequestHandler<K, R>)
                resolve(keyOnlyHandlers, new KeyOnlyHandlerKey(method, key.getClass()));
    }

    private <T> T resolve(Map<?, T> map, Object key) {
        T handler = map.get(key);
        if (handler == null) {
            throw new HandlerNotFoundException("No handler for " + key);
        }
        return handler;
    }
}

