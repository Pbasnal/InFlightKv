package com.bcorp.pojos;

import java.nio.charset.StandardCharsets;

public record DataValue(
        byte[] data,
//        DataType<?> dataTypeCode,
        Class<?> dataType,
        long lastAccessTimeMs,
        long version
) {
    public static DataValue fromString(String str) {
        return new DataValue(str.getBytes(StandardCharsets.UTF_8),
//                DataTypeCode.STRING,
                String.class,
//                new DataType<String>(String.class),
                System.currentTimeMillis(),
                0);
    }

//    public static DataValue fromBytes(byte[] bytes) {
//        return new DataValue(bytes,
//                new DataType(DataTypeCode.BYTES),
//                System.currentTimeMillis(),
//                0);
//    }
}

