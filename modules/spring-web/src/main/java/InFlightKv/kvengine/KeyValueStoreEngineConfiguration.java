package InFlightKv.kvengine;

import InFlightKv.handlers.JsonStringGetValueHandler;
import InFlightKv.handlers.JsonStringSetValueHandler;
//import InFlightKv.handlers.JsonStringPutValueHandler;
import com.bcorp.api.*;
import com.bcorp.api.handlers.HandlerResolver;
import com.bcorp.codec.JsonCodec;
import com.bcorp.kvstore.KeyValueStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeyValueStoreEngineConfiguration {

    @Bean
    public KeyValueStore keyValueStore() {
        return new KeyValueStore();
    }

    @Bean
    public HandlerResolver handlerResolver() {

        HandlerResolver handlerResolver = new HandlerResolver();

        handlerResolver.registerKeyOnlyHandler(CacheRequestMethod.get(), String.class,
                new JsonStringGetValueHandler(new JsonCodec()));

//        handlerResolver.registerKeyOnlyHandler(CacheRequestMethod.REMOVE, String.class,
//                new StringRemoveRequestHandlerHandler(new JsonCodec()));

        handlerResolver.registerKeyValueHandler(CacheRequestMethod.set(), String.class, String.class,
                new JsonStringSetValueHandler(new JsonCodec(), false));

        // Patch is a custom defined type here which is not part of the core engine.
        handlerResolver.registerKeyValueHandler(CustomCacheRequestMethod.patch(), String.class, String.class,
                new JsonStringSetValueHandler(new JsonCodec(), true));

        return handlerResolver;
    }

    @Bean
    public KeyValueStoreEngine keyValueStoreEngine(KeyValueStore keyValueStore, HandlerResolver handlerResolver) {
        return new KeyValueStoreEngine(keyValueStore, handlerResolver);
    }
}
