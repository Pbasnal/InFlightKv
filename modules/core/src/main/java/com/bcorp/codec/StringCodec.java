package com.bcorp.codec;

import com.bcorp.CacheResponse;
import com.bcorp.pojos.DataValue;

import java.nio.charset.StandardCharsets;

public class StringCodec implements Codec<String> {
    @Override
    public DataValue encode(String data) {
        return DataValue.fromString(data);
    }

    @Override
    public CacheResponse<String> decode(DataValue value) {
        return new CacheResponse<>(new String(value.data(), StandardCharsets.UTF_8),
                value.version()
        );
    }
}


