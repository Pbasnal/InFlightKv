package com.bcorp.InFlightKv.utils;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonUtils {
    public static ObjectNode shallowMerge(ObjectNode mainNode, ObjectNode updateNode) {
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
        result.setAll(updateNode);

        return result;
    }
}
