package com.bcorp.loadtest;

/**
 * Results of a counter load test execution
 */
public class CounterLoadTestResults {
    private final long totalOperations;
    private final long successfulUpdates;
    private final long conflictRetries;
    private final int maxRetriesHit;
    private final long totalDurationMs;
    private final long finalCounterValue;

    public CounterLoadTestResults(long totalOperations, long successfulUpdates, long conflictRetries,
                                int maxRetriesHit, long totalDurationMs, long finalCounterValue) {
        this.totalOperations = totalOperations;
        this.successfulUpdates = successfulUpdates;
        this.conflictRetries = conflictRetries;
        this.maxRetriesHit = maxRetriesHit;
        this.totalDurationMs = totalDurationMs;
        this.finalCounterValue = finalCounterValue;
    }

    public long getTotalOperations() {
        return totalOperations;
    }

    public long getSuccessfulUpdates() {
        return successfulUpdates;
    }

    public long getConflictRetries() {
        return conflictRetries;
    }

    public int getMaxRetriesHit() {
        return maxRetriesHit;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public long getFinalCounterValue() {
        return finalCounterValue;
    }

    public double getSuccessRate() {
        return totalOperations > 0 ? (successfulUpdates * 100.0) / totalOperations : 0.0;
    }

    public double getOperationsPerSecond() {
        double seconds = totalDurationMs / 1000.0;
        return seconds > 0 ? totalOperations / seconds : 0.0;
    }

    public double getAverageRetriesPerUpdate() {
        return successfulUpdates > 0 ? (double) conflictRetries / successfulUpdates : 0.0;
    }

    @Override
    public String toString() {
        return "CounterLoadTestResults{" +
                "totalOperations=" + totalOperations +
                ", successfulUpdates=" + successfulUpdates +
                ", conflictRetries=" + conflictRetries +
                ", maxRetriesHit=" + maxRetriesHit +
                ", successRate=" + String.format("%.2f%%", getSuccessRate()) +
                ", totalDurationMs=" + totalDurationMs +
                ", operationsPerSecond=" + String.format("%.2f", getOperationsPerSecond()) +
                ", finalCounterValue=" + finalCounterValue +
                ", averageRetriesPerUpdate=" + String.format("%.2f", getAverageRetriesPerUpdate()) +
                '}';
    }
}
