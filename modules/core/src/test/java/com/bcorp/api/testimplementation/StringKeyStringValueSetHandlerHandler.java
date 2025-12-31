package com.bcorp.api.testimplementation;

import com.bcorp.api.filters.Filter;
import com.bcorp.api.handlers.KeyValueRequestHandler;
import com.bcorp.codec.Codec;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.RequestDataValue;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class StringKeyStringValueSetHandlerHandler implements KeyValueRequestHandler<String, String, ResponseHolder<String>> {
    private final Codec<String> codec;

    public StringKeyStringValueSetHandlerHandler(Codec<String> _codec) {
        this.codec = _codec;
    }

    @Override
    public CompletableFuture<ResponseHolder<String>> handle(String key, String value, List<Filter> filters, KeyValueStore keyValueStore) {
        DataKey dataKey = new DataKey(key);
        RequestDataValue requestDataValue = codec.encode(value);

        return keyValueStore.set(dataKey, requestDataValue, null).thenApply(v -> {
            return ResponseHolder.success(codec.decode(v), v.version());
        });
    }
}
