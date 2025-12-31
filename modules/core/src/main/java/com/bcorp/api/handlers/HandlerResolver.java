package com.bcorp.api.handlers;

import com.bcorp.api.CacheRequestMethod;
import com.bcorp.exceptions.DuplicateHandlerRegistration;
import com.bcorp.exceptions.HandlerNotFoundException;

import java.util.HashMap;
import java.util.Map;

public class HandlerResolver {

    /**
     * Handler resolver for type-safe key-value store operations.
     *
     * <p>This resolver manages handler registration and lookup using class types as keys.
     * Operations are resolved based on different strategies:</p>
     * <ul>
     *   <li><strong>Key-only operations</strong> (GET, REMOVE, EXISTS): Resolved by key class type only</li>
     *   <li><strong>Key-value operations</strong> (SET, PATCH, MERGE): Resolved by both key and value class types</li>
     * </ul>
     *
     * <p>This enables type-safe processing while maintaining extensibility through HandlerKey abstractions.</p>
     *
     * <h3>Inheritance Considerations</h3>
     * <p>Inheritance can cause resolution failures when abstract classes are registered but concrete
     * implementations are used at runtime (e.g., registering for {@code JsonNode} but using {@code ObjectNode}).
     * Mitigations include using concrete types consistently.</p>
     */

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
        KeyOnlyHandlerKey key = new KeyOnlyHandlerKey(method, keyClass);
        if (keyOnlyHandlers.containsKey(key)) {
            throw new DuplicateHandlerRegistration("Handler for " + key + " already registered");
        }
        keyOnlyHandlers.put(new KeyOnlyHandlerKey(method, keyClass), handler);
    }

    public <K, V, R> void registerKeyValueHandler(
            CacheRequestMethod method,
            Class<K> keyClass,
            Class<V> valueClass,
            KeyValueRequestHandler<K, V, R> handler) {

        KeyValueHandlerKey key = new KeyValueHandlerKey(method, keyClass, valueClass);
        if (keyValueHandlers.containsKey(key)) {
            throw new DuplicateHandlerRegistration("Handler for " + key + " already registered");
        }

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

