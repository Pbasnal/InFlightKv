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

        LoadTestService loadTestService = new LoadTestService(config);

        try {
            logger.info("Starting load test with configuration: {}", config);
            LoadTestResults results = loadTestService.runLoadTest();

            logger.info("Load test completed. Results: {}", results);
            printResults(results);

        } catch (Exception e) {
            logger.error("Load test failed", e);
            System.exit(1);
        }
    }

    private static LoadTestConfig parseArgs(String[] args) {
        LoadTestConfig config = new LoadTestConfig();

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
                case "--help" -> {
                    printUsage();
                    return null;
                }
            }
        }

        return config;
    }

    private static void printUsage() {
        System.out.println("InFlightKv Load Test Application");
        System.out.println("Usage: java -jar load-test.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -t, --threads <count>        Number of concurrent threads (default: 10)");
        System.out.println("  -r, --requests <count>       Number of requests per thread (default: 100)");
        System.out.println("  -h, --host <host>            Target host (default: localhost)");
        System.out.println("  -p, --port <port>            Target port (default: 8080)");
        System.out.println("  -d, --duration <seconds>     Maximum test duration in seconds (default: 300)");
        System.out.println("  --help                       Show this help message");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -jar load-test.jar -t 20 -r 500 -h kv-cluster.com -p 8080");
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
}
