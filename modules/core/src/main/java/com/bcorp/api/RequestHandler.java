package com.bcorp.api;

public interface RequestHandler<R extends CacheRequest> {

    boolean supports(R request);

    void handle(R request);
}