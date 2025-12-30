package com.bcorp.api;

import com.bcorp.api.filters.VersionFilter;
import com.bcorp.api.handlers.HandlerResolver;
import com.bcorp.api.handlers.KeyOnlyRequestHandler;
import com.bcorp.api.handlers.KeyValueRequestHandler;
import com.bcorp.kvstore.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KeyValueStoreEngineTest {

    @Mock
    private KeyValueStore keyValueStore;

    @Mock
    private HandlerResolver handlerResolver;

    @Mock
    private KeyValueRequestHandler<String, Integer, String> keyValueHandler;

    @Mock
    private KeyOnlyRequestHandler<String, String> keyOnlyHandler;

    private KeyValueStoreEngine engine;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        engine = new KeyValueStoreEngine(keyValueStore, handlerResolver);
    }

    @Test
    void shouldSetCacheWithoutVersion() {
        // Given
        when(handlerResolver.<String, Integer, String>resolveHandler(CacheRequestMethod.set(), "key", 42))
                .thenReturn(keyValueHandler);
        when(keyValueHandler.handle(eq("key"), eq(42), anyList(), eq(keyValueStore)))
                .thenReturn(CompletableFuture.completedFuture("result"));

        // When
        String result = engine.setCache("key", 42, null, CacheRequestMethod.set());

        // Then
        assertEquals("result", result);
        verify(handlerResolver).resolveHandler(CacheRequestMethod.set(), "key", 42);
        verify(keyValueHandler).handle("key", 42, Collections.emptyList(), keyValueStore);
    }

    @Test
    void shouldSetCacheWithVersion() {
        // Given
        when(handlerResolver.<String, Integer, String>resolveHandler(CacheRequestMethod.set(), "key", 42))
                .thenReturn(keyValueHandler);
        when(keyValueHandler.handle(eq("key"), eq(42), anyList(), eq(keyValueStore)))
                .thenReturn(CompletableFuture.completedFuture("result"));

        // When
        String result = engine.setCache("key", 42, 1L, CacheRequestMethod.set());

        // Then
        assertEquals("result", result);
        verify(handlerResolver).resolveHandler(CacheRequestMethod.set(), "key", 42);

        // Verify that VersionFilter is passed in the filters list
        verify(keyValueHandler).handle(eq("key"), eq(42), argThat(filters -> {
            assertEquals(1, filters.size());
            assertTrue(filters.get(0) instanceof VersionFilter);
            VersionFilter versionFilter = (VersionFilter) filters.get(0);
            assertEquals(1L, versionFilter.version());
            return true;
        }), eq(keyValueStore));
    }

    @Test
    void shouldGetCache() {
        // Given
        when(handlerResolver.<String, String>resolveHandler(CacheRequestMethod.get(), "key"))
                .thenReturn(keyOnlyHandler);
        when(keyOnlyHandler.handle(eq("key"), anyList(), eq(keyValueStore)))
                .thenReturn(CompletableFuture.completedFuture("cached-value"));

        // When
        String result = engine.getCache("key", CacheRequestMethod.get());

        // Then
        assertEquals("cached-value", result);
        verify(handlerResolver).resolveHandler(CacheRequestMethod.get(), "key");
        verify(keyOnlyHandler).handle("key", Collections.emptyList(), keyValueStore);
    }

    @Test
    void shouldRemoveCache() {
        // Given
        when(handlerResolver.<String, String>resolveHandler(CacheRequestMethod.get(), "key"))
                .thenReturn(keyOnlyHandler);
        when(keyOnlyHandler.handle(eq("key"), anyList(), eq(keyValueStore)))
                .thenReturn(CompletableFuture.completedFuture("removed"));

        // When
        String result = engine.removeCache("key", CacheRequestMethod.get());

        // Then
        assertEquals("removed", result);
        verify(handlerResolver).resolveHandler(CacheRequestMethod.get(), "key");
        verify(keyOnlyHandler).handle("key", Collections.emptyList(), keyValueStore);
    }

    @Test
    void shouldHandleDifferentGenericTypes() {
        // Test with different key/value types
        KeyValueRequestHandler<Integer, Double, Integer> intDoubleHandler =
                mock(KeyValueRequestHandler.class);

        when(handlerResolver.<Integer, Double, Integer>resolveHandler(CacheRequestMethod.set(), 123, 45.67))
                .thenReturn(intDoubleHandler);
        when(intDoubleHandler.handle(any(), any(), anyList(), eq(keyValueStore)))
                .thenReturn(CompletableFuture.completedFuture(999));

        // When
        Integer result = engine.setCache(123, 45.67, null, CacheRequestMethod.set());

        // Then
        assertEquals(999, result);
        verify(handlerResolver).resolveHandler(CacheRequestMethod.set(), 123, 45.67);
    }

    @Test
    void shouldHandleDifferentCacheMethods() {
        // Test different CacheRequestMethod types
        when(handlerResolver.<String, String>resolveHandler(CacheRequestMethod.get(), "test-key"))
                .thenReturn(keyOnlyHandler);
        when(keyOnlyHandler.handle(any(), anyList(), eq(keyValueStore)))
                .thenReturn(CompletableFuture.completedFuture("method-result"));

        // When - testing with different method
        String result = engine.getCache("test-key", CacheRequestMethod.get());

        // Then
        assertEquals("method-result", result);
        verify(handlerResolver).resolveHandler(CacheRequestMethod.get(), "test-key");
    }

    @Test
    void shouldPropagateExceptionsFromHandlers() {
        // Given
        RuntimeException expectedException = new RuntimeException("Handler failed");
        when(handlerResolver.<String, String>resolveHandler(any(), any()))
                .thenReturn(keyOnlyHandler);
        when(keyOnlyHandler.handle(any(), anyList(), eq(keyValueStore)))
                .thenReturn(CompletableFuture.failedFuture(expectedException));

        // When & Then

        Throwable thrown = assertThrows(Exception.class, () ->
                engine.getCache("key", CacheRequestMethod.get())
        );
        if (thrown instanceof CompletionException) {
            thrown = thrown.getCause();
        }

        assertEquals(expectedException, thrown);
    }

    @Test
    void shouldHandleNullVersionParameter() {
        // Given
        when(handlerResolver.<String, Integer, String>resolveHandler(CacheRequestMethod.set(), "key", 1))
                .thenReturn(keyValueHandler);
        when(keyValueHandler.handle(any(), any(), anyList(), eq(keyValueStore)))
                .thenReturn(CompletableFuture.completedFuture("null-version-result"));

        // When - explicitly pass null version
        String result = engine.setCache("key", 1, null, CacheRequestMethod.set());

        // Then
        assertEquals("null-version-result", result);
        verify(keyValueHandler).handle(any(), any(), eq(Collections.emptyList()), eq(keyValueStore));
    }

    @Test
    void shouldHandleZeroVersionParameter() {
        // Given
        when(handlerResolver.<String, Integer, String>resolveHandler(CacheRequestMethod.set(), "key", 1))
                .thenReturn(keyValueHandler);
        when(keyValueHandler.handle(any(), any(), anyList(), eq(keyValueStore)))
                .thenReturn(CompletableFuture.completedFuture("zero-version-result"));

        // When - pass zero as version
        String result = engine.setCache("key", 1, 0L, CacheRequestMethod.set());

        // Then
        assertEquals("zero-version-result", result);
        verify(keyValueHandler).handle(any(), any(), argThat(filters -> {
            assertEquals(1, filters.size());
            assertTrue(filters.get(0) instanceof VersionFilter);
            VersionFilter versionFilter = (VersionFilter) filters.get(0);
            assertEquals(0L, versionFilter.version());
            return true;
        }), eq(keyValueStore));
    }

    @Test
    void shouldSupportComplexGenericTypes() {
        // Test with more complex generic types
        KeyValueRequestHandler<String, List<Integer>, Boolean> complexHandler =
                mock(KeyValueRequestHandler.class);

        when(handlerResolver.<String, List<Integer>, Boolean>resolveHandler(CacheRequestMethod.set(), "complex-key", List.of(1, 2, 3)))
                .thenReturn(complexHandler);
        when(complexHandler.handle(any(), any(), anyList(), eq(keyValueStore)))
                .thenReturn(CompletableFuture.completedFuture(true));

        // When
        Boolean result = engine.setCache("complex-key", List.of(1, 2, 3), 5L, CacheRequestMethod.set());

        // Then
        assertTrue(result);
        verify(handlerResolver).resolveHandler(CacheRequestMethod.set(), "complex-key", List.of(1, 2, 3));
    }

    @Test
    void shouldHandleLargeVersionNumbers() {
        // Given
        long largeVersion = Long.MAX_VALUE;
        when(handlerResolver.<String, Integer, String>resolveHandler(CacheRequestMethod.set(), "key", 1))
                .thenReturn(keyValueHandler);
        when(keyValueHandler.handle(any(), any(), anyList(), eq(keyValueStore)))
                .thenReturn(CompletableFuture.completedFuture("large-version-result"));

        // When
        String result = engine.setCache("key", 1, largeVersion, CacheRequestMethod.set());

        // Then
        assertEquals("large-version-result", result);
        verify(keyValueHandler).handle(any(), any(), argThat(filters -> {
            assertEquals(1, filters.size());
            VersionFilter versionFilter = (VersionFilter) filters.get(0);
            assertEquals(largeVersion, versionFilter.version());
            return true;
        }), eq(keyValueStore));
    }

    @Test
    void shouldCreateEngineWithDependencies() {
        // Given
        KeyValueStore testKeyValueStore = mock(KeyValueStore.class);
        HandlerResolver testHandlerResolver = mock(HandlerResolver.class);

        // When
        KeyValueStoreEngine testEngine = new KeyValueStoreEngine(testKeyValueStore, testHandlerResolver);

        // Then
        assertNotNull(testEngine);
        // Constructor assigns the dependencies, but we can't directly verify private fields
        // This test ensures the constructor doesn't throw exceptions
    }
}
