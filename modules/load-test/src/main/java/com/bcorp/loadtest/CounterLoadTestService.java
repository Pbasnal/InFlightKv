package com.bcorp.loadtest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Load test service specifically for testing counter updates with version conflicts
 */
public class CounterLoadTestService {

    private static final Logger logger = LoggerFactory.getLogger(CounterLoadTestService.class);

    private static final String COUNTER_KEY = "shared-counter";
    private static final int MAX_RETRIES = 10;
    private static final long RETRY_DELAY_MS = 10;

    private final LoadTestConfig config;
    private final KvStoreHttpClient httpClient;
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong successfulUpdates = new AtomicLong(0);
    private final AtomicLong conflictRetries = new AtomicLong(0);
    private final AtomicInteger maxRetriesHit = new AtomicInteger(0);

    public CounterLoadTestService(LoadTestConfig config) {
        this.config = config;
        this.httpClient = new KvStoreHttpClient(config.getBaseUrl());
    }

    public CounterLoadTestResults runCounterLoadTest() throws InterruptedException {
        logger.info("Starting counter load test with {} threads, {} updates per thread",
                   config.getThreadCount(), config.getRequestsPerThread());

        // Initialize the counter
        initializeCounter();

        ExecutorService executor = Executors.newFixedThreadPool(config.getThreadCount());
        CountDownLatch latch = new CountDownLatch(config.getThreadCount());

        long startTime = System.currentTimeMillis();

        // Submit worker threads
        for (int i = 0; i < config.getThreadCount(); i++) {
            executor.submit(new CounterUpdateWorker(i, latch));
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
            logger.warn("Counter load test timed out after {} seconds", config.getMaxDurationSeconds());
        }

        // Get final counter value
        long finalCounterValue = getFinalCounterValue();

        return new CounterLoadTestResults(
                totalOperations.get(),
                successfulUpdates.get(),
                conflictRetries.get(),
                maxRetriesHit.get(),
                endTime - startTime,
                finalCounterValue
        );
    }

    private void initializeCounter() {
        try {
            // Try to initialize counter to 0 with ifVersion = -1 to ensure only one client can create it
            httpClient.put(COUNTER_KEY, "{\"count\": 0}", -1L);
            logger.info("Initialized counter key '{}' to 0", COUNTER_KEY);
        } catch (Exception e) {
            logger.warn("Failed to initialize counter, it may already exist: {}", e.getMessage());
        }
    }

    private long getFinalCounterValue() {
        try {
            String response = httpClient.get(COUNTER_KEY);
            if (response != null) {
                // Simple JSON parsing to extract count value
                int countStart = response.indexOf("\"count\":");
                if (countStart != -1) {
                    int countEnd = response.indexOf("}", countStart);
                    if (countEnd != -1) {
                        String countStr = response.substring(countStart + 8, countEnd).trim();
                        return Long.parseLong(countStr);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get final counter value: {}", e.getMessage());
        }
        return -1; // Error value
    }

    /**
     * Worker thread that performs counter updates with retry logic
     */
    private class CounterUpdateWorker implements Runnable {

        private final int threadId;
        private final CountDownLatch latch;
        private final ThreadLocalRandom random = ThreadLocalRandom.current();

        public CounterUpdateWorker(int threadId, CountDownLatch latch) {
            this.threadId = threadId;
            this.latch = latch;
        }

        @Override
        public void run() {
            try {
                logger.debug("Counter worker thread {} starting", threadId);

                for (int i = 0; i < config.getRequestsPerThread(); i++) {
                    performCounterUpdate();
                }

                logger.debug("Counter worker thread {} completed", threadId);
            } catch (Exception e) {
                logger.error("Counter worker thread {} encountered error", threadId, e);
            } finally {
                latch.countDown();
            }
        }

        private void performCounterUpdate() {
            int retryCount = 0;
            boolean success = false;

            while (!success && retryCount < MAX_RETRIES) {
                totalOperations.incrementAndGet();

                try {
                    // Get current counter value
                    String currentValue = httpClient.get(COUNTER_KEY);
                    if (currentValue == null) {
                        logger.warn("Counter key not found, reinitializing...");
                        initializeCounter();
                        currentValue = "{\"count\": 0}";
                    }

                    // Parse current count
                    long currentCount = parseCounterValue(currentValue);

                    // Increment counter
                    long newCount = currentCount + 1;
                    String newValue = "{\"count\": " + newCount + "}";

                    // Try to update with new value
                    httpClient.put(COUNTER_KEY, newValue);

                    successfulUpdates.incrementAndGet();
                    success = true;

                    if (retryCount > 0) {
                        logger.debug("Thread {} succeeded after {} retries", threadId, retryCount);
                    }

                } catch (Exception e) {
                    if (isConflictException(e)) {
                        // Version conflict - retry
                        retryCount++;
                        conflictRetries.incrementAndGet();

                        if (retryCount < MAX_RETRIES) {
                            // Add small random delay before retry
                            try {
                                Thread.sleep(RETRY_DELAY_MS + random.nextInt(5));
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        } else {
                            maxRetriesHit.incrementAndGet();
                            logger.warn("Thread {} hit max retries ({}) for counter update", threadId, MAX_RETRIES);
                        }
                    } else {
                        // Other error - don't retry
                        logger.error("Thread {} failed with non-conflict error: {}", threadId, e.getMessage());
                        break;
                    }
                }
            }
        }

        private long parseCounterValue(String jsonValue) {
            try {
                int countStart = jsonValue.indexOf("\"count\":");
                if (countStart != -1) {
                    int countEnd = jsonValue.indexOf("}", countStart);
                    if (countEnd != -1) {
                        String countStr = jsonValue.substring(countStart + 8, countEnd).trim();
                        return Long.parseLong(countStr);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to parse counter value from: {}", jsonValue);
            }
            return 0; // Default to 0 if parsing fails
        }

        private boolean isConflictException(Exception e) {
            // Check if this is a 409 Conflict response
            String message = e.getMessage();
            return message != null && (message.contains("409") || message.contains("Conflict"));
        }
    }
}
