package com.bcorp.api;

import com.bcorp.CacheResponse;
import com.bcorp.codec.Codec;
import com.bcorp.codec.CodecProvider;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class StringGetRequestHandlerHandler implements KeyOnlyRequestHandler<String> {

    private final CodecProvider codecProvider;

    public StringGetRequestHandlerHandler(CodecProvider _codecProvider) {
        this.codecProvider = _codecProvider;
    }

    @Override
    public CompletableFuture<CacheResponse<?>> handle(String key, List<Filter> filters, KeyValueStore keyValueStore) {

        DataKey dataKey = new DataKey(key);

        return keyValueStore.get(dataKey).thenApply(v -> {
            Codec<?> codec = codecProvider.getCodec(v.dataType());
            return new CacheResponse<>(codec.decode(v), v.version());
        });
    }
}
