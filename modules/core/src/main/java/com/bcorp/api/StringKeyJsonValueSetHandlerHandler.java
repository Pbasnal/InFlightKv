package com.bcorp.api;

import com.bcorp.CacheResponse;
import com.bcorp.codec.Codec;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.DataValue;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class StringKeyJsonValueSetHandlerHandler implements KeyValueRequestHandler<String, ObjectNode> {

    private final Codec<ObjectNode> jsonCodec;
    public StringKeyJsonValueSetHandlerHandler(Codec<ObjectNode> _codec) {
        this.jsonCodec = _codec;
    }

    @Override
    public CompletableFuture<CacheResponse<ObjectNode>> handle(String key, ObjectNode value, List<Filter> filters, KeyValueStore keyValueStore) {

        DataKey dataKey = new DataKey(key);

        return keyValueStore.get(dataKey).thenCompose((existingData) -> {

            if (existingData == null) {
                return keyValueStore.set(dataKey, jsonCodec.encode(value), null);
            }

            ObjectNode existingNode = jsonCodec.decode(existingData);
            ObjectNode merged = shallowMerge(existingNode, value);
            DataValue mergedDataValue = jsonCodec.encode(merged);

            return keyValueStore.set(dataKey, mergedDataValue, existingData.version());
        }).thenApply(v -> {
            ObjectNode node = jsonCodec.decode(v);
            return new CacheResponse<>(node, v.version());
        });
    }

    public ObjectNode shallowMerge(ObjectNode mainNode, ObjectNode updateNode) {
        // 1. Handle null/missing inputs
        if (mainNode == null || mainNode.isNull()) return updateNode.deepCopy();
        if (updateNode == null || updateNode.isNull()) return mainNode.deepCopy();

        // 2. Shallow merge only makes sense for Objects.
        // If either is an Array or Primitive, the update usually replaces the main.
        if (!mainNode.isObject() || !updateNode.isObject()) {
            return updateNode.deepCopy();
        }

        // 3. Create a deep copy of the main node to prevent mutating the original
        // This is critical if mainNode is stored in your In-Memory KV cache.
        ObjectNode result = mainNode.deepCopy();

        // 4. Use putAll for O(N) shallow replacement
        result.setAll((ObjectNode) updateNode);

        return result;
    }
}