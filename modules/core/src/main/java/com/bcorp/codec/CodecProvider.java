package com.bcorp.codec;

import com.bcorp.pojos.DataType;

import java.util.Map;

public class CodecProvider {
    //    private final Codec<?>[] codecsArray;
    private final Map<Class<?>, Codec<?>> allCodecs;

    public CodecProvider(Map<Class<?>, Codec<?>> allCodecs) {
        this.allCodecs = allCodecs;
//        codecsArray = new Codec<?>[DataTypeCode.values().length];
//
//        for (DataType<?> typeCode : allCodecs.keySet()) {
//            register(typeCode, allCodecs.get(typeCode));
//        }
//
//
//        for (int i = 0; i < codecsArray.length; i++) {
//            if (codecsArray[i] == null) {
//                DataTypeCode code = DataTypeCode.values()[i];
//                throw new CodecInitializationFailure("Codec not provided for " + code);
//            }
//        }
    }

//    private void register(DataType<?> typeCode, Codec<?> codec) {
//        if (codec == null) {
//            throw new InvalidCodecRegistration();
//        }
//
//        if (codecsArray[type.ordinal()] != null) {
//            throw new DuplicateCodecRegistration();
//        }
//
//        codecsArray[type.ordinal()] = codec;
//    }

    public <T> Codec<T> getCodec(Class<T> type) {
        return (Codec<T>) allCodecs.get(type);
    }
}

