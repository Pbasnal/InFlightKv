package com.bcorp.codec;


import com.bcorp.CacheResponse;
import com.bcorp.exceptions.JsonDecodingFailed;
import com.bcorp.exceptions.JsonEncodingFailed;
import com.bcorp.pojos.DataValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public interface Codec<T> {
    DataValue encode(T data);

    T decode(DataValue dataValue);
}

