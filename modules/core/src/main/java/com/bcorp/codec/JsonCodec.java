package com.bcorp.codec;

import com.bcorp.exceptions.JsonDecodingFailed;
import com.bcorp.exceptions.JsonEncodingFailed;
import com.bcorp.exceptions.JsonSerializationFailed;
import com.bcorp.pojos.DataValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

public class JsonCodec implements Codec<JsonNode> {
    ObjectMapper mapper = new ObjectMapper();

    @Override
    public DataValue encode(JsonNode data) {
        try {
            byte[] encodedData = mapper.writeValueAsBytes(data);
            return new DataValue(encodedData,
                    ObjectNode.class,
                    System.currentTimeMillis(),
                    0L);
        } catch (JsonProcessingException e) {
            throw new JsonEncodingFailed(e);
        }
    }

    @Override
    public JsonNode decode(DataValue dataValue) {

        try {
            return mapper.readTree(dataValue.data());
        } catch (IOException e) {
            throw new JsonDecodingFailed(e);
        }
    }

    public JsonNode fromString(String jsonBody) {
        try {
            return mapper.readTree(jsonBody);
        } catch (JsonProcessingException e) {
            throw new JsonDecodingFailed(e);
        }
    }

    public String toString(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new JsonSerializationFailed(e);
        }
    }
}
