package com.bcorp.InFlightKv.handlers;

import com.bcorp.InFlightKv.pojos.CacheErrorCode;
import com.bcorp.InFlightKv.pojos.CacheResponse;
// Note: CacheHandlerUtils and CacheExceptionUtils are used but not mocked - they are tested separately
// Note: CacheHandlerUtils and CacheExceptionUtils are used through the handler
import com.bcorp.api.filters.Filter;
import com.bcorp.api.filters.VersionFilter;
import com.bcorp.codec.JsonCodec;
import com.bcorp.exceptions.ConcurrentUpdateException;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.kvstore.KvStoreClock;
import com.bcorp.kvstore.SystemClock;
import com.bcorp.pojos.CachedDataValue;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.RequestDataValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
@DisplayName("JsonStringSetValueHandler Unit Tests")
public class JsonStringSetValueHandlerTest {

    @Mock
    private KeyValueStore keyValueStore;

    @Mock
    private JsonCodec jsonCodec;

    private JsonStringSetValueHandler handlerNoPatching;
    private JsonStringSetValueHandler handlerWithPatching;

    private String testKey = "test-key";
    private DataKey testDataKey;
    private String jsonInput = "{\"newField\":\"newValue\"}";
    private String existingJsonData = "{\"existingField\":\"existingValue\"}";
    private JsonNode inputJsonNode;
    private JsonNode existingJsonNode;
    private CachedDataValue existingRequestDataValue;
    private RequestDataValue newRequestDataValue;
    private CachedDataValue newCachedDataValue;
    private List<Filter> emptyFilters = Collections.emptyList();

    @BeforeEach
    void setUp() {
        handlerNoPatching = new JsonStringSetValueHandler(jsonCodec, false);
        handlerWithPatching = new JsonStringSetValueHandler(jsonCodec, true);
        testDataKey = new DataKey(testKey);

        // Create test JSON nodes
        inputJsonNode = JsonNodeFactory.instance.objectNode().put("newField", "newValue");
        existingJsonNode = JsonNodeFactory.instance.objectNode().put("existingField", "existingValue");

        // Create test data values
        existingRequestDataValue = new CachedDataValue(
                existingJsonData.getBytes(StandardCharsets.UTF_8),
                String.class,
                System.currentTimeMillis(),
                5L
        );

        newRequestDataValue = RequestDataValue.fromString(jsonInput);

        newCachedDataValue = new CachedDataValue(
                jsonInput.getBytes(StandardCharsets.UTF_8),
                String.class,
                System.currentTimeMillis(),
                6L);
    }

    @Test
    @DisplayName("Constructor should create handler with correct patching configuration")
    void constructorShouldCreateHandlerWithCorrectPatchingConfiguration() {
        // Given & When
        JsonStringSetValueHandler handlerNoPatch = new JsonStringSetValueHandler(jsonCodec, false);
        JsonStringSetValueHandler handlerWithPatch = new JsonStringSetValueHandler(jsonCodec, true);

        // Then
        assertNotNull(handlerNoPatch);
        assertNotNull(handlerWithPatch);
        // Note: enablePatching is private, so we test its behavior through handle() method
    }

    @Test
    @DisplayName("Should successfully set value without version checking when patching disabled")
    void shouldSuccessfullySetValueWithoutVersionCheckingWhenPatchingDisabled() throws ExecutionException, InterruptedException {
        // Given
        when(jsonCodec.fromString(jsonInput)).thenReturn(inputJsonNode);
        when(jsonCodec.encode(inputJsonNode)).thenReturn(newRequestDataValue);
        when(keyValueStore.set(eq(testDataKey), eq(newRequestDataValue), eq(null)))
                .thenReturn(CompletableFuture.completedFuture(newCachedDataValue));
        when(jsonCodec.decode(newCachedDataValue)).thenReturn(inputJsonNode);
        when(jsonCodec.toString(inputJsonNode)).thenReturn(jsonInput);

        // When
        CacheResponse<String> response = handlerNoPatching.handle(testKey, jsonInput, emptyFilters, keyValueStore).get();

        // Then
        assertNotNull(response);
        assertNull(response.error(), "Response should not have error");
        assertEquals(jsonInput, response.data());
        assertEquals(6L, response.version());

        verify(jsonCodec).fromString(jsonInput);
        verify(jsonCodec).encode(inputJsonNode);
        verify(keyValueStore).set(testDataKey, newRequestDataValue, null);
        verify(jsonCodec).decode(newCachedDataValue);
        verify(jsonCodec).toString(inputJsonNode);
        verify(keyValueStore, never()).get(any());
    }

    @Test
    @DisplayName("Should successfully set value with version checking when VersionFilter is present")
    void shouldSuccessfullySetValueWithVersionCheckingWhenVersionFilterIsPresent() throws ExecutionException, InterruptedException {
        // Given
        VersionFilter versionFilter = new VersionFilter(5L);
        List<Filter> filtersWithVersion = Collections.singletonList(versionFilter);

        when(jsonCodec.fromString(jsonInput)).thenReturn(inputJsonNode);
        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(existingRequestDataValue));
        when(jsonCodec.encode(inputJsonNode)).thenReturn(newRequestDataValue);
        when(keyValueStore.set(eq(testDataKey), eq(newRequestDataValue), eq(5L)))
                .thenReturn(CompletableFuture.completedFuture(newCachedDataValue));
        when(jsonCodec.decode(newCachedDataValue)).thenReturn(inputJsonNode);
        when(jsonCodec.toString(inputJsonNode)).thenReturn(jsonInput);

        // When
        CacheResponse<String> response = handlerNoPatching.handle(testKey, jsonInput, filtersWithVersion, keyValueStore).get();

        // Then
        assertNotNull(response);
        assertNull(response.error(), "Response should not have error");
        assertEquals(jsonInput, response.data());
        assertEquals(6L, response.version());

        verify(keyValueStore).get(testDataKey);
        verify(keyValueStore).set(testDataKey, newRequestDataValue, 5L);
    }

    @Test
    @DisplayName("Should successfully merge data when patching is enabled")
    void shouldSuccessfullyMergeDataWhenPatchingIsEnabled() throws ExecutionException, InterruptedException {
        // Given
        ObjectNode mergedNode = JsonNodeFactory.instance.objectNode()
                .put("existingField", "existingValue")
                .put("newField", "newValue");

        String mergedJson = "{\"existingField\":\"existingValue\",\"newField\":\"newValue\"}";

        RequestDataValue mergedNodeData = RequestDataValue.fromString(mergedJson);

        when(jsonCodec.decode(existingRequestDataValue)).thenReturn(existingJsonNode);
        when(jsonCodec.decode(newCachedDataValue)).thenReturn(mergedNode);
        when(jsonCodec.fromString(jsonInput)).thenReturn(inputJsonNode);
        when(jsonCodec.encode(mergedNode)).thenReturn(mergedNodeData);
        when(jsonCodec.toString(mergedNode)).thenReturn(mergedJson);

        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(existingRequestDataValue));
        when(keyValueStore.set(eq(testDataKey), eq(mergedNodeData), eq(5L)))
                .thenReturn(CompletableFuture.completedFuture(newCachedDataValue));

        // When
        CacheResponse<String> response = handlerWithPatching.handle(testKey, jsonInput, emptyFilters, keyValueStore).get();

        // Then
        assertNotNull(response);
        assertNull(response.error(), "Response should not have error");
        assertEquals(mergedJson, response.data());
        assertEquals(6L, response.version());

        verify(keyValueStore).get(testDataKey);
        verify(jsonCodec).encode(mergedNode); // Verify merged node was encoded
    }

    @Test
    @DisplayName("Should return error for invalid JSON input")
    void shouldReturnErrorForInvalidJsonInput() throws ExecutionException, InterruptedException {
        // Given
        String invalidJson = "{invalid json";
        com.bcorp.exceptions.JsonDecodingFailed decodingException =
                new com.bcorp.exceptions.JsonDecodingFailed(new IOException("Invalid JSON"));
        when(jsonCodec.fromString(invalidJson)).thenThrow(decodingException);

        // When
        CacheResponse<String> response = handlerNoPatching.handle(testKey, invalidJson, emptyFilters, keyValueStore).get();

        // Then
        assertNotNull(response);
        assertNotNull(response.error(), "Response should have error for invalid JSON");
        assertEquals(CacheErrorCode.WRONG_DATA_TYPE, response.error().errorCode());
        assertEquals("Value is not a proper json string", response.error().errorMessage());
        assertNull(response.data(), "Data should be null for error response");

        verify(jsonCodec).fromString(invalidJson);
        verify(keyValueStore, never()).get(any());
        verify(keyValueStore, never()).set(any(), any(), any());
    }

    @Test
    @DisplayName("Should return conflict error when version doesn't match")
    void shouldReturnConflictErrorWhenVersionDoesNotMatch() throws ExecutionException, InterruptedException {
        // Given
        VersionFilter versionFilter = new VersionFilter(10L); // Different from existing version 5L
        List<Filter> filtersWithVersion = Collections.singletonList(versionFilter);

        when(jsonCodec.fromString(jsonInput)).thenReturn(inputJsonNode);
        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(existingRequestDataValue));

        // When
        CacheResponse<String> response = handlerNoPatching.handle(testKey, jsonInput, filtersWithVersion, keyValueStore).get();

        // Then
        assertNotNull(response);
        assertNotNull(response.error(), "Response should have error for version conflict");
        assertEquals(CacheErrorCode.CONFLICT, response.error().errorCode());
        assertEquals("Expected version doesn't match latest version", response.error().errorMessage());

        verify(keyValueStore).get(testDataKey);
        verify(keyValueStore, never()).set(any(), any(), any());
    }

    @Test
    @DisplayName("Should return not found error when key doesn't exist but version is expected")
    void shouldReturnNotFoundErrorWhenKeyDoesNotExistButVersionIsExpected() throws ExecutionException, InterruptedException {
        // Given
        VersionFilter versionFilter = new VersionFilter(1L);
        List<Filter> filtersWithVersion = Collections.singletonList(versionFilter);

        when(jsonCodec.fromString(jsonInput)).thenReturn(inputJsonNode);
        when(keyValueStore.get(testDataKey)).thenReturn(CompletableFuture.completedFuture(null));

        // When
        CacheResponse<String> response = handlerNoPatching.handle(testKey, jsonInput, filtersWithVersion, keyValueStore).get();

        // Then
        assertNotNull(response);
        assertNotNull(response.error(), "Response should have error for not found with version");
        assertEquals(CacheErrorCode.NOT_FOUND, response.error().errorCode());
        assertEquals("Expected version not found", response.error().errorMessage());

        verify(keyValueStore).get(testDataKey);
        verify(keyValueStore, never()).set(any(), any(), any());
    }

    @Test
    @DisplayName("Should handle exceptions during set operation")
    void shouldHandleExceptionsDuringSetOperation() throws ExecutionException, InterruptedException {
        // Given
        ConcurrentUpdateException concurrentException = new ConcurrentUpdateException();
        when(jsonCodec.fromString(jsonInput)).thenReturn(inputJsonNode);
        when(jsonCodec.encode(inputJsonNode)).thenReturn(newRequestDataValue);
        when(keyValueStore.set(eq(testDataKey), eq(newRequestDataValue), eq(null)))
                .thenReturn(CompletableFuture.failedFuture(concurrentException));

        // When
        CacheResponse<String> response = handlerNoPatching.handle(testKey, jsonInput, emptyFilters, keyValueStore).get();

        // Then
        assertNotNull(response);
        assertNotNull(response.error(), "Response should have error for concurrent update");
        assertEquals(CacheErrorCode.CONFLICT, response.error().errorCode());
        assertTrue(response.error().errorMessage().contains("concurrently"));

        verify(keyValueStore).set(testDataKey, newRequestDataValue, null);
    }

    @Test
    @DisplayName("Should handle generic exceptions during operations")
    void shouldHandleGenericExceptionsDuringOperations() throws ExecutionException, InterruptedException {
        // Given
        RuntimeException genericException = new RuntimeException("Generic storage failure");
        when(jsonCodec.fromString(jsonInput)).thenReturn(inputJsonNode);
        when(jsonCodec.encode(inputJsonNode)).thenReturn(newRequestDataValue);
        when(keyValueStore.set(eq(testDataKey), eq(newRequestDataValue), eq(null)))
                .thenReturn(CompletableFuture.failedFuture(genericException));

        // When
        CacheResponse<String> response = handlerNoPatching.handle(testKey, jsonInput, emptyFilters, keyValueStore).get();

        // Then
        assertNotNull(response);
        assertNotNull(response.error(), "Response should have error for generic exception");
        assertEquals(CacheErrorCode.INTERNAL_ERROR, response.error().errorCode());
        assertTrue(response.error().errorMessage().contains("Something failed"));

        verify(keyValueStore).set(testDataKey, newRequestDataValue, null);
    }

    @Test
    @DisplayName("Should handle various key formats")
    void shouldHandleVariousKeyFormats() throws ExecutionException, InterruptedException {
        // Test different key formats: empty, special chars, unicode
        String[] testKeys = {"", "key/with@special#chars!@#$%^&*()", "ключ-тест-🚀"};

        for (String key : testKeys) {
            DataKey dataKey = new DataKey(key);
            when(jsonCodec.fromString(jsonInput)).thenReturn(inputJsonNode);
            when(jsonCodec.encode(inputJsonNode)).thenReturn(newRequestDataValue);
            when(keyValueStore.set(eq(dataKey), eq(newRequestDataValue), eq(null)))
                    .thenReturn(CompletableFuture.completedFuture(newCachedDataValue));
            when(jsonCodec.decode(newCachedDataValue)).thenReturn(inputJsonNode);
            when(jsonCodec.toString(inputJsonNode)).thenReturn(jsonInput);

            // When
            CacheResponse<String> response = handlerNoPatching.handle(key, jsonInput, emptyFilters, keyValueStore).get();

            // Then
            assertNotNull(response, "Response should always be returned for key: " + key);
            assertNull(response.error(), "Should not have error for key: " + key);
            assertEquals(jsonInput, response.data());

            verify(keyValueStore).set(dataKey, newRequestDataValue, null);
        }
    }

    @Test
    @DisplayName("Should return completable future that is not null")
    void shouldReturnCompletableFutureThatIsNotNull() {
        // Given & When
        CompletableFuture<CacheResponse<String>> result = handlerNoPatching.handle(testKey, jsonInput, emptyFilters, keyValueStore);

        // Then
        assertNotNull(result, "Handler should always return a non-null CompletableFuture");
    }

    @Test
    @DisplayName("Should handle asynchronous completion properly")
    void shouldHandleAsynchronousCompletionProperly() throws ExecutionException, InterruptedException {
        // Given - A future that completes asynchronously
        CompletableFuture<CachedDataValue> asyncFuture = new CompletableFuture<>();
        when(jsonCodec.fromString(jsonInput)).thenReturn(inputJsonNode);
        when(jsonCodec.encode(inputJsonNode)).thenReturn(newRequestDataValue);
        when(keyValueStore.set(eq(testDataKey), eq(newRequestDataValue), eq(null)))
                .thenReturn(asyncFuture);
        when(jsonCodec.decode(newCachedDataValue)).thenReturn(inputJsonNode);
        when(jsonCodec.toString(inputJsonNode)).thenReturn(jsonInput);

        // When - Start the operation
        CompletableFuture<CacheResponse<String>> result = handlerNoPatching.handle(testKey, jsonInput, emptyFilters, keyValueStore);

        // Complete the async operation
        asyncFuture.complete(newCachedDataValue);

        // Then - Wait for completion
        CacheResponse<String> response = result.get();
        assertNotNull(response);
        assertNull(response.error());
        assertEquals(jsonInput, response.data());

        verify(keyValueStore).set(testDataKey, newRequestDataValue, null);
    }
}
