package com.bcorp.api;

import com.bcorp.CacheResponse;
import com.bcorp.codec.Codec;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.DataValue;


import java.util.concurrent.CompletableFuture;

public class StringSetRequestHandler implements IHandleRequests<String> {

    private final Codec<String> stringCodec;

    public StringSetRequestHandler(Codec<String> _stringCodec) {
        this.stringCodec = _stringCodec;
    }

    @Override
    public CompletableFuture<CacheResponse<String>> handle(CacheRequest request, KeyValueStore store) {

        DataValue value = stringCodec.encode((String) request.value().get());

        return store.set(new DataKey(request.key()), value, null)
                .thenApply(v -> {
                    return stringCodec.decode(v);
                });
    }
}
