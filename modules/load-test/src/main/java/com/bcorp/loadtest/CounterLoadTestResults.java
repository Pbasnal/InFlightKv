package com.bcorp.loadtest;

/**
 * Simple results for counter load test
 */
public class CounterLoadTestResults {
    private final long finalCounterValue;
    private final long totalDurationMs;

    public CounterLoadTestResults(long finalCounterValue, long totalDurationMs) {
        this.finalCounterValue = finalCounterValue;
        this.totalDurationMs = totalDurationMs;
    }

    public long getFinalCounterValue() {
        return finalCounterValue;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    @Override
    public String toString() {
        return "Counter Test Results: Final Value = " + finalCounterValue +
               ", Total Time = " + totalDurationMs + "ms";
    }
}
