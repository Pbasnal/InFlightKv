package com.bcorp.apiimpl;

import com.bcorp.CacheResponse;
import com.bcorp.api.Filter;
import com.bcorp.api.KeyValueRequestHandler;
import com.bcorp.codec.Codec;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.DataValue;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class StringKeyStringValueSetHandlerHandler implements KeyValueRequestHandler<String, String, CacheResponse<String>> {
    private final Codec<String> codec;

    public StringKeyStringValueSetHandlerHandler(Codec<String> _codec) {
        this.codec = _codec;
    }

    @Override
    public CompletableFuture<CacheResponse<String>> handle(String key, String value, List<Filter> filters, KeyValueStore keyValueStore) {
        DataKey dataKey = new DataKey(key);
        DataValue dataValue = codec.encode(value);

        return keyValueStore.set(dataKey, dataValue, null).thenApply(v -> {
            return CacheResponse.success(codec.decode(v), v.version());
        });
    }
}
