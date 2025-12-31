package com.bcorp.pojos;

import java.nio.charset.StandardCharsets;

public record CachedDataValue(
        byte[] data,
        Class<?> dataType,
        long lastAccessTimeMs,
        Long version
) {
    public static CachedDataValue fromString(String str, long timeMs) {
        return new CachedDataValue(str.getBytes(StandardCharsets.UTF_8),
                String.class,
                timeMs,
                null);
    }

    public static CachedDataValue createNewFrom(RequestDataValue value, long timeMs) {
        return new CachedDataValue(value.data(),
                value.dataType(),
                timeMs,
                0L);
    }

    public static CachedDataValue createUpdatedFrom(RequestDataValue value, long timeMs, long version) {
        return new CachedDataValue(value.data(),
                value.dataType(),
                timeMs,
                version);
    }
}