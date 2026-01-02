package com.bcorp.InFlightKv.service;

import com.bcorp.InFlightKv.config.ClusterConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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

    public CompletableFuture<List<KeyNodeInfo>> getAllKeysFromCluster(boolean skipOtherNodes) {

        CompletableFuture<List<KeyNodeInfo>> keysOnThisNode = keyValueStoreService.getAllKeys()
                .thenApply(keys -> keys.stream()
                        .map(key -> new KeyNodeInfo(key.key(), clusterService.getCurrentNodeId()))
                        .collect(Collectors.toList()));

        if (skipOtherNodes) {
            return keysOnThisNode;
        }

        List<ClusterConfiguration.NodeInfo> nodes = clusterService.getAllNodes();
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

    /**
     * Fetches keys from a specific node
     *
     * @param node The node to fetch keys from
     * @return CompletableFuture containing the list of key-node associations from that node
     */
    private CompletableFuture<List<KeyNodeInfo>> fetchKeysFromNode(ClusterConfiguration.NodeInfo node) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String keysUrl = node.getInternalUrl() + "/kv?skipOtherNodes=true";
                logger.debug("Fetching keys from node {} at {}", node.getId(), keysUrl);

                ResponseEntity<List<KeyNodeInfo>> response = restTemplate.exchange(
                        keysUrl,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {
                        }
                );

                List<KeyNodeInfo> keys = response.getBody();
                logger.debug("Fetched {} keys from node {}", keys != null ? keys.size() : 0, node.getId());

                return Objects.requireNonNullElse(keys, Collections.emptyList());

            } catch (RestClientException e) {
                logger.warn("Failed to fetch keys from node {}: {}", node.getId(), e.getMessage());
                return Collections.emptyList();
            } catch (Exception e) {
                logger.error("Unexpected error fetching keys from node {}", node.getId(), e);
                return Collections.emptyList();
            }
        });
    }
}
