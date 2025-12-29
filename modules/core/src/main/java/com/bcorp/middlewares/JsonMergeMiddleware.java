package com.bcorp.middlewares;

import com.bcorp.CacheResponse;
import com.bcorp.api.KeyValueStoreApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.concurrent.CompletableFuture;

//public class JsonMergeMiddleware implements KvOperationsMiddleware<JsonNode, CacheResponse<JsonNode>> {
//    private KeyValueStoreApi kvStoreApi;
//    private static final ObjectMapper mapper = new ObjectMapper();
//
//    public JsonMergeMiddleware(KeyValueStoreApi _kvStoreApi) {
//        this.kvStoreApi = _kvStoreApi;
//    }
//
//    public CompletableFuture<CacheResponse<JsonNode>> execute(
//            String key,
//            JsonNode value,
//            FunctionSet<String, JsonNode, CompletableFuture<CacheResponse<JsonNode>>> kvOperation) {
//
//        return kvStoreApi.<JsonNode>get(key).thenCompose(existingData -> {
//            JsonNode merged = shallowMerge(existingData.data(), value);
//            CompletableFuture<CacheResponse<JsonNode>> res = kvOperation.apply(key, merged);
//            return res;
//        });
//    }
//
//    public static JsonNode shallowMerge(JsonNode mainNode, JsonNode updateNode) {
//        // 1. Handle null/missing inputs
//        if (mainNode == null || mainNode.isNull()) return updateNode.deepCopy();
//        if (updateNode == null || updateNode.isNull()) return mainNode.deepCopy();
//
//        // 2. Shallow merge only makes sense for Objects.
//        // If either is an Array or Primitive, the update usually replaces the main.
//        if (!mainNode.isObject() || !updateNode.isObject()) {
//            return updateNode.deepCopy();
//        }
//
//        // 3. Create a deep copy of the main node to prevent mutating the original
//        // This is critical if mainNode is stored in your In-Memory KV cache.
//        ObjectNode result = mainNode.deepCopy();
//
//        // 4. Use putAll for O(N) shallow replacement
//        result.setAll((ObjectNode) updateNode);
//
//        return result;
//    }
//
//
//}
