package com.bcorp.InFlightKv.service;

public class KeyRoutingResult {
    private final String nodeId;
    private final String nodeName;
    private final String host;
    private final int port;
    private final String externalUrl;
    private final boolean shouldRedirect;

    public KeyRoutingResult(String nodeId, String nodeName, String host, int port,
                            String externalUrl, boolean shouldRedirect) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.host = host;
        this.port = port;
        this.externalUrl = externalUrl;
        this.shouldRedirect = shouldRedirect;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public boolean isShouldRedirect() {
        return shouldRedirect;
    }

    @Override
    public String toString() {
        return "KeyRoutingResult{" +
                "node='" + nodeId + '\'' +
                ", nodeName='" + nodeName + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", externalUrl='" + externalUrl + '\'' +
                ", shouldRedirect=" + shouldRedirect +
                '}';
    }
}
