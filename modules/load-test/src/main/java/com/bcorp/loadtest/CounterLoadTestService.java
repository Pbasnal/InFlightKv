package com.bcorp.loadtest;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();
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

    private boolean initializeCounter(int i) {
        try {
            // Try to initialize counter to 0 with ifVersion = -1 to ensure only one client can create it
            String response = httpClient.put(COUNTER_KEY, "{\"count\": 1}", -1L);
            logger.info("Initialized counter key '{}' to 1", COUNTER_KEY);
            return true;
        } catch (Exception e) {
            logger.warn("Failed to initialize counter, it may already exist: {}", e.getMessage());
            return false;
        }
    }

    private long getFinalCounterValue() {
        try {
            String response = httpClient.get(COUNTER_KEY);
            if (response != null) {
                // Simple JSON parsing to extract count value
                return parseCounterValue(response).count;
            }
        } catch (Exception e) {
            logger.warn("Failed to get final counter value: {}", e.getMessage());
        }
        return -1; // Error value
    }

    private CounterData parseCounterValue(String jsonValue) {
        try {
            CacheResponse response = objectMapper.readValue(jsonValue, CacheResponse.class);
            CounterData counterData = objectMapper.readValue(response.data, CounterData.class);
            counterData.version = response.version;
            return counterData;

        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Worker thread that performs counter updates with retry logic
     */
    private class CounterUpdateWorker implements Runnable {

        private final int threadId;
        private final CountDownLatch latch;
        private final ThreadLocalRandom random = ThreadLocalRandom.current();

        private int numberOfSuccessfulOperations = 0;

        public CounterUpdateWorker(int threadId, CountDownLatch latch) {
            this.threadId = threadId;
            this.latch = latch;
        }

        @Override
        public void run() {
            try {
                logger.debug("Counter worker thread {} starting", threadId);

                int i = 0;
                while (numberOfSuccessfulOperations < config.getRequestsPerThread()) {
                    if (performCounterUpdate(threadId)) {
                        i++;
                    }
                }

                logger.info("Counter worker thread {} completed with " + numberOfSuccessfulOperations, threadId);
            } catch (Exception e) {
                logger.error("Counter worker thread {} encountered error", threadId, e);
            } finally {
                latch.countDown();
            }
        }

        private boolean performCounterUpdate(int threadId) {
            totalOperations.incrementAndGet();

            try {
                // Get current counter value
                String currentValue = httpClient.get(COUNTER_KEY);
                boolean isSuccess = false;
                if (currentValue == null) {
//                    logger.warn("Counter key not found, reinitializing...");
                    isSuccess = initializeCounter(threadId);
                } else {
                    // Parse current count
                    CounterData currentData = parseCounterValue(currentValue);

                    // Increment counter
                    long newCount = currentData.count + 1;
                    String newValue = "{\"count\": " + newCount + "}";
                    // Try to update with new value
                    CounterData updated = parseCounterValue(httpClient.put(COUNTER_KEY, newValue, currentData.version));
//                    logger.info("updating counter from "
//                            + objectMapper.writeValueAsString(currentData)
//                            + " to " + objectMapper.writeValueAsString(updated));

                    isSuccess = true;
                }
                if (isSuccess) {
                    successfulUpdates.incrementAndGet();
                    numberOfSuccessfulOperations++;
                }
                return true;

            } catch (Exception e) {
                conflictRetries.incrementAndGet();
                // Add small random delay before retry
                try {
                    Thread.sleep(RETRY_DELAY_MS + random.nextInt(5));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                return false;
            }
        }
    }


    private boolean isConflictException(Exception e) {
        // Check if this is a 409 Conflict response
        String message = e.getMessage();
        return message != null && (message.contains("409") || message.contains("Conflict"));
    }
}
