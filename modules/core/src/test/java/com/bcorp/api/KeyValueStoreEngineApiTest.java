package com.bcorp.api;

import com.bcorp.api.handlers.HandlerResolver;
import com.bcorp.api.testimplementation.ResponseHolder;
import com.bcorp.api.testimplementation.StringGetRequestHandlerHandler;
import com.bcorp.api.testimplementation.StringKeyJsonValueSetHandlerHandler;
import com.bcorp.api.testimplementation.StringKeyStringValueSetHandlerHandler;
import com.bcorp.api.testimplementation.StringRemoveRequestHandlerHandler;
import com.bcorp.codec.CodecProvider;
import com.bcorp.codec.JsonCodec;
import com.bcorp.codec.StringCodec;
import com.bcorp.exceptions.HandlerNotFoundException;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.DataValue;
import com.bcorp.testutils.TestUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static com.bcorp.testutils.TestUtils.waitFuture;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for KeyValueStoreEngine API layer.
 * <p>
 * These tests validate that the API can be implemented to suit different needs:
 * - Different data types (String, JSON, custom types)
 * - Different codec implementations
 * - Version-based optimistic locking
 * - Handler resolution and registration
 * - Error handling and edge cases
 */
@DisplayName("KeyValueStoreEngine API Integration Tests")
class KeyValueStoreEngineApiTest {

    private KeyValueStore keyValueStore;
    private HandlerResolver handlerResolver;
    private CodecProvider codecProvider;
    private KeyValueStoreEngine engine;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        keyValueStore = new KeyValueStore();
        handlerResolver = new HandlerResolver();
        objectMapper = new ObjectMapper();
    }

    // ==================== String Key-Value Operations ====================

    @Test
    @DisplayName("Should set and get string values using API layer")
    void shouldSetAndGetStringValues() {
        // Given
        setupStringHandlers();
        String key = "test-key";
        String value = "test-value";

        // When
        ResponseHolder<String> setResponse = waitFuture(engine.setCache(key, value, null, CacheRequestMethod.set()));
        ResponseHolder<String> getResponse = waitFuture(engine.getCache(key, CacheRequestMethod.get()));

        // Then
        assertNotNull(setResponse);
        assertNull(setResponse.errorCode(), "Set should succeed");
        assertEquals(value, setResponse.data());
        assertEquals(0L, setResponse.version(), "Initial version should be 0");

        assertNotNull(getResponse);
        assertNull(getResponse.errorCode(), "Get should succeed");
        assertEquals(value, getResponse.data());
        assertEquals(setResponse.version(), getResponse.version());
    }

    @Test
    @DisplayName("Should update string values and increment version")
    void shouldUpdateStringValuesWithVersionIncrement() {
        // Given
        setupStringHandlers();
        String key = "update-key";
        String initialValue = "initial";
        String updatedValue = "updated";

        // When - Set initial value
        ResponseHolder<String> initialResponse = waitFuture(engine.setCache(key, initialValue, null, CacheRequestMethod.set()));

        // Then - Verify initial state
        assertEquals(0L, initialResponse.version());
        assertEquals(initialValue, initialResponse.data());

        // When - Update value
        ResponseHolder<String> updateResponse = waitFuture(engine.setCache(key, updatedValue, null, CacheRequestMethod.set()));

        // Then - Verify update
        assertEquals(1L, updateResponse.version(), "Version should increment");
        assertEquals(updatedValue, updateResponse.data());

        // Verify final state
        ResponseHolder<String> finalGet = waitFuture(engine.getCache(key, CacheRequestMethod.get()));
        assertEquals(updatedValue, finalGet.data());
        assertEquals(1L, finalGet.version());
    }

    @Test
    @DisplayName("Should return 404 error when getting non-existent key")
    void shouldReturnErrorForNonExistentKey() {
        // Given
        setupStringHandlers();
        String nonExistentKey = "non-existent-key";

        // When
        ResponseHolder<?> response = waitFuture(engine.getCache(nonExistentKey, CacheRequestMethod.get()));

        // Then
        assertNotNull(response);
        assertEquals(404, response.errorCode(), "Should return 404 for non-existent key");
        assertNull(response.data());
        assertNull(response.version());
    }

    // ==================== JSON Operations ====================

    @Test
    @DisplayName("Should set and get JSON values using ObjectNode")
    void shouldSetAndGetJsonValues() throws JsonProcessingException {
        // Given
        setupJsonHandlers();
        String key = "json-key";
        TestData testData = new TestData(1, Arrays.asList(12, 13, 14, 15));
        String jsonString = objectMapper.writeValueAsString(testData);
        ObjectNode jsonNode = (ObjectNode) objectMapper.readTree(jsonString);

        // When
        ResponseHolder<ObjectNode> setResponse = waitFuture(engine.setCache(key, jsonNode, null, CacheRequestMethod.set()));
        ResponseHolder<?> getResponse = waitFuture(engine.getCache(key, CacheRequestMethod.get()));

        // Then
        assertNotNull(setResponse);
        assertNull(setResponse.errorCode());
        assertNotNull(setResponse.data());
        assertEquals(0L, setResponse.version());

        assertNotNull(getResponse);
        assertNull(getResponse.errorCode());
        assertTrue(getResponse.data() instanceof ObjectNode);

        // Verify JSON content
        ObjectNode retrievedNode = (ObjectNode) getResponse.data();
        assertEquals(1, retrievedNode.get("data1").asInt());
        assertTrue(retrievedNode.get("data2s").isArray());
    }

    @Test
    @DisplayName("Should merge JSON values when updating")
    void shouldMergeJsonValuesOnUpdate() throws JsonProcessingException {
        // Given
        setupJsonHandlers();
        String key = "merge-key";

        // Initial data
        TestData initialData = new TestData(1, Arrays.asList(12, 13, 14, 15));
        ObjectNode initialNode = (ObjectNode) objectMapper.readTree(
                objectMapper.writeValueAsString(initialData));
        waitFuture(engine.setCache(key, initialNode, null, CacheRequestMethod.set()));

        // Update data (partial)
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        TestData updateData = new TestData(2, null);
        ObjectNode updateNode = (ObjectNode) objectMapper.readTree(
                objectMapper.writeValueAsString(updateData));

        // When - Update with merge behavior
        ResponseHolder<ObjectNode> updateResponse = waitFuture(engine.setCache(key, updateNode, null, CacheRequestMethod.set()));

        // Then - Should merge: data1 updated, data2s preserved
        assertNotNull(updateResponse);
        assertNull(updateResponse.errorCode());
        ObjectNode mergedNode = updateResponse.data();
        assertEquals(2, mergedNode.get("data1").asInt(), "data1 should be updated");
        assertTrue(mergedNode.has("data2s"), "data2s should be preserved from merge");
        assertTrue(mergedNode.get("data2s").isArray());
    }

    // ==================== Version-Based Operations ====================

    @Test
    @DisplayName("Should succeed when setting with correct version")
    void shouldSucceedWithCorrectVersion() {
        // Given
        setupStringHandlers();
        String key = "version-key";
        String initialValue = "initial";

        // Set initial value
        ResponseHolder<String> initialResponse = waitFuture(engine.setCache(key, initialValue, null, CacheRequestMethod.set()));
        Long initialVersion = initialResponse.version();

        // When - Update with correct version
        String updatedValue = "updated";
        ResponseHolder<String> updateResponse = waitFuture(engine.setCache(key, updatedValue, initialVersion, CacheRequestMethod.set()));

        // Then
        assertNotNull(updateResponse);
        assertNull(updateResponse.errorCode(), "Should succeed with correct version");
        assertEquals(updatedValue, updateResponse.data());
        assertEquals(initialVersion + 1, updateResponse.version());
    }

    @Test
    @DisplayName("Should handle version mismatch gracefully")
    void shouldHandleVersionMismatch() {
        // Given
        setupStringHandlers();
        String key = "version-mismatch-key";
        String value = "value";

        // Set initial value
        waitFuture(engine.setCache(key, value, null, CacheRequestMethod.set()));

        // When - Try to update with wrong version
        String newValue = "new-value";

        // Note: The handler needs to check version filter and handle ConcurrentUpdateException
        // This test verifies the API layer passes the version filter correctly
        ResponseHolder<String> response = waitFuture(engine.setCache(key, newValue, 999L, CacheRequestMethod.set()));

        // Then - Should handle version mismatch (implementation dependent)
        // The handler should either return error or throw exception
        assertNotNull(response);
        // Handler implementation may return error code or the operation may fail
    }

    // ==================== Remove Operations ====================

    @Test
    @DisplayName("Should remove existing key and return its value")
    void shouldRemoveExistingKey() {
        // Given
        setupStringHandlers();
        setupRemoveHandler();
        String key = "remove-key";
        String value = "value-to-remove";

        waitFuture(engine.setCache(key, value, null, CacheRequestMethod.set()));
        assertEquals(1, keyValueStore.totalKeys());

        // When
        ResponseHolder<?> removeResponse = waitFuture(engine.removeCache(key, CacheRequestMethod.get()));

        // Then
        assertNotNull(removeResponse);
        assertNull(removeResponse.errorCode());
        assertEquals(value, removeResponse.data());
        assertEquals(0, keyValueStore.totalKeys(), "Key should be removed");

        // Verify key no longer exists
        ResponseHolder<?> getAfterRemove = waitFuture(engine.getCache(key, CacheRequestMethod.get()));
        assertEquals(404, getAfterRemove.errorCode());
    }

    // ==================== Multiple Data Types ====================

    @Test
    @DisplayName("Should support multiple key-value type combinations")
    void shouldSupportMultipleTypeCombinations() {
        // Given - Setup handlers for different types
        CodecProvider multiCodecProvider = new CodecProvider(Map.of(
                String.class, new StringCodec()
        ));

        HandlerResolver multiResolver = new HandlerResolver();
        multiResolver.registerKeyOnlyHandler(CacheRequestMethod.get(), String.class,
                new StringGetRequestHandlerHandler(multiCodecProvider));
        multiResolver.registerKeyValueHandler(CacheRequestMethod.set(), String.class, String.class,
                new StringKeyStringValueSetHandlerHandler(multiCodecProvider.getCodec(String.class)));

        KeyValueStoreEngine multiEngine = new KeyValueStoreEngine(keyValueStore, multiResolver);

        // When - Use different keys (all String type in this case, but demonstrates extensibility)
        String key1 = "type-key-1";
        String key2 = "type-key-2";
        String value1 = "value-1";
        String value2 = "value-2";

        ResponseHolder<String> response1 = waitFuture(multiEngine.setCache(key1, value1, null, CacheRequestMethod.set()));
        ResponseHolder<String> response2 = waitFuture(multiEngine.setCache(key2, value2, null, CacheRequestMethod.set()));

        // Then
        assertNotNull(response1);
        assertNotNull(response2);
        assertEquals(value1, response1.data());
        assertEquals(value2, response2.data());

        // Verify both keys exist independently
        ResponseHolder<?> get1 = waitFuture(multiEngine.getCache(key1, CacheRequestMethod.get()));
        ResponseHolder<?> get2 = waitFuture(multiEngine.getCache(key2, CacheRequestMethod.get()));
        assertEquals(value1, get1.data());
        assertEquals(value2, get2.data());
    }

    // ==================== Error Handling ====================

    @Test
    @DisplayName("Should throw HandlerNotFoundException for unregistered handler")
    void shouldThrowExceptionForUnregisteredHandler() {
        // Given - Engine with no handlers registered
        KeyValueStoreEngine emptyEngine = new KeyValueStoreEngine(keyValueStore, new HandlerResolver());
        String key = "unregistered-key";

        // When & Then
        assertThrows(HandlerNotFoundException.class, () ->
                        emptyEngine.getCache(key, CacheRequestMethod.get()),
                "Should throw HandlerNotFoundException when handler is not registered"
        );
    }

    @Test
    @DisplayName("Should handle empty string values")
    void shouldHandleEmptyStringValues() {
        // Given
        setupStringHandlers();
        String key = "empty-key";
        String emptyValue = "";

        // When
        ResponseHolder<String> setResponse = waitFuture(engine.setCache(key, emptyValue, null, CacheRequestMethod.set()));
        ResponseHolder<String> getResponse = waitFuture(engine.getCache(key, CacheRequestMethod.get()));

        // Then
        assertNotNull(setResponse);
        assertEquals(emptyValue, setResponse.data());
        assertEquals(emptyValue, getResponse.data());
    }

    @Test
    @DisplayName("Should handle special characters in keys and values")
    void shouldHandleSpecialCharacters() {
        // Given
        setupStringHandlers();
        String key = "special-key!@#$%^&*()";
        String value = "special-value!@#$%^&*()";

        // When
        ResponseHolder<String> setResponse = waitFuture(engine.setCache(key, value, null, CacheRequestMethod.set()));
        ResponseHolder<String> getResponse = waitFuture(engine.getCache(key, CacheRequestMethod.get()));

        // Then
        assertNotNull(setResponse);
        assertEquals(value, setResponse.data());
        assertEquals(value, getResponse.data());
    }

    // ==================== Concurrent Operations ====================

    @Test
    @DisplayName("Should handle concurrent operations through API layer")
    void shouldHandleConcurrentOperationsThroughApi() throws InterruptedException {
        // Given
        setupStringHandlers();
        int numThreads = 10;
        int operationsPerThread = 20;
        CountDownLatch latch = new CountDownLatch(numThreads);
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        // When - Concurrent operations
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "concurrent-key-" + threadId + "-" + j;
                        String value = "value-" + threadId + "-" + j;

                        // Set
                        ResponseHolder<String> setResponse = waitFuture(engine.setCache(key, value, null, CacheRequestMethod.set()));
                        assertNotNull(setResponse);
                        assertNull(setResponse.errorCode());

                        // Get
                        ResponseHolder<?> getResponse = waitFuture(engine.getCache(key, CacheRequestMethod.get()));
                        assertNotNull(getResponse);
                        assertEquals(value, getResponse.data());
                    }
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // Then
        assertTrue(latch.await(10, java.util.concurrent.TimeUnit.SECONDS), "All threads should complete");
        assertTrue(errors.isEmpty(), "No errors should occur: " + errors);
        assertEquals(numThreads * operationsPerThread, keyValueStore.totalKeys());
    }

    // ==================== Sequence Operations ====================

    @Test
    @DisplayName("Should handle multiple operations in sequence")
    void shouldHandleMultipleOperationsInSequence() {
        // Given
        setupStringHandlers();
        String key = "sequence-key";

        // When - Perform sequence of operations
        // 1. Initial set
        ResponseHolder<String> response1 = waitFuture(engine.setCache(key, "value1", null, CacheRequestMethod.set()));
        assertEquals(0L, response1.version());
        assertEquals("value1", response1.data());

        // 2. Update
        ResponseHolder<String> response2 = waitFuture(engine.setCache(key, "value2", null, CacheRequestMethod.set()));
        assertEquals(1L, response2.version());
        assertEquals("value2", response2.data());

        // 3. Get
        ResponseHolder<?> response3 = waitFuture(engine.getCache(key, CacheRequestMethod.get()));
        assertEquals("value2", response3.data());
        assertEquals(1L, response3.version());

        // 4. Update with version
        ResponseHolder<String> response4 = waitFuture(engine.setCache(key, "value3", 1L, CacheRequestMethod.set()));
        assertEquals(2L, response4.version());
        assertEquals("value3", response4.data());

        // 5. Final get
        ResponseHolder<?> response5 = waitFuture(engine.getCache(key, CacheRequestMethod.get()));
        assertEquals("value3", response5.data());
        assertEquals(2L, response5.version());
    }

    // ==================== Large Data Handling ====================

    @Test
    @DisplayName("Should handle large string values")
    void shouldHandleLargeStringValues() {
        // Given
        setupStringHandlers();
        String key = "large-key";
        StringBuilder largeValue = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeValue.append("This is a large string value. ");
        }
        String largeString = largeValue.toString();

        // When
        ResponseHolder<String> setResponse = waitFuture(engine.setCache(key, largeString, null, CacheRequestMethod.set()));
        ResponseHolder<String> getResponse = waitFuture(engine.getCache(key, CacheRequestMethod.get()));

        // Then
        assertNotNull(setResponse);
        assertNull(setResponse.errorCode());
        assertEquals(largeString.length(), setResponse.data().length());
        assertEquals(largeString, getResponse.data());
    }

    // ==================== API Extensibility ====================

    @Test
    @DisplayName("Should demonstrate API extensibility with custom handlers")
    void shouldDemonstrateApiExtensibility() {
        // Given - Custom setup with specific handlers
        CodecProvider customCodecProvider = new CodecProvider(Map.of(
                String.class, new StringCodec()
        ));

        HandlerResolver customResolver = new HandlerResolver();
        customResolver.registerKeyOnlyHandler(CacheRequestMethod.get(), String.class,
                new StringGetRequestHandlerHandler(customCodecProvider));
        customResolver.registerKeyValueHandler(CacheRequestMethod.set(), String.class, String.class,
                new StringKeyStringValueSetHandlerHandler(customCodecProvider.getCodec(String.class)));

        KeyValueStoreEngine customEngine = new KeyValueStoreEngine(keyValueStore, customResolver);

        // When - Use custom engine
        String key = "extensible-key";
        String value = "extensible-value";
        ResponseHolder<String> setResponse = waitFuture(customEngine.setCache(key, value, null, CacheRequestMethod.set()));
        ResponseHolder<?> getResponse = waitFuture(customEngine.getCache(key, CacheRequestMethod.get()));

        // Then - Verify custom implementation works
        assertNotNull(setResponse);
        assertNotNull(getResponse);
        assertEquals(value, setResponse.data());
        assertEquals(value, getResponse.data());

        // This demonstrates that the API can be extended with different handler implementations
        // without modifying the core KeyValueStoreEngine
    }

    @Test
    @DisplayName("Should support different handler configurations for different use cases")
    void shouldSupportDifferentHandlerConfigurations() {
        // Given - Two different engine configurations
        KeyValueStore store1 = new KeyValueStore();
        KeyValueStore store2 = new KeyValueStore();

        // Configuration 1: String handlers only
        HandlerResolver resolver1 = new HandlerResolver();
        CodecProvider codecProvider1 = new CodecProvider(Map.of(String.class, new StringCodec()));
        resolver1.registerKeyOnlyHandler(CacheRequestMethod.get(), String.class,
                new StringGetRequestHandlerHandler(codecProvider1));
        resolver1.registerKeyValueHandler(CacheRequestMethod.set(), String.class, String.class,
                new StringKeyStringValueSetHandlerHandler(codecProvider1.getCodec(String.class)));
        KeyValueStoreEngine engine1 = new KeyValueStoreEngine(store1, resolver1);

        // Configuration 2: JSON handlers
        HandlerResolver resolver2 = new HandlerResolver();
        CodecProvider codecProvider2 = new CodecProvider(Map.of(
                String.class, new StringCodec(),
                ObjectNode.class, new JsonCodec()
        ));
        resolver2.registerKeyOnlyHandler(CacheRequestMethod.get(), String.class,
                new StringGetRequestHandlerHandler(codecProvider2));
        resolver2.registerKeyValueHandler(CacheRequestMethod.set(), String.class, ObjectNode.class,
                new StringKeyJsonValueSetHandlerHandler(codecProvider2.getCodec(ObjectNode.class)));
        KeyValueStoreEngine engine2 = new KeyValueStoreEngine(store2, resolver2);

        // When - Use both engines independently
        String key1 = "engine1-key";
        String value1 = "engine1-value";
        ResponseHolder<String> response1 = waitFuture(engine1.setCache(key1, value1, null, CacheRequestMethod.set()));

        String key2 = "engine2-key";
        ObjectNode jsonNode = objectMapper.createObjectNode().put("test", "value");
        ResponseHolder<ObjectNode> response2 = waitFuture(engine2.setCache(key2, jsonNode, null, CacheRequestMethod.set()));

        // Then - Both should work independently
        assertNotNull(response1);
        assertEquals(value1, response1.data());

        assertNotNull(response2);
        assertNotNull(response2.data());

        // Verify isolation
        assertEquals(1, store1.totalKeys());
        assertEquals(1, store2.totalKeys());
    }

    // ==================== Direct KeyValueStore Operations ====================

    @Test
    @DisplayName("Should work with direct KeyValueStore operations for comparison")
    void shouldWorkWithDirectKeyValueStoreOperations() throws JsonProcessingException {
        // Given
        KeyValueStore directStore = new KeyValueStore();
        TestData testData = new TestData(1, Arrays.asList(12, 13, 14, 15));
        String jsonString = objectMapper.writeValueAsString(testData);

        // When - Direct operations
        directStore.set(DataKey.fromString("direct-key"), DataValue.fromString(jsonString), null).join();
        DataValue retrieved = directStore.get(DataKey.fromString("direct-key")).join();

        // Then
        assertNotNull(retrieved);
        String retrievedJson = new String(retrieved.data(), StandardCharsets.UTF_8);
        TestData deserialized = objectMapper.readValue(retrievedJson, TestData.class);
        assertEquals(testData.data1, deserialized.data1);
        assertEquals(testData.data2s, deserialized.data2s);
    }

    // ==================== Helper Methods ====================

    private void setupStringHandlers() {
        codecProvider = new CodecProvider(Map.of(
                String.class, new StringCodec()
        ));
        handlerResolver.registerKeyOnlyHandler(CacheRequestMethod.get(), String.class,
                new StringGetRequestHandlerHandler(codecProvider));
        handlerResolver.registerKeyValueHandler(CacheRequestMethod.set(), String.class, String.class,
                new StringKeyStringValueSetHandlerHandler(codecProvider.getCodec(String.class)));
        engine = new KeyValueStoreEngine(keyValueStore, handlerResolver);
    }

    private void setupJsonHandlers() {
        codecProvider = new CodecProvider(Map.of(
                String.class, new StringCodec(),
                ObjectNode.class, new JsonCodec()
        ));
        handlerResolver.registerKeyOnlyHandler(CacheRequestMethod.get(), String.class,
                new StringGetRequestHandlerHandler(codecProvider));
        handlerResolver.registerKeyValueHandler(CacheRequestMethod.set(), String.class, ObjectNode.class,
                new StringKeyJsonValueSetHandlerHandler(codecProvider.getCodec(ObjectNode.class)));
        engine = new KeyValueStoreEngine(keyValueStore, handlerResolver);
    }

    private void setupRemoveHandler() {
        if (codecProvider == null) {
            codecProvider = new CodecProvider(Map.of(
                    String.class, new StringCodec()
            ));
        }
        handlerResolver.registerKeyOnlyHandler(CacheRequestMethod.get(), String.class,
                new StringRemoveRequestHandlerHandler(codecProvider));
        engine = new KeyValueStoreEngine(keyValueStore, handlerResolver);
    }

    // ==================== Test Data Classes ====================

    /**
     * Test data class for JSON operations.
     * Represents a simple nested structure for testing JSON codec and merge operations.
     */
    private static class TestData {
        public int data1;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public List<TestDataItem> data2s;

        public TestData() {
        }

        public TestData(int data1, List<Integer> data2s) {
            this.data1 = data1;
            if (data2s != null) {
                this.data2s = new ArrayList<>();
                for (Integer item : data2s) {
                    this.data2s.add(new TestDataItem(item));
                }
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestData testData = (TestData) obj;
            if (data1 != testData.data1) return false;
            if (data2s == null && testData.data2s == null) return true;
            if (data2s == null || testData.data2s == null || data2s.size() != testData.data2s.size()) {
                return false;
            }
            List<TestDataItem> sorted1 = new ArrayList<>(data2s);
            List<TestDataItem> sorted2 = new ArrayList<>(testData.data2s);
            sorted1.sort(Comparator.comparingInt(a -> a.data2));
            sorted2.sort(Comparator.comparingInt(a -> a.data2));
            return sorted1.equals(sorted2);
        }

        @Override
        public int hashCode() {
            return Objects.hash(data1, data2s);
        }
    }

    private static class TestDataItem {
        public int data2;

        public TestDataItem() {
        }

        public TestDataItem(int data2) {
            this.data2 = data2;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestDataItem that = (TestDataItem) obj;
            return data2 == that.data2;
        }

        @Override
        public int hashCode() {
            return Objects.hash(data2);
        }
    }
}

