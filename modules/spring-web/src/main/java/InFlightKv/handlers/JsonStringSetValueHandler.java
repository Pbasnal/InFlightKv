package InFlightKv.handlers;

import InFlightKv.utils.CacheExceptionUtils;
import InFlightKv.utils.CacheHandlerUtils;
import InFlightKv.utils.Either;
import InFlightKv.pojos.CacheError;
import InFlightKv.pojos.CacheErrorCode;
import InFlightKv.pojos.CacheResponse;
import com.bcorp.api.filters.Filter;
import com.bcorp.api.handlers.KeyValueRequestHandler;
import com.bcorp.api.filters.VersionFilter;
import com.bcorp.codec.JsonCodec;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.DataValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class JsonStringSetValueHandler implements KeyValueRequestHandler<String, String, CacheResponse<String>> {
    private final JsonCodec jsonCodec;
    private final boolean enablePatching;

    public JsonStringSetValueHandler(JsonCodec _jsonCodec, boolean _enablePatching) {
        this.jsonCodec = _jsonCodec;
        this.enablePatching = _enablePatching;
    }

    @Override
    public CompletableFuture<CacheResponse<String>> handle(String key, String value, List<Filter> filters, KeyValueStore keyValueStore) {
        DataKey dataKey = new DataKey(key);

        Either<JsonNode, CacheError> parsingInputJson = CacheHandlerUtils.parseJsonString(value, jsonCodec);
        if (!parsingInputJson.isSuccess()) {
            return CompletableFuture.completedFuture(CacheResponse.failure(parsingInputJson.getErrorResponse()));
        }

        JsonNode inputValueNode = parsingInputJson.getSuccessResponse();

        // Check for version filter
        Long expectedLatestVersion = filters.stream()
                .filter(f -> f instanceof VersionFilter)
                .map(f -> ((VersionFilter) f).version())
                .findFirst()
                .orElse(null);

        if (expectedLatestVersion == null && !enablePatching) {
            return keyValueStore.set(dataKey, jsonCodec.encode(inputValueNode), null)
                    .thenApply(dataValue -> CacheHandlerUtils.handleCacheResponse(dataValue, jsonCodec))
                    .exceptionally(CacheExceptionUtils::handleCacheExceptions);
        } else {
            return keyValueStore.get(dataKey)
                    .thenCompose(existingData -> checkVersionAndSetValue(dataKey,
                            inputValueNode,
                            expectedLatestVersion,
                            existingData,
                            keyValueStore))
                    .exceptionally(CacheExceptionUtils::handleCacheExceptions);
        }
    }

    private CompletableFuture<CacheResponse<String>> checkVersionAndSetValue(
            DataKey key,
            JsonNode inputValueNode,
            Long expectedVersion,
            DataValue existingData,
            KeyValueStore keyValueStore
    ) {
        if (existingData == null) {
            return CompletableFuture.completedFuture(CacheResponse.failure(CacheErrorCode.NOT_FOUND, "Expected version not found"));
        }

        if (expectedVersion != null && expectedVersion != existingData.version()) {
            return CompletableFuture.completedFuture(CacheResponse.failure(CacheErrorCode.CONFLICT, "Expected version doesn't match latest version"));
        }

        Either<DataValue, CacheError> dataToSet;
        if (enablePatching) {
            dataToSet = mergeData(inputValueNode, existingData);
        } else {
            dataToSet = CacheHandlerUtils.encodeJsonNode(inputValueNode, jsonCodec);
        }

        if (!dataToSet.isSuccess()) {
            return CompletableFuture.completedFuture(CacheResponse.failure(dataToSet.getErrorResponse()));
        }

        return keyValueStore.set(key, dataToSet.getSuccessResponse(), existingData.version())
                .thenApply(dataValue -> CacheHandlerUtils.handleCacheResponse(dataValue, jsonCodec));
    }

    private Either<DataValue, CacheError> mergeData(JsonNode inputValueNode, DataValue existingData) {
        Either<JsonNode, CacheError> decodingExistingNode = CacheHandlerUtils.decodeDataValue(existingData, jsonCodec);
        if (!decodingExistingNode.isSuccess()) {
            return Either.failed(decodingExistingNode.getErrorResponse());
        }

        ObjectNode merged = shallowMerge((ObjectNode) decodingExistingNode.getSuccessResponse(), (ObjectNode) inputValueNode);

        Either<DataValue, CacheError> encodingMergedValue = CacheHandlerUtils.encodeJsonNode(merged, jsonCodec);

        return encodingMergedValue;
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
        result.setAll(updateNode);

        return result;
    }
}
