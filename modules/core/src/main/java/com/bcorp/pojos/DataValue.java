package com.bcorp.pojos;

import java.nio.charset.StandardCharsets;

public record DataValue(
        byte[] data,
        Class<?> dataType,
        long lastAccessTimeMs,
        Long version
) {
    public static DataValue fromString(String str) {
        return new DataValue(str.getBytes(StandardCharsets.UTF_8),
                String.class,
                System.currentTimeMillis(),
                null);
    }

    public static DataValue createNewFrom(DataValue value) {
        return new DataValue(value.data(),
                value.dataType(),
                System.currentTimeMillis(),
                0L);
    }

    public static DataValue createUpdatedFrom(DataValue value) {
        return new DataValue(value.data(),
                value.dataType(),
                System.currentTimeMillis(),
                value.version() + 1);
    }
}

