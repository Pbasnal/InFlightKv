package com.bcorp.InFlightKv.service;

import com.bcorp.InFlightKv.pojos.CacheError;
import com.bcorp.InFlightKv.pojos.CacheErrorCode;
import com.bcorp.InFlightKv.pojos.CacheResponse;
import com.bcorp.InFlightKv.utils.CacheExceptionUtils;
import com.bcorp.InFlightKv.utils.CacheHandlerUtils;
import com.bcorp.InFlightKv.utils.Either;
import com.bcorp.InFlightKv.utils.JsonUtils;
import com.bcorp.api.filters.Filter;
import com.bcorp.api.filters.VersionFilter;
import com.bcorp.codec.JsonCodec;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.CachedDataValue;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.RequestDataValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class KeyValueStoreService {
    private final JsonCodec jsonCodec;
    private final KeyValueStore keyValueStore;

    public KeyValueStoreService(KeyValueStore _keyValueStore, JsonCodec _jsonCodec) {
        this.jsonCodec = _jsonCodec;
        this.keyValueStore = _keyValueStore;
    }

    public CompletableFuture<CacheResponse<String>> get(String key) {
        DataKey dataKey = new DataKey(key);

        return keyValueStore.get(dataKey)
                .thenApply(dataValue -> CacheHandlerUtils.handleCacheResponse(dataValue, jsonCodec))
                .exceptionally(CacheExceptionUtils::handleCacheExceptions);
    }

    public CompletableFuture<CacheResponse<String>> remove(String key) {
        DataKey dataKey = new DataKey(key);

        return keyValueStore.remove(dataKey)
                .thenApply(dataValue -> CacheHandlerUtils.handleCacheResponse(dataValue, jsonCodec))
                .exceptionally(CacheExceptionUtils::handleCacheExceptions);
    }

    public CompletableFuture<CacheResponse<String>> set(String key,
                                                        String value,
                                                        Long expectedLatestVersion,
                                                        boolean mergeInputAndExiting) {
        DataKey dataKey = new DataKey(key);

        Either<JsonNode, CacheError> parsingInputJson = CacheHandlerUtils.parseJsonString(value, jsonCodec);
        if (!parsingInputJson.isSuccess()) {
            return CompletableFuture.completedFuture(CacheResponse.failure(parsingInputJson.getErrorResponse()));
        }

        JsonNode inputValueNode = parsingInputJson.getSuccessResponse();

        if (expectedLatestVersion == null && !mergeInputAndExiting) {
            return keyValueStore.set(dataKey, jsonCodec.encode(inputValueNode), null)
                    .thenApply(dataValue -> CacheHandlerUtils.handleCacheResponse(dataValue, jsonCodec))
                    .exceptionally(CacheExceptionUtils::handleCacheExceptions);
        } else {
            return keyValueStore.get(dataKey)
                    .thenCompose(existingData -> checkVersionAndSetValue(dataKey,
                            inputValueNode,
                            expectedLatestVersion,
                            existingData,
                            keyValueStore,
                            mergeInputAndExiting))
                    .exceptionally(CacheExceptionUtils::handleCacheExceptions);
        }
    }

    public CompletableFuture<List<DataKey>> getAllKeys() {
        return keyValueStore.getAllKeys();
    }

    private CompletableFuture<CacheResponse<String>> checkVersionAndSetValue(
            DataKey key,
            JsonNode inputValueNode,
            Long expectedVersion,
            CachedDataValue existingData,
            KeyValueStore keyValueStore,
            boolean mergeInputAndExiting
    ) {
        if (existingData == null) {
            return CompletableFuture.completedFuture(CacheResponse.failure(CacheErrorCode.NOT_FOUND, "Expected version not found"));
        }

        if (expectedVersion != null && expectedVersion != existingData.version()) {
            return CompletableFuture.completedFuture(CacheResponse.failure(CacheErrorCode.CONFLICT, "Expected version doesn't match latest version"));
        }

        Either<RequestDataValue, CacheError> dataToSet;
        if (mergeInputAndExiting) {
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
