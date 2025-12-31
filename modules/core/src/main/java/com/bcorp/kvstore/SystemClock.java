package com.bcorp.kvstore;

public class SystemClock implements KvStoreClock {

    @Override
    public long currentTimeMs() {
        return System.currentTimeMillis();
    }
}
