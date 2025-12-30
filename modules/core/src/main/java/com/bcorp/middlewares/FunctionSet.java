package com.bcorp.middlewares;

@FunctionalInterface
public interface FunctionSet<One, Two, Three> {
    Three apply(One one, Two two);
}
