package com.bcorp.InFlightKv.handlers;

import com.bcorp.InFlightKv.pojos.CacheResponse;
import com.bcorp.InFlightKv.utils.CacheExceptionUtils;
import com.bcorp.InFlightKv.utils.CacheHandlerUtils;
import com.bcorp.api.filters.Filter;
import com.bcorp.api.handlers.KeyOnlyRequestHandler;
import com.bcorp.codec.JsonCodec;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class JsonStringRemoveHandler implements KeyOnlyRequestHandler<String, CacheResponse<String>> {

    private final JsonCodec jsonCodec;

    public JsonStringRemoveHandler(JsonCodec _jsonCodec) {
        this.jsonCodec = _jsonCodec;
    }

    @Override
    public CompletableFuture<CacheResponse<String>> handle(String key, List<Filter> filters, KeyValueStore keyValueStore) {
        DataKey dataKey = new DataKey(key);

        return keyValueStore.remove(dataKey)
                .thenApply(dataValue -> CacheHandlerUtils.handleCacheResponse(dataValue, jsonCodec))
                .exceptionally(CacheExceptionUtils::handleCacheExceptions);
    }
}
