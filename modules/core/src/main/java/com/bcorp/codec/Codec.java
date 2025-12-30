package com.bcorp.codec;

import com.bcorp.pojos.DataValue;

public interface Codec<T> {
    DataValue encode(T data);

    T decode(DataValue dataValue);
}

