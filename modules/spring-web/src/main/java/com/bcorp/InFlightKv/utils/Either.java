package com.bcorp.InFlightKv.utils;

import lombok.Getter;

@Getter
public class Either<T, R> {
    private final T successResponse;
    private final R errorResponse;

    private Either(T _successResponse, R _errorResponse) {
        this.successResponse = _successResponse;
        this.errorResponse = _errorResponse;
    }

    public static <T, R> Either<T, R> success(T response) {
        return new Either<>(response, null);
    }

    public static <T, R> Either<T, R> failed(R response) {
        return new Either<>(null, response);
    }

    public boolean isSuccess() {
        return successResponse != null;
    }
}
