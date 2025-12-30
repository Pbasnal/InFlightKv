package InFlightKv.kvengine;

import InFlightKv.handlers.JsonStringGetValueHandler;
import InFlightKv.handlers.JsonStringPutValueHandler;
import com.bcorp.api.*;
import com.bcorp.codec.CodecProvider;
import com.bcorp.codec.JsonCodec;
import com.bcorp.codec.StringCodec;
import com.bcorp.kvstore.KeyValueStore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class KeyValueStoreEngineConfiguration {

    @Bean
    public KeyValueStore keyValueStore() {
        return new KeyValueStore();
    }

    @Bean
    public HandlerResolver handlerResolver() {

        HandlerResolver handlerResolver = new HandlerResolver();

        handlerResolver.registerKeyOnlyHandler(CacheRequestMethod.GET, String.class,
                new JsonStringGetValueHandler(new JsonCodec()));

//        handlerResolver.registerKeyOnlyHandler(CacheRequestMethod.REMOVE, String.class,
//                new StringRemoveRequestHandlerHandler(new JsonCodec()));

        handlerResolver.registerKeyValueHandler(CacheRequestMethod.SET, String.class, String.class,
                new JsonStringPutValueHandler(new JsonCodec()));

        return handlerResolver;
    }

    @Bean
    public KeyValueStoreEngine keyValueStoreEngine(KeyValueStore keyValueStore, HandlerResolver handlerResolver) {
        return new KeyValueStoreEngine(keyValueStore, handlerResolver);
    }
}
