package com.bcorp.pojos;

import java.nio.charset.StandardCharsets;

public record RequestDataValue(
        byte[] data,
        Class<?> dataType
) {
    public static RequestDataValue fromString(String str) {
        return new RequestDataValue(str.getBytes(StandardCharsets.UTF_8), String.class);
    }

    public static RequestDataValue createNewFrom(RequestDataValue value) {
        return new RequestDataValue(value.data(),
                value.dataType());
    }

    public static RequestDataValue createUpdatedFrom(RequestDataValue value) {
        return new RequestDataValue(value.data(),
                value.dataType());
    }
}

