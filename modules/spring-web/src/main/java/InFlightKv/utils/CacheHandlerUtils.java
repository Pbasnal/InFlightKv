package InFlightKv.utils;

import com.bcorp.CacheError;
import com.bcorp.CacheErrorCode;
import com.bcorp.CacheResponse;
import com.bcorp.codec.JsonCodec;
import com.bcorp.exceptions.JsonDecodingFailed;
import com.bcorp.pojos.DataValue;
import com.fasterxml.jackson.databind.JsonNode;

public class CacheHandlerUtils {

    public static Either<JsonNode, CacheError> parseJsonString(String value, JsonCodec jsonCodec) {
        try {
            // user is allowed to store null/empty value in the cache
            JsonNode node = jsonCodec.fromString(value);
            return Either.success(node);
        } catch (JsonDecodingFailed e) {
            return Either.failed(new CacheError(CacheErrorCode.WRONG_DATA_TYPE, "Value is not a proper json string"));
        }
    }

    public static CacheResponse<String> handleCacheResponse(DataValue cacheResponse, JsonCodec jsonCodec) {

        if (cacheResponse == null) {
            return CacheResponse.notFound();
        }

        Either<JsonNode, CacheError> decodingResponse = decodeSetResponse(cacheResponse, jsonCodec);
        if (!decodingResponse.isSuccess()) {
            return CacheResponse.failure(decodingResponse.getErrorResponse());
        }

        Either<String, CacheError> serializingJsonData = serializeJsonNode(
                decodingResponse.getSuccessResponse(),
                jsonCodec);

        if (!serializingJsonData.isSuccess()) {
            return CacheResponse.failure(serializingJsonData.getErrorResponse());
        }

        return CacheResponse.success(serializingJsonData.getSuccessResponse(), cacheResponse.version());
    }

    public static Either<JsonNode, CacheError> decodeSetResponse(DataValue dataValue, JsonCodec jsonCodec) {
        try {
            JsonNode node = jsonCodec.decode(dataValue);
            return Either.success(node);
        } catch (JsonDecodingFailed e) {
            return Either.failed(new CacheError(CacheErrorCode.WRONG_DATA_TYPE, "Failed to decode data to json"));
        }
    }

    public static Either<String, CacheError> serializeJsonNode(JsonNode node, JsonCodec jsonCodec) {
        try {
            String jsonBody = jsonCodec.toString(node);
            return Either.success(jsonBody);
        } catch (JsonDecodingFailed e) {
            return Either.failed(new CacheError(CacheErrorCode.WRONG_DATA_TYPE, "Failed to serialize json data to string"));
        }
    }
}
