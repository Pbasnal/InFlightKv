package com.bcorp.exceptions;

public class DuplicateRouteDefinition extends RuntimeException {
    public DuplicateRouteDefinition(String message) {
        super(message);
    }
}
