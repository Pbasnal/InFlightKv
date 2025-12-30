package com.bcorp.exceptions;

import com.fasterxml.jackson.core.JsonProcessingException;

public class JsonSerializationFailed extends RuntimeException {
    public JsonSerializationFailed(JsonProcessingException e) {
        super(e);
    }
}
