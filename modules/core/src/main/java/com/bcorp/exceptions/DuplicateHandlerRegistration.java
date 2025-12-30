package com.bcorp.exceptions;

public class DuplicateHandlerRegistration extends RuntimeException {
    public DuplicateHandlerRegistration(String s) {
        super(s);
    }
}
