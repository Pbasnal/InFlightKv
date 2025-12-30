package com.bcorp.apiimpl;

import com.bcorp.api.Filter;
import com.bcorp.api.KeyOnlyRequestHandler;
import com.bcorp.codec.Codec;
import com.bcorp.codec.CodecProvider;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class StringRemoveRequestHandlerHandler implements KeyOnlyRequestHandler<String, ResponseHolder<?>> {

    private final CodecProvider codecProvider;

    public StringRemoveRequestHandlerHandler(CodecProvider _codecProvider) {
        this.codecProvider = _codecProvider;
    }

    @Override
    public CompletableFuture<ResponseHolder<?>> handle(String key, List<Filter> filters, KeyValueStore keyValueStore) {

        DataKey dataKey = new DataKey(key);

        return keyValueStore.remove(dataKey).thenApply(v -> {
            Codec<?> codec = codecProvider.getCodec(v.dataType());
            return ResponseHolder.success(codec.decode(v), v.version());
        });
    }
}
