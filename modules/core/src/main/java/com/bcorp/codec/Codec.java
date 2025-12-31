package com.bcorp.codec;

import com.bcorp.pojos.CachedDataValue;
import com.bcorp.pojos.RequestDataValue;

public interface Codec<T> {
    RequestDataValue encode(T data);

    T decode(CachedDataValue requestDataValue);
}

