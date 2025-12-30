package com.bcorp.api.handlers;

import com.bcorp.api.CacheRequestMethod;
import com.bcorp.exceptions.DuplicateHandlerRegistration;
import com.bcorp.exceptions.HandlerNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class HandlerResolverTest {

    private HandlerResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new HandlerResolver();
    }

    @Test
    void shouldRegisterAndResolveKeyOnlyHandler() {
        // Given
        KeyOnlyRequestHandler<String, String> handler =
                (key, filters, store) -> CompletableFuture.completedFuture("result");

        // When
        resolver.registerKeyOnlyHandler(CacheRequestMethod.get(), String.class, handler);

        // Then
        KeyOnlyRequestHandler<String, String> resolved = resolver.resolveHandler(CacheRequestMethod.get(), "test");
        assertNotNull(resolved);
        assertEquals(handler, resolved);
    }

    @Test
    void shouldRegisterAndResolveKeyValueHandler() {
        // Given
        KeyValueRequestHandler<String, Integer, String> handler =
                (key, value, filters, store) -> CompletableFuture.completedFuture("result");

        // When
        resolver.registerKeyValueHandler(CacheRequestMethod.set(), String.class, Integer.class, handler);

        // Then
        KeyValueRequestHandler<String, Integer, String> resolved = resolver.resolveHandler(CacheRequestMethod.set(), "key", 42);
        assertNotNull(resolved);
        assertEquals(handler, resolved);
    }

    @Test
    void shouldThrowExceptionForDuplicateKeyValueHandlerRegistration() {
        // Given
        KeyValueRequestHandler<String, Integer, String> handler1 =
                (key, value, filters, store) -> CompletableFuture.completedFuture("result1");
        KeyValueRequestHandler<String, Integer, String> handler2 =
                (key, value, filters, store) -> CompletableFuture.completedFuture("result2");

        resolver.registerKeyValueHandler(CacheRequestMethod.set(), String.class, Integer.class, handler1);

        // When & Then
        assertThrows(DuplicateHandlerRegistration.class, () ->
                resolver.registerKeyValueHandler(CacheRequestMethod.set(), String.class, Integer.class, handler2)
        );
    }

    @Test
    void shouldResolveDifferentHandlersForDifferentMethods() {
        // Given
        KeyOnlyRequestHandler<String, String> getHandler =
                (key, filters, store) -> CompletableFuture.completedFuture("get-result");
        KeyOnlyRequestHandler<String, String> setHandler =
                (key, filters, store) -> CompletableFuture.completedFuture("set-result");

        // When
        resolver.registerKeyOnlyHandler(CacheRequestMethod.get(), String.class, getHandler);
        resolver.registerKeyOnlyHandler(CacheRequestMethod.set(), String.class, setHandler);

        // Then
        KeyOnlyRequestHandler<String, String> resolvedGet = resolver.resolveHandler(CacheRequestMethod.get(), "key");
        KeyOnlyRequestHandler<String, String> resolvedSet = resolver.resolveHandler(CacheRequestMethod.set(), "key");

        assertNotNull(resolvedGet);
        assertNotNull(resolvedSet);
        assertEquals(getHandler, resolvedGet);
        assertEquals(setHandler, resolvedSet);
    }

    @Test
    void shouldResolveDifferentHandlersForDifferentKeyTypes() {
        // Given
        KeyOnlyRequestHandler<String, String> stringHandler =
                (key, filters, store) -> CompletableFuture.completedFuture("string-result");
        KeyOnlyRequestHandler<Integer, String> intHandler =
                (key, filters, store) -> CompletableFuture.completedFuture("int-result");

        // When
        resolver.registerKeyOnlyHandler(CacheRequestMethod.get(), String.class, stringHandler);
        resolver.registerKeyOnlyHandler(CacheRequestMethod.get(), Integer.class, intHandler);

        // Then
        KeyOnlyRequestHandler<String, String> resolvedString = resolver.resolveHandler(CacheRequestMethod.get(), "key");
        KeyOnlyRequestHandler<Integer, String> resolvedInt = resolver.resolveHandler(CacheRequestMethod.get(), 42);

        assertNotNull(resolvedString);
        assertNotNull(resolvedInt);
        assertEquals(stringHandler, resolvedString);
        assertEquals(intHandler, resolvedInt);
    }

    @Test
    void shouldResolveDifferentHandlersForDifferentValueTypes() {
        // Given
        KeyValueRequestHandler<String, Integer, String> intHandler =
                (key, value, filters, store) -> CompletableFuture.completedFuture("int-result");
        KeyValueRequestHandler<String, Double, String> doubleHandler =
                (key, value, filters, store) -> CompletableFuture.completedFuture("double-result");

        // When
        resolver.registerKeyValueHandler(CacheRequestMethod.set(), String.class, Integer.class, intHandler);
        resolver.registerKeyValueHandler(CacheRequestMethod.set(), String.class, Double.class, doubleHandler);

        // Then
        KeyValueRequestHandler<String, Integer, String> resolvedInt = resolver.resolveHandler(CacheRequestMethod.set(), "key", 42);
        KeyValueRequestHandler<String, Double, String> resolvedDouble = resolver.resolveHandler(CacheRequestMethod.set(), "key", 3.14);

        assertNotNull(resolvedInt);
        assertNotNull(resolvedDouble);
        assertEquals(intHandler, resolvedInt);
        assertEquals(doubleHandler, resolvedDouble);
    }

    @Test
    void shouldThrowExceptionWhenKeyOnlyHandlerNotFound() {
        // When & Then
        assertThrows(HandlerNotFoundException.class, () ->
                resolver.resolveHandler(CacheRequestMethod.get(), "key")
        );
    }

    @Test
    void shouldThrowExceptionWhenKeyValueHandlerNotFound() {
        // When & Then
        assertThrows(HandlerNotFoundException.class, () ->
                resolver.resolveHandler(CacheRequestMethod.set(), "key", "value")
        );
    }

    @Test
    void shouldThrowExceptionWhenKeyValueHandlerNotFoundForDifferentValueType() {
        // Given
        KeyValueRequestHandler<String, Integer, String> handler =
                (key, value, filters, store) -> CompletableFuture.completedFuture("result");
        resolver.registerKeyValueHandler(CacheRequestMethod.set(), String.class, Integer.class, handler);

        // When & Then - trying to resolve with Double value type but only Integer is registered
        assertThrows(HandlerNotFoundException.class, () ->
                resolver.resolveHandler(CacheRequestMethod.set(), "key", 3.14)
        );
    }

    @Test
    void shouldAllowMultipleRegistrationsForDifferentCombinations() {
        // Given
        KeyValueRequestHandler<String, Integer, String> stringIntHandler =
                (key, value, filters, store) -> CompletableFuture.completedFuture("string-int");
        KeyValueRequestHandler<String, Double, String> stringDoubleHandler =
                (key, value, filters, store) -> CompletableFuture.completedFuture("string-double");
        KeyValueRequestHandler<Integer, Integer, String> intIntHandler =
                (key, value, filters, store) -> CompletableFuture.completedFuture("int-int");

        // When
        resolver.registerKeyValueHandler(CacheRequestMethod.set(), String.class, Integer.class, stringIntHandler);
        resolver.registerKeyValueHandler(CacheRequestMethod.set(), String.class, Double.class, stringDoubleHandler);
        resolver.registerKeyValueHandler(CacheRequestMethod.set(), Integer.class, Integer.class, intIntHandler);

        // Then - all should resolve correctly
        assertNotNull(resolver.resolveHandler(CacheRequestMethod.set(), "key", 42));
        assertNotNull(resolver.resolveHandler(CacheRequestMethod.set(), "key", 3.14));
        assertNotNull(resolver.resolveHandler(CacheRequestMethod.set(), 123, 456));
    }
}
