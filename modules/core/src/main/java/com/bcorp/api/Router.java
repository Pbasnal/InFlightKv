package com.bcorp.api;

import com.bcorp.exceptions.DuplicateRouteDefinition;
import com.bcorp.exceptions.HandlerNotFoundException;

import java.util.HashMap;
import java.util.Map;

public class Router {
    private final Map<CacheRoute, IHandleRequests<?>> handlersMap;

    public Router() {
        handlersMap = new HashMap<>();
    }

    public void addRoute(CacheRoute route, IHandleRequests<?> handler) {
        if (handlersMap.containsKey(route)) {
            throw new DuplicateRouteDefinition("Route " + route + " has been defined already");
        }
        handlersMap.put(route, handler);
    }

    public <T> IHandleRequests<T> getHandler(CacheRequest request) {

        Class<?> clazz = null;
        if (request.value().isPresent()) {
            clazz = request.value().get().getClass();
        } else {
            clazz = request.key().getClass();
        }

        CacheRoute route = new CacheRoute(request.method(), clazz);
        if (!handlersMap.containsKey(route)) {
            throw new HandlerNotFoundException("Route " + route + " has been defined already");
        }
        return (IHandleRequests<T>) handlersMap.get(route);
    }
}

