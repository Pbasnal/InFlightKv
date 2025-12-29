package com.bcorp.codec;

import com.bcorp.CacheResponse;
import com.bcorp.pojos.DataValue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class LongCodec implements Codec<Long> {

    @Override
    public DataValue encode(Long data) {
        return new DataValue(ByteBuffer.allocate(Long.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(data)
                .array(),
                Long.class,
                System.currentTimeMillis(),
                0L);
    }

    @Override
    public CacheResponse<Long> decode(DataValue dataValue) {

        return new CacheResponse<>(ByteBuffer
                .wrap(dataValue.data())
                .order(ByteOrder.LITTLE_ENDIAN)
                .getLong(),
                dataValue.version());

    }
}
