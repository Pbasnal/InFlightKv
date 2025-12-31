package com.bcorp.api.testimplementation;

public record ResponseHolder<T>(
        T data,
        Long version,
        Integer errorCode
) {
    public static <T> ResponseHolder<T> success(T data, long version) {
        return new ResponseHolder<>(data, version, null);
    }

    public static <T> ResponseHolder<T> failure(int errorCode) {
        return new ResponseHolder<>(null, null, errorCode);
    }
}
