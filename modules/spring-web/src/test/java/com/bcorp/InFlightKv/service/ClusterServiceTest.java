package com.bcorp.InFlightKv.service;

import com.bcorp.InFlightKv.config.ClusterConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ClusterServiceTest {

    @Mock
    private ClusterConfiguration clusterConfiguration;

    private ClusterService clusterService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup mock cluster configuration with 3 nodes
        List<ClusterConfiguration.NodeInfo> nodes = Arrays.asList(
            createNode("node-1", "inflight-kv-1", "inflight-kv-1", 8080, "http://inflight-kv-1:8080"),
            createNode("node-2", "inflight-kv-2", "inflight-kv-2", 8080, "http://inflight-kv-2:8080"),
            createNode("node-3", "inflight-kv-3", "inflight-kv-3", 8080, "http://inflight-kv-3:8080")
        );

        when(clusterConfiguration.getNodes()).thenReturn(nodes);

        clusterService = new ClusterService();
        // Use reflection to inject the mock configuration since it's private
        try {
            java.lang.reflect.Field field = ClusterService.class.getDeclaredField("clusterConfiguration");
            field.setAccessible(true);
            field.set(clusterService, clusterConfiguration);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ClusterConfiguration.NodeInfo createNode(String id, String name, String host, int port, String internalUrl) {
        ClusterConfiguration.NodeInfo node = new ClusterConfiguration.NodeInfo();
        // Use reflection to set private fields for testing
        try {
            setField(node, "id", id);
            setField(node, "name", name);
            setField(node, "host", host);
            setField(node, "port", port);
            setField(node, "internalUrl", internalUrl);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return node;
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    @Test
    void shouldRouteKeyToCorrectNode() {
        // Test routing for a specific key
        String testKey = "test-key-123";

        KeyRoutingResult result = clusterService.routeKey(testKey);

        assertNotNull(result);
        assertNotNull(result.getNodeId());
        assertNotNull(result.getNodeName());
        assertNotNull(result.getHost());
        assertEquals(8080, result.getPort());

        // Verify the node ID is one of our configured nodes
        assertTrue(Arrays.asList("node-1", "node-2", "node-3").contains(result.getNodeId()));
    }

    @Test
    void shouldReturnConsistentRoutingForSameKey() {
        String testKey = "consistent-key";

        KeyRoutingResult result1 = clusterService.routeKey(testKey);
        KeyRoutingResult result2 = clusterService.routeKey(testKey);

        // Same key should always route to same node
        assertEquals(result1.getNodeId(), result2.getNodeId());
        assertEquals(result1.getNodeName(), result2.getNodeName());
    }

    @Test
    void shouldThrowExceptionForNullKey() {
        assertThrows(IllegalArgumentException.class, () -> clusterService.routeKey(null));
    }

    @Test
    void shouldThrowExceptionForEmptyKey() {
        assertThrows(IllegalArgumentException.class, () -> clusterService.routeKey(""));
        assertThrows(IllegalArgumentException.class, () -> clusterService.routeKey("   "));
    }

    @Test
    void shouldNotRedirectWhenNoNodesConfigured() {
        when(clusterConfiguration.getNodes()).thenReturn(Arrays.asList());

        KeyRoutingResult result = clusterService.routeKey("test");
        assertFalse(result.isShouldRedirect());
    }

    @Test
    void shouldDistributeKeysAcrossAllNodes() {
        // Test with many keys to ensure distribution
        int testKeys = 1000;
        int[] nodeCounts = new int[3]; // node-1, node-2, node-3

        for (int i = 0; i < testKeys; i++) {
            String key = "distribution-test-" + i;
            KeyRoutingResult result = clusterService.routeKey(key);

            switch (result.getNodeId()) {
                case "node-1" -> nodeCounts[0]++;
                case "node-2" -> nodeCounts[1]++;
                case "node-3" -> nodeCounts[2]++;
            }
        }

        // Each node should get roughly 1/3 of the keys (with some variance due to hashing)
        for (int count : nodeCounts) {
            assertTrue(count > testKeys * 0.2, "Each node should get at least 20% of keys");
            assertTrue(count < testKeys * 0.5, "No node should get more than 50% of keys");
        }
    }
}
