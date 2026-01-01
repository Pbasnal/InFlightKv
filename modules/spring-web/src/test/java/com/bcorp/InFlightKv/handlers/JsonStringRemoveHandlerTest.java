package com.bcorp.InFlightKv.handlers;

import com.bcorp.InFlightKv.pojos.CacheErrorCode;
import com.bcorp.InFlightKv.pojos.CacheResponse;
import com.bcorp.api.filters.Filter;
import com.bcorp.codec.JsonCodec;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.CachedDataValue;
import com.bcorp.pojos.DataKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JsonStringRemoveHandler Unit Tests")
public class JsonStringRemoveHandlerTest {

    @Mock
    private KeyValueStore keyValueStore;

    private JsonCodec jsonCodec = new JsonCodec();

    private JsonStringRemoveHandler handler;
    private String testKey = "test-key";
    private DataKey testDataKey;
    private CachedDataValue testCachedDataValue;
    private JsonNode mockJsonNode;
    private String jsonData = "{\"test\":\"data\"}";
    private List<Filter> emptyFilters = Collections.emptyList();

    @BeforeEach
    void setUp() {
        handler = new JsonStringRemoveHandler(jsonCodec);
        testDataKey = new DataKey(testKey);

        // Create test cached data value
        testCachedDataValue = new CachedDataValue(
                jsonData.getBytes(StandardCharsets.UTF_8),
                String.class,
                System.currentTimeMillis(),
                5L
        );

        mockJsonNode = JsonNodeFactory.instance.objectNode().put("test", "data");
    }

    @Test
    @DisplayName("Constructor should create handler with json codec")
    void constructorShouldCreateHandlerWithJsonCodec() {
        // Given & When
        JsonStringRemoveHandler newHandler = new JsonStringRemoveHandler(jsonCodec);

        // Then
        assertNotNull(newHandler);
    }

    @Test
    @DisplayName("Should successfully remove existing key and return decoded data")
    void shouldSuccessfullyRemoveExistingKeyAndReturnDecodedData() throws ExecutionException, InterruptedException {
        // Given - Mock the CacheHandlerUtils.handleCacheResponse behavior
        // When cacheResponse is not null, it should decode and return success

        when(keyValueStore.remove(testDataKey)).thenReturn(CompletableFuture.completedFuture(testCachedDataValue));

        // When
        CacheResponse<String> response = handler.handle(testKey, emptyFilters, keyValueStore).get();

        // Then
        assertNotNull(response);
        assertNull(response.error(), "Response should not have error");
        assertEquals(jsonData, response.data());
        assertEquals(5L, response.version());

        verify(keyValueStore).remove(testDataKey);
    }

    @Test
    @DisplayName("Should return not found when key does not exist")
    void shouldReturnNotFoundWhenKeyDoesNotExist() throws ExecutionException, InterruptedException {
        // Given - Mock the CacheHandlerUtils.handleCacheResponse behavior
        // When cacheResponse is null, it should return not found

        when(keyValueStore.remove(testDataKey)).thenReturn(CompletableFuture.completedFuture(null));

        // When
        CacheResponse<String> response = handler.handle(testKey, emptyFilters, keyValueStore).get();

        // Then
        assertNotNull(response);
        assertNotNull(response.error(), "Response should have error for not found");
        assertEquals(CacheErrorCode.NOT_FOUND, response.error().errorCode());
        assertEquals("Key not found", response.error().errorMessage());
        assertNull(response.data(), "Data should be null for not found");
        assertNull(response.version(), "Version should be null for not found");

        verify(keyValueStore).remove(testDataKey);
    }

    @Test
    @DisplayName("Should handle exception during remove operation")
    void shouldHandleExceptionDuringRemoveOperation() throws ExecutionException, InterruptedException {
        // Given
        RuntimeException removeException = new RuntimeException("Storage failure");
        when(keyValueStore.remove(testDataKey)).thenReturn(CompletableFuture.failedFuture(removeException));

        // When
        CacheResponse<String> response = handler.handle(testKey, emptyFilters, keyValueStore).get();

        // Then
        assertNotNull(response);
        assertNotNull(response.error(), "Response should have error for exception");
        assertEquals(CacheErrorCode.INTERNAL_ERROR, response.error().errorCode());
        assertTrue(response.error().errorMessage().contains("Something failed"));
        assertNull(response.data(), "Data should be null on error");
        assertNull(response.version(), "Version should be null on error");

        verify(keyValueStore).remove(testDataKey);
    }

    @Test
    @DisplayName("Should accept filters parameter")
    void shouldAcceptFiltersParameter() throws ExecutionException, InterruptedException {
        // Given
        List<Filter> filters = Collections.singletonList(mock(Filter.class));
        when(keyValueStore.remove(testDataKey)).thenReturn(CompletableFuture.completedFuture(testCachedDataValue));

        // When
        CacheResponse<String> response = handler.handle(testKey, filters, keyValueStore).get();

        // Then
        assertNotNull(response);
        assertNull(response.error());
        assertEquals(jsonData, response.data());

        verify(keyValueStore).remove(testDataKey);
    }

    @Test
    @DisplayName("Should handle various key formats")
    void shouldHandleVariousKeyFormats() throws ExecutionException, InterruptedException {
        // Test different key formats: empty, special chars, unicode
        String[] testKeys = {"", "key/with@special#chars!@#$%^&*()", "ключ-тест-🚀"};

        for (String key : testKeys) {
            DataKey dataKey = new DataKey(key);
            when(keyValueStore.remove(dataKey)).thenReturn(CompletableFuture.completedFuture(testCachedDataValue));

            // When
            CacheResponse<String> response = handler.handle(key, emptyFilters, keyValueStore).get();

            // Then
            assertNotNull(response, "Response should always be returned for key: " + key);
            assertNull(response.error(), "Should not have error for key: " + key);
            assertEquals(jsonData, response.data());

            verify(keyValueStore).remove(dataKey);
        }
    }

    @Test
    @DisplayName("Should return completable future that is not null")
    void shouldReturnCompletableFutureThatIsNotNull() {
        // Given
        when(keyValueStore.remove(testDataKey)).thenReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);

        // Then
        assertNotNull(result, "Handler should always return a non-null CompletableFuture");
    }

    @Test
    @DisplayName("Should handle asynchronous completion properly")
    void shouldHandleAsynchronousCompletionProperly() throws ExecutionException, InterruptedException {
        // Given - A future that completes asynchronously
        CompletableFuture<CachedDataValue> asyncFuture = new CompletableFuture<>();
        when(keyValueStore.remove(testDataKey)).thenReturn(asyncFuture);

        // When - Start the operation
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);

        // Complete the async operation
        asyncFuture.complete(testCachedDataValue);

        // Then - Wait for completion
        CacheResponse<String> response = result.get();
        assertNotNull(response);
        assertNull(response.error());
        assertEquals(jsonData, response.data());
        assertEquals(5L, response.version());

        verify(keyValueStore).remove(testDataKey);
    }

    @Test
    @DisplayName("Should handle asynchronous completion with null result (key not found)")
    void shouldHandleAsynchronousCompletionWithNullResult() throws ExecutionException, InterruptedException {
        // Given - A future that completes asynchronously with null
        CompletableFuture<CachedDataValue> asyncFuture = new CompletableFuture<>();
        when(keyValueStore.remove(testDataKey)).thenReturn(asyncFuture);

        // When - Start the operation
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);

        // Complete the async operation with null
        asyncFuture.complete(null);

        // Then - Wait for completion
        CacheResponse<String> response = result.get();
        assertNotNull(response);
        assertNotNull(response.error());
        assertEquals(CacheErrorCode.NOT_FOUND, response.error().errorCode());
        assertNull(response.data());
        assertNull(response.version());

        verify(keyValueStore).remove(testDataKey);
    }
}

