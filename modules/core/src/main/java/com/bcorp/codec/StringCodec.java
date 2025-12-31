package com.bcorp.codec;

import com.bcorp.pojos.CachedDataValue;
import com.bcorp.pojos.RequestDataValue;

import java.nio.charset.StandardCharsets;

public class StringCodec implements Codec<String> {
    @Override
    public RequestDataValue encode(String data) {
        return RequestDataValue.fromString(data);
    }

    @Override
    public String decode(CachedDataValue value) {
        return new String(value.data(), StandardCharsets.UTF_8);
    }
}


