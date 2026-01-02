package com.bcorp.InFlightKv.service;

import com.bcorp.InFlightKv.config.ClusterConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ClusterService {

    private static final Logger logger = LoggerFactory.getLogger(ClusterService.class);

    @Autowired
    private ClusterConfiguration clusterConfiguration;

    @Value("${inflight-kv.node.id:node-1}")
    private String currentNodeId;

    @Value("${inflight-kv.node.name:inflight-kv-1}")
    private String currentNodeName;

    private final RestTemplate restTemplate = new RestTemplate();

    public Optional<ClusterConfiguration.NodeInfo> getNodeById(String nodeId) {
        return clusterConfiguration.getNodes().stream()
                .filter(node -> node.getId().equals(nodeId))
                .findFirst();
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public boolean isNodeHealthy(String nodeId) {
        Optional<ClusterConfiguration.NodeInfo> node = getNodeById(nodeId);
        if (node.isEmpty()) {
            logger.warn("Node {} not found in cluster configuration", nodeId);
            return false;
        }

        try {
            String healthUrl = node.get().getHealthUrl();
            logger.debug("Checking health of node {} at {}", nodeId, healthUrl);

            // Simple health check - you might want to parse the JSON response
            String response = restTemplate.getForObject(healthUrl, String.class);

            boolean isHealthy = response != null && response.contains("\"status\":\"UP\"");
            logger.debug("Node {} health check result: {}", nodeId, isHealthy);

            return isHealthy;
        } catch (RestClientException e) {
            logger.warn("Health check failed for node {}: {}", nodeId, e.getMessage());
            return false;
        }
    }

    public List<ClusterConfiguration.NodeInfo> getAllNodes() {
        return clusterConfiguration.getNodes();
    }

    public List<NodeHealthStatus> checkAllNodesHealth() {
        return clusterConfiguration.getNodes().stream()
                .map(node -> new NodeHealthStatus(node, isNodeHealthy(node.getId())))
                .collect(Collectors.toList());
    }

    public KeyRoutingResult routeKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        List<ClusterConfiguration.NodeInfo> nodes = clusterConfiguration.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return new KeyRoutingResult(
                    currentNodeId,
                    currentNodeName,
                    null,
                    -1,
                    null,
                    false
            );
        }

        // Calculate hash and determine target node index
        int nodeIndex = getNodeId(key);

        ClusterConfiguration.NodeInfo targetNode = nodes.get(nodeIndex);

        // Check if current node is the target node
        boolean shouldRedirect = !targetNode.getId().equals(currentNodeId);

        logger.debug("Key '{}' routed to node '{}' (index {}), redirect: {}",
                key, targetNode.getId(), nodeIndex, shouldRedirect);

        return new KeyRoutingResult(
                targetNode.getId(),
                targetNode.getName(),
                targetNode.getHost(),
                targetNode.getPort(),
                targetNode.getExternalUrl(),
                shouldRedirect
        );
    }

    private int getNodeId(String key) {
        return (key.hashCode() & 0x7fffffff) % clusterConfiguration.getNodes().size();
    }

    public boolean isKeyOwnedByCurrentNode(String key) {
        KeyRoutingResult result = routeKey(key);
        return !result.isShouldRedirect();
    }

    public static class NodeHealthStatus {
        private final ClusterConfiguration.NodeInfo node;
        private final boolean healthy;

        public NodeHealthStatus(ClusterConfiguration.NodeInfo node, boolean healthy) {
            this.node = node;
            this.healthy = healthy;
        }

        public ClusterConfiguration.NodeInfo getNode() {
            return node;
        }

        public boolean isHealthy() {
            return healthy;
        }

        @Override
        public String toString() {
            return "NodeHealthStatus{" +
                    "node=" + node.getId() +
                    ", healthy=" + healthy +
                    '}';
        }
    }
}