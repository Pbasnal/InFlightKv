package com.bcorp.loadtest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

/**
 * Main application class for load testing the InFlightKv cluster
 */
public class LoadTestApplication {

    private static final Logger logger = LoggerFactory.getLogger(LoadTestApplication.class);

    public static void main(String[] args) {
        logger.info("Starting InFlightKv Load Test Application");

        LoadTestConfig config = parseArgs(args);

        if (config == null) {
            printUsage();
            System.exit(1);
        }

        try {
            logger.info("Starting load test with configuration: {}", config);

            if (config.isCounterTest()) {
                CounterLoadTestService counterService = new CounterLoadTestService(config);
                CounterLoadTestResults results = counterService.runCounterLoadTest();
                logger.info("Counter load test completed. Results: {}", results);
                printCounterResults(results);
            } else {
                LoadTestService loadTestService = new LoadTestService(config);
                LoadTestResults results = loadTestService.runLoadTest();
                logger.info("Load test completed. Results: {}", results);
                printResults(results);
            }

        } catch (Exception e) {
            logger.error("Load test failed", e);
            System.exit(1);
        }
    }

    private static LoadTestConfig parseArgs(String[] args) {
        LoadTestConfig config = new LoadTestConfig();
        boolean isCounterTest = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--threads", "-t" -> {
                    if (i + 1 < args.length) {
                        config.setThreadCount(Integer.parseInt(args[++i]));
                    }
                }
                case "--requests", "-r" -> {
                    if (i + 1 < args.length) {
                        config.setRequestsPerThread(Integer.parseInt(args[++i]));
                    }
                }
                case "--host", "-h" -> {
                    if (i + 1 < args.length) {
                        config.setHost(args[++i]);
                    }
                }
                case "--port", "-p" -> {
                    if (i + 1 < args.length) {
                        config.setPort(Integer.parseInt(args[++i]));
                    }
                }
                case "--duration", "-d" -> {
                    if (i + 1 < args.length) {
                        config.setMaxDurationSeconds(Integer.parseInt(args[++i]));
                    }
                }
                case "--counter" -> {
                    isCounterTest = true;
                }
                case "--help" -> {
                    printUsage();
                    return null;
                }
            }
        }

        config.setCounterTest(isCounterTest);
        return config;
    }

    private static void printUsage() {
        System.out.println("InFlightKv Load Test Application");
        System.out.println("Usage: java -jar load-test.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -t, --threads <count>        Number of concurrent threads (default: 3)");
        System.out.println("  -r, --requests <count>       Number of requests per thread (default: 100)");
        System.out.println("  -h, --host <host>            Target host (default: localhost)");
        System.out.println("  -p, --port <port>            Target port (default: 8080)");
        System.out.println("  -d, --duration <seconds>     Maximum test duration in seconds (default: 300)");
        System.out.println("  --counter                    Run counter test mode (single shared counter)");
        System.out.println("  --help                       Show this help message");
        System.out.println();
        System.out.println("Test Modes:");
        System.out.println("  Default: Mixed operations (PUT, GET, PATCH, DELETE, GET_ALL)");
        System.out.println("  --counter: Counter updates with version conflict handling");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Mixed operations test");
        System.out.println("  java -jar load-test.jar -t 5 -r 200 -h localhost -p 8080");
        System.out.println("  # Counter test with retries");
        System.out.println("  java -jar load-test.jar --counter -t 10 -r 50");
    }

    private static void printResults(LoadTestResults results) {
        System.out.println("\n=== Load Test Results ===");
        System.out.println("Total Requests: " + results.getTotalRequests());
        System.out.println("Successful Requests: " + results.getSuccessfulRequests());
        System.out.println("Failed Requests: " + results.getFailedRequests());
        System.out.println("Success Rate: " + String.format("%.2f%%", results.getSuccessRate()));
        System.out.println("Total Duration: " + results.getTotalDurationMs() + "ms");
        System.out.println("Requests/Second: " + String.format("%.2f", results.getRequestsPerSecond()));
        System.out.println("Keys Created: " + results.getKeysCreated());
        System.out.println("Keys Currently Tracked: " + results.getCurrentKeyCount());
        System.out.println("========================\n");
    }

    private static void printCounterResults(CounterLoadTestResults results) {
        System.out.println("\n=== Counter Load Test Results ===");
        System.out.println("Total Operations: " + results.getTotalOperations());
        System.out.println("Successful Updates: " + results.getSuccessfulUpdates());
        System.out.println("Conflict Retries: " + results.getConflictRetries());
        System.out.println("Max Retries Hit: " + results.getMaxRetriesHit());
        System.out.println("Success Rate: " + String.format("%.2f%%", results.getSuccessRate()));
        System.out.println("Total Duration: " + results.getTotalDurationMs() + "ms");
        System.out.println("Operations/Second: " + String.format("%.2f", results.getOperationsPerSecond()));
        System.out.println("Final Counter Value: " + results.getFinalCounterValue());
        System.out.println("Average Retries per Update: " + String.format("%.2f", results.getAverageRetriesPerUpdate()));
        System.out.println("=================================\n");
    }
}
