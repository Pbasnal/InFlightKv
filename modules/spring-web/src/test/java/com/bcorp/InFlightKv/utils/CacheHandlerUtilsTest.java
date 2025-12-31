package com.bcorp.InFlightKv.utils;

import com.bcorp.InFlightKv.pojos.CacheErrorCode;
import com.bcorp.InFlightKv.pojos.CacheResponse;
import com.bcorp.codec.JsonCodec;
import com.bcorp.exceptions.JsonDecodingFailed;
import com.bcorp.pojos.DataValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CacheHandlerUtils.
 *
 * Tests all utility methods for JSON parsing, encoding, decoding, and serialization
 * with both success and error scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CacheHandlerUtils Unit Tests")
public class CacheHandlerUtilsTest {

    @Mock
    private JsonCodec jsonCodec;

    private ObjectMapper objectMapper;
    private JsonNode testJsonNode;
    private String testJsonString;
    private DataValue testDataValue;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        testJsonString = "{\"key\":\"value\",\"number\":42}";
        testJsonNode = objectMapper.createObjectNode()
                .put("key", "value")
                .put("number", 42);
        testDataValue = new DataValue(
                testJsonString.getBytes(StandardCharsets.UTF_8),
                ObjectNode.class,
                System.currentTimeMillis(),
                0L
        );
    }

    // ==================== parseJsonString Tests ====================

    @Test
    @DisplayName("Should successfully parse valid JSON string")
    void shouldParseValidJsonString() {
        // Given
        when(jsonCodec.fromString(testJsonString)).thenReturn(testJsonNode);

        // When
        var result = CacheHandlerUtils.parseJsonString(testJsonString, jsonCodec);

        // Then
        assertTrue(result.isSuccess(), "Should return success for valid JSON");
        assertNotNull(result.getSuccessResponse(), "Success response should not be null");
        assertEquals(testJsonNode, result.getSuccessResponse());
        assertNull(result.getErrorResponse(), "Error response should be null");
        verify(jsonCodec).fromString(testJsonString);
    }

    @Test
    @DisplayName("Should return error when JSON string is invalid")
    void shouldReturnErrorForInvalidJsonString() {
        // Given
        String invalidJson = "{invalid json}";
        JsonDecodingFailed exception = new JsonDecodingFailed(new IOException("Invalid JSON"));
        when(jsonCodec.fromString(invalidJson)).thenThrow(exception);

        // When
        var result = CacheHandlerUtils.parseJsonString(invalidJson, jsonCodec);

        // Then
        assertFalse(result.isSuccess(), "Should return failure for invalid JSON");
        assertNull(result.getSuccessResponse(), "Success response should be null");
        assertNotNull(result.getErrorResponse(), "Error response should not be null");
        assertEquals(CacheErrorCode.WRONG_DATA_TYPE, result.getErrorResponse().errorCode());
        assertEquals("Value is not a proper json string", result.getErrorResponse().errorMessage());
        verify(jsonCodec).fromString(invalidJson);
    }

    @Test
    @DisplayName("Should handle empty JSON string")
    void shouldHandleEmptyJsonString() {
        // Given
        String emptyJson = "";
        JsonNode emptyNode = objectMapper.createObjectNode();
        when(jsonCodec.fromString(emptyJson)).thenReturn(emptyNode);

        // When
        var result = CacheHandlerUtils.parseJsonString(emptyJson, jsonCodec);

        // Then
        assertTrue(result.isSuccess(), "Should handle empty string (comment says user allowed to store null/empty)");
        verify(jsonCodec).fromString(emptyJson);
    }

    // ==================== decodeDataValue Tests ====================

    @Test
    @DisplayName("Should successfully decode DataValue to JsonNode")
    void shouldDecodeDataValueSuccessfully() {
        // Given
        when(jsonCodec.decode(testDataValue)).thenReturn(testJsonNode);

        // When
        var result = CacheHandlerUtils.decodeDataValue(testDataValue, jsonCodec);

        // Then
        assertTrue(result.isSuccess(), "Should return success for valid DataValue");
        assertNotNull(result.getSuccessResponse(), "Success response should not be null");
        assertEquals(testJsonNode, result.getSuccessResponse());
        assertNull(result.getErrorResponse(), "Error response should be null");
        verify(jsonCodec).decode(testDataValue);
    }

    @Test
    @DisplayName("Should return error when decoding fails")
    void shouldReturnErrorWhenDecodingFails() {
        // Given
        JsonDecodingFailed exception = new JsonDecodingFailed(new IOException("Decoding failed"));
        when(jsonCodec.decode(testDataValue)).thenThrow(exception);

        // When
        var result = CacheHandlerUtils.decodeDataValue(testDataValue, jsonCodec);

        // Then
        assertFalse(result.isSuccess(), "Should return failure when decoding fails");
        assertNull(result.getSuccessResponse(), "Success response should be null");
        assertNotNull(result.getErrorResponse(), "Error response should not be null");
        assertEquals(CacheErrorCode.WRONG_DATA_TYPE, result.getErrorResponse().errorCode());
        assertEquals("Failed to decode data to json", result.getErrorResponse().errorMessage());
        verify(jsonCodec).decode(testDataValue);
    }

    // ==================== serializeJsonNode Tests ====================

    @Test
    @DisplayName("Should successfully serialize JsonNode to String")
    void shouldSerializeJsonNodeSuccessfully() {
        // Given
        String expectedJsonString = "{\"key\":\"value\"}";
        when(jsonCodec.toString(testJsonNode)).thenReturn(expectedJsonString);

        // When
        var result = CacheHandlerUtils.serializeJsonNode(testJsonNode, jsonCodec);

        // Then
        assertTrue(result.isSuccess(), "Should return success for valid JsonNode");
        assertNotNull(result.getSuccessResponse(), "Success response should not be null");
        assertEquals(expectedJsonString, result.getSuccessResponse());
        assertNull(result.getErrorResponse(), "Error response should be null");
        verify(jsonCodec).toString(testJsonNode);
    }

    // ==================== encodeJsonNode Tests ====================

    @Test
    @DisplayName("Should successfully encode JsonNode to DataValue")
    void shouldEncodeJsonNodeSuccessfully() {
        // Given
        DataValue expectedDataValue = new DataValue(
                testJsonString.getBytes(StandardCharsets.UTF_8),
                ObjectNode.class,
                System.currentTimeMillis(),
                -1L
        );
        when(jsonCodec.encode(testJsonNode)).thenReturn(expectedDataValue);

        // When
        var result = CacheHandlerUtils.encodeJsonNode(testJsonNode, jsonCodec);

        // Then
        assertTrue(result.isSuccess(), "Should return success for valid JsonNode");
        assertNotNull(result.getSuccessResponse(), "Success response should not be null");
        assertEquals(expectedDataValue, result.getSuccessResponse());
        assertNull(result.getErrorResponse(), "Error response should be null");
        verify(jsonCodec).encode(testJsonNode);
    }

    // ==================== handleCacheResponse Tests ====================

    @Test
    @DisplayName("Should return not found when DataValue is null")
    void shouldReturnNotFoundWhenDataValueIsNull() {
        // When
        CacheResponse<String> result = CacheHandlerUtils.handleCacheResponse(null, jsonCodec);

        // Then
        assertNull(result.data(), "Data should be null");
        assertNull(result.version(), "Version should be null");
        assertNotNull(result.error(), "Error should not be null");
        assertEquals(CacheErrorCode.NOT_FOUND, result.error().errorCode());
        assertEquals("Key not found", result.error().errorMessage());
        verifyNoInteractions(jsonCodec);
    }

    @Test
    @DisplayName("Should successfully handle valid DataValue response")
    void shouldHandleValidDataValueResponse() {
        // Given
        String serializedJson = "{\"key\":\"value\"}";
        when(jsonCodec.decode(testDataValue)).thenReturn(testJsonNode);
        when(jsonCodec.toString(testJsonNode)).thenReturn(serializedJson);

        // When
        CacheResponse<String> result = CacheHandlerUtils.handleCacheResponse(testDataValue, jsonCodec);

        // Then
        assertNotNull(result.data(), "Data should not be null");
        assertEquals(serializedJson, result.data());
        assertEquals(5L, result.version(), "Version should match DataValue version");
        assertNull(result.error(), "Error should be null");
        verify(jsonCodec).decode(testDataValue);
        verify(jsonCodec).toString(testJsonNode);
    }
}
