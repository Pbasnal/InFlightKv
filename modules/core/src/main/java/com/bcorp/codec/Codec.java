package com.bcorp.codec;


import com.bcorp.CacheResponse;
import com.bcorp.pojos.DataValue;

public interface Codec<T> {
    DataValue encode(T data);

    CacheResponse<T> decode(DataValue dataValue);
}

