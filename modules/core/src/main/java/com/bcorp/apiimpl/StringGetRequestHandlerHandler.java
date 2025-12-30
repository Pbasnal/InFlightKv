package com.bcorp.apiimpl;

import com.bcorp.CacheError;
import com.bcorp.CacheErrorCode;
import com.bcorp.CacheResponse;
import com.bcorp.api.Filter;
import com.bcorp.api.KeyOnlyRequestHandler;
import com.bcorp.codec.Codec;
import com.bcorp.codec.CodecProvider;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class StringGetRequestHandlerHandler implements KeyOnlyRequestHandler<String, CacheResponse<?>> {

    private final CodecProvider codecProvider;

    public StringGetRequestHandlerHandler(CodecProvider _codecProvider) {
        this.codecProvider = _codecProvider;
    }

    @Override
    public CompletableFuture<CacheResponse<?>> handle(String key, List<Filter> filters, KeyValueStore keyValueStore) {

        DataKey dataKey = new DataKey(key);

        return keyValueStore.get(dataKey).thenApply(v -> {

            if (v == null) {
                return CacheResponse.failure(new CacheError(CacheErrorCode.NOT_FOUND, "Key not found"));
            }

            Codec<?> codec = codecProvider.getCodec(v.dataType());
            return CacheResponse.success(codec.decode(v), v.version());
        });
    }
}

