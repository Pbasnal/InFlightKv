package com.bcorp.InFlightKv.handlers;

import com.bcorp.InFlightKv.utils.CacheExceptionUtils;
import com.bcorp.InFlightKv.utils.CacheHandlerUtils;
import com.bcorp.InFlightKv.utils.Either;
import com.bcorp.InFlightKv.pojos.CacheError;
import com.bcorp.InFlightKv.pojos.CacheErrorCode;
import com.bcorp.InFlightKv.pojos.CacheResponse;
import com.bcorp.InFlightKv.utils.JsonUtils;
import com.bcorp.api.filters.Filter;
import com.bcorp.api.handlers.KeyValueRequestHandler;
import com.bcorp.api.filters.VersionFilter;
import com.bcorp.codec.JsonCodec;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.CachedDataValue;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.RequestDataValue;
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
            CachedDataValue existingData,
            KeyValueStore keyValueStore
    ) {
        if (existingData == null) {
            return CompletableFuture.completedFuture(CacheResponse.failure(CacheErrorCode.NOT_FOUND, "Expected version not found"));
        }

        if (expectedVersion != null && expectedVersion != existingData.version()) {
            return CompletableFuture.completedFuture(CacheResponse.failure(CacheErrorCode.CONFLICT, "Expected version doesn't match latest version"));
        }

        Either<RequestDataValue, CacheError> dataToSet;
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

    private Either<RequestDataValue, CacheError> mergeData(JsonNode inputValueNode, CachedDataValue existingData) {
        Either<JsonNode, CacheError> decodingExistingNode = CacheHandlerUtils.decodeDataValue(existingData, jsonCodec);
        if (!decodingExistingNode.isSuccess()) {
            return Either.failed(decodingExistingNode.getErrorResponse());
        }

        ObjectNode merged = JsonUtils.shallowMerge((ObjectNode) decodingExistingNode.getSuccessResponse(), (ObjectNode) inputValueNode);

        Either<RequestDataValue, CacheError> encodingMergedValue = CacheHandlerUtils
                .encodeJsonNode(merged, jsonCodec);

        return encodingMergedValue;
    }
}
