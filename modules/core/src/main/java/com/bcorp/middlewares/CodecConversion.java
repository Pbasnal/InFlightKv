package com.bcorp.middlewares;

import com.bcorp.CacheResponse;
import com.bcorp.codec.Codec;
import com.bcorp.codec.CodecProvider;

import java.util.concurrent.CompletableFuture;

public class CodecConversion<T> implements KvOperationsMiddleware<T, CacheResponse<T>> {
    private final CodecProvider codecProvider;

    public CodecConversion(CodecProvider _codecProvider) {
        this.codecProvider = _codecProvider;
    }

    @Override
    public CompletableFuture<CacheResponse<T>> execute(
            String key,
            T value,
            FunctionSet<String, T, CompletableFuture<CacheResponse<T>>> kvOperation) {

        Codec<T> codec = codecProvider
                .getCodec((Class<T>) value.getClass());

//        kvOperation.apply(key, codec.encode(value), null)

        return null;
    }
}
