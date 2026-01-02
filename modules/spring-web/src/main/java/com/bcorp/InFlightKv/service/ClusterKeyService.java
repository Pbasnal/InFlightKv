package com.bcorp.InFlightKv.service;

import com.bcorp.InFlightKv.config.ClusterConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for fetching keys from all nodes in the cluster
 */
@Service
public class ClusterKeyService {

    /**
     * Represents a key and the node it belongs to
     */
    public record KeyNodeInfo(String key, String node) {
    }

    private static final Logger logger = LoggerFactory.getLogger(ClusterKeyService.class);

    @Autowired
    private ClusterService clusterService;

    @Autowired
    private KeyValueStoreService keyValueStoreService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompletableFuture<List<KeyNodeInfo>> getAllKeysFromCluster(boolean skipOtherNodes) {

        CompletableFuture<List<KeyNodeInfo>> keysOnThisNode = keyValueStoreService.getAllKeys()
                .thenApply(keys -> keys.stream()
                        .map(key -> new KeyNodeInfo(key.key(), clusterService.getCurrentNodeId()))
                        .collect(Collectors.toList()));

        List<ClusterConfiguration.NodeInfo> nodes = clusterService.getAllNodes();
        if (skipOtherNodes || nodes == null) {
            return keysOnThisNode;
        }

        List<CompletableFuture<List<KeyNodeInfo>>> nodeFutures = nodes.stream()
                .map(this::fetchKeysFromNode)
                .collect(Collectors.toList());

        nodeFutures.add(keysOnThisNode);

        // Combine all futures and merge results
        return CompletableFuture.allOf(nodeFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<KeyNodeInfo> allKeys = new ArrayList<>();
                    for (CompletableFuture<List<KeyNodeInfo>> future : nodeFutures) {
                        try {
                            List<KeyNodeInfo> nodeKeys = future.join();
                            if (nodeKeys != null) {
                                allKeys.addAll(nodeKeys);
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to fetch keys from a node", e);
                        }
                    }
                    return allKeys;
                });
    }

    private CompletableFuture<List<KeyNodeInfo>> fetchKeysFromNode(ClusterConfiguration.NodeInfo node) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String keysUrl = node.getInternalUrl() + "/kv?skipOtherNodes=true";
                logger.debug("Fetching keys from node {} at {}", node.getId(), keysUrl);

                ResponseEntity<String> response = restTemplate.exchange(
                        keysUrl,
                        HttpMethod.GET,
                        null,
                        String.class
                );

                String content = response.getBody();
                logger.debug("Fetched keys response from node {} (length: {})", node.getId(),
                           content != null ? content.length() : 0);

                if (content == null || content.trim().isEmpty()) {
                    return Collections.emptyList();
                }

                return parseKeyNodeInfoList(content, node.getId());

            } catch (RestClientException e) {
                logger.warn("Failed to fetch keys from node {}: {}", node.getId(), e.getMessage());
                return Collections.emptyList();
            } catch (Exception e) {
                logger.error("Unexpected error fetching keys from node {}", node.getId(), e);
                return Collections.emptyList();
            }
        });
    }

    private List<KeyNodeInfo> parseKeyNodeInfoList(String content, String nodeId) {
        List<KeyNodeInfo> result = new ArrayList<>();
        String[] lines = content.split("\n");

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            try {
                JsonNode node = objectMapper.readTree(line);
                JsonNode keyNode = node.get("key");
                JsonNode nodeField = node.get("node");

                if (keyNode != null && !keyNode.isNull()) {
                    String key = keyNode.asText();
                    // Use the node from the response if available, otherwise use the provided nodeId
                    String nodeValue = (nodeField != null && !nodeField.isNull()) ?
                                     nodeField.asText() : nodeId;
                    result.add(new KeyNodeInfo(key, nodeValue));
                } else {
                    logger.warn("Skipping invalid key-node info line (missing key): {}", line);
                }
            } catch (Exception e) {
                logger.warn("Failed to parse key-node info from line: {} for node {}", line, nodeId, e);
            }
        }

        logger.debug("Parsed {} keys from node {}", result.size(), nodeId);
        return result;
    }
}
