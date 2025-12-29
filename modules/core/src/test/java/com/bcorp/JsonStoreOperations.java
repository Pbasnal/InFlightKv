package com.bcorp;

import com.bcorp.api.*;
import com.bcorp.codec.StringCodec;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.DataValue;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class JsonStoreOperations {
    ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testRandom() {
        Router router = new Router();
        router.addRoute(new CacheRoute(CacheRequestMethod.GET, String.class),
                new StringGetRequestHandler());
        router.addRoute(new CacheRoute(CacheRequestMethod.SET, String.class),
                new StringSetRequestHandler(new StringCodec()));

        KeyValueStore kvStore = new KeyValueStore();

        String key = "a";
        String value = "value";

        CacheRequest req = new CacheRequest(key,
                CacheRequestMethod.SET,
                Optional.of(value),
                Collections.emptyList());

        IHandleRequests<String> stringSetHandler = router.getHandler(new CacheRequest(key,
                        CacheRequestMethod.SET,
                        Optional.of(value),
                        Collections.emptyList()));

        CacheResponse<String> cacheResponse = stringSetHandler.handle(req, kvStore).join();

        assert "value".equals(cacheResponse.data());

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


    private record TypedKey<T>(String key) {
    }

    private interface Gen<T> {
        T rev(String val);

        String unrev(T data);
    }

    private static class StringGen implements Gen<String> {

        @Override
        public String rev(String val) {
            return "asdf" + val;
        }

        @Override
        public String unrev(String data) {
            return data + "asdf";
        }
    }

    private static class WrapperA {
        public int data1;
        public List<WrapperB> data2s;

        public WrapperA() {
        }

        public WrapperA(int _data1, List<Integer> _data2s) {
            this.data1 = _data1;
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
