package com.bcorp.loadtest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for running load tests with multiple threads
 */
public class LoadTestService {

    private static final Logger logger = LoggerFactory.getLogger(LoadTestService.class);

    private final LoadTestConfig config;
    private final ConcurrentSkipListSet<String> sharedKeys;
    private final KvStoreHttpClient httpClient;
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger successfulRequests = new AtomicInteger(0);
    private final AtomicInteger failedRequests = new AtomicInteger(0);
    private final AtomicInteger keysCreated = new AtomicInteger(0);

    public LoadTestService(LoadTestConfig config) {
        this.config = config;
        this.sharedKeys = new ConcurrentSkipListSet<>();
        this.httpClient = new KvStoreHttpClient(config.getBaseUrl());
    }

    public LoadTestResults runLoadTest() throws InterruptedException {
        logger.info("Starting load test with {} threads, {} requests per thread",
                   config.getThreadCount(), config.getRequestsPerThread());

        ExecutorService executor = Executors.newFixedThreadPool(config.getThreadCount());
        CountDownLatch latch = new CountDownLatch(config.getThreadCount());

        long startTime = System.currentTimeMillis();

        // Submit worker threads
        for (int i = 0; i < config.getThreadCount(); i++) {
            executor.submit(new LoadTestWorker(i, latch));
        }

        // Wait for completion or timeout
        boolean completed = latch.await(config.getMaxDurationSeconds(), TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (!completed) {
            logger.warn("Load test timed out after {} seconds", config.getMaxDurationSeconds());
        }

        return new LoadTestResults(
                totalRequests.get(),
                successfulRequests.get(),
                failedRequests.get(),
                endTime - startTime,
                keysCreated.get(),
                sharedKeys.size()
        );
    }

    /**
     * Worker thread that performs load testing operations
     */
    private class LoadTestWorker implements Runnable {

        private final int threadId;
        private final CountDownLatch latch;
        private final ThreadLocalRandom random = ThreadLocalRandom.current();

        public LoadTestWorker(int threadId, CountDownLatch latch) {
            this.threadId = threadId;
            this.latch = latch;
        }

        @Override
        public void run() {
            try {
                logger.debug("Thread {} starting load test", threadId);

                for (int i = 0; i < config.getRequestsPerThread(); i++) {
                    performRandomOperation();
                }

                logger.debug("Thread {} completed load test", threadId);
            } catch (Exception e) {
                logger.error("Thread {} encountered error", threadId, e);
            } finally {
                latch.countDown();
            }
        }

        private void performRandomOperation() {
            totalRequests.incrementAndGet();

            try {
                int operation = random.nextInt(100); // 0-99

                if (operation < 30) { // 30% - PUT (create new key)
                    performPutOperation();
                } else if (operation < 60) { // 30% - GET
                    performGetOperation();
                } else if (operation < 80) { // 20% - PATCH
                    performPatchOperation();
                } else if (operation < 95) { // 15% - DELETE
                    performDeleteOperation();
                } else { // 5% - GET ALL KEYS
                    performGetAllKeysOperation();
                }

                successfulRequests.incrementAndGet();

            } catch (Exception e) {
                logger.debug("Operation failed: {}", e.getMessage());
                failedRequests.incrementAndGet();
            }
        }

        private void performPutOperation() throws IOException {
            String key = generateUniqueKey();
            String value = generateRandomJsonValue();
            httpClient.put(key, value);
            sharedKeys.add(key);
            keysCreated.incrementAndGet();
            logger.trace("PUT: {} -> {}", key, value);
        }

        private void performGetOperation() throws IOException {
            String key = getRandomExistingKey();
            if (key != null) {
                httpClient.get(key);
                logger.trace("GET: {}", key);
            }
        }

        private void performPatchOperation() throws IOException {
            String key = getRandomExistingKey();
            if (key != null) {
                String value = generateRandomJsonValue();
                httpClient.patch(key, value);
                logger.trace("PATCH: {} -> {}", key, value);
            }
        }

        private void performDeleteOperation() throws IOException {
            String key = getRandomExistingKey();
            if (key != null) {
                httpClient.delete(key);
                sharedKeys.remove(key);
                logger.trace("DELETE: {}", key);
            }
        }

        private void performGetAllKeysOperation() throws IOException {
            List<KvStoreHttpClient.KeyNodeInfo> keys = httpClient.getAllKeys();
            logger.trace("GET ALL KEYS: {} keys returned", keys.size());
        }

        private String generateUniqueKey() {
            return "loadtest-key-" + threadId + "-" + System.nanoTime() + "-" + random.nextInt(1000000);
        }

        private String generateRandomJsonValue() {
            return "{\"value\": \"test-data-" + random.nextInt(1000000) + "\", \"thread\": " + threadId + ", \"timestamp\": " + System.currentTimeMillis() + "}";
        }

        private String getRandomExistingKey() {
            if (sharedKeys.isEmpty()) {
                return null;
            }

            // Get a random key from the shared set
            int index = random.nextInt(sharedKeys.size());
            int currentIndex = 0;
            for (String key : sharedKeys) {
                if (currentIndex == index) {
                    return key;
                }
                currentIndex++;
            }
            return null;
        }
    }
}
