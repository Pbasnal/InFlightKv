package com.bcorp.loadtest;

/**
 * Results of a load test execution
 */
public class LoadTestResults {
    private final int totalRequests;
    private final int successfulRequests;
    private final int failedRequests;
    private final long totalDurationMs;
    private final int keysCreated;
    private final int currentKeyCount;

    public LoadTestResults(int totalRequests, int successfulRequests, int failedRequests,
                          long totalDurationMs, int keysCreated, int currentKeyCount) {
        this.totalRequests = totalRequests;
        this.successfulRequests = successfulRequests;
        this.failedRequests = failedRequests;
        this.totalDurationMs = totalDurationMs;
        this.keysCreated = keysCreated;
        this.currentKeyCount = currentKeyCount;
    }

    public int getTotalRequests() {
        return totalRequests;
    }

    public int getSuccessfulRequests() {
        return successfulRequests;
    }

    public int getFailedRequests() {
        return failedRequests;
    }

    public double getSuccessRate() {
        return totalRequests > 0 ? (successfulRequests * 100.0) / totalRequests : 0.0;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public double getRequestsPerSecond() {
        double seconds = totalDurationMs / 1000.0;
        return seconds > 0 ? totalRequests / seconds : 0.0;
    }

    public int getKeysCreated() {
        return keysCreated;
    }

    public int getCurrentKeyCount() {
        return currentKeyCount;
    }

    @Override
    public String toString() {
        return "LoadTestResults{" +
                "totalRequests=" + totalRequests +
                ", successfulRequests=" + successfulRequests +
                ", failedRequests=" + failedRequests +
                ", successRate=" + String.format("%.2f%%", getSuccessRate()) +
                ", totalDurationMs=" + totalDurationMs +
                ", requestsPerSecond=" + String.format("%.2f", getRequestsPerSecond()) +
                ", keysCreated=" + keysCreated +
                ", currentKeyCount=" + currentKeyCount +
                '}';
    }
}
