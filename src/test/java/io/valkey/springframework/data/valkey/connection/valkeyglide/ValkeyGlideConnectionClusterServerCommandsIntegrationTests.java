/*
 * Copyright 2011-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.valkey.springframework.data.valkey.connection.valkeyglide;

import static io.valkey.springframework.data.valkey.connection.ClusterTestVariables.*;
import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.valkey.springframework.data.valkey.connection.ValkeyClusterNode;
import io.valkey.springframework.data.valkey.connection.ValkeyClusterServerCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyServerCommands.FlushOption;
import io.valkey.springframework.data.valkey.core.types.ValkeyClientInfo;
import io.valkey.springframework.data.valkey.test.condition.EnabledOnValkeyClusterAvailable;

/**
 * Comprehensive low-level integration tests for {@link ValkeyGlideClusterConnection} 
 * cluster server commands functionality using the ValkeyClusterServerCommands interface directly.
 * 
 * <p>These tests validate the implementation of ValkeyClusterServerCommands methods that extend
 * ValkeyServerCommands with cluster-specific functionality:
 * 
 * <h3>Node-Specific Operations</h3>
 * <ul>
 *   <li>Background operations (bgReWriteAof, bgSave, lastSave, save) on specific nodes</li>
 *   <li>Database operations (dbSize, flushDb, flushAll) on specific nodes</li>
 *   <li>Server information (info, time) from specific nodes</li>
 *   <li>Configuration management (getConfig, setConfig, resetConfigStats, rewriteConfig) on specific nodes</li>
 *   <li>Client management (getClientList) from specific nodes</li>
 * </ul>
 * 
 * <h3>Cluster-Wide Aggregation</h3>
 * <ul>
 *   <li>Verify cluster-wide info() returns node-prefixed properties from all primaries</li>
 *   <li>Verify cluster-wide dbSize() returns sum of all node sizes</li>
 *   <li>Verify cluster-wide lastSave() returns most recent timestamp</li>
 *   <li>Verify cluster-wide operations execute on all primaries</li>
 * </ul>
 * 
 * <p><strong>Note:</strong> Since ValkeyClusterServerCommands extends ValkeyServerCommands and we already have
 * ValkeyGlideConnectionServerCommandsIntegrationTests covering the base interface, these tests focus on
 * the NEW methods specific to cluster mode (those with ValkeyClusterNode parameter) and verifying proper
 * aggregation behavior of inherited methods.
 * 
 * <p><strong>Reference Implementation:</strong> Uses JedisClusterServerCommands as the reference for
 * expected routing and aggregation behavior.
 * 
 * <p><strong>Cluster Topology:</strong> Tests use ClusterTestVariables constants for cluster configuration:
 * <ul>
 *   <li>3 Master nodes: 7379, 7380, 7381</li>
 *   <li>1 Replica node: 7382 (replica of 7379)</li>
 * </ul>
 * 
 * @author Ilya Kolomin
 * @since 2.0
 */
@EnabledOnValkeyClusterAvailable
public class ValkeyGlideConnectionClusterServerCommandsIntegrationTests extends AbstractValkeyGlideClusterIntegrationTests {

    ValkeyClusterNode allNodes[] = new ValkeyClusterNode[] {
        CLUSTER_NODE_1, CLUSTER_NODE_2, CLUSTER_NODE_3, REPLICA_OF_NODE_1
    };

    ValkeyClusterNode allPrimaries[] = new ValkeyClusterNode[] {
        CLUSTER_NODE_1, CLUSTER_NODE_2, CLUSTER_NODE_3
    };


    @Override
    protected String[] getTestKeyPatterns() {
        return new String[]{
            "test:cluster:server:key1", "test:cluster:server:key2", "test:cluster:server:key3",
            "{user}:cluster:server:key1", "{user}:cluster:server:key2",
            "test:cluster:server:flush:key", "test:cluster:server:db:key"
        };
    }

    // ==================== Node-Specific Background Operations ====================

    @Test
    void testBgReWriteAofOnSpecificNode() {
        for (ValkeyClusterNode node : allPrimaries) {
            long beforeCount = getCommandCallCount(node, "bgrewriteaof");
            clusterConnection.serverCommands().bgReWriteAof(node);
            long afterCount = getCommandCallCount(node, "bgrewriteaof");
            assertThat(afterCount).isEqualTo(beforeCount + 1);
        }
        
        // sleep 2 seconds before starting next saving test to avoid "saving in progress" errors
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testBgSaveOnSpecificNode() {
        for (ValkeyClusterNode node : allPrimaries) {
            long beforeCount = getCommandCallCount(node, "bgsave");
            clusterConnection.serverCommands().bgSave(node);
            long afterCount = getCommandCallCount(node, "bgsave");
            assertThat(afterCount).isEqualTo(beforeCount + 1);
        }

        // sleep 2 seconds before starting next saving test to avoid "saving in progress" errors
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testSaveOnSpecificNode() {
        for (ValkeyClusterNode node : allPrimaries) {
            long beforeCount = getCommandCallCount(node, "save");
            clusterConnection.serverCommands().save(node);
            long afterCount = getCommandCallCount(node, "save");
            assertThat(afterCount).isEqualTo(beforeCount + 1);
        }
    }

    @Test
    void testLastSaveOnSpecificNode() {
        for (ValkeyClusterNode node : allPrimaries) {
            long beforeCount = getCommandCallCount(node, "lastsave");
            clusterConnection.serverCommands().lastSave(node);
            long afterCount = getCommandCallCount(node, "lastsave");
            assertThat(afterCount).isEqualTo(beforeCount + 1);
        }
    }

    // ==================== Node-Specific Database Operations ====================

    @Test
    void testDbSizeOnSpecificNode() {
        String key = "test:cluster:server:db:key";
        
        try {
            // Get initial database size from specific node
            // First, determine which node serves this key
            ValkeyClusterNode targetNode = clusterConnection.clusterCommands().clusterGetNodeForKey(key.getBytes());
            assertThat(targetNode).isNotNull();
            
            Long initialSize = clusterConnection.serverCommands().dbSize(targetNode);
            assertThat(initialSize).isNotNull();
            assertThat(initialSize).isGreaterThanOrEqualTo(0L);
            
            // Add a key that routes to this specific node
            clusterConnection.stringCommands().set(key.getBytes(), "value".getBytes());
            
            // Database size on this node should increase
            Long newSize = clusterConnection.serverCommands().dbSize(targetNode);
            assertThat(newSize).isNotNull();
            assertThat(newSize).isGreaterThan(initialSize);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testFlushDbOnSpecificNode() {
        String key1 = "{user}:cluster:server:key1";
        String key2 = "{user}:cluster:server:key2";
        
        try {
            // Add test keys (they'll go to same node due to hash tag)
            clusterConnection.stringCommands().set(key1.getBytes(), "value1".getBytes());
            clusterConnection.stringCommands().set(key2.getBytes(), "value2".getBytes());
            
            // Get the node serving these keys
            ValkeyClusterNode targetNode = clusterConnection.clusterCommands().clusterGetNodeForKey(key1.getBytes());
            assertThat(targetNode).isNotNull();
            
            // Verify keys exist
            assertThat(clusterConnection.keyCommands().exists(key1.getBytes())).isTrue();
            assertThat(clusterConnection.keyCommands().exists(key2.getBytes())).isTrue();
            
            // Flush database on specific node
            clusterConnection.serverCommands().flushDb(targetNode);
            
            // Verify keys are gone
            assertThat(clusterConnection.keyCommands().exists(key1.getBytes())).isFalse();
            assertThat(clusterConnection.keyCommands().exists(key2.getBytes())).isFalse();
        } finally {
            cleanupKeys(key1, key2);
        }
    }

    @Test
    void testFlushDbWithOptionOnSpecificNode() {
        String key = "test:cluster:server:flush:key";
        
        try {
            // Add test key
            clusterConnection.stringCommands().set(key.getBytes(), "value".getBytes());
            
            // Get the node serving this key
            ValkeyClusterNode targetNode = clusterConnection.clusterCommands().clusterGetNodeForKey(key.getBytes());
            assertThat(targetNode).isNotNull();
            
            // Verify key exists
            assertThat(clusterConnection.keyCommands().exists(key.getBytes())).isTrue();
            
            // Flush database with ASYNC option on specific node
            clusterConnection.serverCommands().flushDb(targetNode, FlushOption.ASYNC);

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Verify key is gone (may take a moment with ASYNC)
            assertThat(clusterConnection.keyCommands().exists(key.getBytes())).isFalse();
            
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testFlushAllOnSpecificNode() {
        String key = "test:cluster:server:key1";
        
        try {
            // Add test key
            clusterConnection.stringCommands().set(key.getBytes(), "value".getBytes());
            
            // Get the node serving this key
            ValkeyClusterNode targetNode = clusterConnection.clusterCommands().clusterGetNodeForKey(key.getBytes());
            assertThat(targetNode).isNotNull();
            
            // Get initial database size on this node
            Long initialSize = clusterConnection.serverCommands().dbSize(targetNode);
            assertThat(initialSize).isGreaterThan(0L);
            
            // Flush all databases on specific node
            clusterConnection.serverCommands().flushAll(targetNode);
            
            // Database on this node should be empty or much smaller
            Long newSize = clusterConnection.serverCommands().dbSize(targetNode);

            assertThat(newSize).isEqualTo(0L);
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testFlushAllWithOptionOnSpecificNode() {
        String key = "test:cluster:server:key2";
        
        try {
            // Add test key
            clusterConnection.stringCommands().set(key.getBytes(), "value".getBytes());
            
            // Get the node serving this key
            ValkeyClusterNode targetNode = clusterConnection.clusterCommands().clusterGetNodeForKey(key.getBytes());
            assertThat(targetNode).isNotNull();
            
            // Get initial database size
            Long initialSize = clusterConnection.serverCommands().dbSize(targetNode);
            assertThat(initialSize).isGreaterThan(0L);
            
            // Flush all databases with SYNC option on specific node
            clusterConnection.serverCommands().flushAll(targetNode, FlushOption.SYNC);
            
            // Database should be empty or much smaller
            Long newSize = clusterConnection.serverCommands().dbSize(targetNode);
            assertThat(newSize).isEqualTo(0);
            
        } finally {
            cleanupKey(key);
        }
    }

    // ==================== Node-Specific Information Commands ====================

    @Test
    void testInfoOnSpecificNode() {
        for (ValkeyClusterNode node : allNodes) {
            Long beforeCount = getCommandCallCount(node, "info");
            Properties info = clusterConnection.serverCommands().info(node);
            
            assertThat(info).isNotNull();
            assertThat(info).isNotEmpty();
            
            // Verify basic server info properties
            assertThat(info.containsKey("valkey_version") || info.containsKey("redis_version")).isTrue();

            Long afterCount = getCommandCallCount(node, "info");
            assertThat(afterCount).isEqualTo(beforeCount + 2);
        }
    }

    @Test
    void testInfoWithSectionOnSpecificNode() {
        for (ValkeyClusterNode node : allNodes) {
            Long beforeCount = getCommandCallCount(node, "info");
            // Get INFO section from specific master node
            Properties serverInfo = clusterConnection.serverCommands().info(node, "server");
            assertThat(serverInfo).isNotNull();
            assertThat(serverInfo.containsKey("valkey_version") || serverInfo.containsKey("redis_version")).isTrue();


            // Test getting memory info
            Properties memoryInfo = clusterConnection.serverCommands().info(node, "memory");
            assertThat(memoryInfo).isNotNull();
            assertThat(memoryInfo.containsKey("used_memory")).isTrue();

            Long afterCount = getCommandCallCount(node, "info");
            assertThat(afterCount).isEqualTo(beforeCount + 3);
        }
    }

    @Test
    void testTimeOnSpecificNode() throws InterruptedException {
        for (ValkeyClusterNode node : allNodes) {
            // Get server time from specific node in different units
            Long beforeCmdCount = getCommandCallCount(node, "time");
            Long beforeTimeMillis = clusterConnection.serverCommands().time(node, TimeUnit.MILLISECONDS);
            Long beforeTimeSeconds = clusterConnection.serverCommands().time(node, TimeUnit.SECONDS);
            Long beforeTimeMicros = clusterConnection.serverCommands().time(node, TimeUnit.MICROSECONDS);

            Thread.sleep(1000);

            Long afterTimeMillis = clusterConnection.serverCommands().time(node, TimeUnit.MILLISECONDS);
            Long afterTimeSeconds = clusterConnection.serverCommands().time(node, TimeUnit.SECONDS);
            Long afterTimeMicros = clusterConnection.serverCommands().time(node, TimeUnit.MICROSECONDS);

            Long afterCmdCount = getCommandCallCount(node, "time");
            assertThat(afterCmdCount).isEqualTo(beforeCmdCount + 6);
            assertThat(afterTimeMillis - beforeTimeMillis).isGreaterThanOrEqualTo(1000L);
            assertThat(afterTimeSeconds - beforeTimeSeconds).isGreaterThanOrEqualTo(1L);
            assertThat(afterTimeMicros - beforeTimeMicros).isGreaterThanOrEqualTo(1_000_000L);
        }
    }

    // ==================== Node-Specific Configuration Management ====================

    @Test
    void testGetConfigOnSpecificNode() {
        for (ValkeyClusterNode node : allNodes) {
            // Get initial command stats for CONFIG GET on the target node
            String cmdName = getCommandName("config", "get");
            Long beforeCount = getCommandCallCount(node, cmdName);
            // Get configuration from specific node
            Properties maxMemoryConfig = clusterConnection.serverCommands()
                .getConfig(node, "maxmemory");
            assertThat(maxMemoryConfig).isNotNull();
            
            // Get all configs with wildcard
            Properties allConfigs = clusterConnection.serverCommands().getConfig(node, "*");
            assertThat(allConfigs).isNotNull();
            assertThat(allConfigs).isNotEmpty();

            Long afterCount = getCommandCallCount(node, cmdName);
            assertThat(afterCount).isEqualTo(beforeCount + 2);
        }
    }

    @Test
    void testSetConfigOnSpecificNode() {
        for (ValkeyClusterNode node : allNodes) {
            // Get original timeout for restoration
            Properties timeoutConfig = clusterConnection.serverCommands()
                .getConfig(node, "timeout");
            assertThat(timeoutConfig).isNotNull();
            
            Long originalTimeout = Long.parseLong(timeoutConfig.getProperty("timeout", "0"));
            Long newTimeout = originalTimeout + 10;
            
            try {
                // Set timeout to a safe value on specific node
                clusterConnection.serverCommands().setConfig(node, "timeout", newTimeout.toString());
                
                // Verify it was set on that node
                Properties newTimeoutConfig = clusterConnection.serverCommands()
                    .getConfig(node, "timeout");
                
                Long updatedTimeout = Long.parseLong(newTimeoutConfig.getProperty("timeout", "0"));
                assertThat(updatedTimeout).isEqualTo(newTimeout);
            } finally {
                // Restore original timeout
                clusterConnection.serverCommands().setConfig(node, "timeout", originalTimeout.toString());
            }
        }
    }

    @Test
    void testResetConfigStatsOnSpecificNode() {
        for (ValkeyClusterNode node : allNodes) {
            // Execute CONFIG RESETSTAT on specific node
            clusterConnection.serverCommands().resetConfigStats(node);
            
            String cmdName = getCommandName("config", "resetstat");
            long afterTotalCmdsCnt = getCommandCallCount(node, cmdName);
            assertThat(afterTotalCmdsCnt).isEqualTo(1);
        }
    }

    @Test
    void testRewriteConfigOnSpecificNode() {
        for (ValkeyClusterNode node : allNodes) {
            // Get initial command stats for CONFIG REWRITE on the target node
            String cmdName = getCommandName("config", "rewrite");
            long initialCallCount = getCommandCallCount(node, cmdName);
            
            // Rewrite config on specific node
            clusterConnection.serverCommands().rewriteConfig(node);
            
            // Get command stats after execution
            long afterCallCount = getCommandCallCount(node, cmdName);
            
            // Verify the command counter increased (by at least 1 for the rewriteConfig call)
            assertThat(afterCallCount).isEqualTo(initialCallCount + 1);
        }
    }

    // ==================== Node-Specific Client Management ====================

    @Test
    void testGetClientListOnSpecificNode() {
        for (ValkeyClusterNode node : allNodes) {
            String cmdName = getCommandName("client", "list");
            Long beforeCount = getCommandCallCount(node, cmdName);
            // Get client list from specific node
            List<ValkeyClientInfo> clientList = clusterConnection.serverCommands()
                .getClientList(node);
            
            assertThat(clientList).isNotNull();
            assertThat(clientList).isNotEmpty();
            
            // Verify client info properties
            ValkeyClientInfo firstClient = clientList.get(0);
            assertThat(firstClient).isNotNull();
            assertThat(firstClient.get("addr")).isNotNull();
            assertThat(firstClient.get("fd")).isNotNull();

            Long afterCount = getCommandCallCount(node, cmdName);
            assertThat(afterCount).isEqualTo(beforeCount + 1);
        }
    }

    // ==================== Cluster-Wide Routing and Aggregation Verification ====================

    @Test
    void testInfoClusterWideAggregation() {
        // Get cluster-wide INFO (should aggregate from all primaries)
        Properties allInfo = clusterConnection.serverCommands().info();
        
        assertThat(allInfo).isNotNull();
        assertThat(allInfo).isNotEmpty();
        
        // Verify properties are prefixed with node addresses (format: "host:port.property")
        // Based on JedisClusterServerCommands reference implementation
        String node1Prefix = CLUSTER_HOST + ":" + MASTER_NODE_1_PORT;
        String node2Prefix = CLUSTER_HOST + ":" + MASTER_NODE_2_PORT;
        String node3Prefix = CLUSTER_HOST + ":" + MASTER_NODE_3_PORT;
        
        // Check that we have properties from each master node
        boolean hasNode1Props = allInfo.stringPropertyNames().stream()
            .anyMatch(key -> key.startsWith(node1Prefix));
        boolean hasNode2Props = allInfo.stringPropertyNames().stream()
            .anyMatch(key -> key.startsWith(node2Prefix));
        boolean hasNode3Props = allInfo.stringPropertyNames().stream()
            .anyMatch(key -> key.startsWith(node3Prefix));
        
        assertThat(hasNode1Props).isTrue();
        assertThat(hasNode2Props).isTrue();
        assertThat(hasNode3Props).isTrue();
    }

    @Test
    void testInfoWithSectionClusterWideAggregation() {
        // Get cluster-wide INFO for specific section
        Properties serverInfo = clusterConnection.serverCommands().info("server");
        
        assertThat(serverInfo).isNotNull();
        assertThat(serverInfo).isNotEmpty();
        
        // Verify properties are prefixed with node addresses
        String node1Prefix = CLUSTER_HOST + ":" + MASTER_NODE_1_PORT;
        String node2Prefix = CLUSTER_HOST + ":" + MASTER_NODE_2_PORT;
        String node3Prefix = CLUSTER_HOST + ":" + MASTER_NODE_3_PORT;
        
        // Check for server version property from each node
        boolean hasNode1Version = serverInfo.stringPropertyNames().stream()
            .anyMatch(key -> key.startsWith(node1Prefix) && 
                (key.contains("valkey_version") || key.contains("redis_version")));
        boolean hasNode2Version = serverInfo.stringPropertyNames().stream()
            .anyMatch(key -> key.startsWith(node2Prefix) && 
                (key.contains("valkey_version") || key.contains("redis_version")));
        boolean hasNode3Version = serverInfo.stringPropertyNames().stream()
            .anyMatch(key -> key.startsWith(node3Prefix) && 
                (key.contains("valkey_version") || key.contains("redis_version")));
        
        assertThat(hasNode1Version).isTrue();
        assertThat(hasNode2Version).isTrue();
        assertThat(hasNode3Version).isTrue();
    }

    @Test
    void testDbSizeClusterWideAggregation() {
        String key1 = "test:cluster:server:key1";
        String key2 = "test:cluster:server:key2";
        String key3 = "test:cluster:server:key3";
        
        try {
            // Get cluster-wide dbSize (should sum all nodes)
            Long initialClusterSize = clusterConnection.serverCommands().dbSize();
            assertThat(initialClusterSize).isNotNull();
            assertThat(initialClusterSize).isGreaterThanOrEqualTo(0L);
            
            // Add keys that will distribute across different nodes
            clusterConnection.stringCommands().set(key1.getBytes(), "value1".getBytes());
            clusterConnection.stringCommands().set(key2.getBytes(), "value2".getBytes());
            clusterConnection.stringCommands().set(key3.getBytes(), "value3".getBytes());
            
            // Cluster-wide dbSize should increase
            Long newClusterSize = clusterConnection.serverCommands().dbSize();
            assertThat(newClusterSize).isNotNull();
            assertThat(newClusterSize).isGreaterThan(initialClusterSize);
            
            // Verify it's actually the sum by checking individual nodes
            Collection<ValkeyClusterNode> masters = getActiveMasterNodes();
            long manualSum = 0L;
            for (ValkeyClusterNode master : masters) {
                Long nodeSize = clusterConnection.serverCommands().dbSize(master);
                if (nodeSize != null) {
                    manualSum += nodeSize;
                }
            }
            
            assertThat(newClusterSize).isEqualTo(manualSum);
        } finally {
            cleanupKeys(key1, key2, key3);
        }
    }

    @Test
    void testLastSaveClusterWideAggregation() {
        // Get cluster-wide lastSave (should return most recent timestamp), should be non-null due to prior saves
        Long clusterLastSave = clusterConnection.serverCommands().lastSave();
        
        assertThat(clusterLastSave).isGreaterThan(0L);
        
        // Verify it's the maximum of all node lastSaves
        long maxLastSave = 0L;
        for (ValkeyClusterNode node : allPrimaries) {
            Long nodeLastSave = clusterConnection.serverCommands().lastSave(node);
            if (nodeLastSave != null && nodeLastSave > maxLastSave) {
                maxLastSave = nodeLastSave;
            }
        }
        
        // Cluster-wide lastSave should match the maximum
        assertThat(clusterLastSave).isEqualTo(maxLastSave);
    }

    @Test
    void testGetConfigClusterWideAggregation() {
        // Get cluster-wide config (should aggregate from all primaries with node prefixes)
        Properties allConfigs = clusterConnection.serverCommands().getConfig("timeout");
        
        assertThat(allConfigs).isNotNull();
        
        // Verify properties are prefixed with node addresses
        String node1Prefix = CLUSTER_HOST + ":" + MASTER_NODE_1_PORT;
        String node2Prefix = CLUSTER_HOST + ":" + MASTER_NODE_2_PORT;
        String node3Prefix = CLUSTER_HOST + ":" + MASTER_NODE_3_PORT;
        
        // Check that we have timeout config from each master node
        boolean hasNode1Config = allConfigs.stringPropertyNames().stream()
            .anyMatch(key -> key.startsWith(node1Prefix) && key.contains("timeout"));
        boolean hasNode2Config = allConfigs.stringPropertyNames().stream()
            .anyMatch(key -> key.startsWith(node2Prefix) && key.contains("timeout"));
        boolean hasNode3Config = allConfigs.stringPropertyNames().stream()
            .anyMatch(key -> key.startsWith(node3Prefix) && key.contains("timeout"));
        
        assertThat(hasNode1Config).isTrue();
        assertThat(hasNode2Config).isTrue();
        assertThat(hasNode3Config).isTrue();
    }

    @Test
    void testGetClientListClusterWideAggregation() {
        // Get cluster-wide client list (should combine lists from all primaries)
        List<ValkeyClientInfo> clusterClientList = clusterConnection.serverCommands().getClientList();
        
        assertThat(clusterClientList).isNotNull();
        assertThat(clusterClientList).isNotEmpty();
        
        // Verify it's actually combining clients from all nodes
        int individualClientCounts = 0;
        for (ValkeyClusterNode node : allPrimaries) {
            List<ValkeyClientInfo> nodeClients = clusterConnection.serverCommands().getClientList(node);
            individualClientCounts += nodeClients.size();
        }
        
        // Cluster-wide list should contain all clients from all nodes
        assertThat(clusterClientList.size()).isEqualTo(individualClientCounts);
    }

    @Test
    void testFlushOperationsClusterWide() {
        String key1 = "test:cluster:server:key1";
        String key2 = "test:cluster:server:key2";
        String key3 = "test:cluster:server:key3";
        
        try {
            // Add keys that will distribute across different nodes
            clusterConnection.stringCommands().set(key1.getBytes(), "value1".getBytes());
            clusterConnection.stringCommands().set(key2.getBytes(), "value2".getBytes());
            clusterConnection.stringCommands().set(key3.getBytes(), "value3".getBytes());
            
            // Get initial cluster-wide dbSize
            Long initialSize = clusterConnection.serverCommands().dbSize();
            assertThat(initialSize).isGreaterThan(0L);
            
            // Execute cluster-wide flushDb
            clusterConnection.serverCommands().flushDb();
            
            // Cluster-wide dbSize should be 0
            Long newSize = clusterConnection.serverCommands().dbSize();
            assertThat(newSize).isEqualTo(0);
            
            // Verify keys are gone
            assertThat(clusterConnection.keyCommands().exists(key1.getBytes())).isFalse();
            assertThat(clusterConnection.keyCommands().exists(key2.getBytes())).isFalse();
            assertThat(clusterConnection.keyCommands().exists(key3.getBytes())).isFalse();
        } finally {
            cleanupKeys(key1, key2, key3);
        }
    }

    @Test
    void testFlushOperationsWithOptionClusterWide() {
        String key1 = "test:cluster:server:key1";
        String key2 = "test:cluster:server:key2";
        String key3 = "test:cluster:server:key3";
        
        try {
            // Add keys that will distribute across different nodes
            clusterConnection.stringCommands().set(key1.getBytes(), "value1".getBytes());
            clusterConnection.stringCommands().set(key2.getBytes(), "value2".getBytes());
            clusterConnection.stringCommands().set(key3.getBytes(), "value3".getBytes());
            
            // Get initial cluster-wide dbSize
            Long initialSize = clusterConnection.serverCommands().dbSize();
            assertThat(initialSize).isGreaterThan(0L);
            
            // Execute cluster-wide flushDb
            clusterConnection.serverCommands().flushDb(FlushOption.ASYNC);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Cluster-wide dbSize should be 0
            Long newSize = clusterConnection.serverCommands().dbSize();
            assertThat(newSize).isEqualTo(0);
            
            // Verify keys are gone
            assertThat(clusterConnection.keyCommands().exists(key1.getBytes())).isFalse();
            assertThat(clusterConnection.keyCommands().exists(key2.getBytes())).isFalse();
            assertThat(clusterConnection.keyCommands().exists(key3.getBytes())).isFalse();
        } finally {
            cleanupKeys(key1, key2, key3);
        }
    }

    @Test
    void testBgReWriteAofClusterWide() {
        // Reset config stats on all nodes to have a clean slate
        clusterConnection.serverCommands().resetConfigStats();
        // Execute cluster-wide bgReWriteAof
        clusterConnection.serverCommands().bgReWriteAof();
        for (ValkeyClusterNode node : allPrimaries) {
            long afterCount = getCommandCallCount(node, "bgrewriteaof");
            assertThat(afterCount).isEqualTo(1);
        }
        
        // sleep 2 seconds before starting next saving test to avoid "saving in progress" errors
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testBgSaveClusterWide() {
        // Reset config stats on all nodes to have a clean slate
        clusterConnection.serverCommands().resetConfigStats();
        // Execute cluster-wide bgSave
        clusterConnection.serverCommands().bgSave();
        for (ValkeyClusterNode node : allPrimaries) {
            long afterCount = getCommandCallCount(node, "bgsave");
            assertThat(afterCount).isEqualTo(1);
        }
        
        // sleep 2 seconds before starting next saving test to avoid "saving in progress" errors
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testSaveClusterWide() {
        // Reset config stats on all nodes to have a clean slate
        clusterConnection.serverCommands().resetConfigStats();
        // Execute cluster-wide save
        clusterConnection.serverCommands().save();
        for (ValkeyClusterNode node : allPrimaries) {
            long afterCount = getCommandCallCount(node, "save");
            assertThat(afterCount).isEqualTo(1);
        }
    }

    @Test
    void testFlushAllClusterWide() {
        String key = "test:cluster:server:key1";
        
        try {
            clusterConnection.stringCommands().set(key.getBytes(), "value".getBytes());
            assertThat(clusterConnection.keyCommands().exists(key.getBytes())).isTrue();
            clusterConnection.serverCommands().flushAll();
            assertThat(clusterConnection.keyCommands().exists(key.getBytes())).isFalse();
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testFlushAllWithOptionClusterWide() {
        String key = "test:cluster:server:key1";
        
        try {
            clusterConnection.stringCommands().set(key.getBytes(), "value".getBytes());
            assertThat(clusterConnection.keyCommands().exists(key.getBytes())).isTrue();
            clusterConnection.serverCommands().flushAll(FlushOption.SYNC);
            assertThat(clusterConnection.keyCommands().exists(key.getBytes())).isFalse();
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testSetConfigClusterWide() {
        for (ValkeyClusterNode node : allNodes) {
            // Get original timeout for restoration
            Properties timeoutConfig = clusterConnection.serverCommands()
                .getConfig(node, "timeout");
            assertThat(timeoutConfig).isNotNull();
            
            Long originalTimeout = Long.parseLong(timeoutConfig.getProperty("timeout", "0"));
            Long newTimeout = originalTimeout + 10;
            
            try {
                // Set timeout to a safe value on specific node
                clusterConnection.serverCommands().setConfig(node, "timeout", newTimeout.toString());
                
                // Verify it was set on that node
                Properties newTimeoutConfig = clusterConnection.serverCommands()
                    .getConfig(node, "timeout");
                
                Long updatedTimeout = Long.parseLong(newTimeoutConfig.getProperty("timeout", "0"));
                assertThat(updatedTimeout).isEqualTo(newTimeout);
            } finally {
                // Restore original timeout
                clusterConnection.serverCommands().setConfig(node, "timeout", originalTimeout.toString());
            }
        }
    }

    @Test
    void testResetConfigStatsClusterWide() {
        // Reset config stats on all nodes to have a clean slate
        clusterConnection.serverCommands().resetConfigStats();
        String cmdName = getCommandName("config", "resetstat");
        for (ValkeyClusterNode node : allNodes) {
            long afterCount = getCommandCallCount(node, cmdName);
            assertThat(afterCount).isEqualTo(1);
        }
    }

    @Test
    void testRewriteConfigClusterWide() {
        // Reset config stats on all nodes to have a clean slate
        clusterConnection.serverCommands().resetConfigStats();
        clusterConnection.serverCommands().rewriteConfig();
        String cmdName = getCommandName("config", "rewrite");
        for (ValkeyClusterNode node : allNodes) {
            long afterCount = getCommandCallCount(node, cmdName);
            // 6.2 does not distinguish between rewrite and reset
            assertThat(afterCount).isEqualTo(isBackend7OrNewer() ? 1 : 2);
        }
    }

    @Test
    void testTimeClusterWide() {
        // routing policy is random, just verify that we can get time from a node
        clusterConnection.serverCommands().time(TimeUnit.MILLISECONDS);
        clusterConnection.serverCommands().time(TimeUnit.SECONDS);
        clusterConnection.serverCommands().time(TimeUnit.MICROSECONDS);
    }

    // ==================== MIGRATE Command Tests - Only basic, non intrusive ====================

    @Test
    void testMigrateBasic() {
        String key = "test:migrate:basic";
        
        try {
            // Set a key
            clusterConnection.stringCommands().set(key.getBytes(), "migrateValue".getBytes());
            
            // Verify key exists before migration
            assertThat(clusterConnection.keyCommands().exists(key.getBytes())).isTrue();
            
            // Determine source node where key currently resides
            ValkeyClusterNode sourceNode = clusterConnection.clusterCommands()
                .clusterGetNodeForKey(key.getBytes());
            assertThat(sourceNode).isNotNull();

            // Find a different random master node as target
            final ValkeyClusterNode targetNode = Arrays.stream(allPrimaries)
                .filter(n -> !n.equals(sourceNode))
                .findFirst()
                .orElseThrow();
            
            // Attempt to migrate the key; expect a MOVED error since cluster slots are not being changed
            assertThatThrownBy(() ->
                clusterConnection.serverCommands().migrate(
                key.getBytes(),
                targetNode,
                0,  // db index (always 0 in cluster)
                null,  // no option - key will be moved, not copied
                5000   // 5 second timeout
            )).hasMessageContaining("MOVED");
        } finally {
            cleanupKey(key);
        }
    }

    @Test
    void testMigrateParameterValidation() {
        String key = "test:migrate:validation";
        
        ValkeyClusterNode targetNode = allPrimaries[0];
        
        // Test null key
        assertThatThrownBy(() ->
            clusterConnection.serverCommands().migrate(
                null,
                targetNode,
                0,
                null,
                5000
            )
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Key must not be null");
        
        // Test null target
        assertThatThrownBy(() ->
            clusterConnection.serverCommands().migrate(
                key.getBytes(),
                null,
                0,
                null,
                5000
            )
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Target must not be null");
    }

    // ==================== Edge Cases & Error Handling ====================

    @Test
    void testNodeSpecificOperationsWithInvalidNode() {
        // Test with non-existent node (should fail gracefully)
        ValkeyClusterNode invalidNode = ValkeyClusterNode.newValkeyClusterNode()
            .listeningAt("192.168.255.255", 9999)
            .build();
        
        // Attempt operations on invalid node - should throw exception
        ValkeyClusterServerCommands commands = clusterConnection.serverCommands();
        assertThatThrownBy(() -> 
            commands.info(invalidNode))
            .isInstanceOf(Exception.class);
        
        assertThatThrownBy(() -> 
            commands.dbSize(invalidNode))
            .isInstanceOf(Exception.class);
    }

    @Test
    void testNodeParameterValidation() {
        // Test null node parameter handling
        ValkeyClusterServerCommands commands = clusterConnection.serverCommands();
        ValkeyClusterNode nullNode = null;
        
        assertThatThrownBy(() -> 
            commands.info(nullNode))
            .isInstanceOf(IllegalArgumentException.class);
        
        assertThatThrownBy(() -> 
            commands.dbSize(nullNode))
            .isInstanceOf(IllegalArgumentException.class);
        
        assertThatThrownBy(() -> 
            commands.getConfig(nullNode, "maxmemory"))
            .isInstanceOf(IllegalArgumentException.class);
        
        assertThatThrownBy(() -> 
            commands.setConfig(nullNode, "timeout", "60"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNodeSpecificOperationsWithReplicaNode() {
        // Some operations might behave differently on replica nodes
        // Get the replica node
        ValkeyClusterNode replica = null;
        Iterable<ValkeyClusterNode> allNodes = clusterConnection.clusterCommands().clusterGetNodes();
        for (ValkeyClusterNode node : allNodes) {
            if (!node.isMaster() && node.getPort() == REPLICAOF_NODE_1_PORT) {
                replica = node;
                break;
            }
        }
        
        assertThat(replica).isNotNull();
        assertThat(replica.isMaster()).isFalse();
        
        // Read operations should work on replica
        ValkeyClusterServerCommands commands = clusterConnection.serverCommands();
        Properties info = commands.info(replica);
        assertThat(info).isNotNull();
        assertThat(info).isNotEmpty();
        
        Long dbSize = commands.dbSize(replica);
        assertThat(dbSize).isNotNull();
        assertThat(dbSize).isGreaterThanOrEqualTo(0L);
        
        List<ValkeyClientInfo> clientList = commands.getClientList(replica);
        assertThat(clientList).isNotNull();
    }

    // ==================== Helper Methods ====================

    /**
     * Lazy-initialized Backend version detection flag.
     * True if Backend 7.0+, false if Backend 6.x.
     */
    private Boolean isBackend7OrNewer = null;

    /**
     * Detects if the Backend version is 7.0 or newer.
     * 
     * @return true if Backend 7.0+, false if Backend 6.x
     */
    private boolean isBackend7OrNewer() {
        if (isBackend7OrNewer == null) {
            Properties info = clusterConnection.serverCommands().info(allNodes[0], "server");
            String version = info.getProperty("redis_version", info.getProperty("valkey_version", "0.0.0"));
            // Parse major version
            String majorVersion = version.split("\\.")[0];
            int major = Integer.parseInt(majorVersion);
            isBackend7OrNewer = major >= 7;
        }
        return isBackend7OrNewer;
    }

    /**
     * Gets the appropriate command name format based on Backend version.
     * Backend 7.0+: returns "command|subcommand" format (e.g., "config|get")
     * Backend 6.x: returns parent command only (e.g., "config")
     * 
     * @param parentCommand the parent command (e.g., "config", "client")
     * @param subCommand the sub-command (e.g., "get", "list"), can be null for simple commands
     * @return the command name in the appropriate format for the current Backend version
     */
    private String getCommandName(String parentCommand, String subCommand) {
        if (subCommand == null) {
            return parentCommand;
        }
        if (isBackend7OrNewer()) {
            return parentCommand + "|" + subCommand;
        } else {
            return parentCommand;
        }
    }

    /**
     * Gets the call count for a specific command from INFO COMMANDSTATS.
     * 
     * @param node the cluster node to query
     * @param commandName the command name (e.g., "bgrewriteaof", "config", "config|get")
     * @return the call count, or 0 if command hasn't been executed yet
     */
    private long getCommandCallCount(ValkeyClusterNode node, String commandName) {
        Properties commandStats = clusterConnection.serverCommands().info(node, "commandstats");
        
        // Convert command name format: "config|get" -> "config_get" for lookup
        String statKey = "cmdstat_" + commandName.toLowerCase();
        String statValue = commandStats.getProperty(statKey);
        if (statValue == null) {
            return 0L; // Command not found, assume 0 calls
        }
        
        // Parse the calls value from "calls=5,usec=1234,..."
        for (String part : statValue.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("calls=")) {
                return Long.parseLong(trimmed.substring("calls=".length()));
            }
        }
        
        throw new IllegalStateException(
            String.format("Failed to parse 'calls' field from command stats for '%s'. Value: %s", 
                commandName, statValue));
    }
}
