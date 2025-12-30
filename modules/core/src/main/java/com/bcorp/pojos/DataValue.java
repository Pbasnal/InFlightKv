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
}

