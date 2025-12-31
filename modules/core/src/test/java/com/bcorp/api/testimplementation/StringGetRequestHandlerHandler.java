package com.bcorp.api.testimplementation;

import com.bcorp.api.filters.Filter;
import com.bcorp.api.handlers.KeyOnlyRequestHandler;
import com.bcorp.codec.Codec;
import com.bcorp.codec.CodecProvider;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class StringGetRequestHandlerHandler implements KeyOnlyRequestHandler<String, ResponseHolder<?>> {

    private final CodecProvider codecProvider;

    public StringGetRequestHandlerHandler(CodecProvider _codecProvider) {
        this.codecProvider = _codecProvider;
    }

    @Override
    public CompletableFuture<ResponseHolder<?>> handle(String key, List<Filter> filters, KeyValueStore keyValueStore) {

        DataKey dataKey = new DataKey(key);

        return keyValueStore.get(dataKey).thenApply(v -> {

            if (v == null) {
                return ResponseHolder.failure(404);
            }

            Codec<?> codec = codecProvider.getCodec(v.dataType());
            return ResponseHolder.success(codec.decode(v), v.version());
        });
    }
}

