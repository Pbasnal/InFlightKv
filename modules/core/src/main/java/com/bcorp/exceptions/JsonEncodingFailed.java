package com.bcorp.exceptions;

import com.fasterxml.jackson.core.JsonProcessingException;

public class JsonEncodingFailed extends RuntimeException {
    public JsonEncodingFailed(JsonProcessingException e) {
        super(e);
    }
}
