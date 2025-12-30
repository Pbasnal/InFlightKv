package InFlightKv.kvengine;

import com.bcorp.api.CacheRequestMethod;

public interface CustomCacheRequestMethod extends CacheRequestMethod {
    record Patch() implements CustomCacheRequestMethod {}

    static CacheRequestMethod patch() {
        return new Patch();
    }
}
