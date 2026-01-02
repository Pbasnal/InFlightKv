package com.bcorp.InFlightKv.service;

import com.bcorp.InFlightKv.pojos.CacheError;
import com.bcorp.InFlightKv.pojos.CacheErrorCode;
import com.bcorp.InFlightKv.pojos.CacheResponse;
import com.bcorp.codec.JsonCodec;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.CachedDataValue;
import com.bcorp.pojos.DataKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for KeyValueStoreService.
 * <p>
 * Tests the get and remove operations with both success and error scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KeyValueStoreService Unit Tests")
class KeyValueStoreServiceTest {

    @Mock
    private KeyValueStore keyValueStore;

    @Mock
    private JsonCodec jsonCodec;

    private KeyValueStoreService keyValueStoreService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        keyValueStoreService = new KeyValueStoreService(keyValueStore, jsonCodec);
        objectMapper = new ObjectMapper();
    }

    private CachedDataValue createTestCachedData() {
        String testJsonString = "{\"key\":\"value\",\"number\":42}";
        return new CachedDataValue(
                testJsonString.getBytes(StandardCharsets.UTF_8),
                ObjectNode.class,
                System.currentTimeMillis(),
                1L
        );
    }

    private void setupJsonCodecForSuccess(CachedDataValue cachedData) {
        JsonNode testJsonNode = objectMapper.createObjectNode()
                .put("key", "value")
                .put("number", 42);
        String testJsonString = "{\"key\":\"value\",\"number\":42}";

        when(jsonCodec.decode(cachedData)).thenReturn(testJsonNode);
        when(jsonCodec.toString(testJsonNode)).thenReturn(testJsonString);
    }

    // ==================== get Tests ====================

    @Test
    @DisplayName("Should successfully get existing key")
    void shouldGetExistingKey() {
        // Given
        String key = "test-key";
        DataKey dataKey = new DataKey(key);
        CachedDataValue cachedData = createTestCachedData();
        setupJsonCodecForSuccess(cachedData);
        when(keyValueStore.get(dataKey)).thenReturn(CompletableFuture.completedFuture(cachedData));

        // When
        CompletableFuture<CacheResponse<String>> resultFuture = keyValueStoreService.get(key);
        CacheResponse<String> result = resultFuture.join();

        // Then
        assertNotNull(result, "Result should not be null");
        assertEquals("{\"key\":\"value\",\"number\":42}", result.data(), "Data should match expected JSON");
        assertEquals(1L, result.version(), "Version should match");
        assertNull(result.error(), "Error should be null for successful operation");
    }

    @Test
    @DisplayName("Should return not found when key doesn't exist")
    void shouldReturnNotFoundWhenKeyDoesNotExist() {
        // Given
        String key = "non-existent-key";
        DataKey dataKey = new DataKey(key);
        when(keyValueStore.get(dataKey)).thenReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<CacheResponse<String>> resultFuture = keyValueStoreService.get(key);
        CacheResponse<String> result = resultFuture.join();

        // Then
        assertNotNull(result, "Result should not be null");
        assertNull(result.data(), "Data should be null");
        assertNull(result.version(), "Version should be null");
        assertNotNull(result.error(), "Error should not be null");
        assertEquals(CacheErrorCode.NOT_FOUND, result.error().errorCode());
        assertEquals("Key not found", result.error().errorMessage());
    }

    @Test
    @DisplayName("Should handle exception during get operation")
    void shouldHandleExceptionDuringGet() {
        // Given
        String key = "test-key";
        DataKey dataKey = new DataKey(key);
        RuntimeException testException = new RuntimeException("Storage error");
        when(keyValueStore.get(dataKey)).thenReturn(CompletableFuture.failedFuture(testException));

        // When
        CompletableFuture<CacheResponse<String>> resultFuture = keyValueStoreService.get(key);
        CacheResponse<String> result = resultFuture.join();

        // Then
        assertNotNull(result, "Result should not be null");
        assertNull(result.data(), "Data should be null");
        assertNull(result.version(), "Version should be null");
        assertNotNull(result.error(), "Error should not be null");
        assertEquals(CacheErrorCode.INTERNAL_ERROR, result.error().errorCode());
        assertEquals("Something failed during processing of the request. Try again", result.error().errorMessage());
    }

    // ==================== remove Tests ====================

    @Test
    @DisplayName("Should successfully remove existing key")
    void shouldRemoveExistingKey() {
        // Given
        String key = "test-key";
        DataKey dataKey = new DataKey(key);
        CachedDataValue cachedData = createTestCachedData();
        setupJsonCodecForSuccess(cachedData);
        when(keyValueStore.remove(dataKey)).thenReturn(CompletableFuture.completedFuture(cachedData));

        // When
        CompletableFuture<CacheResponse<String>> resultFuture = keyValueStoreService.remove(key);
        CacheResponse<String> result = resultFuture.join();

        // Then
        assertNotNull(result, "Result should not be null");
        assertEquals("{\"key\":\"value\",\"number\":42}", result.data(), "Data should match expected JSON");
        assertEquals(1L, result.version(), "Version should match");
        assertNull(result.error(), "Error should be null for successful operation");
    }

    @Test
    @DisplayName("Should return not found when removing non-existent key")
    void shouldReturnNotFoundWhenRemovingNonExistentKey() {
        // Given
        String key = "non-existent-key";
        DataKey dataKey = new DataKey(key);
        when(keyValueStore.remove(dataKey)).thenReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<CacheResponse<String>> resultFuture = keyValueStoreService.remove(key);
        CacheResponse<String> result = resultFuture.join();

        // Then
        assertNotNull(result, "Result should not be null");
        assertNull(result.data(), "Data should be null");
        assertNull(result.version(), "Version should be null");
        assertNotNull(result.error(), "Error should not be null");
        assertEquals(CacheErrorCode.NOT_FOUND, result.error().errorCode());
        assertEquals("Key not found", result.error().errorMessage());
    }

    @Test
    @DisplayName("Should handle exception during remove operation")
    void shouldHandleExceptionDuringRemove() {
        // Given
        String key = "test-key";
        DataKey dataKey = new DataKey(key);
        RuntimeException testException = new RuntimeException("Remove failed");
        when(keyValueStore.remove(dataKey)).thenReturn(CompletableFuture.failedFuture(testException));

        // When
        CompletableFuture<CacheResponse<String>> resultFuture = keyValueStoreService.remove(key);
        CacheResponse<String> result = resultFuture.join();

        // Then
        assertNotNull(result, "Result should not be null");
        assertNull(result.data(), "Data should be null");
        assertNull(result.version(), "Version should be null");
        assertNotNull(result.error(), "Error should not be null");
        assertEquals(CacheErrorCode.INTERNAL_ERROR, result.error().errorCode());
        assertEquals("Something failed during processing of the request. Try again", result.error().errorMessage());
    }
}
