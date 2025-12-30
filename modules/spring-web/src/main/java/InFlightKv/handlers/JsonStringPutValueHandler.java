package InFlightKv.handlers;

import InFlightKv.utils.CacheExceptionUtils;
import InFlightKv.utils.Either;
import InFlightKv.utils.CacheHandlerUtils;
import com.bcorp.CacheError;
import com.bcorp.CacheErrorCode;
import com.bcorp.CacheResponse;
import com.bcorp.api.Filter;
import com.bcorp.api.KeyValueRequestHandler;
import com.bcorp.api.VersionFilter;
import com.bcorp.codec.JsonCodec;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.DataValue;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class JsonStringPutValueHandler implements KeyValueRequestHandler<String, String, CacheResponse<String>> {

    private final JsonCodec jsonCodec;

    public JsonStringPutValueHandler(JsonCodec _jsonCodec) {
        this.jsonCodec = _jsonCodec;
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

        if (expectedLatestVersion == null) {
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

        if (expectedVersion != existingData.version()) {
            return CompletableFuture.completedFuture(CacheResponse.failure(CacheErrorCode.CONFLICT, "Expected version doesn't match latest version"));
        }
        return keyValueStore.set(key, jsonCodec.encode(inputValueNode), existingData.version())
                .thenApply(dataValue -> CacheHandlerUtils.handleCacheResponse(dataValue, jsonCodec));
    }
}
