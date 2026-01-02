package com.bcorp.InFlightKv.config;

import com.bcorp.codec.JsonCodec;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.kvstore.KvStoreClock;
import com.bcorp.kvstore.SystemClock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeyValueStoreConfiguration {

    @Bean
    public KvStoreClock kvStoreClock() {
        return new SystemClock();
    }

    @Bean
    public JsonCodec jsonCodec() {
        return new JsonCodec();
    }

    @Bean
    public KeyValueStore keyValueStore(KvStoreClock clock) {
        return new KeyValueStore(clock);
    }
}
