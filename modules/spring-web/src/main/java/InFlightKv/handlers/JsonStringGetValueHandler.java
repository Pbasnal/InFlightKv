package InFlightKv.handlers;

import InFlightKv.utils.CacheExceptionUtils;
import InFlightKv.utils.CacheHandlerUtils;
import InFlightKv.pojos.CacheResponse;
import com.bcorp.api.filters.Filter;
import com.bcorp.api.handlers.KeyOnlyRequestHandler;
import com.bcorp.codec.JsonCodec;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class JsonStringGetValueHandler implements KeyOnlyRequestHandler<String, CacheResponse<String>> {

    private final JsonCodec jsonCodec;

    public JsonStringGetValueHandler(JsonCodec _jsonCodec) {
        this.jsonCodec = _jsonCodec;
    }

    @Override
    public CompletableFuture<CacheResponse<String>> handle(String key, List<Filter> filters, KeyValueStore keyValueStore) {
        DataKey dataKey = new DataKey(key);

        return keyValueStore.get(dataKey)
                .thenApply(dataValue -> CacheHandlerUtils.handleCacheResponse(dataValue, jsonCodec))
                .exceptionally(CacheExceptionUtils::handleCacheExceptions);
    }
}
