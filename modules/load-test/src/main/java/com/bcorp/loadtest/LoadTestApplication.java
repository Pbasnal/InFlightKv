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

        LoadTestConfig config = new LoadTestConfig();

        try {
            logger.info("Starting load test with configuration: {}", config);
            CounterLoadTestService counterService = new CounterLoadTestService(config);
            CounterLoadTestResults results = counterService.runCounterLoadTest();
            logger.info("Counter load test completed. Results: {}", results);
            printCounterResults(results);

        } catch (Exception e) {
            logger.error("Load test failed", e);
            System.exit(1);
        }
    }

    private static void printCounterResults(CounterLoadTestResults results) {
        System.out.println("\n=== Counter Load Test Results ===");
        System.out.println("Final Counter Value: " + results.getFinalCounterValue());
        System.out.println("Total Duration: " + results.getTotalDurationMs() + "ms");
        System.out.println("=================================\n");
    }
}
