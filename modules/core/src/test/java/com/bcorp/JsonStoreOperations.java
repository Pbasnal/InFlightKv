package com.bcorp;

import com.bcorp.api.*;
import com.bcorp.apiimpl.StringGetRequestHandlerHandler;
import com.bcorp.apiimpl.StringKeyJsonValueSetHandlerHandler;
import com.bcorp.apiimpl.StringKeyStringValueSetHandlerHandler;
import com.bcorp.codec.CodecProvider;
import com.bcorp.codec.JsonCodec;
import com.bcorp.codec.StringCodec;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.DataValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class JsonStoreOperations {
    ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testStagedProcessing() {
        String key = "key";
        String value = "value";
        KeyValueStore keyValueStore = new KeyValueStore();

        CodecProvider codecProvider = new CodecProvider(Map.of(
                String.class, new StringCodec()
        ));

        HandlerResolver handlerResolver = new HandlerResolver();
        handlerResolver.registerKeyOnlyHandler(CacheRequestMethod.get(), String.class, new StringGetRequestHandlerHandler(codecProvider));
        handlerResolver.registerKeyValueHandler(CacheRequestMethod.set(), String.class, String.class, new StringKeyStringValueSetHandlerHandler(codecProvider.getCodec(String.class)));

        KeyValueStoreEngine kvApi = new KeyValueStoreEngine(keyValueStore, handlerResolver);
        CacheResponse<String> setResponse = kvApi.setCache(key, value, null, CacheRequestMethod.set());

        CacheResponse<String> getResponse = (CacheResponse<String>) kvApi.getCache(key, CacheRequestMethod.get());

        assert setResponse.data().equals(getResponse.data());
    }

    @Test
    public void testJsonProcessing() throws JsonProcessingException {

        String key = "key";

        WrapperA wrappedData = new WrapperA(1, List.of(12, 13, 14, 15));
        String valueAsJsonStr = mapper.writeValueAsString(wrappedData);

        System.out.println(valueAsJsonStr);

        KeyValueStore keyValueStore = new KeyValueStore();
        CodecProvider codecProvider = new CodecProvider(Map.of(
                String.class, new StringCodec(),
                ObjectNode.class, new JsonCodec()
        ));

        HandlerResolver handlerResolver = new HandlerResolver();
        handlerResolver.registerKeyOnlyHandler(CacheRequestMethod.get(), String.class, new StringGetRequestHandlerHandler(codecProvider));
        handlerResolver.registerKeyValueHandler(CacheRequestMethod.set(), String.class, ObjectNode.class, new StringKeyJsonValueSetHandlerHandler(codecProvider.getCodec(ObjectNode.class)));
        KeyValueStoreEngine kvApi = new KeyValueStoreEngine(keyValueStore, handlerResolver);


        JsonNode node = mapper.readTree(valueAsJsonStr);
        CacheResponse<JsonNode> setResponse = kvApi.setCache(key, node, null, CacheRequestMethod.set());
        CacheResponse<JsonNode> getResponse = (CacheResponse<JsonNode>) kvApi.getCache(key, CacheRequestMethod.get());

        assert setResponse.data().equals(getResponse.data());

        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        WrapperA wrappedData2 = new WrapperA(2, null);
        valueAsJsonStr = mapper.writeValueAsString(wrappedData2);

        node = mapper.readTree(valueAsJsonStr);
        setResponse = kvApi.setCache(key, node, null, CacheRequestMethod.set());
        getResponse = (CacheResponse<JsonNode>) kvApi.getCache(key, CacheRequestMethod.get());

        WrapperA expectedObject = new WrapperA(2, List.of(12, 13, 14, 15));
        valueAsJsonStr = mapper.writeValueAsString(expectedObject);


        assert valueAsJsonStr.equals(mapper.writeValueAsString(getResponse.data()));
    }


    @Test
    public void testSetAndGetJsonData() throws JacksonException {

        WrapperA wrappedData = new WrapperA(1, List.of(12, 13, 14, 15));
        String jsonString = mapper.writeValueAsString(wrappedData);

        System.out.println(jsonString);

        KeyValueStore kvStore = new KeyValueStore();

        kvStore.set(DataKey.from("d1"),
                DataValue.fromString(jsonString),
                null).join();

        DataValue value = kvStore.get(DataKey.from("d1")).join();

        String storedJsonString = new String(value.data(), StandardCharsets.UTF_8);

        WrapperA storedWrappedA = mapper.readValue(storedJsonString, WrapperA.class);

        assert storedWrappedA.equals(wrappedData);
    }

    private static class WrapperA {
        public int data1;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public List<WrapperB> data2s;

        public WrapperA() {
        }

        public WrapperA(int _data1, List<Integer> _data2s) {
            this.data1 = _data1;

            if (_data2s == null) return;

            data2s = new ArrayList<>();
            for (int i = 0; i < _data2s.size(); i++) {
                data2s.add(new WrapperB(_data2s.get(i)));
            }
        }

        public boolean equals(WrapperA objA) {
            if (objA == null) return false;
            if (data1 != objA.data1) return false;
            if (data2s == null && objA.data2s == null) return true;
            if (data2s == null || objA.data2s == null || data2s.size() != objA.data2s.size()) return false;


            data2s.sort(Comparator.comparingInt(a -> a.data2));
            objA.data2s.sort(Comparator.comparingInt(a -> a.data2));

            boolean areData2sEqual = true;
            for (int i = 0; i < data2s.size(); i++) {
                areData2sEqual &= data2s.get(i).equals(objA.data2s.get(i));

            }
            return areData2sEqual;
        }
    }

    private static class WrapperB {
        public int data2;

        public WrapperB() {
        }

        public WrapperB(int _data2) {
            this.data2 = _data2;
        }

        public boolean equals(WrapperB objB) {
            if (objB == null) return false;
            return data2 == objB.data2;
        }
    }
}
