package com.bcorp.InFlightKv.handlers;

import com.bcorp.InFlightKv.pojos.CacheErrorCode;
import com.bcorp.InFlightKv.pojos.CacheResponse;
// Note: CacheHandlerUtils and CacheExceptionUtils are used but not mocked - they are tested separately
// Note: CacheHandlerUtils and CacheExceptionUtils are used through the handler
import com.bcorp.api.filters.Filter;
import com.bcorp.codec.JsonCodec;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.CachedDataValue;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.RequestDataValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JsonStringGetValueHandler Unit Tests")
public class JsonStringGetValueHandlerTest {

    @Mock
    private KeyValueStore keyValueStore;

    @Mock
    private JsonCodec jsonCodec;

    private JsonStringGetValueHandler handler;
    private String testKey = "test-key";
    private DataKey testDataKey;
    private CachedDataValue testCachedDataValue;
    private JsonNode mockJsonNode;
    private String jsonData = "{\"test\":\"data\"}";
    private List<Filter> emptyFilters = Collections.emptyList();

    @BeforeEach
    void setUp() {
        handler = new JsonStringGetValueHandler(jsonCodec);
        testDataKey = new DataKey(testKey);

        testCachedDataValue = new CachedDataValue(
                jsonData.getBytes(StandardCharsets.UTF_8),
                String.class,
                System.currentTimeMillis(),
                5L
        );

        mockJsonNode = JsonNodeFactory.instance.objectNode().put("test", "data");
    }

    @Test
    @DisplayName("Should successfully retrieve and return existing key value")
    void shouldSuccessfullyRetrieveExistingKeyValue() throws ExecutionException, InterruptedException {
        // Given
        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(testCachedDataValue));
        when(jsonCodec.decode(testCachedDataValue)).thenReturn(mockJsonNode);
        when(jsonCodec.toString(mockJsonNode)).thenReturn(jsonData);

        // When
        CacheResponse<String> response = handler.handle(testKey, emptyFilters, keyValueStore).get();

        // Then
        assertNotNull(response);
        assertNull(response.error(), "Response should not have error");
        assertEquals(jsonData, response.data());
        assertEquals(5L, response.version());

        verify(keyValueStore).get(testDataKey);
        verify(jsonCodec).decode(testCachedDataValue);
        verify(jsonCodec).toString(mockJsonNode);
    }

    @Test
    @DisplayName("Should return not found when key does not exist")
    void shouldReturnNotFoundWhenKeyDoesNotExist() throws ExecutionException, InterruptedException {
        // Given
        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response);
        assertNotNull(response.error(), "Response should have error for not found");
        assertEquals(CacheErrorCode.NOT_FOUND, response.error().errorCode());
        assertEquals("Key not found", response.error().errorMessage());
        assertNull(response.data(), "Data should be null for not found");
        assertNull(response.version(), "Version should be null for not found");

        verify(keyValueStore).get(testDataKey);
    }

    @Test
    @DisplayName("Should handle exception during key retrieval")
    void shouldHandleExceptionDuringKeyRetrieval() throws ExecutionException, InterruptedException {
        // Given
        RuntimeException originalException = new RuntimeException("Storage failure");
        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.failedFuture(originalException));

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then - Response should always be returned, even on exceptions
        assertNotNull(response, "Response should always be returned, even on exceptions");
        assertNotNull(response.error(), "Response should have error when KeyValueStore fails");
        assertNull(response.data(), "Data should be null on error");
        assertNull(response.version(), "Version should be null on error");

        verify(keyValueStore).get(testDataKey);
    }


    @Test
    @DisplayName("Should handle JsonCodec decoding errors")
    void shouldHandleJsonCodecDecodingErrors() throws ExecutionException, InterruptedException {
        // Given - Mock JsonCodec to throw exception during decode
        com.bcorp.exceptions.JsonDecodingFailed decodingException =
                new com.bcorp.exceptions.JsonDecodingFailed(new IOException("Decoding failed"));
        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(testCachedDataValue));
        when(jsonCodec.decode(testCachedDataValue)).thenThrow(decodingException);

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then - Should handle the decoding error gracefully
        assertNotNull(response, "Response should always be returned, even on errors");
        assertNotNull(response.error(), "Response should have error for decoding failure");
        assertEquals(CacheErrorCode.WRONG_DATA_TYPE, response.error().errorCode());
        assertEquals("Failed to decode data to json", response.error().errorMessage());
        assertNull(response.data(), "Data should be null on error");
        assertNull(response.version(), "Version should be null on error");

        verify(keyValueStore).get(testDataKey);
        verify(jsonCodec).decode(testCachedDataValue);
    }

    @Test
    @DisplayName("Should handle JsonCodec serialization errors")
    void shouldHandleJsonCodecSerializationErrors() throws ExecutionException, InterruptedException {
        // Given - Mock JsonCodec to throw exception during serialization
        com.fasterxml.jackson.databind.JsonNode mockJsonNode =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("test", "data");
        com.bcorp.exceptions.JsonDecodingFailed serializationException =
                new com.bcorp.exceptions.JsonDecodingFailed(new IOException("Serialization failed"));

        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(testCachedDataValue));
        when(jsonCodec.decode(testCachedDataValue)).thenReturn(mockJsonNode);
        when(jsonCodec.toString(mockJsonNode)).thenThrow(serializationException);

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then - Should handle the serialization error gracefully
        assertNotNull(response, "Response should always be returned, even on errors");
        assertNotNull(response.error(), "Response should have error for serialization failure");
        assertEquals(CacheErrorCode.WRONG_DATA_TYPE, response.error().errorCode());
        assertEquals("Failed to serialize json data to string", response.error().errorMessage());
        assertNull(response.data(), "Data should be null on error");
        assertNull(response.version(), "Version should be null on error");

        verify(keyValueStore).get(testDataKey);
        verify(jsonCodec).decode(testCachedDataValue);
        verify(jsonCodec).toString(mockJsonNode);
    }

    @Test
    @DisplayName("Should accept filters parameter")
    void shouldAcceptFiltersParameter() throws ExecutionException, InterruptedException {
        // Given
        List<Filter> filters = Collections.singletonList(mock(Filter.class));
        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(testCachedDataValue));
        when(jsonCodec.decode(testCachedDataValue)).thenReturn(mockJsonNode);
        when(jsonCodec.toString(mockJsonNode)).thenReturn(jsonData);

        // When
        CacheResponse<String> response = handler.handle(testKey, filters, keyValueStore).get();

        // Then
        assertNotNull(response);
        assertNull(response.error());
        assertEquals(jsonData, response.data());

        verify(keyValueStore).get(testDataKey);
    }

    @Test
    @DisplayName("Should handle various key formats")
    void shouldHandleVariousKeyFormats() throws ExecutionException, InterruptedException {
        // Test different key formats: empty, special chars, unicode
        String[] testKeys = {"", "key/with@special#chars!@#$%^&*()", "ключ-тест-🚀"};

        for (String key : testKeys) {
            DataKey dataKey = new DataKey(key);
            when(keyValueStore.get(dataKey)).thenReturn(CompletableFuture.completedFuture(testCachedDataValue));
            when(jsonCodec.decode(testCachedDataValue)).thenReturn(mockJsonNode);
            when(jsonCodec.toString(mockJsonNode)).thenReturn(jsonData);

            // When
            CacheResponse<String> response = handler.handle(key, emptyFilters, keyValueStore).get();

            // Then
            assertNotNull(response, "Response should always be returned for key: " + key);
            assertNull(response.error(), "Should not have error for key: " + key);
            assertEquals(jsonData, response.data());

            verify(keyValueStore).get(dataKey);
        }
    }

    @Test
    @DisplayName("Should return completable future that is not null")
    void shouldReturnCompletableFutureThatIsNotNull() {
        // Given
        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(testCachedDataValue));

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
        when(keyValueStore.get(testDataKey)).thenReturn(asyncFuture);
        when(jsonCodec.decode(testCachedDataValue)).thenReturn(mockJsonNode);
        when(jsonCodec.toString(mockJsonNode)).thenReturn(jsonData);

        // When - Start the operation
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);

        // Complete the async operation
        asyncFuture.complete(testCachedDataValue);

        // Then - Wait for completion
        CacheResponse<String> response = result.get();
        assertNotNull(response);
        assertNull(response.error());
        assertEquals(jsonData, response.data());

        verify(keyValueStore).get(testDataKey);
    }

}
