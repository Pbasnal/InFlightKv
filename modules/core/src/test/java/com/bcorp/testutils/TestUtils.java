package com.bcorp.testutils;

import io.cucumber.core.runtime.CucumberExecutionContext;

import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class TestUtils {
    public static <T> T waitFuture(CompletableFuture<T> fut) {
        return assertDoesNotThrow(() -> fut.get(1, TimeUnit.SECONDS));
    }

    public static boolean waitFor(CountDownLatch latch, long timeInSec) {
        return assertDoesNotThrow(() -> latch.await(timeInSec, TimeUnit.SECONDS));
    }

    public static void assertIfThrows(Class<?> throwableClass, CucumberExecutionContext.ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
            if (e instanceof CompletionException || e instanceof ExecutionException) {
                assertInstanceOf(throwableClass, e.getCause());
            } else {
                assertInstanceOf(throwableClass, e);
            }
        }
    }

    public static CompletableFuture<Void>[] runInFutures(int numOfThreads,
                                                   int operationsPerThread,
                                                   BiConsumer<Integer, Integer> runnable,
                                                   ExecutorService executor) {
        CompletableFuture<Void>[] futures = new CompletableFuture[numOfThreads];
        // Submit concurrent operations
        for (int threadId = 0; threadId < numOfThreads; threadId++) {
            final int tId = threadId;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    runnable.accept(tId, operationsPerThread);
                } catch (Exception e) {
                    throw new RuntimeException("Thread " + tId + " failed", e);
                }
            }, executor);
            futures[threadId] = future;
        }
        return futures;
    }


}
