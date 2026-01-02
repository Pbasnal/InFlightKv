package com.bcorp.loadtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP client for interacting with the KV store API
 */
public class KvStoreHttpClient {

    private static final Logger logger = LoggerFactory.getLogger(KvStoreHttpClient.class);

    private final String baseUrl;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public KvStoreHttpClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * PUT operation - create or update a key-value pair
     */
    public void put(String key, String value) throws IOException {
        HttpPut put = new HttpPut(baseUrl + "/kv/" + key);
        put.setEntity(new StringEntity(value, ContentType.APPLICATION_JSON));

        try (ClassicHttpResponse response = httpClient.execute(put)) {
            int statusCode = response.getCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("PUT failed with status: " + statusCode + " for key: " + key);
            }
        }
    }

    /**
     * GET operation - retrieve a value by key
     */
    public String get(String key) throws IOException {
        HttpGet get = new HttpGet(baseUrl + "/kv/" + key);

        try (ClassicHttpResponse response = httpClient.execute(get)) {
            int statusCode = response.getCode();
            if (statusCode == 404) {
                return null; // Key not found
            }
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("GET failed with status: " + statusCode + " for key: " + key);
            }

            HttpEntity entity = response.getEntity();
            try {
                return entity != null ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : null;
            } catch (ParseException e) {
                throw new IOException("Failed to parse response", e);
            }
        }
    }

    /**
     * PATCH operation - partially update a key-value pair
     */
    public void patch(String key, String value) throws IOException {
        HttpPatch patch = new HttpPatch(baseUrl + "/kv/" + key);
        patch.setEntity(new StringEntity(value, ContentType.APPLICATION_JSON));

        try (ClassicHttpResponse response = httpClient.execute(patch)) {
            int statusCode = response.getCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("PATCH failed with status: " + statusCode + " for key: " + key);
            }
        }
    }

    /**
     * DELETE operation - remove a key-value pair
     */
    public void delete(String key) throws IOException {
        HttpDelete delete = new HttpDelete(baseUrl + "/kv/" + key);

        try (ClassicHttpResponse response = httpClient.execute(delete)) {
            int statusCode = response.getCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("DELETE failed with status: " + statusCode + " for key: " + key);
            }
        }
    }

    /**
     * GET ALL KEYS operation - retrieve all keys in the cluster
     */
    public List<KeyNodeInfo> getAllKeys() throws IOException {
        HttpGet get = new HttpGet(baseUrl + "/kv");

        try (ClassicHttpResponse response = httpClient.execute(get)) {
            int statusCode = response.getCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("GET ALL KEYS failed with status: " + statusCode);
            }

            HttpEntity entity = response.getEntity();
            if (entity == null) {
                return new ArrayList<>();
            }

            try {
                String content = EntityUtils.toString(entity, StandardCharsets.UTF_8);
                return parseKeyNodeInfoList(content);
            } catch (ParseException e) {
                throw new IOException("Failed to parse response", e);
            }
        }
    }

    private List<KeyNodeInfo> parseKeyNodeInfoList(String content) throws IOException {
        List<KeyNodeInfo> result = new ArrayList<>();

        try {
            // Try to parse as JSON array first (current controller format)
            JsonNode rootNode = objectMapper.readTree(content);
            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    JsonNode keyNode = node.get("key");
                    JsonNode nodeIdNode = node.get("node");

                    if (keyNode != null && nodeIdNode != null && !keyNode.isNull() && !nodeIdNode.isNull()) {
                        String key = keyNode.asText();
                        String nodeId = nodeIdNode.asText();
                        result.add(new KeyNodeInfo(key, nodeId));
                    }
                }
            } else {
                // Fallback: try to parse as NDJSON (one JSON object per line)
                String[] lines = content.split("\n");
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;

                    JsonNode node = objectMapper.readTree(line);
                    JsonNode keyNode = node.get("key");
                    JsonNode nodeIdNode = node.get("node");

                    if (keyNode != null && nodeIdNode != null && !keyNode.isNull() && !nodeIdNode.isNull()) {
                        String key = keyNode.asText();
                        String nodeId = nodeIdNode.asText();
                        result.add(new KeyNodeInfo(key, nodeId));
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse key-node response: {}", content.substring(0, Math.min(200, content.length())), e);
        }

        return result;
    }

    /**
     * Close the HTTP client
     */
    public void close() throws IOException {
        httpClient.close();
    }

    /**
     * Simple data class for key-node information
     */
    public static class KeyNodeInfo {
        private final String key;
        private final String nodeId;

        public KeyNodeInfo(String key, String nodeId) {
            this.key = key;
            this.nodeId = nodeId;
        }

        public String getKey() {
            return key;
        }

        public String getNodeId() {
            return nodeId;
        }

        @Override
        public String toString() {
            return "KeyNodeInfo{" +
                    "key='" + key + '\'' +
                    ", nodeId='" + nodeId + '\'' +
                    '}';
        }
    }
}
