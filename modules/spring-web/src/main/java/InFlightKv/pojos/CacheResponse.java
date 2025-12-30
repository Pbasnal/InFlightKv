package InFlightKv.pojos;

public record CacheResponse<T>(
        T data,
        Long version,
        CacheError error
) {
    public static <T> CacheResponse<T> success(T data, long version) {
        return new CacheResponse<>(data, version, null);
    }

    public static <T> CacheResponse<T> failure(CacheError error) {
        return new CacheResponse<>(null, null, error);
    }

    public static <T> CacheResponse<T> failure(CacheErrorCode errorCode, String message) {
        return new CacheResponse<>(null, null, new CacheError(errorCode, message));
    }

    public static <T> CacheResponse<T> notFound() {
        return new CacheResponse<>(null, null, new CacheError(CacheErrorCode.NOT_FOUND, "Key not found"));
    }
}

