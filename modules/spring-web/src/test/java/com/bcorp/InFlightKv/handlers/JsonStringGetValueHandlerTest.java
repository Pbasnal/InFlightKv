package com.bcorp.InFlightKv.handlers;

import com.bcorp.InFlightKv.pojos.CacheErrorCode;
import com.bcorp.InFlightKv.pojos.CacheResponse;
// Note: CacheHandlerUtils and CacheExceptionUtils are used but not mocked - they are tested separately
import com.bcorp.InFlightKv.utils.CacheExceptionUtils;
import com.bcorp.InFlightKv.utils.CacheHandlerUtils;
import com.bcorp.api.filters.Filter;
import com.bcorp.codec.JsonCodec;
import com.bcorp.exceptions.ConcurrentUpdateException;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.DataValue;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JsonStringGetValueHandler.
 * <p>
 * Tests the handler's ability to retrieve values from KeyValueStore
 * and convert them to CacheResponse<String> using JsonCodec.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JsonStringGetValueHandler Unit Tests")
public class JsonStringGetValueHandlerTest {

    @Mock
    private KeyValueStore keyValueStore;

    @Mock
    private JsonCodec jsonCodec;

    private JsonStringGetValueHandler handler;
    private String testKey;
    private DataKey testDataKey;
    private DataValue testDataValue;
    private String jsonData = "{\"test\":\"data\"}";
    private List<Filter> emptyFilters;

    @BeforeEach
    void setUp() {
        handler = new JsonStringGetValueHandler(jsonCodec);
        testKey = "test-key";
        testDataKey = new DataKey(testKey);

        // Create a DataValue with mock JSON data
        testDataValue = new DataValue(
                jsonData.getBytes(StandardCharsets.UTF_8),
                String.class,
                System.currentTimeMillis(),
                5L
        );

        // Setup default mock behavior for successful cases
//        JsonNode mockJsonNode = JsonNodeFactory.instance.objectNode().put("test", "data");
//        when(jsonCodec.decode(testDataValue)).thenReturn(mockJsonNode);
//        when(jsonCodec.toString(mockJsonNode)).thenReturn(jsonData);

        emptyFilters = Collections.emptyList();
    }

    @Test
    @DisplayName("Should successfully retrieve and return existing key value")
    void shouldSuccessfullyRetrieveExistingKeyValue() throws ExecutionException, InterruptedException {
        // Given
        String jsonData = "{\"test\":\"data\"}";
        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(testDataValue));
        JsonNode mockJsonNode = JsonNodeFactory.instance.objectNode().put("test", "data");
        when(jsonCodec.decode(testDataValue)).thenReturn(mockJsonNode);
        when(jsonCodec.toString(mockJsonNode)).thenReturn(jsonData);

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response);
        assertNull(response.error(), "Response should not have error");
        assertNotNull(response.data(), "Data should not be null");
        assertEquals(5L, response.version(), "Version should match DataValue version");

        verify(keyValueStore).get(testDataKey);
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
    @DisplayName("Should handle ConcurrentUpdateException during retrieval")
    void shouldHandleConcurrentUpdateExceptionDuringRetrieval() throws ExecutionException, InterruptedException {
        // Given
        ConcurrentUpdateException concurrentException =
                new ConcurrentUpdateException();

        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.failedFuture(concurrentException));

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then - Response should always be returned, even on concurrent update exceptions
        assertNotNull(response, "Response should always be returned, even on exceptions");
        assertNotNull(response.error(), "Response should have error for concurrent updates");
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
        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(testDataValue));
        when(jsonCodec.decode(testDataValue)).thenThrow(decodingException);

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
        verify(jsonCodec).decode(testDataValue);
    }

    @Test
    @DisplayName("Should handle JsonCodec serialization errors")
    void shouldHandleJsonCodecSerializationErrors() throws ExecutionException, InterruptedException {
        // Given - Mock JsonCodec to throw exception during serialization
        com.fasterxml.jackson.databind.JsonNode mockJsonNode =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("test", "data");
        com.bcorp.exceptions.JsonDecodingFailed serializationException =
                new com.bcorp.exceptions.JsonDecodingFailed(new IOException("Serialization failed"));

        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(testDataValue));
        when(jsonCodec.decode(testDataValue)).thenReturn(mockJsonNode);
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
        verify(jsonCodec).decode(testDataValue);
        verify(jsonCodec).toString(mockJsonNode);
    }

    @Test
    @DisplayName("Should accept filters parameter (though not used in current implementation)")
    void shouldAcceptFiltersParameter() throws ExecutionException, InterruptedException {
        // Given
        List<Filter> filters = Collections.singletonList(mock(Filter.class));
        // Setup default mock behavior for successful cases
        JsonNode mockJsonNode = JsonNodeFactory.instance.objectNode().put("test", "data");
        when(jsonCodec.decode(testDataValue)).thenReturn(mockJsonNode);
        when(jsonCodec.toString(mockJsonNode)).thenReturn(jsonData);

        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(testDataValue));

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, filters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response, "Response should always be returned");
        assertNull(response.error(), "Response should not have error");
        assertNotNull(response.data(), "Data should not be null");
        assertEquals(5L, response.version(), "Version should match DataValue version");

        // Note: Current implementation doesn't use filters, but the parameter is accepted
        verify(keyValueStore).get(testDataKey);
    }

    @Test
    @DisplayName("Should handle empty string keys")
    void shouldHandleEmptyStringKeys() throws ExecutionException, InterruptedException {
        // Given
        String emptyKey = "";
        DataKey emptyDataKey = new DataKey(emptyKey);
        JsonNode mockJsonNode = JsonNodeFactory.instance.objectNode().put("test", "data");
        when(jsonCodec.decode(testDataValue)).thenReturn(mockJsonNode);
        when(jsonCodec.toString(mockJsonNode)).thenReturn(jsonData);

        when(keyValueStore.get(emptyDataKey)).thenReturn(CompletableFuture.completedFuture(testDataValue));

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(emptyKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response, "Response should always be returned");
        assertNull(response.error(), "Response should not have error");
        assertNotNull(response.data(), "Data should not be null");
        assertEquals(5L, response.version(), "Version should match DataValue version");

        verify(keyValueStore).get(emptyDataKey);
    }

    @Test
    @DisplayName("Should handle keys with special characters")
    void shouldHandleKeysWithSpecialCharacters() throws ExecutionException, InterruptedException {
        // Given
        String specialKey = "key/with@special#chars!@#$%^&*()";
        DataKey specialDataKey = new DataKey(specialKey);

        when(keyValueStore.get(specialDataKey)).thenReturn(CompletableFuture.completedFuture(testDataValue));
        // Setup default mock behavior for successful cases
        JsonNode mockJsonNode = JsonNodeFactory.instance.objectNode().put("test", "data");
        when(jsonCodec.decode(testDataValue)).thenReturn(mockJsonNode);
        when(jsonCodec.toString(mockJsonNode)).thenReturn(jsonData);

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(specialKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response, "Response should always be returned");
        assertNull(response.error(), "Response should not have error");
        assertNotNull(response.data(), "Data should not be null");
        assertEquals(5L, response.version(), "Version should match DataValue version");

        verify(keyValueStore).get(specialDataKey);
    }

    @Test
    @DisplayName("Should handle null JsonCodec (edge case)")
    void shouldHandleNullJsonCodec() {
        // Given - Handler created with null JsonCodec (edge case)
        JsonStringGetValueHandler nullCodecHandler = new JsonStringGetValueHandler(null);

        // When & Then - This should not throw NPE during construction
        assertNotNull(nullCodecHandler, "Handler should be created even with null JsonCodec");

        // Note: Actual behavior when handling would depend on CacheHandlerUtils.handleCacheResponse
        // but this tests that the constructor doesn't fail
    }

    @Test
    @DisplayName("Should handle DataValue with null version")
    void shouldHandleDataValueWithNullVersion() throws ExecutionException, InterruptedException {
        // Given
        DataValue nullVersionDataValue = DataValue.fromString("null-version");
        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(nullVersionDataValue));

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response);
        assertNull(response.error());
        assertNull(response.version(), "Null version should be preserved");
    }

    @Test
    @DisplayName("Should return completable future that is not null")
    void shouldReturnCompletableFutureThatIsNotNull() {
        // Given
        DataValue zeroVersionDataValue = DataValue.fromString("zero-version");
        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(zeroVersionDataValue));

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);

        // Then
        assertNotNull(result, "Handler should always return a non-null CompletableFuture");

        // Note: The future may be completed or not, but it should exist
    }

    @Test
    @DisplayName("Should create handler with JsonCodec dependency")
    void shouldCreateHandlerWithJsonCodecDependency() {
        // Given
        JsonCodec testCodec = mock(JsonCodec.class);

        // When
        JsonStringGetValueHandler codecHandler = new JsonStringGetValueHandler(testCodec);

        // Then
        assertNotNull(codecHandler, "Handler should be created with JsonCodec dependency");

        // Note: We can't directly verify the internal codec field, but construction should succeed
    }

    @Test
    @DisplayName("Should handle asynchronous completion properly")
    void shouldHandleAsynchronousCompletionProperly() throws ExecutionException, InterruptedException {
        // Given - A future that completes asynchronously
        CompletableFuture<DataValue> asyncFuture = new CompletableFuture<>();

        when(keyValueStore.get(testDataKey)).thenReturn(asyncFuture);
        JsonNode mockJsonNode = JsonNodeFactory.instance.objectNode().put("test", "data");
        when(jsonCodec.decode(testDataValue)).thenReturn(mockJsonNode);
        when(jsonCodec.toString(mockJsonNode)).thenReturn(jsonData);

        // When - Start the operation
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);

        // Complete the async operation
        asyncFuture.complete(testDataValue);

        // Then - Wait for completion
        CacheResponse<String> response = result.get();
        assertNotNull(response, "Response should always be returned");
        assertNull(response.error(), "Response should not have error");
        assertNotNull(response.data(), "Data should not be null");
        assertEquals(5L, response.version(), "Version should match DataValue version");

        verify(keyValueStore).get(testDataKey);
    }

    @Test
    @DisplayName("Should handle very large keys")
    void shouldHandleVeryLargeKeys() throws ExecutionException, InterruptedException {
        // Given
        StringBuilder largeKey = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeKey.append("large-key-part-").append(i).append("-");
        }
        String veryLargeKey = largeKey.toString();
        DataKey largeDataKey = new DataKey(veryLargeKey);

        JsonNode mockJsonNode = JsonNodeFactory.instance.objectNode().put("test", "data");
        when(jsonCodec.decode(testDataValue)).thenReturn(mockJsonNode);
        when(jsonCodec.toString(mockJsonNode)).thenReturn(jsonData);

        when(keyValueStore.get(largeDataKey)).thenReturn(CompletableFuture.completedFuture(testDataValue));

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(veryLargeKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response);
        assertNull(response.error());
        assertEquals(jsonData, response.data());

        verify(keyValueStore).get(largeDataKey);
    }

    @Test
    @DisplayName("Should handle unicode characters in keys")
    void shouldHandleUnicodeCharactersInKeys() throws ExecutionException, InterruptedException {
        // Given
        String unicodeKey = "ключ-тест-🚀-тест";
        String jsonData = "{\"test\":\"data\"}";
        DataKey unicodeDataKey = new DataKey(unicodeKey);

        // Setup default mock behavior for successful cases
        JsonNode mockJsonNode = JsonNodeFactory.instance.objectNode().put("test", "data");
        when(jsonCodec.decode(testDataValue)).thenReturn(mockJsonNode);
        when(jsonCodec.toString(mockJsonNode)).thenReturn(jsonData);

        when(keyValueStore.get(unicodeDataKey)).thenReturn(CompletableFuture.completedFuture(testDataValue));

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(unicodeKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response);
        assertNull(response.error());
        assertEquals(jsonData, response.data());

        verify(keyValueStore).get(unicodeDataKey);
    }
}
