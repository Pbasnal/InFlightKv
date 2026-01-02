package com.bcorp.loadtest;


public class LoadTestConfig {
    private int threadCount = 3;
    private int requestsPerThread = 100;
    private String host = "localhost";
    private int port = 8080;
    private int maxDurationSeconds = 300; // 5 minutes
    private boolean counterTest = true;

    public String getBaseUrl() {
        return "http://" + host + ":" + port;
    }

    @Override
    public String toString() {
        return "LoadTestConfig{" +
                "threadCount=" + threadCount +
                ", requestsPerThread=" + requestsPerThread +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", maxDurationSeconds=" + maxDurationSeconds +
                ", counterTest=" + counterTest +
                '}';
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public int getRequestsPerThread() {
        return requestsPerThread;
    }

    public void setRequestsPerThread(int requestsPerThread) {
        this.requestsPerThread = requestsPerThread;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public void setMaxDurationSeconds(int maxDurationSeconds) {
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public boolean isCounterTest() {
        return counterTest;
    }

    public void setCounterTest(boolean counterTest) {
        this.counterTest = counterTest;
    }


}
