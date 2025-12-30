package com.bcorp.codec;

import java.util.Map;

public class CodecProvider {
    private final Map<Class<?>, Codec<?>> allCodecs;

    public CodecProvider(Map<Class<?>, Codec<?>> allCodecs) {
        this.allCodecs = allCodecs;
    }

    public <C, T extends Codec<C>> T getCodec(Class<C> type) {
        return (T) allCodecs.get(type);
    }
}

