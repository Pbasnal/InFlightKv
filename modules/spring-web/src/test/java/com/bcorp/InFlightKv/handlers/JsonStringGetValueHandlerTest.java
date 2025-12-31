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
    private List<Filter> emptyFilters;

    @BeforeEach
    void setUp() {
        jsonCodec = new JsonCodec();
        handler = new JsonStringGetValueHandler(jsonCodec);
        testKey = "test-key";
        testDataKey = new DataKey(testKey);

        // Create a DataValue with actual JSON data that the JsonCodec can handle
        String jsonData = "{\"test\":\"data\"}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = jsonCodec.fromString(jsonData);
        testDataValue = jsonCodec.encode(jsonNode);

        emptyFilters = Collections.emptyList();
    }

    @Test
    @DisplayName("Should successfully retrieve and return existing key value")
    void shouldSuccessfullyRetrieveExistingKeyValue() throws ExecutionException, InterruptedException {
        // Given
        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(testDataValue));

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response);
        assertNull(response.error(), "Response should not have error");
        assertNotNull(response.data(), "Data should not be null");
        assertEquals(0L, response.version(), "Version should match DataValue version");

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
    @DisplayName("Should handle JsonCodec decoding errors in CacheHandlerUtils")
    void shouldHandleJsonCodecDecodingErrors() throws ExecutionException, InterruptedException {
        // Given - DataValue exists but JsonCodec fails to decode it
        CacheResponse<String> decodingErrorResponse = CacheResponse.failure(CacheErrorCode.WRONG_DATA_TYPE,
                "Failed to decode data to json");

        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(testDataValue));

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response);
        assertNotNull(response.error(), "Response should have error");
        assertEquals(CacheErrorCode.WRONG_DATA_TYPE, response.error().errorCode());
        assertEquals("Failed to decode data to json", response.error().errorMessage());

        verify(keyValueStore).get(testDataKey);
    }

    @Test
    @DisplayName("Should handle JsonCodec serialization errors in CacheHandlerUtils")
    void shouldHandleJsonCodecSerializationErrors() throws ExecutionException, InterruptedException {
        // Given - DataValue exists but JsonCodec fails to serialize it
        CacheResponse<String> serializationErrorResponse = CacheResponse.failure(CacheErrorCode.WRONG_DATA_TYPE,
                "Failed to serialize json data to string");

        when(keyValueStore.get(testDataKey))
                .thenReturn(CompletableFuture.completedFuture(testDataValue));
//        (testDataValue, jsonCodec)).thenReturn(serializationErrorResponse);

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response);
        assertNotNull(response.error(), "Response should have error");
        assertEquals(CacheErrorCode.WRONG_DATA_TYPE, response.error().errorCode());
        assertEquals("Failed to serialize json data to string", response.error().errorMessage());

        verify(keyValueStore).get(testDataKey);
    }

    @Test
    @DisplayName("Should accept filters parameter (though not used in current implementation)")
    void shouldAcceptFiltersParameter() throws ExecutionException, InterruptedException {
        // Given
        List<Filter> filters = Collections.singletonList(mock(Filter.class));
        CacheResponse<String> expectedResponse = CacheResponse.success("data", 1L);

        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(testDataValue));
//(testDataValue, jsonCodec)).thenReturn(expectedResponse);

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, filters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response);
        assertNull(response.error(), "Response should not have error");

        // Note: Current implementation doesn't use filters, but the parameter is accepted
        verify(keyValueStore).get(testDataKey);
    }

    @Test
    @DisplayName("Should handle empty string keys")
    void shouldHandleEmptyStringKeys() throws ExecutionException, InterruptedException {
        // Given
        String emptyKey = "";
        DataKey emptyDataKey = new DataKey(emptyKey);
        CacheResponse<String> expectedResponse = CacheResponse.success("empty-key-data", 1L);

        when(keyValueStore.get(emptyDataKey)).thenReturn(CompletableFuture.completedFuture(testDataValue));
//(testDataValue, jsonCodec)).thenReturn(expectedResponse);

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(emptyKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response);
        assertNull(response.error(), "Response should not have error");

        verify(keyValueStore).get(emptyDataKey);
    }

    @Test
    @DisplayName("Should handle keys with special characters")
    void shouldHandleKeysWithSpecialCharacters() throws ExecutionException, InterruptedException {
        // Given
        String specialKey = "key/with@special#chars!@#$%^&*()";
        DataKey specialDataKey = new DataKey(specialKey);
        CacheResponse<String> expectedResponse = CacheResponse.success("special-data", 1L);

        when(keyValueStore.get(specialDataKey)).thenReturn(CompletableFuture.completedFuture(testDataValue));
//(testDataValue, jsonCodec)).thenReturn(expectedResponse);

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(specialKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response);
        assertNull(response.error(), "Response should not have error");

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
    @DisplayName("Should preserve version from DataValue in successful response")
    void shouldPreserveVersionFromDataValueInSuccessfulResponse() throws ExecutionException, InterruptedException {
        // Given
        long expectedVersion = 42L;
        DataValue versionedDataValue = new DataValue(
                "test".getBytes(StandardCharsets.UTF_8),
                String.class,
                System.currentTimeMillis(),
                expectedVersion
        );
        CacheResponse<String> versionedResponse = CacheResponse.success("versioned-data", expectedVersion);

        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(versionedDataValue));
//(versionedDataValue, jsonCodec)).thenReturn(versionedResponse);

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response);
        assertNull(response.error());
        assertEquals(expectedVersion, response.version(), "Version should be preserved");
    }

    @Test
    @DisplayName("Should handle DataValue with zero version")
    void shouldHandleDataValueWithZeroVersion() throws ExecutionException, InterruptedException {
        // Given
        DataValue zeroVersionDataValue = new DataValue(
                "zero-version".getBytes(StandardCharsets.UTF_8),
                String.class,
                System.currentTimeMillis(),
                0L
        );
        CacheResponse<String> zeroVersionResponse = CacheResponse.success("zero-version-data", 0L);

        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(zeroVersionDataValue));
//(zeroVersionDataValue, jsonCodec)).thenReturn(zeroVersionResponse);

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response);
        assertNull(response.error());
        assertEquals(0L, response.version(), "Zero version should be preserved");

//        verify(CacheHandlerUtils).handleCacheResponse(zeroVersionDataValue, jsonCodec);
    }

    @Test
    @DisplayName("Should handle DataValue with null version")
    void shouldHandleDataValueWithNullVersion() throws ExecutionException, InterruptedException {
        // Given
        DataValue nullVersionDataValue = DataValue.fromString("null-version");
        CacheResponse<String> nullVersionResponse = CacheResponse.<String>success("null-version-data", 0);

        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(nullVersionDataValue));
//(nullVersionDataValue, jsonCodec)).thenReturn(nullVersionResponse);

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response);
        assertNull(response.error());
        assertNull(response.version(), "Null version should be preserved");

//        verify(CacheHandlerUtils).handleCacheResponse(nullVersionDataValue, jsonCodec);
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
    void shouldHandleAsynchronousCompletionProperly() {
        // Given - A future that completes asynchronously
        CompletableFuture<DataValue> asyncFuture = new CompletableFuture<>();
        CacheResponse<String> expectedResponse = CacheResponse.success("async-data", 1L);

        when(keyValueStore.get(testDataKey)).thenReturn(asyncFuture);
//(testDataValue, jsonCodec)).thenReturn(expectedResponse);

        // When - Start the operation
        CompletableFuture<CacheResponse<String>> result = handler.handle(testKey, emptyFilters, keyValueStore);

        // Complete the async operation
        asyncFuture.complete(testDataValue);

        // Then - Wait for completion
        assertDoesNotThrow(() -> {
            CacheResponse<String> response = result.get();
            assertNotNull(response);
            assertNull(response.error());
            assertEquals("async-data", response.data());
        });

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
        CacheResponse<String> expectedResponse = CacheResponse.success("large-key-data", 1L);

        when(keyValueStore.get(largeDataKey)).thenReturn(CompletableFuture.completedFuture(testDataValue));
//(testDataValue, jsonCodec)).thenReturn(expectedResponse);

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(veryLargeKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response);
        assertNull(response.error());
        assertEquals("large-key-data", response.data());

        verify(keyValueStore).get(largeDataKey);
    }

    @Test
    @DisplayName("Should handle unicode characters in keys")
    void shouldHandleUnicodeCharactersInKeys() throws ExecutionException, InterruptedException {
        // Given
        String unicodeKey = "ключ-тест-🚀-тест";
        DataKey unicodeDataKey = new DataKey(unicodeKey);
        CacheResponse<String> expectedResponse = CacheResponse.success("unicode-data", 1L);

        when(keyValueStore.get(unicodeDataKey)).thenReturn(CompletableFuture.completedFuture(testDataValue));
//(testDataValue, jsonCodec)).thenReturn(expectedResponse);

        // When
        CompletableFuture<CacheResponse<String>> result = handler.handle(unicodeKey, emptyFilters, keyValueStore);
        CacheResponse<String> response = result.get();

        // Then
        assertNotNull(response);
        assertNull(response.error());
        assertEquals("unicode-data", response.data());

        verify(keyValueStore).get(unicodeDataKey);
    }
}
