package com.bcorp.InFlightKv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration class for InFlightKv cluster setup.
 * Reads cluster node information from application-docker.yaml
 */
@Configuration
@ConfigurationProperties(prefix = "inflight-kv.cluster")
public class ClusterConfiguration {

    private List<NodeInfo> nodes;

    public List<NodeInfo> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeInfo> nodes) {
        this.nodes = nodes;
    }

    /**
     * Information about a cluster node
     */
    public static class NodeInfo {
        private String id;
        private String name;
        private String host;
        private int port;
        private String externalUrl;
        private String internalUrl;
        private String healthUrl;

        // Getters and setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
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

        public String getExternalUrl() {
            return externalUrl;
        }

        public void setExternalUrl(String externalUrl) {
            this.externalUrl = externalUrl;
        }

        public String getInternalUrl() {
            return internalUrl;
        }

        public void setInternalUrl(String internalUrl) {
            this.internalUrl = internalUrl;
        }

        public String getHealthUrl() {
            return healthUrl;
        }

        public void setHealthUrl(String healthUrl) {
            this.healthUrl = healthUrl;
        }

        @Override
        public String toString() {
            return "NodeInfo{" +
                    "id='" + id + '\'' +
                    ", name='" + name + '\'' +
                    ", host='" + host + '\'' +
                    ", port=" + port +
                    ", externalUrl='" + externalUrl + '\'' +
                    ", internalUrl='" + internalUrl + '\'' +
                    ", healthUrl='" + healthUrl + '\'' +
                    '}';
        }
    }
}
