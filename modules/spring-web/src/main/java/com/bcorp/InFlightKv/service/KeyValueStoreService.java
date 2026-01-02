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
                                                        boolean mergeInputAndExisting) {
        DataKey dataKey = new DataKey(key);

        Either<JsonNode, CacheError> parsingInputJson = CacheHandlerUtils.parseJsonString(value, jsonCodec);
        if (!parsingInputJson.isSuccess()) {
            return CompletableFuture.completedFuture(CacheResponse.failure(parsingInputJson.getErrorResponse()));
        }

        JsonNode inputValueNode = parsingInputJson.getSuccessResponse();
        boolean needsRead = mergeInputAndExisting || expectedLatestVersion != null;

        if (!needsRead) {
            return writeValue(dataKey, inputValueNode, null);
        }

        return keyValueStore.get(dataKey)
                .thenCompose(existingData -> {
                            Either<Long, CacheError> prevVersionCheck = versionCheck(existingData, expectedLatestVersion);
                            if (!prevVersionCheck.isSuccess()) {
                                return CompletableFuture.completedFuture(CacheResponse.failure(prevVersionCheck.getErrorResponse()));
                            }

                            Either<RequestDataValue, CacheError> dataToSet = getDataToSet(inputValueNode, existingData, mergeInputAndExisting);
                            if (dataToSet.isSuccess()) {
                                Long prevVersion = prevVersionCheck.getSuccessResponse();
                                return keyValueStore.set(dataKey, dataToSet.getSuccessResponse(), prevVersion)
                                        .thenApply(cacheValue -> CacheHandlerUtils.handleCacheResponse(cacheValue, jsonCodec));
                            }
                            return CompletableFuture.completedFuture(CacheResponse.failure(dataToSet.getErrorResponse()));
                        }
                );
    }

    public CompletableFuture<List<DataKey>> getAllKeys() {
        return keyValueStore.getAllKeys();
    }


    private Either<Long, CacheError> versionCheck(CachedDataValue value, Long version) {
        if (version == null || (value != null && version.equals(value.version()))) {
            return Either.success(version);
        }
        return Either.failed(new CacheError(CacheErrorCode.CONFLICT, "Expected version doesn't match latest version"));
    }

    private Either<RequestDataValue, CacheError> getDataToSet(JsonNode inputValueNode,
                                                              CachedDataValue existingData,
                                                              boolean mergeInputAndExisting) {
        if (mergeInputAndExisting && existingData != null) {
            return mergeData(inputValueNode, existingData);
        } else {
            return CacheHandlerUtils.encodeJsonNode(inputValueNode, jsonCodec);
        }
    }

    private CompletableFuture<CacheResponse<String>> writeValue(
            DataKey key,
            JsonNode inputNode,
            Long prevVersion
    ) {
        Either<RequestDataValue, CacheError> encoded =
                CacheHandlerUtils.encodeJsonNode(inputNode, jsonCodec);

        if (!encoded.isSuccess()) {
            return CompletableFuture.completedFuture(
                    CacheResponse.failure(encoded.getErrorResponse())
            );
        }

        return keyValueStore
                .set(key, encoded.getSuccessResponse(), prevVersion)
                .thenApply(dv -> CacheHandlerUtils.handleCacheResponse(dv, jsonCodec));
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
